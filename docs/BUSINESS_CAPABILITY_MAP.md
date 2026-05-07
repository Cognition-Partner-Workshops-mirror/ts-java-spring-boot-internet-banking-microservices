# Business Capability Map

> **Repository:** ts-java-spring-boot-internet-banking-microservices  
> **Date:** 2026-05-07  
> **Scope:** All 6 microservices + supporting infrastructure

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Microservice Inventory](#2-microservice-inventory)
3. [Business Capability Taxonomy](#3-business-capability-taxonomy)
4. [Service-to-Capability Mapping Matrix](#4-service-to-capability-mapping-matrix)
5. [Capability Overlaps](#5-capability-overlaps)
6. [Capability Gaps](#6-capability-gaps)
7. [Service Boundary Assessment](#7-service-boundary-assessment)

---

## 1. Executive Summary

The internet banking platform comprises **6 microservices** — 3 business services (core-banking, fund-transfer, utility-payment), 1 user management service, and 2 infrastructure services (API gateway, service registry + config server). Analysis reveals:

- **3 significant capability overlaps** where the same business logic is duplicated across services (user management, transaction state tracking, payment DTO definitions)
- **9 capability gaps** where important banking functions are not covered by any service
- **Service boundaries are aligned to technical payment channels** (fund transfer vs. utility payment) rather than to cohesive business domains, leading to tight coupling with core-banking and redundant orchestration patterns

---

## 2. Microservice Inventory

### 2.1 Business Services

| Service | Port | Database | Primary Responsibility |
|---|---|---|---|
| **core-banking-service** | — | MySQL/PostgreSQL | System of record for accounts, users, and transaction execution |
| **internet-banking-fund-transfer-service** | — | Separate DB | Orchestrates peer-to-peer fund transfers via core-banking |
| **internet-banking-utility-payment-service** | — | Separate DB | Orchestrates utility bill payments via core-banking |
| **internet-banking-user-service** | — | Separate DB | User registration, lifecycle management, Keycloak integration |

### 2.2 Infrastructure Services

| Service | Primary Responsibility |
|---|---|
| **internet-banking-api-gateway** | Request routing, OAuth2/JWT security, user identity propagation (`X-Auth-Id` header) |
| **internet-banking-service-registry** | Netflix Eureka service discovery server |
| **internet-banking-config-server** | Centralized configuration management (Spring Cloud Config) |

---

## 3. Business Capability Taxonomy

The following capability model organizes the business functions expected of an internet banking platform:

```
Internet Banking Platform
|
+-- L1: Customer Management
|   +-- L2: User Registration & Onboarding
|   +-- L2: User Profile Management
|   +-- L2: User Authentication & Authorization
|   +-- L2: User Status Lifecycle (Approval Workflow)
|
+-- L1: Account Management
|   +-- L2: Bank Account Inquiry
|   +-- L2: Account Opening / Closure
|   +-- L2: Account Status Management
|   +-- L2: Balance Inquiry
|   +-- L2: Utility Account Registry
|
+-- L1: Payment Processing
|   +-- L2: Fund Transfer (P2P / Internal)
|   +-- L2: Utility / Bill Payment
|   +-- L2: Payment Scheduling
|   +-- L2: Payment Status Tracking
|   +-- L2: Payment History / Inquiry
|
+-- L1: Transaction Management
|   +-- L2: Transaction Recording (Ledger)
|   +-- L2: Balance Updates (Debit/Credit)
|   +-- L2: Transaction Validation
|   +-- L2: Transaction Reconciliation
|
+-- L1: Risk & Compliance
|   +-- L2: Amount Limit Enforcement
|   +-- L2: Fraud Detection
|   +-- L2: Duplicate / Idempotency Control
|   +-- L2: AML / KYC Checks
|   +-- L2: Regulatory Reporting
|
+-- L1: Platform Operations
|   +-- L2: API Routing & Security
|   +-- L2: Service Discovery
|   +-- L2: Configuration Management
|   +-- L2: Distributed Tracing / Observability
|   +-- L2: Notification & Alerting
|   +-- L2: Audit Trail
```

---

## 4. Service-to-Capability Mapping Matrix

Legend: **P** = Primary owner | **S** = Secondary / partial | **—** = Not implemented

| Business Capability | core-banking | fund-transfer | utility-payment | user-service | api-gateway | service-registry / config-server |
|---|---|---|---|---|---|---|
| **Customer Management** | | | | | | |
| User Registration & Onboarding | S (stores user entity) | — | — | **P** (Keycloak + local DB) | — | — |
| User Profile Management | S (read-only lookup by NIC) | — | — | **P** (CRUD operations) | — | — |
| User Authentication & Authorization | — | — | — | S (Keycloak integration) | **P** (OAuth2 JWT filter) | — |
| User Status Lifecycle | — | — | — | **P** (approve/reject workflow) | — | — |
| **Account Management** | | | | | | |
| Bank Account Inquiry | **P** (read by account number) | — | — | — | — | — |
| Account Opening / Closure | — | — | — | — | — | — |
| Account Status Management | — | — | — | — | — | — |
| Balance Inquiry | **P** (actual + available balance) | — | — | — | — | — |
| Utility Account Registry | **P** (CRUD for utility providers) | — | — | — | — | — |
| **Payment Processing** | | | | | | |
| Fund Transfer (P2P) | S (executes debit/credit) | **P** (orchestration, status tracking) | — | — | — | — |
| Utility / Bill Payment | S (executes debit) | — | **P** (orchestration, status tracking) | — | — | — |
| Payment Scheduling | — | — | — | — | — | — |
| Payment Status Tracking | — | S (local entity status) | S (local entity status) | — | — | — |
| Payment History / Inquiry | — | **P** (paginated fund transfers) | **P** (paginated utility payments) | — | — | — |
| **Transaction Management** | | | | | | |
| Transaction Recording | **P** (ledger entries) | S (local shadow copy) | S (local shadow copy) | — | — | — |
| Balance Updates | **P** (debit/credit operations) | — | — | — | — | — |
| Transaction Validation | **P** (balance check only) | — | — | — | — | — |
| Transaction Reconciliation | — | — | — | — | — | — |
| **Risk & Compliance** | | | | | | |
| Amount Limit Enforcement | — | — | — | — | — | — |
| Fraud Detection | — | — | — | — | — | — |
| Duplicate / Idempotency Control | — | — | — | — | — | — |
| AML / KYC Checks | — | — | — | — | — | — |
| Regulatory Reporting | — | — | — | — | — | — |
| **Platform Operations** | | | | | | |
| API Routing & Security | — | — | — | — | **P** | — |
| Service Discovery | — | — | — | — | — | **P** |
| Configuration Management | — | — | — | — | — | **P** |
| Distributed Tracing | S (Micrometer/Zipkin) | S (Micrometer/Zipkin) | S (Micrometer/Zipkin) | S (Micrometer/Zipkin) | S (Micrometer/Zipkin) | — |
| Notification & Alerting | — | — | — | — | — | — |
| Audit Trail | S (`AuditAware` base) | **P** (`AuditAware` + created/modified tracking) | **P** (`AuditAware` + created/modified tracking) | **P** (`AuditAware` + created/modified tracking) | — | — |

---

## 5. Capability Overlaps

### Overlap 1: User Management — Dual User Stores

| Aspect | core-banking-service | internet-banking-user-service |
|---|---|---|
| **Entity** | `UserEntity` (firstName, lastName, email, identificationNumber) | `UserEntity` (authId, email, identification, status, password) |
| **Table** | `banking_core_user` | Separate user table |
| **API** | `GET /api/v1/user/{identification}` | `POST /api/v1/bank-users/register`, `PATCH /update/{id}`, `GET /{id}` |
| **Behavior** | Read-only user lookup; master record for NIC-to-profile mapping | Full lifecycle: registration, Keycloak sync, status approval |

**Impact:** User data is split across two databases with no synchronization mechanism beyond the initial registration Feign call. If core-banking user data changes (e.g., email update), the user-service copy becomes stale. The `identification` field in user-service maps to `identificationNumber` in core-banking — naming inconsistency adds confusion.

### Overlap 2: Transaction State Tracking — Triple Record Keeping

Each payment creates records in **three** places:

| Layer | Fund Transfer | Utility Payment |
|---|---|---|
| **Orchestration service DB** | `FundTransferEntity` (fromAccount, toAccount, amount, status, transactionReference) | `UtilityPaymentEntity` (providerId, amount, referenceNumber, account, transactionId, status) |
| **Core banking transaction ledger** | `TransactionEntity` (amount, transactionType, referenceNumber, transactionId, account) | `TransactionEntity` (amount, transactionType, referenceNumber, transactionId, account) |
| **Core banking account entity** | Balance fields updated on `BankAccountEntity` | Balance fields updated on `BankAccountEntity` |

**Impact:** The orchestration services (fund-transfer, utility-payment) maintain their own transaction records with `status` fields (PENDING/PROCESSING/SUCCESS/FAILED), but these are **not synchronized** with the core-banking ledger. If the Feign call succeeds but the status update in the orchestration service fails (e.g., network partition after core processes the transfer), the records diverge — core shows a completed transaction while the orchestrator still shows PENDING.

### Overlap 3: Payment DTO Definitions — Duplicate Classes

| Class | Defined In | Fields |
|---|---|---|
| `FundTransferRequest` | fund-transfer-service | fromAccount, toAccount, amount, authID |
| `FundTransferRequest` | core-banking-service | fromAccount, toAccount, amount |
| `UtilityPaymentRequest` | fund-transfer-service | providerId, amount, referenceNumber, account |
| `UtilityPaymentRequest` | utility-payment-service | providerId, amount, referenceNumber, account |
| `UtilityPaymentRequest` | core-banking-service | providerId, amount, referenceNumber, account |
| `FundTransferResponse` | fund-transfer-service | message, transactionId |
| `FundTransferResponse` | core-banking-service | message, transactionId |
| `UtilityPaymentResponse` | fund-transfer-service | *(empty class — 0 fields)* |
| `UtilityPaymentResponse` | utility-payment-service | message, transactionId |
| `UtilityPaymentResponse` | core-banking-service | message, transactionId |
| `AccountResponse` | fund-transfer-service | number, actualBalance, id, type, status, availableBalance |
| `AccountResponse` | utility-payment-service | *(referenced via Feign client)* |

**Impact:** 10+ DTO classes are duplicated across services with subtle differences (e.g., `authID` field present in fund-transfer but absent in core-banking). Changes to the payment contract require coordinated updates across multiple services. The empty `UtilityPaymentResponse` in fund-transfer-service suggests incomplete refactoring.

### Overlap 4: Exception Handling — Replicated Error Framework

| Class | Duplicated In |
|---|---|
| `GlobalExceptionHandler` | core-banking, fund-transfer, utility-payment, user-service |
| `SimpleBankingGlobalException` | core-banking, fund-transfer, utility-payment, user-service |
| `ErrorResponse` | core-banking, fund-transfer, utility-payment, user-service |
| `EntityNotFoundException` | core-banking, user-service |

**Impact:** Four copies of the same exception handling framework. Error response format could diverge across services, making client-side error handling inconsistent.

### Overlap 5: Configuration & Audit Infrastructure

| Class | Duplicated In |
|---|---|
| `AuditAware` (base DTO) | fund-transfer, utility-payment, user-service |
| `AuditConfig` | fund-transfer, utility-payment, user-service |
| `AuditorAwareConfig` | fund-transfer, utility-payment, user-service |
| `AppAuthUserFilter` | fund-transfer, utility-payment, user-service |
| `ApiRequestContext` | fund-transfer, utility-payment, user-service |
| `ApiRequestContextHolder` | fund-transfer, utility-payment, user-service |
| `BaseMapper<E,D>` | core-banking, fund-transfer, utility-payment, user-service |
| `CustomFeignClientConfiguration` | fund-transfer, utility-payment |

**Impact:** Boilerplate code duplicated across 3-4 services. A bug fix or enhancement to the audit trail requires changes in every service.

---

## 6. Capability Gaps

The following business capabilities are **not implemented** by any service:

| # | Missing Capability | Business Impact | Priority |
|---|---|---|---|
| G-01 | **Account Opening / Closure** | Users cannot open new bank accounts through the internet banking channel; accounts must be pre-provisioned in core-banking DB | HIGH |
| G-02 | **Account Status Management** | No API to activate, suspend, or close accounts; `AccountStatus` enum exists (PENDING, ACTIVE, DORMANT, BLOCKED) but no operations change it | HIGH |
| G-03 | **Amount Limit Enforcement** | No transaction limits of any kind; unlimited transfers possible | CRITICAL |
| G-04 | **Fraud Detection** | No velocity checks, unusual activity detection, or risk scoring | HIGH |
| G-05 | **Duplicate / Idempotency Control** | Same payment can be submitted and processed multiple times | CRITICAL |
| G-06 | **AML / KYC Checks** | No screening against sanctions lists or suspicious activity reporting | HIGH (Regulatory) |
| G-07 | **Payment Scheduling** | No future-dated or recurring payment capability | MEDIUM |
| G-08 | **Notification & Alerting** | No email, SMS, or push notifications for transactions, OTPs, or account alerts — RabbitMQ is in the Docker Compose stack but no messaging code exists in any service | MEDIUM |
| G-09 | **Transaction Reconciliation** | No mechanism to detect or resolve discrepancies between orchestration-service records and core-banking ledger | HIGH |
| G-10 | **Regulatory Reporting** | No capability to generate regulatory reports or export transaction data in standard formats | MEDIUM |
| G-11 | **Audit Log Querying** | `AuditAware` captures created/modified metadata but no API exposes audit trails for compliance review | MEDIUM |

---

## 7. Service Boundary Assessment

### 7.1 Current Boundary Model

```
                      +-----------------------+
                      |    API Gateway        |
                      | (Routing + OAuth2)    |
                      +----------+------------+
                                 |
              +------------------+------------------+
              |                  |                   |
    +---------v------+  +-------v--------+  +-------v---------+
    | User Service   |  | Fund Transfer  |  | Utility Payment |
    | (Registration, |  | Service        |  | Service          |
    |  Lifecycle,    |  | (Orchestrate   |  | (Orchestrate     |
    |  Keycloak)     |  |  P2P transfers)|  |  bill payments)  |
    +-------+--------+  +-------+--------+  +--------+--------+
            |                    |                     |
            |          +--------v---------------------v--------+
            +--------->|         Core Banking Service           |
                       | (Accounts, Users, Transactions,       |
                       |  Balance Management, Utility Accounts)|
                       +---------------------------------------+
```

### 7.2 Boundary Alignment Analysis

| Criterion | Assessment | Evidence |
|---|---|---|
| **Aligned to business domains?** | **Partially** | User-service aligns to Customer Management domain. But fund-transfer and utility-payment are split by *payment channel* (not domain), while both are part of the same "Payment Processing" capability. Core-banking is a monolithic catch-all. |
| **Single responsibility?** | **No** | Core-banking-service handles 5 distinct capabilities: user management, account management, transaction execution, balance management, and utility account registry. It is a *distributed monolith's core*. |
| **Loose coupling?** | **No** | Fund-transfer and utility-payment services are **thin orchestration wrappers** — their only business logic is saving a local record and calling core-banking via Feign. All validation, balance management, and transaction recording happen in core-banking. |
| **Data sovereignty?** | **Violated** | User data exists in both core-banking and user-service databases with no sync. Transaction data exists in both orchestration services and core-banking with no reconciliation. |
| **Independent deployability?** | **Partially** | Orchestration services can be deployed independently, but any change to payment DTOs requires coordinated deployment of orchestration service + core-banking since they share the same contract with no versioning. |
| **Autonomous capability?** | **No** | Fund-transfer and utility-payment services cannot function without core-banking being available. They have no fallback, circuit breaker, or queue-based decoupling. |

### 7.3 Boundary Classification

| Service | Classification | Justification |
|---|---|---|
| **core-banking-service** | **Inner Monolith** | Concentrates account, user, transaction, and validation logic in a single deployable; all other services depend on it synchronously |
| **fund-transfer-service** | **Anemic Orchestrator** | Contains almost no business logic; primarily a Feign passthrough with local state tracking that adds complexity without adding value |
| **utility-payment-service** | **Anemic Orchestrator** | Same as fund-transfer; structurally identical orchestration pattern |
| **user-service** | **Well-Bounded Domain** | Owns user registration lifecycle, Keycloak integration, and approval workflow — clear domain boundary, though it leaks user data to core-banking |
| **api-gateway** | **Infrastructure (Correct)** | Properly handles cross-cutting concerns: routing, authentication, identity propagation |
| **service-registry / config-server** | **Infrastructure (Correct)** | Standard Spring Cloud infrastructure; well-scoped |

### 7.4 Coupling Analysis

| Dependency | Communication | Coupling Type | Strength |
|---|---|---|---|
| fund-transfer → core-banking | Synchronous Feign (REST) | Temporal + Behavioral | **Tight** — fund-transfer fails if core-banking is unavailable |
| utility-payment → core-banking | Synchronous Feign (REST) | Temporal + Behavioral | **Tight** — same as above |
| user-service → core-banking | Synchronous Feign (REST) | Temporal (registration only) | **Moderate** — only called during registration to validate NIC |
| api-gateway → all services | HTTP routing via Eureka | Location (service discovery) | **Loose** — standard gateway pattern |
| all services → config-server | Bootstrap config fetch | Temporal (startup only) | **Moderate** — services fail to start without config-server |
| all services → service-registry | Eureka registration/discovery | Location | **Moderate** — standard service mesh concern |
