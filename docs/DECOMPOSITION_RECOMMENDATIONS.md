# Decomposition Recommendations

## 1. Executive Summary

Based on the Business Capability Map analysis, this document recommends restructuring the microservices to better align with business domains, eliminate redundant data stores, and address critical operational gaps. The recommendations are prioritized by business impact and implementation feasibility.

---

## 2. Recommendations Overview

| # | Recommendation | Type | Priority | Effort |
|---|---------------|------|----------|--------|
| R1 | Decompose core-banking-service into Account, Transaction, and User services | Split | P0 — Critical | High |
| R2 | Merge fund-transfer and utility-payment into a unified Payment Orchestration service | Merge | P1 — High | Medium |
| R3 | Extract a shared DTO / contract library | Restructure | P1 — High | Low |
| R4 | Implement Notification Service | New | P1 — High | Medium |
| R5 | Add a Payment Validation / Risk service | New | P2 — Medium | Medium |
| R6 | Implement Saga / Outbox pattern for distributed transactions | Restructure | P0 — Critical | High |

---

## 3. Detailed Recommendations

### R1: Decompose `core-banking-service` (Split)

#### Business Justification

`core-banking-service` currently spans three distinct bounded contexts:
- **Account Management** — Account CRUD, balance inquiries, utility account management
- **Transaction Processing** — Fund transfer execution, utility payment execution, balance updates
- **User Management** — User lookup by identification number

This violates the Single Responsibility Principle at the service level. Changes to transaction processing logic require redeploying the account and user functionality. The service is the single point of failure for the entire system.

#### Technical Approach

Split into three services:

**a) Account Service** (new `banking-account-service`)
- Owns `banking_core_account` and `banking_core_utility_account` tables
- Exposes: GET account by number, GET utility account by provider, POST create account, PATCH update account status
- Provides balance read APIs consumed by the transaction service

**b) Transaction Service** (new `banking-transaction-service`)
- Owns `banking_core_transaction` table
- Exposes: POST fund-transfer, POST utility-payment, GET transaction history
- Calls Account Service via Feign to validate and update balances
- Implements the actual debit/credit logic

**c) Core User Service** (keep existing `core-banking` user endpoints or merge into `user-service` — see R1c below)
- The `UserController` and `UserService` in core-banking can be absorbed into `internet-banking-user-service`
- The `banking_core_user` table is already the source-of-truth for user identity data

**R1c: Merge core-banking user endpoints into user-service**
- `internet-banking-user-service` already calls core-banking to validate users during registration
- Consolidating user data into a single service eliminates the circular dependency and the triple user store (core DB, user-service DB, Keycloak)
- The merged user-service would own the `banking_core_user` table directly

#### Migration Steps

1. Create `banking-account-service` with Account-related entities, repositories, and controllers extracted from core-banking
2. Create `banking-transaction-service` with Transaction-related logic extracted from core-banking
3. Update fund-transfer-service and utility-payment-service Feign clients to point to the new service names
4. Migrate core-banking user endpoints into user-service
5. Update API gateway routing rules
6. Decommission `core-banking-service`

---

### R2: Merge Fund Transfer and Utility Payment Services (Merge)

#### Business Justification

`fund-transfer-service` and `utility-payment-service` are structurally identical:
- Both are thin orchestration layers
- Both follow the same pattern: receive request → persist locally → call core-banking → update status
- Both have identical infrastructure code (audit, filters, Feign configuration, exception handling)
- Both will eventually publish to RabbitMQ for notifications

From a business perspective, "fund transfer" and "utility payment" are both **payment types** within the **Payment Processing** capability. Separate services add deployment and operational overhead without meaningful domain isolation.

#### Technical Approach

Create a unified **Payment Orchestration Service** (`internet-banking-payment-service`):

```
internet-banking-payment-service/
├── controller/
│   ├── FundTransferController.java      (preserved API paths)
│   └── UtilityPaymentController.java    (preserved API paths)
├── service/
│   ├── PaymentOrchestrationService.java (common orchestration logic)
│   ├── FundTransferService.java         (fund-transfer specifics)
│   └── UtilityPaymentService.java       (utility-payment specifics)
├── model/
│   ├── entity/
│   │   ├── PaymentEntity.java           (unified, with paymentType discriminator)
│   │   └── ...
│   ├── dto/
│   │   ├── request/
│   │   └── response/
│   └── PaymentType.java                 (FUND_TRANSFER, UTILITY_PAYMENT, ...)
└── ...
```

Key changes:
- Unified `PaymentEntity` with a `paymentType` discriminator, replacing separate `FundTransferEntity` and `UtilityPaymentEntity`
- Common orchestration logic (persist → call downstream → update → publish notification) extracted into a shared base
- API paths remain unchanged for backward compatibility
- Single Feign client configuration instead of two copies
- Single database instead of two

#### Migration Steps

1. Create the merged service with both controllers
2. Migrate data from both existing databases into the unified schema
3. Update API gateway routing to point `/fund-transfer/**` and `/utility-payment/**` to the new service
4. Decommission the two separate services

---

### R3: Extract Shared DTO / Contract Library

#### Business Justification

DTOs, exceptions, and utility classes are copy-pasted across services, causing field drift (e.g., `authID` present in one `FundTransferRequest` but not the other). This makes it impossible to guarantee contract compatibility between services.

#### Technical Approach

Create a shared Maven module `internet-banking-common`:

```
internet-banking-common/
├── src/main/java/com/javatodev/finance/common/
│   ├── dto/
│   │   ├── request/
│   │   │   ├── FundTransferRequest.java
│   │   │   └── UtilityPaymentRequest.java
│   │   ├── response/
│   │   │   ├── FundTransferResponse.java
│   │   │   ├── UtilityPaymentResponse.java
│   │   │   └── AccountResponse.java
│   │   └── AuditAware.java
│   ├── exception/
│   │   ├── SimpleBankingGlobalException.java
│   │   ├── ErrorResponse.java
│   │   └── GlobalExceptionHandler.java
│   ├── filter/
│   │   ├── ApiRequestContext.java
│   │   ├── ApiRequestContextHolder.java
│   │   └── AppAuthUserFilter.java
│   └── config/
│       ├── CustomFeignClientConfiguration.java
│       └── audit/
└── pom.xml
```

All services declare this as a dependency. Eliminates ~20 duplicated files.

---

### R4: Implement Notification Service

#### Business Justification

The README documents a planned Notification Service that was never built. Payment confirmation notifications are a basic expectation for internet banking. RabbitMQ is already in the technology stack but not wired up.

#### Technical Approach

1. Add RabbitMQ publisher to the Payment Orchestration Service (R2) — publish a `PaymentCompletedEvent` after successful transactions
2. Create `internet-banking-notification-service` that consumes these events
3. Implement email, SMS, and/or push notification channels
4. Add a `notification_log` table for audit trail

---

### R5: Add Payment Validation / Risk Service

#### Business Justification

As documented in the Gap Analysis, the system lacks: amount limits, fraud detection, velocity checks, account ownership verification, duplicate detection, AML/CFT screening, and input validation. These cannot be bolted on as afterthoughts — they require a dedicated domain service.

#### Technical Approach

Create `internet-banking-risk-service`:
- Called by the Payment Orchestration Service before executing any payment
- Implements: amount limit checks, velocity checks (N transfers per hour), duplicate detection (idempotency), account ownership verification, sanctions list screening
- Returns approve/reject/review decision
- Maintains its own rule configuration database

---

### R6: Implement Saga / Outbox Pattern

#### Business Justification

The current orchestration pattern has a critical data consistency gap: the fund-transfer and utility-payment services persist a local record, call core-banking synchronously, then update the local record. If the update step fails (network timeout, service crash), the payment is completed in core-banking but marked as PENDING in the orchestrating service — with no recovery mechanism.

#### Technical Approach

**Option A — Outbox Pattern (Recommended)**
1. Instead of calling core-banking synchronously, the Payment Orchestration Service writes a `payment_outbox` event to its database in the same transaction as the payment record
2. A background poller (or CDC with Debezium) publishes the outbox event to a message broker (RabbitMQ or Kafka)
3. The Transaction Service (from R1) consumes the event, processes the payment, and publishes a completion/failure event
4. The Payment Orchestration Service consumes the completion event and updates the payment status

**Option B — Saga with Compensating Transactions**
1. If the core-banking call fails, retry with exponential backoff
2. If the status update fails after a successful core-banking call, implement a compensating transaction (reversal) in core-banking
3. Add a reconciliation job that detects and resolves inconsistencies

---

## 4. Dependency Diagrams

### 4.1 Current Architecture

```
                    ┌─────────────┐
                    │ API Gateway │
                    │   (8082)    │
                    └──┬──┬──┬───┘
                       │  │  │
          ┌────────────┘  │  └────────────┐
          ▼               ▼               ▼
   ┌─────────────┐ ┌────────────┐ ┌──────────────┐
   │    User     │ │   Fund     │ │   Utility    │
   │   Service   │ │  Transfer  │ │   Payment    │
   │   (8083)    │ │  Service   │ │   Service    │
   │             │ │  (8084)    │ │   (8085)     │
   └──────┬──────┘ └─────┬──────┘ └──────┬───────┘
          │               │               │
          │    Feign      │    Feign      │    Feign
          ▼               ▼               ▼
   ┌─────────────────────────────────────────────┐
   │          core-banking-service (8092)          │
   │  ┌───────────┬──────────────┬──────────────┐ │
   │  │ Accounts  │ Transactions │    Users     │ │
   │  └───────────┴──────────────┴──────────────┘ │
   │                  MySQL                        │
   └───────────────────────────────────────────────┘

   Dependencies: user-service ──► core-banking (user lookup)
                 fund-transfer ──► core-banking (execute transfer)
                 utility-payment ──► core-banking (execute payment)
                 ALL ──► service-registry (Eureka)
                 ALL ──► config-server (configuration)
```

**Problems:**
- `core-banking-service` is a single point of failure and a deployment bottleneck
- Three services depend synchronously on `core-banking-service`
- Transaction data is duplicated in fund-transfer and utility-payment databases
- No async communication; no event-driven decoupling

---

### 4.2 Proposed Architecture

```
                       ┌─────────────┐
                       │ API Gateway │
                       │   (8082)    │
                       └──┬──┬──┬───┘
                          │  │  │
             ┌────────────┘  │  └───────────┐
             ▼               ▼               │
      ┌─────────────┐ ┌────────────────┐    │
      │    User     │ │    Payment     │    │
      │   Service   │ │ Orchestration  │◄───┘
      │   (merged)  │ │   Service      │
      │             │ │ (fund+utility) │
      │ Keycloak +  │ └──┬──────┬─────┘
      │ Core Users  │    │      │
      └─────────────┘    │      │ publish
                         │      ▼
               Feign     │  ┌────────────┐    consume    ┌──────────────┐
               ──────────┘  │  Message   │──────────────►│ Notification │
                            │  Broker    │               │   Service    │
                            │ (RabbitMQ) │               └──────────────┘
                            └──────┬─────┘
                                   │ consume
                                   ▼
                    ┌──────────────────────────────┐
                    │   Transaction Service (new)   │
                    │   - Execute fund transfers     │
                    │   - Execute utility payments   │
                    │   - Record transactions         │
                    └──────────────┬────────────────┘
                                   │ Feign
                                   ▼
                    ┌──────────────────────────────┐
                    │    Account Service (new)      │
                    │   - Balance management        │
                    │   - Account lifecycle          │
                    │   - Utility accounts           │
                    └──────────────────────────────┘

      ┌──────────────┐          ┌──────────────────┐
      │    Risk      │◄─────── │  Payment Orch.    │
      │   Service    │ (sync)  │  calls before     │
      │   (new)      │         │  processing       │
      └──────────────┘         └──────────────────┘

      Infrastructure (unchanged):
      ┌──────────────────┐  ┌──────────────────┐
      │ Service Registry │  │  Config Server   │
      │ Eureka (8081)    │  │     (8090)       │
      └──────────────────┘  └──────────────────┘
```

**Improvements:**
- `core-banking-service` eliminated; replaced by focused Account and Transaction services
- Single Payment Orchestration Service reduces operational complexity
- Event-driven notification via message broker
- Risk service provides pre-processing validation
- User data consolidated in one service
- Outbox/saga pattern ensures data consistency

---

## 5. Implementation Roadmap

### Phase 1 — Foundation (Weeks 1–4) — P0

| Task | Recommendation | Effort |
|------|---------------|--------|
| Extract shared DTO library | R3 | 1 week |
| Implement outbox pattern in existing services | R6 | 2 weeks |
| Add input validation annotations to all DTOs | Gap Analysis P0 | 1 week |
| Add currency field to payment payloads | Gap Analysis P0 | 1 week |

### Phase 2 — Core Decomposition (Weeks 5–10) — P0/P1

| Task | Recommendation | Effort |
|------|---------------|--------|
| Extract Account Service from core-banking | R1a | 2 weeks |
| Extract Transaction Service from core-banking | R1b | 2 weeks |
| Merge user endpoints into user-service | R1c | 1 week |
| Decommission core-banking-service | R1 | 1 week |

### Phase 3 — Payment Consolidation (Weeks 11–14) — P1

| Task | Recommendation | Effort |
|------|---------------|--------|
| Merge fund-transfer + utility-payment | R2 | 2 weeks |
| Implement Notification Service | R4 | 2 weeks |

### Phase 4 — Risk & Compliance (Weeks 15–18) — P2

| Task | Recommendation | Effort |
|------|---------------|--------|
| Implement Risk Service | R5 | 3 weeks |
| Add fraud detection rules | R5 | 1 week |

---

## 6. Risk Assessment

| Risk | Mitigation |
|------|-----------|
| Data migration errors during service split | Run dual-write period; reconciliation jobs; feature flags |
| API breaking changes for gateway consumers | Keep existing API paths; version new endpoints as v2 |
| Increased network latency from more service calls | Use async messaging where possible; cache account data |
| Team unfamiliarity with saga/outbox patterns | Start with simple outbox poller before moving to CDC |
| Deployment complexity increase | Invest in CI/CD pipeline improvements and Kubernetes manifests |

---

## 7. Success Metrics

| Metric | Current | Target |
|--------|---------|--------|
| Services with single bounded context | 2/6 (33%) | 8/8 (100%) |
| Duplicated DTO classes | ~20 files | 0 (shared library) |
| Payment data consistency | Eventual (no guarantee) | Guaranteed (outbox/saga) |
| Mean time to deploy a payment change | Requires deploying 3 services | 1 service |
| Input validation coverage | 0% | 100% |
| ISO 20022 field coverage | ~15% | >80% |
