# Business Capability Map

## 1. Executive Summary

This document maps each of the 6 microservices in the Internet Banking platform to its business capabilities, identifies overlaps and gaps, and assesses whether service boundaries align with business domains or technical layers.

**Key Finding:** Service boundaries are drawn along a mix of business domain and technical orchestration lines. The core-banking-service acts as an oversized "god service" that owns users, accounts, and transactions — three distinct business domains — while the fund-transfer and utility-payment services are thin orchestration layers with almost no independent business logic. Significant code duplication exists across services, and several important business capabilities (notifications, reporting, audit) are entirely absent.

---

## 2. Microservice Inventory

| # | Service | Port | Technology Stack | Primary Role |
|---|---------|------|-----------------|--------------|
| 1 | `internet-banking-api-gateway` | 8082 | Spring Cloud Gateway, OAuth2/Keycloak | API routing, authentication |
| 2 | `internet-banking-service-registry` | 8081 | Netflix Eureka | Service discovery |
| 3 | `internet-banking-config-server` | 8090 | Spring Cloud Config | Centralized configuration |
| 4 | `core-banking-service` | 8092 | Spring Boot, Spring Data JPA, MySQL, Flyway | Accounts, users, transactions |
| 5 | `internet-banking-fund-transfer-service` | 8084 | Spring Boot, Spring Data JPA, OpenFeign, MySQL | Fund transfer orchestration |
| 6 | `internet-banking-utility-payment-service` | 8085 | Spring Boot, Spring Data JPA, OpenFeign, MySQL | Utility payment orchestration |

---

## 3. Business Capability Mapping

### 3.1 Capability-to-Service Matrix

| Business Capability | api-gateway | service-registry | config-server | core-banking | fund-transfer | utility-payment |
|---------------------|:-----------:|:----------------:|:-------------:|:------------:|:-------------:|:---------------:|
| **API Routing** | **PRIMARY** | | | | | |
| **Authentication & Authorization** | **PRIMARY** | | | | | |
| **Service Discovery** | | **PRIMARY** | | | | |
| **Configuration Management** | | | **PRIMARY** | | | |
| **User Management (Core)** | | | | **PRIMARY** | | |
| **User Registration & IAM** | | | | | | |
| **Account Management** | | | | **PRIMARY** | | |
| **Balance Management** | | | | **PRIMARY** | | |
| **Fund Transfer Orchestration** | | | | | **PRIMARY** | |
| **Fund Transfer Execution** | | | | **PRIMARY** | | |
| **Utility Payment Orchestration** | | | | | | **PRIMARY** |
| **Utility Payment Execution** | | | | **PRIMARY** | | |
| **Transaction Recording** | | | | **PRIMARY** | SECONDARY | SECONDARY |
| **Utility Provider Management** | | | | **PRIMARY** | | |
| **Audit Trail** | | | | PARTIAL | PARTIAL | PARTIAL |
| **Error Handling** | | | | DUPLICATE | DUPLICATE | DUPLICATE |
| **DTO Mapping** | | | | DUPLICATE | DUPLICATE | DUPLICATE |

> **Legend:** PRIMARY = owns the capability; SECONDARY = stores a local record; PARTIAL = incomplete implementation; DUPLICATE = same code copy-pasted across services

### 3.2 User Service (Special Case)

The `internet-banking-user-service` is listed separately because it spans a unique set of capabilities:

| Business Capability | user-service |
|---------------------|:------------:|
| **User Registration** | **PRIMARY** |
| **User Status Management** | **PRIMARY** |
| **IAM Integration (Keycloak)** | **PRIMARY** |
| **User Lookup (by NIC)** | Delegates to core-banking via Feign |
| **Error Handling** | DUPLICATE |
| **DTO Mapping** | DUPLICATE |

---

## 4. Detailed Capability Analysis per Service

### 4.1 internet-banking-api-gateway

**Business Capabilities:**
- **API Routing:** Routes requests to downstream services based on path prefix (`/user/**`, `/fund-transfer/**`, `/payment/**`, `/core/**`)
- **Authentication:** OAuth2 resource server with JWT validation via Keycloak
- **User Identity Propagation:** Extracts principal name and forwards as `X-Auth-Id` header

**Observations:**
- This is the only service that validates JWT tokens. Downstream services trust the `X-Auth-Id` header blindly.
- No rate limiting, request throttling, or API versioning at the gateway level.
- No request/response logging or payload transformation.

### 4.2 internet-banking-service-registry

**Business Capabilities:**
- **Service Discovery:** Netflix Eureka server enabling dynamic service location

**Observations:**
- Pure infrastructure service with no business logic. Correctly scoped.

### 4.3 internet-banking-config-server

**Business Capabilities:**
- **Configuration Management:** Serves externalized configuration from a Git repository for all services

**Observations:**
- Pure infrastructure service with no business logic. Correctly scoped.

### 4.4 core-banking-service

**Business Capabilities (7 capabilities in one service):**
1. **User Management:** CRUD operations for `banking_core_user` (name, email, NIC)
2. **Account Management:** Bank account lookup by number; utility account lookup by provider name or ID
3. **Balance Management:** Actual/available balance reads and writes on `banking_core_account`
4. **Fund Transfer Execution:** `TransactionService.internalFundTransfer()` — debits source, credits destination, records transaction entries
5. **Utility Payment Execution:** `TransactionService.utilPayment()` — debits source account, records transaction entry
6. **Transaction Recording:** Persists `TransactionEntity` records with type, amount, reference number
7. **Utility Provider Management:** Manages `banking_core_utility_account` entities (provider ID, name, number)

**Observations:**
- This service is a **monolith within the microservices architecture**. It owns the entire ledger, all account types, user data, and all transaction execution logic.
- Has its own Flyway migrations managing 4 tables in `banking_core_service` schema.
- The `TransactionService` contains the double-deduction bug affecting both fund transfers and utility payments.
- No separation between read and write paths (query vs. command).

### 4.5 internet-banking-fund-transfer-service

**Business Capabilities:**
1. **Fund Transfer Orchestration:** Accepts transfer requests, saves a PENDING entity, calls core-banking via Feign, updates entity to SUCCESS
2. **Fund Transfer History:** Paginated retrieval of fund transfer records

**Observations:**
- This service is a **thin orchestration proxy** — it delegates all real logic (validation, balance operations, transaction recording) to core-banking-service.
- Maintains its own `fund_transfer` table in a separate MySQL schema for tracking transfer status.
- Contains duplicate DTO classes (`FundTransferRequest`, `FundTransferResponse`, `UtilityPaymentRequest`, `UtilityPaymentResponse`, `AccountResponse`) that mirror core-banking DTOs.
- No business rules of its own: no amount limits, no duplicate checking, no scheduling.
- No error handling for Feign failures — if core-banking call fails, the entity stays PENDING forever.

### 4.6 internet-banking-utility-payment-service

**Business Capabilities:**
1. **Utility Payment Orchestration:** Accepts payment requests, saves a PROCESSING entity, calls core-banking via Feign, updates entity to SUCCESS
2. **Utility Payment History:** Paginated retrieval of utility payment records

**Observations:**
- Structurally identical to the fund-transfer-service; same orchestration pattern, same gap profile.
- Maintains its own `utility_payment` table in a separate MySQL schema.
- Contains duplicate DTO classes mirroring core-banking DTOs.
- No error handling for Feign failures — if core-banking call fails, the entity stays PROCESSING forever.
- Uses `BankingCoreRestClient` (Feign) instead of `BankingCoreFeignClient` — different naming convention from fund-transfer-service for the same pattern.

---

## 5. Capability Overlaps

### 5.1 Code Duplication (Same Logic in Multiple Services)

| Duplicated Component | Services | Impact |
|---------------------|----------|--------|
| `AuditAware` (base entity with audit timestamps) | fund-transfer, utility-payment, user-service | Changes must be replicated in 3 places; risk of drift |
| `BaseMapper<E, D>` (generic entity-DTO mapper) | fund-transfer, utility-payment, user-service | Identical abstract class copy-pasted |
| `GlobalExceptionHandler` | core-banking, fund-transfer, utility-payment, user-service | Each has its own handler; all return HTTP 400 for everything |
| `ErrorResponse` | core-banking, fund-transfer, utility-payment, user-service | Identical response DTO |
| `SimpleBankingGlobalException` | core-banking, fund-transfer, utility-payment, user-service | Identical exception class |
| `AppAuthUserFilter` (extracts X-Auth-Id header) | fund-transfer, utility-payment, user-service | Same servlet filter duplicated across 3 services |
| `CustomFeignClientConfiguration` | fund-transfer, utility-payment, user-service | Same Feign config duplicated |
| `TransactionStatus` enum | fund-transfer, utility-payment | Identical enum (PENDING, PROCESSING, SUCCESS, FAILED) |
| Request/Response DTOs | core-banking, fund-transfer, utility-payment | `FundTransferRequest`, `UtilityPaymentRequest`, response DTOs copied across services |

### 5.2 Transaction Recording Overlap

| Aspect | core-banking | fund-transfer | utility-payment |
|--------|:------------:|:-------------:|:---------------:|
| Records transaction entity | YES (`banking_core_transaction`) | YES (`fund_transfer`) | YES (`utility_payment`) |
| Tracks status | NO (implicit success) | YES (PENDING → SUCCESS) | YES (PROCESSING → SUCCESS) |
| Stores amount | YES | YES | YES |
| Stores account reference | YES (FK to `banking_core_account`) | YES (string `fromAccount`, `toAccount`) | YES (string `account`) |

**Problem:** Transaction data is fragmented across 3 databases. There is no single source of truth for "all transactions." Reconciliation between the `banking_core_transaction` table and the `fund_transfer` / `utility_payment` tables requires cross-database queries.

---

## 6. Capability Gaps

### 6.1 Missing Business Capabilities

| # | Missing Capability | Business Impact | Typical Owner |
|---|-------------------|-----------------|---------------|
| CG-01 | **Notification Service** (email, SMS, push) | Users receive no confirmation of transactions, registration, or status changes | Dedicated notification service |
| CG-02 | **Transaction History / Statement Service** | No unified view of all transactions across transfer types | Dedicated or core-banking |
| CG-03 | **Audit & Compliance Service** | No formal audit log; only JPA audit timestamps exist; no who-did-what trail | Dedicated audit service |
| CG-04 | **Payment Scheduling** | No future-dated or recurring payment support | fund-transfer / utility-payment |
| CG-05 | **Dispute / Chargeback Management** | No mechanism to reverse or dispute a transaction | Dedicated dispute service |
| CG-06 | **Reporting & Analytics** | No business intelligence, transaction summaries, or regulatory reports | Dedicated reporting service |
| CG-07 | **Rate Limiting & Fraud Detection** | No transaction velocity checks, anomaly detection, or fraud scoring | API gateway or dedicated service |
| CG-08 | **Fee Management** | No transaction fees, service charges, or fee schedule | Core-banking or dedicated |
| CG-09 | **Currency / FX Management** | No multi-currency support or exchange rate handling | Core-banking or dedicated |
| CG-10 | **Account Lifecycle Management** | No account creation, closure, freeze, or type-change flows | Core-banking |
| CG-11 | **Beneficiary Management** | No saved/favorite beneficiary list for repeat transfers | fund-transfer or user-service |
| CG-12 | **Compensation / Saga Orchestration** | No rollback when downstream calls fail; stuck PENDING/PROCESSING states | Orchestration layer |

---

## 7. Service Boundary Assessment

### 7.1 Alignment Analysis

| Service | Aligned To | Assessment |
|---------|-----------|------------|
| api-gateway | **Infrastructure** (cross-cutting) | Correctly scoped as infrastructure |
| service-registry | **Infrastructure** (cross-cutting) | Correctly scoped as infrastructure |
| config-server | **Infrastructure** (cross-cutting) | Correctly scoped as infrastructure |
| core-banking-service | **Multiple business domains** (user, account, transaction) | **MISALIGNED** — monolith packaging; violates single-responsibility principle at the domain level |
| fund-transfer-service | **Technical layer** (orchestration proxy) | **MISALIGNED** — too thin; no independent business logic; effectively an API facade over core-banking |
| utility-payment-service | **Technical layer** (orchestration proxy) | **MISALIGNED** — too thin; structurally identical to fund-transfer; same assessment |
| user-service | **Business domain** (user registration + IAM) | **Partially aligned** — has clear domain boundary but depends on core-banking for user lookup |

### 7.2 Domain Boundary Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                      API Gateway (8082)                         │
│              [Authentication · Routing · Identity]              │
└──────────┬──────────┬────────────┬──────────────┬───────────────┘
           │          │            │              │
     ┌─────▼────┐ ┌───▼────┐ ┌────▼─────┐ ┌─────▼──────┐
     │  User    │ │ Fund   │ │ Utility  │ │   Core     │
     │ Service  │ │Transfer│ │ Payment  │ │  Banking   │
     │ (8085)   │ │(8084)  │ │ (8085)   │ │  (8092)    │
     │          │ │        │ │          │ │            │
     │·Register │ │·Orch-  │ │·Orch-    │ │·Users      │
     │·Approve  │ │ estrate│ │ estrate  │ │·Accounts   │
     │·Keycloak │ │·Store  │ │·Store    │ │·Balances   │
     │          │ │ status │ │ status   │ │·Transfers  │
     │          │ │        │ │          │ │·Payments   │
     │          │ │        │ │          │ │·Utility    │
     │          │ │        │ │          │ │ Accounts   │
     └─────┬────┘ └───┬────┘ └────┬─────┘ │·Transaction│
           │          │           │        │ Recording  │
           │          │           │        └────────────┘
           │          │           │              ▲
           └──────────┴───────────┴──────────────┘
                    All delegate to core-banking
                         via OpenFeign
```

### 7.3 Key Boundary Problems

1. **Core-banking is a monolith:** It combines user management, account management, balance management, transaction execution, and utility provider management into a single deployable unit. These are 5+ distinct business capabilities that should be independently evolvable.

2. **Fund-transfer and utility-payment are anemic:** They contain no business rules — all validation, balance operations, and transaction recording happen in core-banking. Their only unique value is maintaining a separate status record (PENDING → SUCCESS), which could be a field in the core transaction entity.

3. **No domain events:** Services communicate only through synchronous REST calls. There are no domain events (e.g., "TransferCompleted," "PaymentProcessed") that other services could subscribe to. This means adding a notification service or audit service would require modifying the existing services.

4. **Data ownership is unclear for transactions:** Core-banking records transactions in `banking_core_transaction`, while fund-transfer and utility-payment each record their own copy. This violates the "single source of truth" principle.

5. **User domain is split:** Core-banking owns `banking_core_user` (name, email, NIC), while user-service owns `user` (authId, status, identification). The user-service calls core-banking to look up users during registration, creating a circular dependency in the user domain.

---

## 8. Summary

| Metric | Value |
|--------|-------|
| Total services | 6 |
| Infrastructure services (correctly scoped) | 3 |
| Business services | 3 (+1 user-service) |
| Code-duplicated components | 9+ |
| Missing business capabilities | 12 |
| Misaligned service boundaries | 3 of 4 business services |
| Independent business logic in fund-transfer | ~0 lines (pure delegation) |
| Independent business logic in utility-payment | ~0 lines (pure delegation) |
| Business capabilities crammed into core-banking | 7 |
