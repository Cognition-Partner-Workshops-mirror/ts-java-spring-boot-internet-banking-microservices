# Decomposition Recommendations

> **Scope**: Service restructuring recommendations for the Internet Banking Microservices platform
> **Based on**: [Business Capability Map](./BUSINESS_CAPABILITY_MAP.md) and [Payment Gap Analysis](./PAYMENT_GAP_ANALYSIS.md)
> **Date**: May 2026

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Recommendation 1 — Decompose core-banking-service](#2-recommendation-1--decompose-core-banking-service)
3. [Recommendation 2 — Merge Fund Transfer and Utility Payment into a Payment Orchestration Service](#3-recommendation-2--merge-fund-transfer-and-utility-payment-into-a-payment-orchestration-service)
4. [Recommendation 3 — Extract a Shared Library](#4-recommendation-3--extract-a-shared-library)
5. [Recommendation 4 — Introduce Asynchronous Messaging](#5-recommendation-4--introduce-asynchronous-messaging)
6. [Recommendation 5 — Add a Notification Service](#6-recommendation-5--add-a-notification-service)
7. [Current vs. Proposed Architecture](#7-current-vs-proposed-architecture)
8. [Prioritization Matrix](#8-prioritization-matrix)
9. [Migration Sequence](#9-migration-sequence)

---

## 1. Executive Summary

The current architecture has 6 microservices organized around a technical layering pattern (thin orchestrators calling a monolithic core). This assessment recommends 5 structural changes that realign services to business domains, reduce duplication, and address critical payment gaps:

| # | Recommendation                                      | Type    | Impact  | Feasibility | Priority |
|---|-----------------------------------------------------|---------|---------|-------------|----------|
| 1 | Decompose `core-banking-service` into 3 services   | Split   | **High**  | Medium      | **P1**   |
| 2 | Merge fund-transfer + utility-payment services      | Merge   | **High**  | High        | **P1**   |
| 3 | Extract shared library module                        | Extract | **Medium**| High        | **P1**   |
| 4 | Introduce async messaging (RabbitMQ/Kafka)          | Add     | **High**  | Medium      | **P2**   |
| 5 | Add notification service                             | Add     | **Medium**| Medium      | **P3**   |

---

## 2. Recommendation 1 — Decompose core-banking-service

### Problem

`core-banking-service` contains three distinct bounded contexts in a single deployable:

- **User Management**: `UserController`, `UserService`, `UserEntity`, `UserRepository`
- **Account Management**: `AccountController`, `AccountService`, `BankAccountEntity`, `UtilityAccountEntity`, repositories
- **Transaction Execution**: `TransactionController`, `TransactionService`, `TransactionEntity`, `TransactionRepository`

This creates a monolith inside the microservices architecture. All payment processing, account queries, and user lookups are bottlenecked through one service with one database.

### Business Justification

- **Independent scaling**: Transaction execution has fundamentally different load patterns than user lookups. During peak payment windows, the transaction engine needs to scale without scaling the user management layer.
- **Independent deployment**: Changes to user profile fields should not require redeploying the transaction engine (and vice versa), reducing deployment risk.
- **Fault isolation**: A bug in transaction processing should not take down account inquiry or user lookup.
- **Team ownership**: Different teams can own different bounded contexts with clear API contracts.

### Technical Approach

Split `core-banking-service` into:

| New Service               | Owns                                         | Database Schema                  |
|---------------------------|----------------------------------------------|----------------------------------|
| `account-service`         | BankAccountEntity, UtilityAccountEntity       | `banking_core_account`, `banking_core_utility_account` |
| `transaction-service`     | TransactionEntity, balance updates            | `banking_core_transaction` (+ writes to account balances via API or shared DB initially) |
| `core-user-service`       | UserEntity                                    | `banking_core_user`              |

**Step-by-step migration**:

1. **Phase A — Code separation**: Create three new Gradle projects. Move controllers, services, entities, and repositories into the appropriate project. Each service gets its own Flyway migration set.
2. **Phase B — Database separation**: Initially all three services can share the same MySQL instance (different schemas). Over time, migrate to separate database instances.
3. **Phase C — API contract stabilization**: Define explicit OpenAPI contracts for each new service. Update Feign clients in fund-transfer-service, utility-payment-service, and user-service.
4. **Phase D — Decommission**: Remove the old `core-banking-service` once all consumers have migrated.

**Key risk**: The `TransactionService.internalFundTransfer()` method directly updates `BankAccountEntity` balances via the `BankAccountRepository`. After decomposition, the transaction-service will need to call the account-service API to update balances, introducing a distributed transaction concern. This should be addressed alongside Recommendation 4 (async messaging / saga pattern).

---

## 3. Recommendation 2 — Merge Fund Transfer and Utility Payment into a Payment Orchestration Service

### Problem

`internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service` are structurally identical thin orchestrators:

```
fund-transfer-service                    utility-payment-service
├── FundTransferController               ├── UtilityPaymentController
├── FundTransferService                  ├── UtilityPaymentService
├── FundTransferEntity                   ├── UtilityPaymentEntity
├── FundTransferRepository               ├── UtilityPaymentRepository
├── BankingCoreFeignClient               ├── BankingCoreRestClient
├── CustomFeignClientConfiguration       ├── CustomFeignClientConfiguration
├── AppAuthUserFilter                    ├── AppAuthUserFilter
├── GlobalExceptionHandler               ├── GlobalExceptionHandler
├── AuditAware                           ├── AuditAware
└── TransactionStatus                    └── TransactionStatus
```

Both follow the identical pattern: save entity with initial status -> Feign call to core-banking -> update entity with final status -> return response. Maintaining two separate services doubles the infrastructure cost, deployment overhead, and code duplication without providing any business benefit.

### Business Justification

- **Operational simplification**: One service to deploy, monitor, and scale instead of two.
- **Unified payment history**: All payment records in one database enables cross-payment-type queries (e.g., "show all outgoing transactions this month").
- **Extensibility**: Adding a new payment type (e.g., bill pay, P2P, QR payments) becomes adding a new controller endpoint and entity rather than spinning up an entirely new microservice.
- **Consistent payment standards**: ISO 20022 gaps identified in the Payment Gap Analysis can be addressed once in a unified payment domain model.

### Technical Approach

Create `internet-banking-payment-service` that combines both:

| Endpoint                        | Method | Description                       |
|---------------------------------|--------|-----------------------------------|
| `POST /api/v1/payments/transfer`| POST   | Fund transfer (replaces `/api/v1/transfer`) |
| `GET  /api/v1/payments/transfer`| GET    | List fund transfers               |
| `POST /api/v1/payments/utility` | POST   | Utility payment (replaces `/api/v1/utility-payment`) |
| `GET  /api/v1/payments/utility` | GET    | List utility payments             |
| `GET  /api/v1/payments`         | GET    | List all payments (new)           |

**Step-by-step migration**:

1. **Phase A**: Create `internet-banking-payment-service` with both controllers, services, entities, and repositories from the two existing services.
2. **Phase B**: Introduce a common `PaymentEntity` supertype with shared fields (id, amount, status, transactionReference, account, timestamps) and type-specific subtypes via JPA `@Inheritance`.
3. **Phase C**: Update API gateway routes to point `/fund-transfer/**` and `/payment/**` to the new service.
4. **Phase D**: Decommission the two old services.
5. **Phase E**: Add the ISO 20022 fields (currency, idempotency key, remittance info, debtor/creditor party info) to the unified payment model.

**Key benefit**: The unified service is the natural place to implement payment-level cross-cutting concerns like idempotency, duplicate detection, transfer limits, and payment scheduling.

---

## 4. Recommendation 3 — Extract a Shared Library

### Problem

12+ classes are copy-pasted across 3-4 services:

| Class                            | Copies |
|----------------------------------|--------|
| `AuditAware`                     | 3      |
| `GlobalExceptionHandler`         | 4      |
| `ErrorResponse`                  | 4      |
| `SimpleBankingGlobalException`   | 4      |
| `AppAuthUserFilter`              | 3      |
| `ApiRequestContext`              | 3      |
| `ApiRequestContextHolder`        | 3      |
| `CustomFeignClientConfiguration` | 2      |
| `TransactionStatus`              | 2      |
| `FundTransferRequest`            | 2      |
| `UtilityPaymentRequest`          | 3      |
| `AccountResponse`                | 2      |

### Business Justification

- **Consistency**: A bug fix (e.g., the `GlobalExceptionHandler` returning 400 for all errors) must be applied once, not in 4 places.
- **Developer velocity**: New services inherit all cross-cutting concerns automatically.
- **Reduced code review burden**: Shared behavior is reviewed and tested once.

### Technical Approach

Create two shared modules published to an internal Maven/Gradle repository (or use Gradle composite builds):

| Module                          | Contains                                                                     |
|---------------------------------|------------------------------------------------------------------------------|
| `banking-common`                | `AuditAware`, `GlobalExceptionHandler`, `ErrorResponse`, `SimpleBankingGlobalException`, `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`, `TransactionStatus` |
| `banking-core-api-client`       | Feign client interfaces, request/response DTOs (`FundTransferRequest`, `FundTransferResponse`, `UtilityPaymentRequest`, `UtilityPaymentResponse`, `AccountResponse`), `CustomFeignClientConfiguration` |

**Step-by-step migration**:

1. Create a `banking-common` Gradle subproject at the repo root.
2. Move shared classes into it with proper packaging.
3. Add `banking-common` as a dependency in each service's `build.gradle`.
4. Delete the duplicated classes from each service.
5. Fix any package import differences.
6. Create `banking-core-api-client` for the Feign client contracts and shared DTOs.

---

## 5. Recommendation 4 — Introduce Asynchronous Messaging

### Problem

All inter-service communication is synchronous via OpenFeign. This creates:
- **Tight coupling**: Caller blocks until callee responds; any slowdown cascades.
- **No fault tolerance**: No circuit breaker, no retry, no fallback. If core-banking-service is down, all payments fail immediately.
- **No compensation**: Failed Feign calls leave payment records in PENDING/PROCESSING forever.
- **No event notification**: Other services (e.g., a future notification service) cannot react to payment events.

### Business Justification

- **Resilience**: Payments can be queued and retried automatically when the downstream service recovers.
- **Decoupling**: New consumers (notifications, analytics, audit) can subscribe to payment events without modifying the payment service.
- **Saga support**: Enables the choreography or orchestration patterns needed for distributed transaction management across the decomposed services.
- **Eventual consistency**: Addresses the dual-write problem between the orchestrating service and core-banking.

### Technical Approach

The `docker-compose.yml` already references RabbitMQ but it is not used in code. Activate it:

| Event                        | Producer                  | Consumers                                     | Queue/Exchange          |
|------------------------------|---------------------------|-----------------------------------------------|-------------------------|
| `PaymentInitiated`           | payment-service           | transaction-service                            | `payment.initiated`     |
| `PaymentCompleted`           | transaction-service       | payment-service, notification-service           | `payment.completed`     |
| `PaymentFailed`              | transaction-service       | payment-service, notification-service           | `payment.failed`        |
| `UserRegistered`             | user-service              | notification-service                            | `user.registered`       |
| `UserApproved`               | user-service              | notification-service                            | `user.approved`         |
| `BalanceUpdated`             | transaction-service       | account-service (or reverse)                    | `account.balance`       |

**Step-by-step migration**:

1. Add Spring AMQP / Spring Cloud Stream dependencies to the services.
2. Define event classes in the shared library.
3. Implement the Transactional Outbox pattern: write events to an outbox table in the same database transaction as the business operation, then publish to RabbitMQ via a polling publisher or CDC.
4. Add Resilience4j circuit breakers around remaining synchronous Feign calls during the transition period.
5. Gradually replace synchronous Feign calls with async event-driven flows.

---

## 6. Recommendation 5 — Add a Notification Service

### Problem

There is no mechanism to notify users about:
- Transaction confirmations (fund transfer completed, utility payment processed)
- Transaction failures
- Account alerts (low balance, suspicious activity)
- Registration status changes (account approved)

### Business Justification

- **Regulatory compliance**: Many jurisdictions require transaction confirmations be sent to customers.
- **Customer experience**: Users expect real-time notifications for financial transactions.
- **Fraud mitigation**: Alert on suspicious transactions enables faster customer response.

### Technical Approach

| Aspect            | Details                                                            |
|-------------------|--------------------------------------------------------------------|
| Service name      | `internet-banking-notification-service`                            |
| Trigger mechanism | Consumes events from RabbitMQ (depends on Recommendation 4)       |
| Channels          | Email (via SMTP/SES), SMS (via SNS/Twilio), Push (via FCM/APNs)  |
| Template engine   | Thymeleaf or FreeMarker for email templates                        |
| Persistence       | Notification log table for audit and retry                         |

---

## 7. Current vs. Proposed Architecture

### Current Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        API Gateway                           │
│                    (JWT, X-Auth-Id)                          │
└──────┬────────────────┬──────────────────┬──────────────────┘
       │                │                  │
       ▼                ▼                  ▼
┌──────────────┐ ┌──────────────┐ ┌────────────────────┐
│ user-service │ │fund-transfer │ │ utility-payment    │
│              │ │  service     │ │    service         │
└──────┬───────┘ └──────┬───────┘ └─────────┬──────────┘
       │     Feign      │    Feign          │    Feign
       │                │                   │
       └────────────────▼───────────────────┘
              ┌──────────────────────┐
              │ core-banking-service │
              │ ┌──────┐ ┌────────┐ │
              │ │Users │ │Accounts│ │
              │ └──────┘ └────────┘ │
              │ ┌──────────────────┐│
              │ │  Transactions    ││
              │ └──────────────────┘│
              └──────────┬──────────┘
                         │
              ┌──────────▼──────────┐
              │   MySQL (4 schemas) │
              └─────────────────────┘
```

**Issues**: Star topology, monolithic core, duplicated thin orchestrators, synchronous-only, no events.

### Proposed Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                           API Gateway                                │
│                    (JWT, OAuth2, Rate Limiting)                      │
└──────┬────────────────┬──────────────────┬──────────────────────────┘
       │                │                  │
       ▼                ▼                  ▼
┌──────────────┐ ┌──────────────────┐ ┌────────────────────┐
│ user-service │ │ payment-service  │ │ notification-      │
│ + Keycloak   │ │ (transfer +      │ │    service         │
│              │ │  utility merged) │ │ (email/SMS/push)   │
└──────┬───────┘ └──────┬───────────┘ └─────────▲──────────┘
       │                │                       │ Events
       │    ┌───────────┼───────────────────────┤
       │    │           │                       │
       │    │    ┌──────▼───────────┐           │
       │    │    │  RabbitMQ /      │───────────┘
       │    │    │  Event Bus       │
       │    │    └──┬──────────┬────┘
       │    │       │          │
       ▼    ▼       ▼          ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│core-user-    │ │ account-     │ │ transaction- │
│  service     │ │   service    │ │   service    │
└──────┬───────┘ └──────┬───────┘ └──────┬───────┘
       │                │                │
       ▼                ▼                ▼
   ┌────────┐     ┌──────────┐    ┌──────────────┐
   │users DB│     │accounts  │    │transactions  │
   │        │     │   DB     │    │     DB       │
   └────────┘     └──────────┘    └──────────────┘
```

**Benefits**: Domain-aligned boundaries, event-driven communication, independent scaling, unified payment domain, fault isolation.

### Shared Library Dependency Graph

```
┌─────────────────────────────────────────────────────┐
│                    banking-common                     │
│  AuditAware, GlobalExceptionHandler, ErrorResponse,  │
│  AppAuthUserFilter, TransactionStatus, etc.          │
└──────────┬────────────┬──────────────┬──────────────┘
           │            │              │
           ▼            ▼              ▼
    user-service  payment-service  account-service ...

┌─────────────────────────────────────────────────────┐
│              banking-core-api-client                  │
│  Feign interfaces, Request/Response DTOs             │
└──────────┬────────────┬──────────────────────────────┘
           │            │
           ▼            ▼
    payment-service  user-service
```

---

## 8. Prioritization Matrix

### Priority 1 — High Impact, High Feasibility (Do First)

| # | Recommendation                        | Impact                                    | Effort   | Dependencies     |
|---|---------------------------------------|-------------------------------------------|----------|------------------|
| 3 | Extract shared library                | Eliminates 12+ duplicated classes         | **Small** | None            |
| 2 | Merge fund-transfer + utility-payment | Unified payment domain, halves services   | **Medium**| Shared library  |

**Rationale**: The shared library is the foundation for all other changes. Merging the payment services is straightforward (combining two nearly identical codebases) and immediately reduces operational overhead.

### Priority 2 — High Impact, Medium Feasibility (Do Next)

| # | Recommendation                         | Impact                                    | Effort   | Dependencies                |
|---|----------------------------------------|-------------------------------------------|----------|-----------------------------|
| 1 | Decompose core-banking-service         | Fault isolation, independent scaling      | **Large** | Shared library, async messaging |
| 4 | Introduce async messaging              | Resilience, decoupling, saga support      | **Large** | Shared library              |

**Rationale**: These are transformational changes that require careful planning and execution. The decomposition of core-banking-service should happen in conjunction with introducing async messaging to avoid creating distributed synchronous dependencies.

### Priority 3 — Medium Impact, Medium Feasibility (Do Later)

| # | Recommendation                  | Impact                                    | Effort   | Dependencies          |
|---|---------------------------------|-------------------------------------------|----------|-----------------------|
| 5 | Add notification service        | Regulatory compliance, customer experience| **Medium**| Async messaging      |

**Rationale**: Depends on the event bus being in place. Can be deferred until the foundational restructuring is complete.

---

## 9. Migration Sequence

### Phase 1: Foundation (Weeks 1-4)

```
Week 1-2: Extract banking-common shared library
  └── Move duplicated classes to shared module
  └── Update all service build.gradle files
  └── Verify all services build and pass tests

Week 3-4: Merge payment services
  └── Create internet-banking-payment-service
  └── Migrate fund-transfer + utility-payment code
  └── Update gateway routes
  └── Decommission old services
```

### Phase 2: Core Restructuring (Weeks 5-12)

```
Week 5-6: Add Resilience4j circuit breakers
  └── Wrap all Feign calls with circuit breakers
  └── Add retry policies and fallback handlers
  └── Fix the double-deduction bug in TransactionService

Week 7-8: Activate RabbitMQ
  └── Define event schemas in shared library
  └── Implement Transactional Outbox pattern
  └── Payment service publishes PaymentInitiated events

Week 9-12: Decompose core-banking-service
  └── Extract core-user-service
  └── Extract account-service
  └── Extract transaction-service
  └── Migrate database schemas
  └── Update all Feign clients and event consumers
```

### Phase 3: Enhancement (Weeks 13-16)

```
Week 13-14: Add notification service
  └── Implement event consumers for payment and user events
  └── Set up email/SMS delivery channels
  └── Add notification templates

Week 15-16: ISO 20022 alignment
  └── Add currency, idempotency key, party info to payment DTOs
  └── Implement transfer limits and duplicate detection
  └── Add structured status codes and reason codes
```

### Risk Mitigations During Migration

| Risk                                           | Mitigation                                                              |
|------------------------------------------------|-------------------------------------------------------------------------|
| Breaking existing API consumers                | Maintain backward-compatible gateway routes during transition           |
| Data migration errors                          | Run old and new services in parallel (dual-write) during cutover        |
| Increased latency from service decomposition   | Use async messaging to decouple where possible                          |
| Team coordination overhead                     | Assign clear service ownership before starting decomposition            |
| Regression bugs during merge/split             | Add comprehensive integration tests before any restructuring            |
