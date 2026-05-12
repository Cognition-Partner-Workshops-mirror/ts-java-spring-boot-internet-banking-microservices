# Decomposition Recommendations

> Recommendations for restructuring the internet-banking microservices based on the [Business Capability Map](BUSINESS_CAPABILITY_MAP.md) and [Payment Gap Analysis](PAYMENT_GAP_ANALYSIS.md).

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Recommendation 1 — Decompose Core Banking Service](#2-recommendation-1--decompose-core-banking-service)
3. [Recommendation 2 — Merge Fund Transfer and Utility Payment into Payment Orchestration Service](#3-recommendation-2--merge-fund-transfer-and-utility-payment-into-payment-orchestration-service)
4. [Recommendation 3 — Extract Shared Library (banking-common)](#4-recommendation-3--extract-shared-library-banking-common)
5. [Recommendation 4 — Introduce Event-Driven Communication](#5-recommendation-4--introduce-event-driven-communication)
6. [Recommendation 5 — Consolidate User Domain](#6-recommendation-5--consolidate-user-domain)
7. [Recommendation 6 — Add New Capability Services](#7-recommendation-6--add-new-capability-services)
8. [Current vs. Proposed Architecture](#8-current-vs-proposed-architecture)
9. [Prioritization Matrix](#9-prioritization-matrix)
10. [Migration Sequencing](#10-migration-sequencing)

---

## 1. Executive Summary

The current architecture has three structural problems:

1. **Core-banking is a monolith** — It owns 6+ business capabilities and is a single point of failure.
2. **Fund-transfer and utility-payment are redundant** — Near-identical thin orchestrators that should be consolidated.
3. **No shared library** — Cross-cutting concerns are copy-pasted across 4 services.

This document proposes 6 recommendations to restructure the platform into a capability-aligned architecture. Changes are prioritized by impact and feasibility to enable incremental migration.

---

## 2. Recommendation 1 — Decompose Core Banking Service

### Business Justification

The `core-banking-service` currently handles account management, transaction recording, ledger operations, user CRUD, fund transfer execution, and utility payment execution. This violates the single-responsibility principle and creates:

- **Single point of failure** — If core-banking goes down, ALL business operations stop.
- **Scaling bottleneck** — Cannot scale payment processing independently from account queries.
- **Deployment risk** — Any change to account management risks breaking payment processing.
- **Team ownership conflict** — Multiple teams cannot own independent services.

### Technical Approach

Split `core-banking-service` into three focused services:

#### 2.1 Account Service (new)

**Owns:** `banking_core_user`, `banking_core_account`, `banking_core_utility_account` tables.

**Capabilities:** BC-1 (Account Management), BC-6 (User Data — profile portion).

**API surface:**
- `GET /api/v1/accounts/{account_number}` — Read bank account
- `GET /api/v1/accounts/utility/{provider}` — Read utility account
- `PUT /api/v1/accounts/{account_number}/balance` — Update balance (internal only)
- `GET /api/v1/users/{identification}` — Read user profile
- `GET /api/v1/users` — List users

**Key changes:**
- Extract `AccountService`, `BankAccountEntity`, `UserEntity`, `UtilityAccountEntity` from core-banking.
- Add optimistic locking (`@Version`) to `BankAccountEntity` for concurrent balance updates.
- Add account status validation (reject transactions on DORMANT/BLOCKED accounts).

#### 2.2 Transaction/Ledger Service (new)

**Owns:** `banking_core_transaction` table.

**Capabilities:** BC-4 (Transaction Management), BC-5 (Ledger Management).

**API surface:**
- `POST /api/v1/transactions` — Record a transaction
- `GET /api/v1/transactions?accountId={id}&page={n}` — Query transaction history
- `GET /api/v1/transactions/{transactionId}` — Get transaction by ID

**Key changes:**
- Extract `TransactionService`, `TransactionEntity`, `TransactionRepository` from core-banking.
- This service becomes the single source of truth for all financial records.
- Add consolidated transaction history endpoint (currently missing — see gap BC-4 in capability map).

#### 2.3 Payment Execution Service (refactored from core-banking)

**Owns:** No new tables — orchestrates calls to Account Service and Transaction Service.

**Capabilities:** BC-2 (Fund Transfer execution), BC-3 (Payment Processing execution).

**API surface:**
- `POST /api/v1/payments/fund-transfer` — Execute fund transfer (debit + credit + record)
- `POST /api/v1/payments/utility` — Execute utility payment (debit + record)

**Key changes:**
- Move `internalFundTransfer()` and `utilPayment()` logic here.
- Fix the double-deduction bug during extraction.
- Add saga/compensation pattern for multi-step operations.

### Effort Estimate: **Large** (4-6 weeks)

---

## 3. Recommendation 2 — Merge Fund Transfer and Utility Payment into Payment Orchestration Service

### Business Justification

The `fund-transfer-service` and `utility-payment-service` are structurally identical:

| Aspect               | Fund Transfer Service              | Utility Payment Service           |
|----------------------|------------------------------------|------------------------------------|
| Package structure    | controller → service → feign → entity | controller → service → feign → entity |
| Flow                 | Save PENDING → Feign call → Save SUCCESS | Save PROCESSING → Feign call → Save SUCCESS |
| Duplicated classes   | GlobalExceptionHandler, BaseMapper, AuditAware, AppAuthUserFilter | Same 4 classes, copy-pasted |
| Lines of business logic | ~20 lines in FundTransferService | ~20 lines in UtilityPaymentService |
| Database tables      | 1 (`fund_transfer`)               | 1 (`utility_payment`)              |

Maintaining two separate services, two separate deployments, two separate databases, and two separate CI pipelines for functionally identical code is wasteful.

### Technical Approach

Create a single **`payment-orchestration-service`** that handles both fund transfers and utility payments:

**API surface:**
- `POST /api/v1/payments/transfer` — Initiate fund transfer
- `GET  /api/v1/payments/transfer` — List fund transfers (paginated)
- `POST /api/v1/payments/utility` — Initiate utility payment
- `GET  /api/v1/payments/utility` — List utility payments (paginated)
- `GET  /api/v1/payments` — Unified payment history (new capability)

**Database:** Single schema `banking_payment_service` with both `fund_transfer` and `utility_payment` tables.

**Key changes:**
- Merge the two Feign clients into one `CoreBankingClient`.
- Unify exception handling, filters, mappers, and audit config into single implementations.
- Add idempotency key support (client-provided in request header).
- Add FAILED status handling with retry/compensation.
- Future: Add scheduled payment support via this service.

### Effort Estimate: **Medium** (2-3 weeks)

---

## 4. Recommendation 3 — Extract Shared Library (banking-common)

### Business Justification

The following classes are duplicated across 3-4 services with minor variations:

| Class                          | Duplicated In                                       | Variation        |
|--------------------------------|----------------------------------------------------|------------------|
| `GlobalExceptionHandler`       | core, fund-transfer, utility-payment, user-service | Minor differences|
| `ErrorResponse`                | core, fund-transfer, utility-payment, user-service | Identical        |
| `SimpleBankingGlobalException` | core, fund-transfer, utility-payment, user-service | Identical        |
| `BaseMapper<E,D>`              | core, fund-transfer, utility-payment, user-service | Identical        |
| `AuditAware`                   | fund-transfer, utility-payment, user-service       | Identical        |
| `AppAuthUserFilter`            | fund-transfer, utility-payment, user-service       | Identical        |
| `ApiRequestContext`            | fund-transfer, utility-payment, user-service       | Identical        |
| `ApiRequestContextHolder`      | fund-transfer, utility-payment, user-service       | Identical        |
| `CustomFeignClientConfiguration`| fund-transfer, utility-payment, user-service      | Identical        |

A `banking-common` module already exists in the repo but these classes have not been migrated into it.

### Technical Approach

1. Move all duplicated classes into the `banking-common` Gradle module.
2. Publish as a local composite build dependency (already configured).
3. Delete duplicated classes from each service and replace with imports from `banking-common`.
4. Standardize `GlobalExceptionHandler` to use proper HTTP status codes (not blanket 400).
5. Add shared Bean Validation constraint annotations and DTO base classes.

### Effort Estimate: **Small** (1 week)

---

## 5. Recommendation 4 — Introduce Event-Driven Communication

### Business Justification

All inter-service communication is synchronous REST via OpenFeign. This creates:

- **Tight temporal coupling** — The caller blocks until the downstream service responds.
- **Cascading failures** — Core-banking downtime makes fund-transfer and utility-payment completely unavailable.
- **No audit trail** — Failed Feign calls leave orphaned records with no mechanism for eventual consistency.

### Technical Approach

Introduce an event broker (RabbitMQ is already referenced in project documentation) for specific flows:

#### Phase 1 — Payment Status Events

```
Payment Orchestration        Event Broker           Account Service
Service                      (RabbitMQ)             / Ledger Service
        │                         │                        │
        │  PaymentInitiated       │                        │
        ├────────────────────────►│                        │
        │                         │  PaymentInitiated      │
        │                         ├───────────────────────►│
        │                         │                        │
        │                         │  PaymentExecuted       │
        │                         │◄───────────────────────┤
        │  PaymentExecuted        │                        │
        │◄────────────────────────┤                        │
        │                         │                        │
```

**Events:**
- `PaymentInitiated` — Orchestrator publishes after saving PENDING record.
- `PaymentExecuted` / `PaymentFailed` — Execution service publishes after debit/credit.
- `PaymentCompleted` — Orchestrator consumes and updates to SUCCESS/FAILED.

#### Phase 2 — User Registration Events

- `UserRegistered` — Published when user-service creates a Keycloak user.
- `UserApproved` — Published when admin approves a user.
- Downstream services can react to user lifecycle changes without synchronous coupling.

### Effort Estimate: **Large** (3-4 weeks)

---

## 6. Recommendation 5 — Consolidate User Domain

### Business Justification

User data is currently split between:
- `core-banking-service` → `banking_core_user` (firstName, lastName, email, identificationNumber)
- `user-service` → `user` table (authId, identification, status)
- `Keycloak` → credentials, email verification, enabled status

This means user registration requires both core-banking AND Keycloak to be available. There is no single service that owns the complete user lifecycle.

### Technical Approach

Make the user-service the sole owner of user profile data:

1. **Migrate** `banking_core_user` fields into the user-service's `user` table (add firstName, lastName, email columns).
2. **Seed** user data in user-service during account creation (or migrate via data script).
3. **Remove** user CRUD endpoints from core-banking-service.
4. **Update** account-service (from Recommendation 1) to reference users by ID without needing the full profile.
5. **Expose** user profile API only from user-service.

This gives the user-service complete ownership of BC-6 (User Administration) and BC-7 (Identity & Access Management).

### Effort Estimate: **Medium** (2-3 weeks)

---

## 7. Recommendation 6 — Add New Capability Services

Based on the capability gaps identified in the Business Capability Map:

### 7.1 Notification Service (new)

**Capabilities:** BC-8 (Notification Services)

**Scope:**
- Transaction notifications (payment confirmations, debit alerts)
- User lifecycle notifications (registration confirmation, approval)
- Configurable channels (email initially, SMS/push later)

**Integration:** Consumes events from the event broker (Recommendation 4).

### 7.2 Payment Limits & Risk Service (new)

**Capabilities:** Payment Limits Management, Duplicate Detection

**Scope:**
- Per-user and per-account transaction limits (daily, per-transaction)
- Velocity checks (too many transactions in short window)
- Duplicate payment detection using idempotency keys
- Blacklist/sanctions checking (future)

**Integration:** Called by Payment Orchestration Service before initiating payment execution.

### Effort Estimate: **Medium** each (2-3 weeks per service)

---

## 8. Current vs. Proposed Architecture

### 8.1 Current Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         CURRENT STATE                               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                         │
│  │ Registry │  │  Config  │  │  Zipkin  │   Infrastructure         │
│  │  (8081)  │  │  (8090)  │  │  (9411)  │                         │
│  └──────────┘  └──────────┘  └──────────┘                         │
│                                                                     │
│  ┌───────────────────────────────────────┐                         │
│  │        API Gateway (8082)             │   Edge                  │
│  │   JWT validation + path routing       │                         │
│  └───────────┬──────────┬──────────┬─────┘                         │
│              │          │          │                                 │
│    ┌─────────▼──┐ ┌─────▼──────┐ ┌▼────────────┐                  │
│    │   User     │ │   Fund     │ │  Utility    │   Orchestrators  │
│    │  Service   │ │  Transfer  │ │  Payment    │   (thin)         │
│    │  (8083)    │ │  (8084)    │ │  (8085)     │                  │
│    └─────┬──────┘ └─────┬──────┘ └──┬──────────┘                  │
│          │              │           │                               │
│          │    ALL sync Feign calls  │                               │
│          └──────────┐   │   ┌───────┘                              │
│                     ▼   ▼   ▼                                      │
│             ┌─────────────────────┐                                │
│             │   CORE BANKING      │   *** Monolith ***             │
│             │   (8092)            │                                │
│             │                     │                                │
│             │  Users + Accounts   │                                │
│             │  + Transactions     │                                │
│             │  + Ledger           │                                │
│             │  + Payment Exec     │                                │
│             └─────────┬───────────┘                                │
│                       │                                             │
│                ┌──────▼──────┐                                     │
│                │  MySQL (4   │                                     │
│                │  schemas)   │                                     │
│                └─────────────┘                                     │
│                                                                     │
│  Keycloak (8080) ◄──── User Service                                │
└─────────────────────────────────────────────────────────────────────┘
```

### 8.2 Proposed Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        PROPOSED STATE                               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                         │
│  │ Registry │  │  Config  │  │  Zipkin  │   Infrastructure         │
│  │  (8081)  │  │  (8090)  │  │  (9411)  │                         │
│  └──────────┘  └──────────┘  └──────────┘                         │
│                                                                     │
│  ┌───────────────────────────────────────┐                         │
│  │        API Gateway (8082)             │   Edge                  │
│  │   JWT + rate limiting + routing       │                         │
│  └──────┬──────────┬──────────┬──────────┘                         │
│         │          │          │                                     │
│  ┌──────▼──────┐   │   ┌─────▼──────────┐                         │
│  │   User      │   │   │   Payment      │   Domain Services       │
│  │  Service    │   │   │  Orchestration │                         │
│  │  (profile   │   │   │   Service      │                         │
│  │  + IAM)     │   │   │  (merged FT +  │                         │
│  └──────┬──────┘   │   │   UP + sched.) │                         │
│         │          │   └──────┬──────────┘                         │
│     Keycloak       │          │                                    │
│                    │          │ events                              │
│         ┌──────────┘    ┌─────▼──────────┐                         │
│         │               │  RabbitMQ      │   Event Broker          │
│         │               │  (event bus)   │                         │
│         │               └──┬─────────┬───┘                         │
│         │                  │         │                              │
│  ┌──────▼──────┐   ┌──────▼───┐  ┌──▼───────────┐                │
│  │  Account    │   │  Payment │  │ Transaction/ │   Core Services │
│  │  Service    │   │  Exec.   │  │ Ledger Svc   │                 │
│  │  (accounts  │   │  Service │  │ (single      │                 │
│  │  + balances)│   │  (debit/ │  │  source of   │                 │
│  │             │   │  credit) │  │  truth)      │                 │
│  └──────┬──────┘   └──────┬──┘  └──────┬───────┘                  │
│         │                 │            │                            │
│  ┌──────▼──┐      ┌──────▼──┐   ┌─────▼────┐                     │
│  │ Account │      │ Account │   │ Txn DB   │    Databases         │
│  │   DB    │      │   DB    │   │          │    (per-service)     │
│  └─────────┘      └─────────┘   └──────────┘                     │
│                                                                     │
│  ┌──────────────┐  ┌──────────────┐                                │
│  │ Notification │  │ Limits/Risk  │   New Capability Services      │
│  │   Service    │  │   Service    │                                │
│  └──────────────┘  └──────────────┘                                │
│                                                                     │
│  ┌────────────────────────────────┐                                │
│  │    banking-common (shared lib) │   Shared Library               │
│  │  exceptions, mappers, DTOs,   │                                │
│  │  audit, filters, validation   │                                │
│  └────────────────────────────────┘                                │
└─────────────────────────────────────────────────────────────────────┘
```

### 8.3 Key Structural Changes Summary

| Current                              | Proposed                                  | Change Type |
|--------------------------------------|-------------------------------------------|-------------|
| `core-banking-service` (monolith)    | Account Service + Payment Execution + Transaction/Ledger Service | **Split**   |
| `fund-transfer-service`              | Payment Orchestration Service             | **Merge**   |
| `utility-payment-service`            | Payment Orchestration Service             | **Merge**   |
| `user-service` (partial user data)   | User Service (full user ownership)        | **Expand**  |
| Duplicated cross-cutting classes     | `banking-common` shared library           | **Extract** |
| (missing)                            | Event Broker (RabbitMQ)                   | **New**     |
| (missing)                            | Notification Service                      | **New**     |
| (missing)                            | Payment Limits/Risk Service               | **New**     |
| `api-gateway`                        | API Gateway (unchanged)                   | No change   |
| `service-registry`                   | Service Registry (unchanged)              | No change   |
| `config-server`                      | Config Server (unchanged)                 | No change   |

---

## 9. Prioritization Matrix

Recommendations are prioritized by two axes:

- **Impact**: How much the change improves system reliability, maintainability, and business capability.
- **Feasibility**: How easy and low-risk the change is to implement.

| #   | Recommendation                              | Impact     | Feasibility | Priority    | Effort  |
|-----|---------------------------------------------|------------|-------------|-------------|---------|
| R-3 | Extract shared library (banking-common)     | 🟡 Medium  | 🟢 High     | **P1 — Do First** | Small  |
| R-2 | Merge FT + UP → Payment Orchestration       | 🟠 High    | 🟢 High     | **P1 — Do First** | Medium |
| R-1 | Decompose core-banking                      | 🔴 Critical| 🟡 Medium   | **P2 — Do Next**  | Large  |
| R-5 | Consolidate user domain                     | 🟡 Medium  | 🟡 Medium   | **P2 — Do Next**  | Medium |
| R-4 | Introduce event-driven communication        | 🟠 High    | 🔴 Low      | **P3 — Plan**     | Large  |
| R-6 | Add notification + limits services          | 🟠 High    | 🟡 Medium   | **P3 — Plan**     | Medium |

---

## 10. Migration Sequencing

### Phase 1 — Foundation (Weeks 1-3)

**Goal:** Reduce duplication, establish shared patterns, consolidate redundant services.

```
Week 1:     R-3  Extract banking-common shared library
            ├── Move duplicated classes to banking-common
            ├── Standardize GlobalExceptionHandler (proper HTTP codes)
            ├── Add shared Bean Validation constraints
            └── Update all services to use banking-common

Week 2-3:   R-2  Merge payment services
            ├── Create payment-orchestration-service
            ├── Migrate fund-transfer endpoints and entities
            ├── Migrate utility-payment endpoints and entities
            ├── Add idempotency key support
            ├── Add FAILED status handling
            ├── Update gateway routes
            └── Decommission fund-transfer-service and utility-payment-service
```

**Deliverables:** Shared library published; single payment orchestration service deployed; 2 services decommissioned.

### Phase 2 — Core Decomposition (Weeks 4-8)

**Goal:** Break the core-banking monolith into focused services; consolidate user domain.

```
Week 4-5:   R-5  Consolidate user domain
            ├── Migrate user profile fields to user-service
            ├── Remove user CRUD from core-banking
            └── Update user-service to be sole user profile owner

Week 5-8:   R-1  Decompose core-banking
            ├── Extract Account Service (accounts + balances)
            ├── Extract Transaction/Ledger Service (transaction history)
            ├── Refactor remaining core-banking into Payment Execution Service
            ├── Fix double-deduction bug during extraction
            ├── Add optimistic locking to BankAccountEntity
            ├── Add account status validation
            └── Update all Feign clients to point to new services
```

**Deliverables:** Core-banking fully decomposed into 3 services; user-service owns full user lifecycle.

### Phase 3 — Advanced Capabilities (Weeks 9-14)

**Goal:** Add event-driven architecture and new business capabilities.

```
Week 9-11:  R-4  Introduce event-driven communication
            ├── Deploy RabbitMQ to infrastructure
            ├── Implement payment status events
            ├── Add saga pattern for payment orchestration
            └── Add dead letter queues for failed events

Week 12-14: R-6  Add new capability services
            ├── Build Notification Service (email alerts)
            ├── Build Payment Limits/Risk Service
            ├── Integrate limits service into payment orchestration
            └── Integrate notification service with event broker
```

**Deliverables:** Event-driven payment flow; notification and risk services operational.

---

### Risk Mitigation

| Risk                                           | Mitigation                                                        |
|------------------------------------------------|-------------------------------------------------------------------|
| Data migration errors during core-banking split | Run dual-read mode: write to new service, read from both, compare |
| Service discovery disruption during migration  | Use Eureka service names; update one consumer at a time           |
| Breaking Feign contracts                       | Add Spring Cloud Contract tests before making changes             |
| Downtime during database migration             | Use Flyway migrations; run schema changes during maintenance window|
| Team unfamiliarity with event-driven patterns  | Start with a single event flow (payment status) as a pilot        |
