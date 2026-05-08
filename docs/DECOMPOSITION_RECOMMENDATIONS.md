# Decomposition Recommendations

## 1. Executive Summary

Based on the Business Capability Map and Payment Gap Analysis, this document recommends restructuring the service architecture to align service boundaries with business domains, eliminate code duplication, and address the critical gaps in the payment domain.

**Core Thesis:** The current architecture has the wrong services being "micro" and the wrong service being "macro." Core-banking-service is an oversized monolith handling 7+ business capabilities, while fund-transfer-service and utility-payment-service are too thin to justify separate deployment. The recommended approach is to decompose core-banking into domain-aligned services and consolidate the two payment orchestration services into a unified payment service with real business logic.

---

## 2. Recommendation Summary

| # | Recommendation | Action | Priority | Impact | Feasibility |
|---|---------------|--------|----------|--------|-------------|
| R-01 | Merge fund-transfer and utility-payment into a unified Payment Service | **MERGE** | HIGH | HIGH | MEDIUM |
| R-02 | Extract Account Service from core-banking | **SPLIT** | HIGH | HIGH | MEDIUM |
| R-03 | Extract Transaction/Ledger Service from core-banking | **SPLIT** | HIGH | HIGH | HIGH |
| R-04 | Create a shared library for common code | **NEW** | HIGH | MEDIUM | HIGH |
| R-05 | Introduce domain events (async messaging) | **RESTRUCTURE** | MEDIUM | HIGH | MEDIUM |
| R-06 | Introduce a Notification Service | **NEW** | MEDIUM | MEDIUM | HIGH |
| R-07 | Introduce an Audit Service | **NEW** | MEDIUM | MEDIUM | MEDIUM |
| R-08 | Consolidate user domain ownership | **RESTRUCTURE** | LOW | MEDIUM | LOW |

---

## 3. Detailed Recommendations

### R-01: Merge Fund-Transfer and Utility-Payment into a Unified Payment Service

#### Business Justification

The fund-transfer-service and utility-payment-service are structurally identical:
- Both accept a payment request, save a PENDING/PROCESSING entity, call core-banking via Feign, and update status to SUCCESS.
- Both have zero independent business rules.
- Both duplicate the same DTOs, exception handling, mapper patterns, and Feign configuration.
- Maintaining two services for what is essentially the same orchestration pattern doubles the operational burden (deployments, monitoring, database schemas) with no business benefit.

A unified Payment Service can house all payment types (fund transfer, utility payment, and future types like bill pay, P2P, international transfers) under a single domain boundary, with shared validation, idempotency, and error handling.

#### Technical Approach

1. Create a new `internet-banking-payment-service` with a unified domain model:
   - `Payment` entity with a `paymentType` discriminator (FUND_TRANSFER, UTILITY_PAYMENT, etc.)
   - Shared status lifecycle: PENDING → PROCESSING → SUCCESS / FAILED
   - Single database schema: `banking_payment_service`

2. Consolidate endpoints:
   - `POST /api/v1/payments/fund-transfer` — initiate fund transfer
   - `POST /api/v1/payments/utility-payment` — initiate utility payment
   - `GET /api/v1/payments` — list all payments (with type filter)
   - `GET /api/v1/payments/{id}` — get payment by ID

3. Add the missing business logic that justifies this service's existence:
   - Input validation (amount limits, null checks, account format)
   - Idempotency key support (`Idempotency-Key` header)
   - Duplicate detection (check for matching request within time window)
   - Compensation/rollback on Feign failure (update status to FAILED, publish event)
   - Currency code on all payment requests

4. Retire `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`.

#### Proposed Payment Entity

```java
@Entity
@Table(name = "payment")
public class PaymentEntity extends AuditAware {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private PaymentType paymentType;        // FUND_TRANSFER, UTILITY_PAYMENT

    private String idempotencyKey;           // Client-supplied deduplication key
    private String endToEndId;               // ISO 20022 EndToEndId
    private String sourceAccount;            // Debtor account number
    private String destinationAccount;       // Creditor account number (fund transfer)
    private Long utilityProviderId;           // Creditor provider ID (utility payment)
    private String referenceNumber;           // Remittance reference
    private BigDecimal amount;
    private String currencyCode;             // ISO 4217 currency code
    private String initiatedBy;              // Auth user ID (from X-Auth-Id header)
    private String transactionReference;     // UUID from core-banking

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;            // PENDING, PROCESSING, SUCCESS, FAILED

    private String failureReason;            // Structured error code on failure
}
```

---

### R-02: Extract Account Service from Core-Banking

#### Business Justification

Account management (creation, lookup, status changes, balance queries) is a distinct business domain from transaction execution. Currently, `core-banking-service` owns both, which means:
- Account schema changes force redeployment of transaction logic.
- Account queries and transaction writes compete for the same database connection pool.
- No independent scaling: read-heavy account lookups cannot scale separately from write-heavy transaction processing.

#### Technical Approach

1. Create `account-service` owning:
   - `banking_core_account` table (bank accounts)
   - `banking_core_utility_account` table (utility provider accounts)
   - Account CRUD operations
   - Balance read operations (for validation purposes)
   - Account status management (PENDING, ACTIVE, DORMANT, BLOCKED)

2. Expose APIs:
   - `GET /api/v1/accounts/{account_number}` — get bank account
   - `GET /api/v1/accounts/utility/{provider_id}` — get utility account
   - `PATCH /api/v1/accounts/{account_number}/status` — update account status
   - `POST /api/v1/accounts/{account_number}/debit` — atomic debit (called by ledger service)
   - `POST /api/v1/accounts/{account_number}/credit` — atomic credit (called by ledger service)

3. Add missing validation:
   - Account status checks (reject transactions on DORMANT/BLOCKED accounts)
   - Optimistic locking (`@Version`) on balance fields to prevent race conditions

---

### R-03: Extract Transaction/Ledger Service from Core-Banking

#### Business Justification

Transaction recording and balance mutation are the most critical financial operations in the system. They deserve:
- Independent scaling under load
- Strict transactional guarantees
- A dedicated database optimized for append-only ledger writes
- Clear ownership of the double-entry bookkeeping domain

This also resolves the current problem where transaction data is fragmented across 3 databases (core-banking, fund-transfer, utility-payment).

#### Technical Approach

1. Create `ledger-service` owning:
   - `banking_core_transaction` table (the single source of truth for all transactions)
   - Balance mutation operations (debit/credit with proper locking)
   - Transaction ID generation (UUID + structured format)

2. Expose APIs:
   - `POST /api/v1/ledger/fund-transfer` — execute atomic fund transfer (debit + credit + record)
   - `POST /api/v1/ledger/debit` — execute single-account debit (for utility payments)
   - `GET /api/v1/ledger/transactions` — query transactions (with filters)
   - `GET /api/v1/ledger/transactions/{transactionId}` — get transaction by ID

3. Fix the double-deduction bug during extraction:
   ```java
   // CURRENT (buggy):
   fromAccount.setActualBalance(fromAccount.getActualBalance().subtract(amount));
   fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(amount)); // double-deduction

   // FIXED:
   BigDecimal newBalance = fromAccount.getActualBalance().subtract(amount);
   fromAccount.setActualBalance(newBalance);
   fromAccount.setAvailableBalance(newBalance);
   ```

4. Add optimistic locking on `BankAccountEntity` to prevent concurrent balance corruption.

---

### R-04: Create a Shared Library for Common Code

#### Business Justification

9+ components are copy-pasted across 3–4 services. Any bug fix or enhancement must be manually replicated in each service, creating maintenance overhead and inevitable drift.

#### Technical Approach

1. Create `banking-common` library (published as a Gradle/Maven artifact):
   ```
   banking-common/
   ├── src/main/java/com/javatodev/finance/common/
   │   ├── audit/AuditAware.java
   │   ├── exception/
   │   │   ├── GlobalExceptionHandler.java
   │   │   ├── ErrorResponse.java
   │   │   └── SimpleBankingGlobalException.java
   │   ├── filter/AppAuthUserFilter.java
   │   ├── mapper/BaseMapper.java
   │   ├── model/TransactionStatus.java
   │   └── config/CustomFeignClientConfiguration.java
   ```

2. Each microservice adds `banking-common` as a dependency and removes its local copies.

3. Add shared DTOs for inter-service communication (request/response contracts).

---

### R-05: Introduce Domain Events (Async Messaging)

#### Business Justification

All inter-service communication is currently synchronous REST via OpenFeign. This creates:
- Tight coupling: if core-banking is down, fund-transfer and utility-payment fail immediately.
- No event trail: other services cannot react to "payment completed" or "user registered" events.
- No compensation mechanism: failed Feign calls leave transactions stuck in PENDING/PROCESSING.

Domain events enable loose coupling, enable the Notification Service (R-06) and Audit Service (R-07), and support eventual consistency patterns (saga/choreography).

#### Technical Approach

1. Activate the already-declared RabbitMQ dependency (mentioned in docker-compose but not used in code).

2. Define domain events:
   - `PaymentInitiated` — published by Payment Service when a new payment is created
   - `PaymentCompleted` — published by Ledger Service after successful execution
   - `PaymentFailed` — published by Payment Service or Ledger Service on failure
   - `UserRegistered` — published by User Service after Keycloak user creation
   - `UserApproved` — published by User Service after admin approval
   - `AccountDebited` / `AccountCredited` — published by Ledger Service

3. Consumers:
   - Notification Service subscribes to all events for email/SMS notifications
   - Audit Service subscribes to all events for compliance logging
   - Payment Service subscribes to `PaymentCompleted` / `PaymentFailed` to update its local status

---

### R-06: Introduce a Notification Service

#### Business Justification

Users currently receive no feedback when they register, when their account is approved, or when a payment is processed. This is a fundamental user experience gap for a banking application.

#### Technical Approach

1. Create `notification-service` that subscribes to domain events (R-05).
2. Support email (via SMTP/SES), SMS (via SNS/Twilio), and in-app notifications.
3. Template-based message rendering per event type.
4. Notification preferences per user (opt-in/opt-out by channel).

---

### R-07: Introduce an Audit Service

#### Business Justification

Financial services require comprehensive audit trails for regulatory compliance (SOX, PCI-DSS, AML/KYC). The current system has only JPA audit timestamps and no formal who-did-what logging.

#### Technical Approach

1. Create `audit-service` that subscribes to domain events (R-05).
2. Immutable append-only audit log with: timestamp, actor, action, entity type, entity ID, before/after state.
3. Query API for compliance officers to search and filter audit entries.

---

### R-08: Consolidate User Domain Ownership

#### Business Justification

User data is currently split between core-banking-service (`banking_core_user`: name, email, NIC) and user-service (`user`: authId, status). During registration, user-service calls core-banking to look up the user by NIC, creating a circular dependency. The user domain should have a single owner.

#### Technical Approach

1. Migrate `banking_core_user` data into the user-service's database.
2. User-service becomes the single source of truth for all user data (profile + auth + status).
3. Other services query user-service (or consume UserCreated/UserUpdated events) instead of core-banking for user lookups.
4. Remove user management APIs from core-banking-service.

---

## 4. Dependency Diagrams

### 4.1 Current Architecture

```
                           ┌───────────────────┐
                           │    API Gateway     │
                           │     (8082)         │
                           │  OAuth2 + Routing  │
                           └─┬───┬───┬───┬─────┘
                             │   │   │   │
              ┌──────────────┘   │   │   └──────────────┐
              │                  │   │                   │
              ▼                  ▼   ▼                   ▼
    ┌─────────────────┐  ┌────────────────┐  ┌──────────────────┐
    │  User Service   │  │ Fund Transfer  │  │ Utility Payment  │
    │    (8085)       │  │   Service      │  │    Service       │
    │                 │  │   (8084)       │  │    (8085)        │
    │ ·Registration   │  │               │  │                  │
    │ ·Keycloak CRUD  │  │ ·Orchestrate  │  │ ·Orchestrate     │
    │ ·Status mgmt    │  │ ·Store status │  │ ·Store status    │
    └───────┬─────────┘  └──────┬────────┘  └───────┬──────────┘
            │                   │                    │
            │    Feign          │    Feign            │    Feign
            │  (sync REST)      │  (sync REST)        │  (sync REST)
            │                   │                    │
            └──────────┐        │         ┌──────────┘
                       │        │         │
                       ▼        ▼         ▼
              ┌──────────────────────────────────┐
              │        Core Banking Service       │
              │            (8092)                  │
              │                                    │
              │  ·User CRUD      ·Account CRUD     │
              │  ·Balance mgmt   ·Fund transfer    │
              │  ·Utility pay    ·Transaction log   │
              │  ·Utility accts                     │
              │                                    │
              │  [banking_core_service schema]      │
              │  4 tables, 7+ capabilities          │
              └──────────────────────────────────┘

    Infrastructure:
    ┌──────────────┐  ┌──────────────┐  ┌──────────┐
    │   Eureka     │  │ Config Server│  │ Keycloak │
    │   (8081)     │  │   (8090)     │  │  (8080)  │
    └──────────────┘  └──────────────┘  └──────────┘

    Problems:
    ✗ Core-banking is a monolith (7+ capabilities)
    ✗ Fund-transfer & utility-payment are anemic proxies
    ✗ All communication is synchronous
    ✗ Transaction data fragmented across 3 databases
    ✗ 9+ duplicated code components
    ✗ No event-driven communication
```

### 4.2 Proposed Architecture

```
                           ┌───────────────────┐
                           │    API Gateway     │
                           │     (8082)         │
                           │  OAuth2 + Routing  │
                           │  + Rate Limiting   │
                           └─┬───┬───┬───┬─────┘
                             │   │   │   │
              ┌──────────────┘   │   │   └──────────────┐
              │                  │   │                   │
              ▼                  ▼   ▼                   ▼
    ┌─────────────────┐  ┌────────────────┐  ┌──────────────────┐
    │  User Service   │  │   Payment      │  │  Account Service │
    │                 │  │   Service      │  │                  │
    │ ·Registration   │  │ (merged)       │  │ ·Bank accounts   │
    │ ·Profile CRUD   │  │               │  │ ·Utility accts   │
    │ ·Keycloak IAM   │  │ ·Fund transfer│  │ ·Balance queries │
    │ ·Status mgmt    │  │ ·Utility pay  │  │ ·Status mgmt     │
    │ ·All user data  │  │ ·Validation   │  │ ·Optimistic lock │
    │                 │  │ ·Idempotency  │  │                  │
    │                 │  │ ·Dedup        │  │                  │
    └───────┬─────────┘  └──────┬────────┘  └───────┬──────────┘
            │                   │                    │
            │   Events          │   REST + Events     │   REST
            │                   │                    │
            │                   ▼                    │
            │         ┌──────────────────┐           │
            │         │  Ledger Service  │◄──────────┘
            │         │                  │
            │         │ ·Debit / Credit  │
            │         │ ·Double-entry    │
            │         │ ·Transaction log │
            │         │ ·Atomic balance  │
            │         │  mutations       │
            │         └────────┬─────────┘
            │                  │
            │      Domain Events (RabbitMQ)
            │                  │
            ▼                  ▼
    ┌──────────────────────────────────────────┐
    │           Message Broker (RabbitMQ)       │
    │                                          │
    │  Events: PaymentInitiated,               │
    │  PaymentCompleted, PaymentFailed,        │
    │  UserRegistered, UserApproved,           │
    │  AccountDebited, AccountCredited         │
    └────────┬──────────┬──────────┬───────────┘
             │          │          │
             ▼          ▼          ▼
    ┌──────────────┐ ┌──────────┐ ┌──────────┐
    │ Notification │ │  Audit   │ │ (Future) │
    │   Service    │ │ Service  │ │ Reporting│
    │              │ │          │ │ Service  │
    │ ·Email/SMS   │ │ ·Immut.  │ │          │
    │ ·Templates   │ │  log     │ │          │
    │ ·Preferences │ │ ·Query   │ │          │
    └──────────────┘ └──────────┘ └──────────┘

    Shared:
    ┌─────────────────────────────┐
    │     banking-common library  │
    │  AuditAware, BaseMapper,   │
    │  ErrorResponse, Exception, │
    │  FeignConfig, AuthFilter   │
    └─────────────────────────────┘

    Infrastructure (unchanged):
    ┌──────────────┐  ┌──────────────┐  ┌──────────┐
    │   Eureka     │  │ Config Server│  │ Keycloak │
    │   (8081)     │  │   (8090)     │  │  (8080)  │
    └──────────────┘  └──────────────┘  └──────────┘

    Improvements:
    ✓ Each service owns one business domain
    ✓ Payment Service has real business logic (validation, idempotency)
    ✓ Ledger Service is the single transaction source of truth
    ✓ Account Service owns all account data with proper locking
    ✓ Event-driven communication enables loose coupling
    ✓ Notification and Audit services added
    ✓ Shared library eliminates code duplication
```

---

## 5. Prioritized Implementation Roadmap

### Phase 1: Fix Critical Issues & Quick Wins (Sprints 1–2)

| Step | Recommendation | Effort | Risk if Deferred |
|------|---------------|--------|------------------|
| 1.1 | Fix the double-deduction bug in `TransactionService` | Small | Financial loss (CRITICAL) |
| 1.2 | Add input validation (amount > 0, required fields) to fund-transfer and utility-payment | Small | Fraudulent transactions (CRITICAL) |
| 1.3 | Create `banking-common` shared library (R-04) | Medium | Ongoing code drift |
| 1.4 | Add account status validation in `TransactionService` | Small | Compliance violation |
| 1.5 | Add optimistic locking (`@Version`) to `BankAccountEntity` | Small | Race condition overdrafts |

### Phase 2: Merge Payment Services (Sprints 3–5)

| Step | Recommendation | Effort | Risk if Deferred |
|------|---------------|--------|------------------|
| 2.1 | Create unified Payment Service (R-01) with merged fund-transfer + utility-payment | Large | Continued code duplication |
| 2.2 | Add idempotency key support to Payment Service | Medium | Duplicate transactions (CRITICAL) |
| 2.3 | Add compensation/rollback logic for Feign failures | Medium | Stuck PENDING transactions |
| 2.4 | Add currency code to payment payloads | Small | Multi-currency blocked |
| 2.5 | Add end-to-end ID generation and propagation | Small | No audit trail |
| 2.6 | Retire old fund-transfer and utility-payment services | Small | — |

### Phase 3: Decompose Core-Banking (Sprints 6–9)

| Step | Recommendation | Effort | Risk if Deferred |
|------|---------------|--------|------------------|
| 3.1 | Extract Account Service (R-02) from core-banking | Large | Monolith scaling issues |
| 3.2 | Extract Ledger Service (R-03) from core-banking | Large | Transaction fragmentation |
| 3.3 | Migrate data from 3 transaction tables to unified ledger | Large | Data inconsistency |
| 3.4 | Consolidate user domain (R-08) — move user data to user-service | Medium | Split domain ownership |
| 3.5 | Remove deprecated APIs from core-banking (may retire entirely) | Medium | — |

### Phase 4: Event-Driven & New Services (Sprints 10–12)

| Step | Recommendation | Effort | Risk if Deferred |
|------|---------------|--------|------------------|
| 4.1 | Introduce RabbitMQ messaging and domain events (R-05) | Large | Tight coupling persists |
| 4.2 | Create Notification Service (R-06) | Medium | Users uninformed |
| 4.3 | Create Audit Service (R-07) | Medium | Compliance gap |
| 4.4 | Add structured error responses (ISO 20022 pain.002 status codes) | Medium | API quality |
| 4.5 | Add ISO 20022 party information (debtor/creditor names, addresses) | Medium | Regulatory compliance |

---

## 6. Migration Strategy

### Recommended Approach: Strangler Fig Pattern

Rather than a big-bang rewrite, use the Strangler Fig pattern:

1. **New services are built alongside existing ones.** The API Gateway routes traffic to the new service for new endpoints, while existing endpoints continue to be served by the old services.

2. **Gradually migrate traffic.** Once a new service is validated in production, update the Gateway to route existing endpoint paths to the new service.

3. **Retire old services** only after all their endpoints have been migrated and no traffic is flowing to them.

```
Phase 1: Fix bugs in-place
         core-banking ──────────────────────►  core-banking (patched)

Phase 2: New Payment Service alongside old
         fund-transfer  ─┐
                         ├──► Payment Service (new, parallel)
         utility-payment─┘
         Gateway routes new endpoints → Payment Service
         Gateway routes old endpoints → old services (unchanged)
         After validation: retire old services

Phase 3: Extract from core-banking
         core-banking ──► Account Service (new, parallel)
                      ──► Ledger Service (new, parallel)
         Gateway routes account endpoints → Account Service
         Gateway routes ledger endpoints → Ledger Service
         After validation: retire core-banking

Phase 4: Add event-driven services
         Add RabbitMQ → Notification Service, Audit Service
         No migration needed — these are net-new capabilities
```

---

## 7. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Data migration errors during ledger consolidation | Medium | Critical | Run dual-write period; reconciliation checks; rollback plan |
| Service boundary wrong on first attempt | Low | Medium | Strangler Fig allows incremental correction |
| Increased operational complexity (more services) | High | Medium | Shared library, standardized CI/CD, centralized observability |
| Performance regression during decomposition | Medium | Medium | Load test before and after; canary deployments |
| Team skill gap for event-driven architecture | Medium | Low | Training; start with simple event patterns; expand gradually |
| Dependency on RabbitMQ availability | Low | High | HA cluster setup; fallback to synchronous for critical paths |

---

## 8. Success Metrics

| Metric | Current | Target |
|--------|---------|--------|
| Duplicated code components across services | 9+ | 0 (via shared library) |
| Business capabilities per service (max) | 7 (core-banking) | 2–3 |
| Independent business logic in payment services | ~0 lines | Full validation + idempotency |
| Transaction data sources | 3 databases | 1 (unified ledger) |
| Payment ISO 20022 compliance | ~10–15% | >60% |
| Services with no error handling for failures | 2 | 0 |
| Double-deduction bug status | Active | Fixed |
| Idempotent payment endpoints | 0 | All POST endpoints |
