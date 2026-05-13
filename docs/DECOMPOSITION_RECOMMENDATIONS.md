# Decomposition Recommendations

> **Scope**: Restructuring the 6-microservice internet banking platform for better domain alignment  
> **Date**: 2026-05-13  
> **Inputs**: [Business Capability Map](./BUSINESS_CAPABILITY_MAP.md), [Payment Gap Analysis](./PAYMENT_GAP_ANALYSIS.md)

---

## 1. Executive Summary

The current architecture has three structural problems:

1. **Core Banking is a monolith** — it owns accounts, users, transactions, and utility providers in a single service with a single database schema
2. **Fund Transfer and Utility Payment are redundant** — they are near-identical orchestration wrappers separated by payment type rather than business domain
3. **User data is split** across Core Banking and User Service with no synchronization

This document recommends **5 structural changes**, prioritized by business impact and implementation feasibility.

---

## 2. Recommendations

### R1: Merge Fund Transfer + Utility Payment → **Payment Service**

| Attribute            | Details                                                                                           |
|----------------------|--------------------------------------------------------------------------------------------------|
| **Action**           | **MERGE** `internet-banking-fund-transfer-service` + `internet-banking-utility-payment-service`    |
| **New Service**      | `internet-banking-payment-service`                                                                |
| **Business Case**    | Both services perform the same pattern: persist a local record, call core banking, update status. Separating by payment type adds operational overhead (2 DBs, 2 builds, 2 deployments) without isolation benefit. A unified Payment Service owns the "Payment Processing" capability and can share validation, idempotency, and error handling logic. |
| **Technical Approach** | 1. Create a new `internet-banking-payment-service` module<br>2. Combine `FundTransferController` and `UtilityPaymentController` under a single service<br>3. Merge the two MySQL schemas (`banking_core_fund_transfer_service` + `banking_core_utility_payment_service`) into `banking_payment_service`<br>4. Unify the DTO model — create a common `PaymentRequest` base with type-specific subtypes<br>5. Implement shared cross-cutting concerns: idempotency, validation, retry/compensation<br>6. Update API Gateway routes: `/fund-transfer/**` and `/payment/**` → both route to the new service<br>7. Decommission the two old services |
| **Impact**           | HIGH — reduces operational surface by 50% for payment services; enables shared payment logic       |
| **Feasibility**      | HIGH — minimal business logic to migrate; mostly structural consolidation                          |
| **Risk**             | LOW — both services are thin wrappers; merge is largely mechanical                                 |

### R2: Extract Account Service from Core Banking

| Attribute            | Details                                                                                          |
|----------------------|--------------------------------------------------------------------------------------------------|
| **Action**           | **SPLIT** account management out of `core-banking-service`                                        |
| **New Service**      | `account-service`                                                                                 |
| **Business Case**    | Account Management (BC-01) is a distinct bounded context: account lifecycle, balance inquiry, account types/status. Currently entangled with transaction processing in core banking. Separating it allows independent scaling (account reads are high-volume) and cleaner domain boundaries. |
| **Technical Approach** | 1. Extract `AccountController`, `AccountService`, `BankAccountEntity`, `BankAccountRepository`, `BankAccountMapper` into new `account-service`<br>2. Migrate `banking_core_account` and `banking_core_utility_account` tables to new `banking_account_service` schema<br>3. Expose REST APIs: CRUD for bank accounts, balance inquiry, utility account management<br>4. Transaction Service in core banking calls Account Service via Feign to read/update balances<br>5. Fund Transfer / Payment Service calls Account Service directly for pre-validation (fail-fast)<br>6. Consider an event-driven pattern: Account Service publishes `BalanceUpdated` events |
| **Impact**           | HIGH — establishes clear account ownership; enables independent scaling                            |
| **Feasibility**      | MEDIUM — requires careful handling of the transactional boundary between account updates and transaction recording |
| **Risk**             | MEDIUM — balance updates currently happen in same DB transaction as transaction recording; splitting requires distributed transaction handling (saga pattern or eventual consistency) |

### R3: Extract Transaction / Ledger Service from Core Banking

| Attribute            | Details                                                                                          |
|----------------------|--------------------------------------------------------------------------------------------------|
| **Action**           | **SPLIT** transaction ledger out of `core-banking-service`                                        |
| **New Service**      | `transaction-ledger-service`                                                                      |
| **Business Case**    | The Transaction Ledger (BC-04) is a distinct capability: recording financial events, providing transaction history, supporting reconciliation and audit. Currently, there is no read API for transactions — extracting it into its own service is an opportunity to add transaction history, statements, and reconciliation endpoints. |
| **Technical Approach** | 1. Extract `TransactionEntity`, `TransactionRepository` into new `transaction-ledger-service`<br>2. Migrate `banking_core_transaction` table to new `banking_ledger_service` schema<br>3. Expose APIs: record transaction, query by account, query by date range, reconciliation<br>4. Payment Service calls Ledger Service to record transactions after successful balance update<br>5. Consider event-sourcing: Ledger Service consumes `PaymentCompleted` events |
| **Impact**           | MEDIUM — enables transaction history, audit, and reconciliation capabilities                       |
| **Feasibility**      | MEDIUM — depends on R2 being completed first; requires distributed coordination                    |
| **Risk**             | MEDIUM — splitting the atomic debit+record operation requires saga or outbox pattern               |

### R4: Consolidate User Data Ownership

| Attribute            | Details                                                                                          |
|----------------------|--------------------------------------------------------------------------------------------------|
| **Action**           | **RESTRUCTURE** user data ownership between `core-banking-service` and `internet-banking-user-service` |
| **New Model**        | User Service becomes the **single source of truth** for all customer data                          |
| **Business Case**    | Currently, `core-banking-service` owns `banking_core_user` (firstName, lastName, email, NIC) and `user-service` owns a separate `user` table (authId, identification, status). This split-brain means neither service has the full customer picture, and there is no sync mechanism. A single User/Customer Service should own all customer data. |
| **Technical Approach** | 1. Extend `user-service` schema to include all fields from `banking_core_user` (firstName, lastName, email, NIC, status, authId)<br>2. User Service exposes APIs for user CRUD, lookup by NIC, lookup by authId<br>3. Core Banking stops managing users directly — calls User Service via Feign for user lookups<br>4. Migrate existing data from `banking_core_user` to the User Service schema<br>5. Remove `UserController`, `UserEntity`, `UserMapper` from core banking<br>6. Remove `BankingCoreRestClient.readUser()` from user service — it now owns user data locally |
| **Impact**           | HIGH — eliminates split-brain data; establishes clear customer data ownership                      |
| **Feasibility**      | HIGH — user service already has most of the infrastructure; primarily a data migration              |
| **Risk**             | LOW-MEDIUM — account-to-user FK in core banking needs to be replaced with user-id reference; requires data migration |

### R5: Create Shared Library for Cross-Cutting Concerns

| Attribute            | Details                                                                                          |
|----------------------|--------------------------------------------------------------------------------------------------|
| **Action**           | **EXTRACT** duplicated code into a shared library                                                 |
| **New Module**       | `banking-common` (Gradle composite build or published artifact)                                   |
| **Business Case**    | 13+ classes are duplicated across 3-4 services (see [Business Capability Map §4.1](./BUSINESS_CAPABILITY_MAP.md#41-code-duplication-across-services)). Changes must be applied in every copy, and drift has already occurred (GlobalExceptionHandler uses builder in core but constructor in fund-transfer). A shared library eliminates this duplication. |
| **Technical Approach** | 1. Create `banking-common` module with shared code:<br>&nbsp;&nbsp;— `BaseMapper<E,D>`<br>&nbsp;&nbsp;— `AuditAware`<br>&nbsp;&nbsp;— `GlobalExceptionHandler`<br>&nbsp;&nbsp;— `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`<br>&nbsp;&nbsp;— `AuditConfig`, `AuditorAwareConfig`<br>&nbsp;&nbsp;— `ErrorResponse`, `SimpleBankingGlobalException`<br>&nbsp;&nbsp;— `TransactionStatus` enum<br>&nbsp;&nbsp;— `CustomFeignClientConfiguration`<br>2. Publish as a Gradle included build or Maven local artifact<br>3. Update each service to depend on `banking-common` and remove local copies<br>4. Standardize `GlobalExceptionHandler` to use proper HTTP status codes |
| **Impact**           | MEDIUM — reduces maintenance burden; prevents drift; single place to fix bugs                      |
| **Feasibility**      | HIGH — mechanical refactoring with low risk                                                        |
| **Risk**             | LOW — no business logic changes; pure structural cleanup                                           |

---

## 3. Dependency Diagrams

### 3.1 Current Service Interactions

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │ HTTPS
       ▼
┌──────────────────────┐
│   API Gateway (8082) │──── JWT validation via Keycloak
│   Spring Cloud GW    │
└──────┬───────────────┘
       │ Routes by path prefix
       ├──────────────────────────┬────────────────────────┬─────────────────────┐
       │                          │                        │                     │
       ▼                          ▼                        ▼                     ▼
┌──────────────┐  ┌───────────────────┐  ┌────────────────────┐  ┌──────────────────┐
│ User Service │  │ Fund Transfer Svc │  │ Utility Payment Svc│  │ Core Banking Svc │
│   (8083)     │  │    (8084)         │  │     (8085)         │  │    (8092)        │
│              │  │                   │  │                    │  │                  │
│ DB: user     │  │ DB: fund_transfer │  │ DB: utility_payment│  │ DB: core_service │
└──────┬───────┘  └────────┬──────────┘  └─────────┬─────────┘  └──────────────────┘
       │                   │                       │                      ▲
       │                   │    Feign (sync)       │    Feign (sync)      │
       │                   └───────────────────────┴──────────────────────┘
       │                                  Feign (sync)                    │
       └──────────────────────────────────────────────────────────────────┘

Infrastructure:
┌──────────────────┐  ┌──────────────────┐  ┌──────────┐  ┌──────────┐
│ Service Registry │  │  Config Server   │  │  MySQL   │  │ Keycloak │
│  Eureka (8081)   │  │    (8090)        │  │  (3306)  │  │  (8080)  │
└──────────────────┘  └──────────────────┘  └──────────┘  └──────────┘
       ▲                        ▲                 ▲            ▲
       │ (all services register)│ (all services)  │ (4 schemas)│ (user svc + gw)
```

**Key observations:**
- All application services depend on Core Banking via synchronous Feign
- Core Banking is the single point of failure — if it goes down, all payment and user operations fail
- No asynchronous communication exists
- 4 separate MySQL schemas on 1 MySQL instance (no physical isolation)

### 3.2 Proposed Service Interactions (After R1–R5)

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │ HTTPS
       ▼
┌──────────────────────┐
│   API Gateway (8082) │──── JWT validation via Keycloak
│   Spring Cloud GW    │
└──────┬───────────────┘
       │
       ├─────────────────────┬────────────────────────┐
       │                     │                        │
       ▼                     ▼                        ▼
┌──────────────┐  ┌──────────────────┐  ┌──────────────────────┐
│ User Service │  │ Payment Service  │  │  Account Service     │
│   (8083)     │  │   (8084)         │  │     (8092)           │
│              │  │ [R1: merged]     │  │  [R2: extracted]     │
│ [R4: sole    │  │                  │  │                      │
│  user owner] │  │ • Fund Transfer  │  │ • Account CRUD       │
│              │  │ • Utility Payment│  │ • Balance inquiry     │
│ DB: user_svc │  │ • Idempotency    │  │ • Utility providers   │
│ (expanded)   │  │ • Validation     │  │                      │
└──────────────┘  │ • Compensation   │  │ DB: account_service   │
       ▲          │                  │  └───────────┬──────────┘
       │          │ DB: payment_svc  │              │
       │          └────────┬─────────┘              │
       │                   │                        │
       │                   │   ┌────────────────────┘
       │                   │   │
       │                   ▼   ▼
       │          ┌──────────────────────┐
       │          │  Ledger Service      │
       │          │  [R3: extracted]     │
       │          │                      │
       │          │ • Transaction record │
       │          │ • History / query    │
       │          │ • Reconciliation     │
       │          │                      │
       │          │ DB: ledger_service   │
       │          └──────────────────────┘
       │
       └──── Payment Svc calls User Svc for payer identity

Shared: banking-common [R5] — cross-cutting concerns library

Communication:
─────── Sync REST (Feign) for queries
═══════ Async events (future) for state changes
```

**Key improvements:**
- Core Banking monolith decomposed into Account Service + Ledger Service
- Payment Service owns all payment types with shared infrastructure
- User Service is the single source of truth for customer data
- Path to event-driven architecture (Account → publishes BalanceUpdated, Payment → publishes PaymentCompleted)

---

## 4. Prioritization Matrix

| Priority | Recommendation | Impact | Feasibility | Effort   | Dependencies    | Timeline      |
|----------|---------------|--------|-------------|----------|-----------------|---------------|
| **P1**   | R5: Shared Library         | MEDIUM | HIGH   | **Small** (1-2 weeks)  | None            | Immediate      |
| **P2**   | R1: Merge Payment Services | HIGH   | HIGH   | **Medium** (2-4 weeks) | R5 (optional)   | Month 1        |
| **P3**   | R4: Consolidate Users      | HIGH   | HIGH   | **Medium** (2-3 weeks) | None            | Month 1-2      |
| **P4**   | R2: Extract Account Svc    | HIGH   | MEDIUM | **Large** (4-6 weeks)  | R4              | Month 2-3      |
| **P5**   | R3: Extract Ledger Svc     | MEDIUM | MEDIUM | **Large** (4-6 weeks)  | R2              | Month 3-4      |

### Phased Execution Plan

**Phase 1 — Foundation (Weeks 1-4)**
- R5: Create `banking-common` shared library (eliminate code duplication)
- R1: Merge Fund Transfer + Utility Payment into Payment Service
- Fix critical payment bugs (double-deduction, add validation)

**Phase 2 — Domain Alignment (Weeks 5-10)**
- R4: Consolidate user data ownership in User Service
- R2: Extract Account Service from Core Banking
- Add idempotency and compensation patterns to Payment Service

**Phase 3 — Full Decomposition (Weeks 11-16)**
- R3: Extract Transaction Ledger Service
- Add transaction history and reconciliation APIs
- Introduce async messaging (Spring Cloud Stream / RabbitMQ) for event-driven patterns

---

## 5. Detailed Justification

### 5.1 Why Merge Payment Services (R1) Before Splitting Core Banking (R2-R3)?

1. **Lower risk**: Merging two simple services is safer than splitting a complex one
2. **Immediate benefit**: Reduces deployment surface, enables shared validation/idempotency
3. **Prepares for R2-R3**: A unified Payment Service is a better consumer of the future Account and Ledger services than two thin wrappers
4. **Fixes critical gaps**: Provides a single place to implement idempotency, currency support, and ISO 20022 alignment (see [Payment Gap Analysis](./PAYMENT_GAP_ANALYSIS.md))

### 5.2 Why Shared Library (R5) First?

1. **Zero business risk**: Pure structural refactoring
2. **Unblocks everything else**: Every subsequent recommendation benefits from shared exception handling, audit, and mapper infrastructure
3. **Fixes drift**: Standardizes the 4 different GlobalExceptionHandler implementations into one correct version
4. **Quick win**: Can be done in 1-2 weeks with high confidence

### 5.3 Why Not Split Core Banking All at Once?

Extracting both Account Service and Ledger Service simultaneously introduces distributed transaction complexity for the `debit account + record transaction` operation that currently runs in a single `@Transactional` method. The recommended approach:

1. First extract Account Service (R2) — the debit/credit operations move out, but ledger recording can temporarily stay as a synchronous call-back
2. Then extract Ledger Service (R3) — introduce the outbox pattern or saga to coordinate account updates with ledger entries
3. This staged approach limits the blast radius of each change

---

## 6. Migration Risk Register

| Risk                                         | Likelihood | Impact | Mitigation                                                          |
|----------------------------------------------|-----------|--------|---------------------------------------------------------------------|
| Data loss during schema migration            | LOW       | HIGH   | Use Flyway migrations; run in parallel (dual-write) before cutover   |
| Distributed transaction failures (R2+R3)     | MEDIUM    | HIGH   | Implement saga pattern with compensation; extensive integration testing |
| API contract breaking changes                | MEDIUM    | MEDIUM | Version APIs (v1 → v2); maintain backward-compatible endpoints during transition |
| Service discovery changes break routing      | LOW       | MEDIUM | Update Eureka registrations and Gateway routes atomically; blue-green deploy |
| Performance regression from added network hops| LOW      | MEDIUM | Profile latency before/after; use connection pooling and caching     |
| Team unfamiliarity with new patterns (sagas) | MEDIUM    | LOW    | Conduct design sessions; start with simpler compensating transactions |
