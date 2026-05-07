# Decomposition Recommendations

> **Repository:** ts-java-spring-boot-internet-banking-microservices  
> **Date:** 2026-05-07  
> **Based on:** [Business Capability Map](./BUSINESS_CAPABILITY_MAP.md) and [Payment Gap Analysis](./PAYMENT_GAP_ANALYSIS.md)

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Current Architecture Dependency Diagram](#2-current-architecture-dependency-diagram)
3. [Recommendations](#3-recommendations)
4. [Proposed Architecture Dependency Diagram](#4-proposed-architecture-dependency-diagram)
5. [Shared Library Strategy](#5-shared-library-strategy)
6. [Migration Sequencing](#6-migration-sequencing)
7. [Priority Matrix](#7-priority-matrix)

---

## 1. Executive Summary

The current architecture exhibits a **distributed monolith** pattern: fund-transfer and utility-payment services are thin orchestration wrappers with no meaningful business logic, while core-banking-service concentrates all critical capabilities (accounts, transactions, validation, user data). This creates a single point of failure, tight synchronous coupling, and duplicated code across services.

This document recommends **5 structural changes** organized into 3 phases:

| Phase | Recommendation | Summary |
|---|---|---|
| Phase 1 | **R-01**: Merge fund-transfer + utility-payment into a unified Payment Service | Eliminate redundant orchestrators; create a single payment domain service |
| Phase 1 | **R-02**: Extract a shared library for cross-cutting concerns | Remove 4x duplicated exception handling, audit, filter, and mapper code |
| Phase 2 | **R-03**: Extract Account Service from core-banking | Isolate account management as an independent domain service |
| Phase 2 | **R-04**: Extract Transaction Ledger Service from core-banking | Separate the ledger/recording concern from balance operations |
| Phase 3 | **R-05**: Introduce an Event Bus for async payment processing | Replace synchronous Feign chains with event-driven processing |

---

## 2. Current Architecture Dependency Diagram

```
                          Clients (Web/Mobile)
                                 |
                                 v
                    +========================+
                    |      API Gateway       |
                    |  (OAuth2 + Routing)    |
                    +========================+
                       |        |         |
          +------------+   +---+---+  +---+-----------+
          |                |       |  |               |
          v                v       |  v               |
+-------------------+ +--------+  | +------------------+
| User Service      | | Fund   |  | | Utility Payment  |
|                   | | Transfer|  | | Service          |
| - Registration    | | Service |  | |                  |
| - Keycloak sync   | |        |  | | - Orchestrate    |
| - Status mgmt    | | - Orch.|  | |   bill payments  |
+--------+----------+ +---+----+  | +--------+---------+
         |                 |      |          |
         |   Feign (sync)  |      |  Feign   |
         |                 v      |  (sync)  |
         |    +===================v==========v========+
         +--->|        Core Banking Service            |
              |                                        |
              |  - User lookup (by NIC)                |
              |  - Bank account CRUD                   |
              |  - Utility account registry             |
              |  - Fund transfer execution             |
              |  - Utility payment execution           |
              |  - Balance management (debit/credit)    |
              |  - Transaction recording (ledger)       |
              |  - Balance validation                   |
              +========================================+
                              |
                         [Single DB]
                    (accounts, users, transactions,
                     utility_accounts)

    +-------------------+     +-------------------+
    | Service Registry  |     | Config Server     |
    | (Eureka)          |     | (Spring Cloud)    |
    +-------------------+     +-------------------+
```

**Problem summary:**
- Core-banking is a **God Service** handling 8+ distinct responsibilities
- Fund-transfer and utility-payment are **structurally identical** thin wrappers
- All payment paths require **synchronous round-trips** to core-banking
- **No async messaging** despite RabbitMQ being in the Docker Compose stack

---

## 3. Recommendations

### R-01: Merge Fund-Transfer + Utility-Payment into a Unified Payment Service

#### Business Justification

Fund-transfer-service and utility-payment-service share:
- Identical architectural pattern (save local entity → Feign call to core-banking → update local status)
- Same cross-cutting infrastructure (audit, filters, Feign config, exception handling)
- Same dependency graph (both depend only on core-banking-service)
- Overlapping DTO definitions (both define `UtilityPaymentRequest`, `AccountResponse`, etc.)
- Same transaction status model (`PENDING`, `PROCESSING`, `SUCCESS`, `FAILED`)

They differ only in:
- The type of payment (fund transfer vs. utility bill)
- The specific core-banking endpoint called

Maintaining two services for this adds operational overhead (two deployments, two databases, two CI pipelines, two monitoring dashboards) without any benefit in independent scalability or team autonomy.

#### Technical Approach

1. **Create `internet-banking-payment-service`** as the merged service
2. **Unify the domain model:**
   ```
   PaymentEntity
   ├── id: Long
   ├── paymentType: PaymentType (FUND_TRANSFER | UTILITY_PAYMENT)
   ├── fromAccount: String
   ├── toAccount: String (nullable — not used for utility payments)
   ├── providerId: Long (nullable — not used for fund transfers)
   ├── amount: BigDecimal
   ├── currency: String (NEW — ISO 4217)
   ├── referenceNumber: String
   ├── transactionReference: String
   ├── status: TransactionStatus
   ├── idempotencyKey: String (NEW)
   └── extends AuditAware
   ```
3. **Merge controllers** under a shared `/api/v1/payments` base path:
   - `POST /api/v1/payments/fund-transfer` — fund transfer
   - `POST /api/v1/payments/utility-payment` — utility bill payment
   - `GET /api/v1/payments?type={FUND_TRANSFER|UTILITY_PAYMENT}` — unified payment history
   - `GET /api/v1/payments/{id}` — payment status by ID
4. **Single Feign client** to core-banking-service (merged from `BankingCoreFeignClient` and `BankingCoreRestClient`)
5. **Maintain backward compatibility** by keeping old gateway routes (`/fund-transfer/**`, `/utility-payment/**`) as aliases during transition
6. **Single database** for all payment records

#### Impact

| Metric | Before | After |
|---|---|---|
| Deployable units | 2 | 1 |
| Databases | 2 | 1 |
| Duplicated DTO classes | 10+ | 0 |
| CI pipelines | 2 | 1 |
| Lines of code | ~350 + ~300 | ~400 (estimated) |

---

### R-02: Extract a Shared Library for Cross-Cutting Concerns

#### Business Justification

The following code is duplicated across 3-4 services with only package name differences:

| Component | Duplicated In | Copies |
|---|---|---|
| `GlobalExceptionHandler` | core-banking, fund-transfer, utility-payment, user-service | 4 |
| `SimpleBankingGlobalException` | core-banking, fund-transfer, utility-payment, user-service | 4 |
| `ErrorResponse` | core-banking, fund-transfer, utility-payment, user-service | 4 |
| `AuditAware` | fund-transfer, utility-payment, user-service | 3 |
| `AuditConfig` + `AuditorAwareConfig` | fund-transfer, utility-payment, user-service | 3 |
| `AppAuthUserFilter` + `ApiRequestContext` + `ApiRequestContextHolder` | fund-transfer, utility-payment, user-service | 3 |
| `BaseMapper<E,D>` | core-banking, fund-transfer, utility-payment, user-service | 4 |
| `CustomFeignClientConfiguration` | fund-transfer, utility-payment | 2 |

A bug fix to `GlobalExceptionHandler` currently requires 4 coordinated PRs across 4 services.

#### Technical Approach

1. **Create `banking-common` Gradle module** (published as an internal library or included as a composite build)
2. **Extract into the shared library:**
   ```
   banking-common/
   ├── src/main/java/com/javatodev/finance/common/
   │   ├── exception/
   │   │   ├── GlobalExceptionHandler.java
   │   │   ├── SimpleBankingGlobalException.java
   │   │   ├── ErrorResponse.java
   │   │   ├── EntityNotFoundException.java
   │   │   └── InsufficientFundsException.java
   │   ├── audit/
   │   │   ├── AuditAware.java
   │   │   ├── AuditConfig.java
   │   │   └── AuditorAwareConfig.java
   │   ├── filter/
   │   │   ├── AppAuthUserFilter.java
   │   │   ├── ApiRequestContext.java
   │   │   └── ApiRequestContextHolder.java
   │   ├── mapper/
   │   │   └── BaseMapper.java
   │   └── feign/
   │       └── CustomFeignClientConfiguration.java
   ```
3. **Add as dependency** in each service's `build.gradle`:
   ```groovy
   implementation project(':banking-common')
   ```
4. **Remove duplicated classes** from each service after migration

#### Impact

| Metric | Before | After |
|---|---|---|
| Duplicated exception classes | 16 instances | 1 source of truth |
| Duplicated infrastructure classes | 20+ instances | 1 source of truth |
| Bug fix propagation | 4 services | 1 library |

---

### R-03: Extract Account Service from Core-Banking

#### Business Justification

Core-banking-service currently serves as the system of record for:
- Bank accounts (CRUD, status, balances)
- Utility accounts (registry of providers)
- Users (lookup by NIC)
- Transactions (ledger, balance updates)

Account management is a distinct business domain from transaction processing. Combining them in one service means:
- Account queries and payment processing compete for the same database connection pool
- Scaling account inquiry independently of transaction processing is impossible
- A bug in transaction processing can take down account inquiry (and vice versa)

#### Technical Approach

1. **Create `account-service`** owning:
   - `BankAccountEntity`, `UtilityAccountEntity`, `UserEntity` (core-banking user)
   - `AccountController` — bank account inquiry
   - `AccountService` — account read operations
   - `UserController` + `UserService` — NIC-based user lookup
   - Account status management (NEW capability — currently a gap)
   - Account opening/closure (NEW capability — currently a gap)
2. **Core-banking-service retains:**
   - `TransactionService` — fund transfer execution, utility payment execution
   - `TransactionEntity` — ledger entries
   - Balance update operations (debit/credit) — but now calls account-service to read/update balances
3. **Database split:**
   - Account-service gets its own database with `banking_core_account`, `banking_core_utility_account`, `banking_core_user` tables
   - Core-banking retains `banking_core_transaction` table with a foreign key replaced by an account number reference

#### Impact

| Metric | Before | After |
|---|---|---|
| Core-banking responsibilities | 8+ | 3 (transaction execution, balance ops, ledger) |
| Account inquiry availability | Coupled to transaction processing | Independent |
| Account management capabilities | Read-only | Full lifecycle (open, close, status management) |

---

### R-04: Extract Transaction Ledger Service from Core-Banking

#### Business Justification

After R-03, core-banking still handles both:
- Transaction **execution** (orchestrating debits and credits)
- Transaction **recording** (persisting ledger entries)

These are distinct concerns: execution needs strong consistency and low latency, while recording/reporting needs durability and query performance. Separating them enables:
- Immutable ledger with append-only semantics
- Independent scaling of transaction queries (statements, history)
- Foundation for event sourcing in the future

#### Technical Approach

1. **Create `transaction-ledger-service`** owning:
   - `TransactionEntity` — immutable ledger entries
   - `TransactionRepository` — persistence
   - Read APIs for transaction history, statements, and reconciliation
   - Transaction search and filtering capabilities (NEW)
2. **Rename remaining core-banking to `payment-execution-service`:**
   - Focuses solely on validating and executing payments
   - Calls account-service for balance checks and updates
   - Publishes transaction events (consumed by ledger-service for recording)
3. **Introduce event-based recording:**
   - Payment-execution-service publishes `TransactionCompleted` events to RabbitMQ
   - Transaction-ledger-service consumes events and persists ledger entries
   - This decouples execution latency from recording durability

#### Impact

| Metric | Before | After |
|---|---|---|
| Core-banking responsibilities | 3 (post R-03) | 1 (payment execution only) |
| Transaction history queries | Same DB as execution | Independent, optimized for reads |
| Ledger immutability | Mutable (same service writes and reads) | Append-only (separate write path) |

---

### R-05: Introduce Event Bus for Async Payment Processing

#### Business Justification

Currently, every payment follows a synchronous Feign chain:

```
Client → Gateway → Payment Service → Core Banking → (balance check) → (debit) → (credit) → (record)
```

If any step fails, the entire chain fails. There is no retry mechanism, no dead-letter queue, and no compensation logic. The Docker Compose stack already includes RabbitMQ, but no service uses it.

#### Technical Approach

1. **Phase 1 — Event notifications (non-breaking):**
   - After a payment completes, publish `PaymentCompleted` event to RabbitMQ
   - Introduce a `notification-service` that consumes events and sends email/SMS/push notifications (filling capability gap G-08)
   - Introduce `audit-service` that consumes events for compliance logging (filling gap G-11)

2. **Phase 2 — Async payment processing (breaking change):**
   - Payment Service accepts request, validates inputs, assigns idempotency key, persists with PENDING status, and returns `202 Accepted` with payment ID
   - Publishes `PaymentInitiated` event to RabbitMQ
   - Payment-execution-service consumes event, processes payment, publishes `PaymentCompleted` or `PaymentFailed`
   - Payment Service consumes result event and updates status
   - Client polls `GET /api/v1/payments/{id}` for status (or uses webhooks/SSE)

3. **Dead-letter queue** for failed payments — enables manual review and retry

```
Payment Service                    RabbitMQ                Payment Execution
     |                                |                          |
     |-- PaymentInitiated ----------->|                          |
     |                                |-- PaymentInitiated ----->|
     |                                |                          |-- validate
     |                                |                          |-- execute
     |                                |<-- PaymentCompleted -----|
     |<-- PaymentCompleted -----------|                          |
     |-- update status                |                          |
     |                                |                          |
     |                          +-----v------+                   |
     |                          | Dead Letter |                  |
     |                          | Queue       |                  |
     |                          +-------------+                  |
```

#### Impact

| Metric | Before | After |
|---|---|---|
| Payment processing model | Synchronous (blocking) | Asynchronous (non-blocking) |
| Failure handling | Silent failure, orphaned records | DLQ, retry, compensation |
| End-to-end latency (client) | Full processing time | Immediate acceptance + async processing |
| RabbitMQ utilization | 0% (deployed but unused) | Active |
| Notification capability | None | Event-driven notifications |

---

## 4. Proposed Architecture Dependency Diagram

### After Phase 1 (R-01 + R-02)

```
                          Clients (Web/Mobile)
                                 |
                                 v
                    +========================+
                    |      API Gateway       |
                    |  (OAuth2 + Routing)    |
                    +========================+
                       |        |         
          +------------+   +---+------------+
          |                |                
          v                v                
+-------------------+ +---------------------+
| User Service      | | Payment Service     |  <-- MERGED (R-01)
|                   | | (Fund Transfer +    |
| - Registration    | |  Utility Payment)   |
| - Keycloak sync   | |                     |
| - Status mgmt    | | Uses: banking-common |  <-- SHARED LIB (R-02)
+--------+----------+ +----------+----------+
         |                        |
         |   Feign (sync)         | Feign (sync)
         |                        |
         |    +-------------------v-----------+
         +--->|     Core Banking Service       |
              |                                |
              |  Uses: banking-common          |
              +================================+
                              |
                         [Single DB]
```

### After Phase 2 (R-03 + R-04)

```
                          Clients (Web/Mobile)
                                 |
                                 v
                    +========================+
                    |      API Gateway       |
                    +========================+
                       |        |         
          +------------+   +---+------------+
          |                |                
          v                v                
+-------------------+ +---------------------+
| User Service      | | Payment Service     |
+--------+----------+ +----+----------+-----+
         |                 |          |
         v                 v          v
+-------------------+ +----------+ +-------------------+
| Account Service   | | Payment  | | Transaction       |
|                   | | Execution| | Ledger Service     |
| - Bank accounts   | | Service  | |                   |
| - Utility accounts| |          | | - Immutable ledger|
| - User (NIC)     | | - Validate| | - Tx history      |
| - Account status  | | - Execute| | - Statements      |
+-------------------+ +----+-----+ +--------+----------+
         ^                  |                ^
         |   REST (sync)    |                |
         +------------------+   Events (async, RabbitMQ)
                                             |
                                    +--------+
                                    |
                               Payment Execution
                               publishes events
```

### After Phase 3 (R-05)

```
                          Clients (Web/Mobile)
                                 |
                                 v
                    +========================+
                    |      API Gateway       |
                    +========================+
                       |        |         
          +------------+   +---+----+
          |                |        |
          v                v        |
+-------------------+ +----v--------v-------+
| User Service      | | Payment Service     |
+--------+----------+ +----+-------+--------+
         |                 |       |
         v                 v       v (202 Accepted)
+-------------------+  +--v-----------+
| Account Service   |  |  RabbitMQ    |
+-------------------+  +--+-----------+
         ^                 |    |    |
         |                 v    |    v
         |   +-------------+   |  +-------------------+
         +---| Payment     |   |  | Notification Svc  |
             | Execution   |   |  | (email/SMS/push)  |
             | Service     |   |  +-------------------+
             +------+------+   |
                    |          v
                    |   +-------------------+
                    +-->| Transaction       |
                        | Ledger Service    |
                        +-------------------+

    +-------------------+     +-------------------+
    | Service Registry  |     | Config Server     |
    +-------------------+     +-------------------+
```

---

## 5. Shared Library Strategy

### Module: `banking-common`

| Package | Contents | Used By |
|---|---|---|
| `common.exception` | `GlobalExceptionHandler`, `SimpleBankingGlobalException`, `ErrorResponse`, `EntityNotFoundException`, `InsufficientFundsException` | All services |
| `common.audit` | `AuditAware`, `AuditConfig`, `AuditorAwareConfig` | Payment, User, Account, Ledger |
| `common.filter` | `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder` | Payment, User, Account |
| `common.mapper` | `BaseMapper<E,D>` | All services |
| `common.feign` | `CustomFeignClientConfiguration` | Payment, User |
| `common.dto` | Shared payment DTOs (`FundTransferRequest`, `UtilityPaymentRequest`, response DTOs) | Payment, Execution |
| `common.model` | Shared enums (`TransactionStatus`, `TransactionType`, `AccountStatus`, `AccountType`) | All services |

### Versioning Strategy

- Use **semantic versioning** (e.g., `1.0.0`, `1.1.0`, `2.0.0`)
- Publish to an internal Maven/Gradle repository or use Gradle composite builds
- Services pin to a specific version; bump explicitly when ready
- Breaking changes require major version bump and coordinated rollout

---

## 6. Migration Sequencing

### Phase 1: Consolidate (Sprints 1-4)

| Sprint | Task | Risk |
|---|---|---|
| 1 | Create `banking-common` module; extract exception + audit + filter classes | Low — purely additive |
| 1 | Publish `banking-common`; update all services to use it; remove duplicates | Low — no behavior change |
| 2 | Create `internet-banking-payment-service` with unified domain model | Medium — new service |
| 2-3 | Migrate fund-transfer endpoints + data into payment-service | Medium — requires data migration |
| 3 | Migrate utility-payment endpoints + data into payment-service | Medium — same as above |
| 4 | Update API Gateway routes; deprecate old services; run parallel | Low — routing change only |
| 4 | Decommission fund-transfer-service and utility-payment-service | Low — after validation period |

### Phase 2: Decompose Core-Banking (Sprints 5-9)

| Sprint | Task | Risk |
|---|---|---|
| 5-6 | Extract `account-service` from core-banking (accounts + utility accounts + user lookup) | High — DB split required |
| 6 | Update payment-service and user-service Feign clients to call account-service | Medium — contract change |
| 7-8 | Extract `transaction-ledger-service` from core-banking | High — DB split + event introduction |
| 8 | Rename remaining core-banking to `payment-execution-service` | Low — rename only |
| 9 | Introduce RabbitMQ events between execution and ledger services | Medium — new integration pattern |

### Phase 3: Event-Driven Architecture (Sprints 10-14)

| Sprint | Task | Risk |
|---|---|---|
| 10 | Implement event publishing for payment completion notifications | Low — additive |
| 11 | Create `notification-service` consuming payment events | Low — new service, no existing impact |
| 12-13 | Convert payment processing to async (SAGA pattern with RabbitMQ) | High — fundamental flow change |
| 14 | Implement dead-letter queue handling and compensation logic | Medium — error handling |

---

## 7. Priority Matrix

| Rec | Impact | Feasibility | Priority | Phase |
|---|---|---|---|---|
| **R-01**: Merge payment services | **High** — eliminates 2 anemic services, reduces operational overhead by 50% for payment domain | **High** — same codebase patterns, straightforward merge | **P1** | Phase 1 |
| **R-02**: Shared library | **Medium** — eliminates 20+ duplicated classes, single source of truth for cross-cutting concerns | **High** — extract and replace, no behavior change | **P1** | Phase 1 |
| **R-03**: Extract Account Service | **High** — enables independent scaling, fills account lifecycle gaps, reduces core-banking blast radius | **Medium** — requires database split and Feign client rewiring | **P2** | Phase 2 |
| **R-04**: Extract Ledger Service | **Medium** — enables immutable ledger, independent query scaling, event sourcing foundation | **Medium** — requires event introduction and data migration | **P2** | Phase 2 |
| **R-05**: Event Bus / Async | **High** — resilient payment processing, enables notifications, uses existing RabbitMQ infrastructure | **Low-Medium** — fundamental architectural shift, requires SAGA pattern implementation | **P3** | Phase 3 |

### Quick Wins (Can be done independently of decomposition)

These improvements from the [Payment Gap Analysis](./PAYMENT_GAP_ANALYSIS.md) should be addressed in parallel:

| Quick Win | Effort | Impact |
|---|---|---|
| Fix available balance double-deduction bug | 1 hour | Critical — prevents financial data corruption |
| Add Jakarta Bean Validation to payment DTOs | 1 day | Critical — prevents malformed payments |
| Add idempotency key to payment requests | 2 days | Critical — prevents duplicate payments |
| Add currency field to payment DTOs | 1 day | High — foundation for multi-currency |
| Add amount limits (configurable min/max) | 2 days | High — basic fraud prevention |
| Add account status check before transfer | 0.5 days | High — prevent transfers on blocked accounts |
