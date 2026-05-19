# Decomposition Recommendations

> **Scope**: Service restructuring recommendations based on the Business Capability Map analysis  
> **Date**: May 2025

---

## 1. Executive Summary

The current architecture has 6 microservices, but the service boundaries do not align with business capabilities. Core banking is a monolithic "god service" handling accounts, users, transactions, and balance management. Fund transfer and utility payment are nearly identical orchestration shims with minimal business logic. User service conflates profile management with IAM integration. 

This document recommends **4 structural changes** prioritized by impact and feasibility.

---

## 2. Recommendations

### R-01: Merge Fund Transfer and Utility Payment into a Unified Payment Service

**Priority**: HIGH | **Impact**: HIGH | **Feasibility**: HIGH

#### Business Justification

Fund Transfer Service and Utility Payment Service are structurally identical:
- Both accept a payment request, save an entity with initial status, delegate to core banking via Feign, and update status on response
- They share 9 duplicated classes (BaseMapper, AuditAware, GlobalExceptionHandler, etc.)
- The distinguishing business logic (debit-only vs. debit-credit) lives in core banking, not in these services
- From a domain perspective, both are **payment orchestration** — they initiate a payment and track its lifecycle

Keeping them separate creates:
- Double maintenance burden for identical cross-cutting code
- Two deployment pipelines for functionally equivalent services
- Artificial API fragmentation (clients must know which service handles which payment type)

#### Technical Approach

1. Create `internet-banking-payment-service` with a unified payment orchestrator
2. Merge `FundTransferEntity` and `UtilityPaymentEntity` into a single `PaymentEntity` with a `paymentType` discriminator (`FUND_TRANSFER`, `UTILITY_PAYMENT`)
3. Unify endpoints under `/api/v1/payments`:
   - `POST /api/v1/payments/transfer` — fund transfers
   - `POST /api/v1/payments/utility` — utility payments
   - `GET /api/v1/payments` — all payments (filterable by type)
   - `GET /api/v1/payments/{id}` — single payment status
4. Single Feign client to core banking with methods for both transaction types
5. Single database schema `banking_payment_service` with one `payment` table
6. Eliminate all duplicated cross-cutting code by having one copy
7. Update API Gateway routes: `/payment/**` → payment-service

#### Proposed Unified Payment Entity

```
payment
├── id (BIGINT, PK)
├── payment_type (ENUM: FUND_TRANSFER, UTILITY_PAYMENT)
├── from_account (VARCHAR)
├── to_account (VARCHAR, nullable — not used for utility)
├── provider_id (BIGINT, nullable — not used for transfer)
├── amount (DECIMAL 19,2)
├── currency (VARCHAR 3) ← NEW: ISO 4217
├── reference_number (VARCHAR)
├── transaction_reference (VARCHAR — from core banking)
├── status (ENUM: PENDING, PROCESSING, SUCCESS, FAILED)
├── idempotency_key (VARCHAR, UNIQUE) ← NEW
├── created_date (TIMESTAMP)
├── created_by (VARCHAR)
├── modified_date (TIMESTAMP)
├── modified_by (VARCHAR)
└── version (BIGINT)
```

#### Migration Steps

1. Create `internet-banking-payment-service` project with Gradle build
2. Copy fund-transfer service as the base (it has the superset of Feign methods)
3. Add utility payment request/response DTOs and controller endpoint
4. Create Flyway migration to create the unified `payment` table
5. Migrate data from `fund_transfer` and `utility_payment` tables
6. Update gateway routing
7. Remove old `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`
8. Update docker-compose

---

### R-02: Extract Transaction Processing from Core Banking into a Dedicated Transaction Service

**Priority**: HIGH | **Impact**: HIGH | **Feasibility**: MEDIUM

#### Business Justification

Core banking currently mixes four distinct responsibilities:
1. **Account Management** — CRUD for bank accounts and utility accounts
2. **User Lookup** — finding users by NIC
3. **Balance Management** — debiting and crediting accounts
4. **Transaction Processing** — executing fund transfers and utility payments

These have different change frequencies and risk profiles:
- Account management changes rarely
- Transaction processing changes frequently (new payment types, validation rules, fee structures)
- A bug in transaction processing should not require redeploying account management

Separating transaction processing:
- Reduces core banking to a clean **system of record** for accounts and balances
- Creates a focused **transaction engine** that can evolve independently
- Aligns with banking domain patterns (account servicing vs. payment processing)

#### Technical Approach

1. Create `transaction-service` containing:
   - `TransactionService.fundTransfer()` — account validation, balance check, debit/credit
   - `TransactionService.utilPayment()` — account validation, balance check, debit
   - `TransactionEntity` and `TransactionRepository`
   - Balance update logic
2. Core banking retains:
   - `AccountService` — account CRUD
   - `UserService` — user lookup
   - `BankAccountEntity`, `UserEntity`, `UtilityAccountEntity`
3. Transaction service calls core banking for account lookup (read-only)
4. Transaction service owns the `banking_core_transaction` table and performs balance updates via its own database connection (or shared DB with separate schema)

#### Alternative: Keep Transaction Logic in Core Banking (Simpler)

If extracting a separate service is too disruptive, the minimum viable restructuring is to:
- Split `TransactionService` into `FundTransferProcessor` and `UtilityPaymentProcessor` within core banking
- Add a `TransactionController` per payment type with separate validation
- This achieves better separation of concerns without adding network hops

---

### R-03: Separate IAM Integration from User Profile Management

**Priority**: MEDIUM | **Impact**: MEDIUM | **Feasibility**: HIGH

#### Business Justification

User service currently handles two distinct capabilities:
1. **User Profile Management** — storing user records, managing status (PENDING → APPROVED)
2. **IAM Integration** — creating Keycloak users, setting passwords, enabling/disabling accounts, email verification

These should be separated because:
- Keycloak integration is an **infrastructure concern** — it should be swappable (e.g., migrating from Keycloak to Auth0 or Okta)
- User profile management is a **business concern** — it should not be coupled to a specific IAM provider
- The Keycloak admin API has different availability and failure modes than the local database

#### Technical Approach

1. Extract `KeycloakUserService`, `KeycloakManager`, and `KeycloakProperties` into a dedicated `iam-integration-service` or a shared library
2. User service communicates with IAM via an abstraction (interface) rather than direct Keycloak SDK calls
3. IAM events (user created, user enabled) can be published as domain events for other services to consume

#### Simpler Alternative: Interface Extraction

If a separate service is too heavy:
1. Define an `IamProvider` interface in user-service with methods: `createUser()`, `enableUser()`, `readUser()`
2. Implement `KeycloakIamProvider` that wraps current `KeycloakUserService`
3. This makes the IAM provider swappable without a separate service

---

### R-04: Extract a Shared Library for Cross-Cutting Concerns

**Priority**: HIGH | **Impact**: MEDIUM | **Feasibility**: HIGH

#### Business Justification

9 classes are duplicated identically across 3-4 services:
- `BaseMapper`, `AuditAware`, `GlobalExceptionHandler`, `AppAuthUserFilter`, `ErrorResponse`, `SimpleBankingGlobalException`, `ApiRequestContext`, `ApiRequestContextHolder`, `AuditorAwareConfig`

Any bug fix (e.g., the `GlobalExceptionHandler` returning HTTP 400 for everything) must be applied 4 times. Any improvement (e.g., adding correlation IDs) must be coded 4 times.

**Note**: The `banking-common` shared library already exists in the repo but these classes have not been moved into it.

#### Technical Approach

1. Move all duplicated classes into `banking-common`:
   - `com.javatodev.finance.common.exception` — ErrorResponse, SimpleBankingGlobalException, GlobalExceptionHandler
   - `com.javatodev.finance.common.audit` — AuditAware, AuditorAwareConfig
   - `com.javatodev.finance.common.filter` — AppAuthUserFilter, ApiRequestContext, ApiRequestContextHolder
   - `com.javatodev.finance.common.mapper` — BaseMapper
2. Add `banking-common` as a Gradle dependency in all services (already configured as composite build)
3. Delete local copies from each service
4. Fix `GlobalExceptionHandler` to return appropriate HTTP status codes while centralizing it

---

## 3. Dependency Diagram — Current vs. Proposed

### 3.1 Current Architecture

```
                         ┌──────────────┐
                         │   Keycloak   │
                         │  (IAM/OIDC)  │
                         └──────┬───────┘
                                │ JWT validation
                                ▼
┌─────────┐            ┌──────────────────┐
│  Client  │───REST───▶│   API Gateway    │
└─────────┘            │   (port 8082)    │
                       └────────┬─────────┘
                                │ routes by path prefix
              ┌─────────────────┼─────────────────────────┐
              ▼                 ▼                          ▼
   ┌──────────────────┐ ┌─────────────────┐  ┌───────────────────────┐
   │  User Service    │ │ Fund Transfer   │  │  Utility Payment      │
   │  (port 8083)     │ │ Service (8084)  │  │  Service (8085)       │
   │                  │ │                 │  │                       │
   │  ● User CRUD     │ │ ● Save PENDING  │  │  ● Save PROCESSING   │
   │  ● Keycloak IAM  │ │ ● Feign to core │  │  ● Feign to core     │
   │  ● Status mgmt   │ │ ● Update SUCCESS│  │  ● Update SUCCESS    │
   └───────┬──────────┘ └───────┬─────────┘  └──────────┬────────────┘
           │ Feign               │ Feign                  │ Feign
           ▼                     ▼                        ▼
      ┌───────────────────────────────────────────────────────┐
      │              Core Banking Service (8092)              │
      │                                                       │
      │  ● Account CRUD    ● User Lookup                      │
      │  ● Balance Mgmt    ● Transaction Processing           │
      │  ● Transaction Recording (debit/credit entries)       │
      │                                                       │
      │  >> 4 responsibilities in 1 service <<                │
      └───────────────────────────────────────────────────────┘
              │                │               │
         MySQL (core)    MySQL (ft)      MySQL (up)
         4 tables        1 table         1 table

    ┌────────────────┐  ┌──────────────────┐
    │ Service Registry│  │  Config Server   │
    │   (Eureka)     │  │  (Spring Cloud)  │
    └────────────────┘  └──────────────────┘
```

**Problems visible in diagram:**
- Core Banking is a bottleneck — all business services depend on it
- Fund Transfer and Utility Payment are nearly identical boxes
- Three separate MySQL schemas for what is logically one payment domain
- User Service couples profile management with Keycloak integration

### 3.2 Proposed Architecture (After R-01 through R-04)

```
                         ┌──────────────┐
                         │   Keycloak   │
                         │  (IAM/OIDC)  │
                         └──────┬───────┘
                                │ JWT validation
                                ▼
┌─────────┐            ┌──────────────────┐
│  Client  │───REST───▶│   API Gateway    │
└─────────┘            │   (port 8082)    │
                       └────────┬─────────┘
                                │ routes by path prefix
              ┌─────────────────┼──────────────────┐
              ▼                 ▼                   ▼
   ┌──────────────────┐ ┌─────────────────────┐ ┌─────────────────────┐
   │  User Service    │ │  Payment Service    │ │  Core Banking       │
   │  (port 8083)     │ │  (NEW — merged)     │ │  Service (8092)     │
   │                  │ │                     │ │  (SLIMMED)          │
   │  ● User CRUD     │ │ ● Fund Transfers    │ │                     │
   │  ● Status mgmt   │ │ ● Utility Payments  │ │ ● Account CRUD      │
   │  ● IamProvider   │ │ ● Payment lifecycle │ │ ● Balance Mgmt      │
   │    (interface)   │ │ ● Idempotency       │ │ ● Tx Recording      │
   └───────┬──────────┘ │ ● Unified schema    │ │ ● User Lookup       │
           │ Feign       └───────┬─────────────┘ └──────────┬──────────┘
           ▼                     │ Feign                     │
      ┌────────────┐             ▼                          │
      │ Core Banking│◀───────────────────────────────────────┘
      │ (read user) │         MySQL (payment)          MySQL (core)
      └────────────┘          1 unified table          4 tables

    ┌───────────────────────────────────────────────────┐
    │              banking-common (shared lib)           │
    │  ● BaseMapper  ● AuditAware  ● GlobalExHandler    │
    │  ● AppAuthUserFilter  ● ErrorResponse             │
    │  ● ApiRequestContext  ● AuditorAwareConfig        │
    └───────────────────────────────────────────────────┘

    ┌────────────────┐  ┌──────────────────┐
    │ Service Registry│  │  Config Server   │
    │   (Eureka)     │  │  (Spring Cloud)  │
    └────────────────┘  └──────────────────┘
```

**Improvements visible in diagram:**
- Fund Transfer + Utility Payment merged → single Payment Service
- Core Banking slimmed — no longer processing payment orchestration logic
- `banking-common` eliminates duplicated cross-cutting code
- User Service decoupled from Keycloak via `IamProvider` interface
- One unified payment schema instead of two

---

## 4. Prioritization Matrix

| # | Recommendation | Impact | Feasibility | Priority | Effort Estimate |
|---|---------------|--------|-------------|----------|-----------------|
| **R-04** | Extract shared library (banking-common) | Medium | High | **DO FIRST** | 1-2 days |
| **R-01** | Merge fund-transfer + utility-payment → Payment Service | High | High | **DO SECOND** | 3-5 days |
| **R-03** | Separate IAM from user-service (interface extraction) | Medium | High | **DO THIRD** | 1-2 days |
| **R-02** | Extract transaction processing from core banking | High | Medium | **DO FOURTH** | 5-8 days |

**Rationale for ordering:**
1. **R-04 first** — Eliminates code duplication before any merging happens, so the merge (R-01) starts from a cleaner base
2. **R-01 second** — Highest impact reduction (remove 2 services, add 1). Simpler than refactoring core banking
3. **R-03 third** — Quick win that improves testability and reduces Keycloak coupling
4. **R-02 fourth** — Most complex change. Core banking is the system of record; extracting transaction processing requires careful data migration and possibly distributed transactions

---

## 5. Risk Assessment

| Risk | Mitigation |
|------|------------|
| **Data migration during payment service merge** | Run both old and new services in parallel with dual-write during transition. Use feature flags to gradually shift traffic. |
| **Breaking API contracts for fund-transfer and utility-payment clients** | Maintain old endpoints as aliases in the gateway during migration period. Deprecate after all clients are updated. |
| **Core banking refactoring introduces balance bugs** | The existing double-deduction bug (G-02 in Payment Gap Analysis) should be fixed BEFORE any structural changes. Add comprehensive transaction tests first. |
| **Shared library versioning conflicts** | Use semantic versioning on `banking-common`. Each service pins to a specific version. Coordinated upgrades via CI pipeline. |
| **Keycloak abstraction over-engineering** | Start with simple interface extraction (R-03 alternative), not a separate service. Only extract to a service if multiple consumers need IAM integration. |

---

## 6. Prerequisites Before Restructuring

Before executing any decomposition recommendation, these foundational issues from the Payment Gap Analysis should be resolved:

1. **Fix the double-deduction bug** (G-02) — this is a live financial correctness issue
2. **Add input validation** (G-04) — prevents null/negative values from corrupting data during migration
3. **Add basic test coverage** — current coverage is minimal; restructuring without tests is blind refactoring
4. **Fix HTTP status codes** (G-11) — clients need proper error semantics before API changes
5. **Add idempotency keys** (G-03) — prevents duplicate transactions during service migration cutover
