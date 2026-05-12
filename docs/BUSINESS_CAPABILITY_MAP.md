# Business Capability Map

> Mapping of each microservice in the internet-banking platform to business capabilities, with analysis of overlaps, gaps, and service boundary alignment.

---

## Table of Contents

1. [Service Inventory](#1-service-inventory)
2. [Business Capability Taxonomy](#2-business-capability-taxonomy)
3. [Service-to-Capability Matrix](#3-service-to-capability-matrix)
4. [Capability Overlaps](#4-capability-overlaps)
5. [Capability Gaps](#5-capability-gaps)
6. [Service Boundary Assessment](#6-service-boundary-assessment)
7. [Summary Heatmap](#7-summary-heatmap)

---

## 1. Service Inventory

| # | Service                                   | Port | Tech Stack                          | Primary Role                                    |
|---|-------------------------------------------|------|-------------------------------------|-------------------------------------------------|
| 1 | `internet-banking-api-gateway`            | 8082 | Spring Cloud Gateway, WebFlux       | Edge routing, OAuth2/JWT authentication          |
| 2 | `internet-banking-service-registry`       | 8081 | Netflix Eureka                      | Service discovery                                |
| 3 | `internet-banking-config-server`          | 8090 | Spring Cloud Config                 | Centralized configuration management             |
| 4 | `core-banking-service`                    | 8092 | Spring Boot MVC, JPA, Flyway        | System of record: users, accounts, transactions  |
| 5 | `internet-banking-fund-transfer-service`  | 8084 | Spring Boot MVC, JPA, OpenFeign     | Fund transfer orchestration                      |
| 6 | `internet-banking-utility-payment-service`| 8085 | Spring Boot MVC, JPA, OpenFeign     | Utility payment orchestration                    |
| 7 | `internet-banking-user-service`           | 8083 | Spring Boot MVC, JPA, Keycloak SDK  | User registration and identity management        |

---

## 2. Business Capability Taxonomy

Business capabilities are organized into three tiers:

### Tier 1 — Core Banking Capabilities (Revenue-generating)

| ID   | Capability                | Description                                                        |
|------|---------------------------|--------------------------------------------------------------------|
| BC-1 | Account Management        | Create, read, update, close bank accounts; manage balances         |
| BC-2 | Fund Transfer             | Move money between accounts (intra-bank and inter-bank)            |
| BC-3 | Payment Processing        | Process bill payments to third-party utility/service providers     |
| BC-4 | Transaction Management    | Record, query, and report on financial transactions                |
| BC-5 | Ledger Management         | Maintain the authoritative record of debits, credits, and balances |

### Tier 2 — Supporting Capabilities (Enable core operations)

| ID   | Capability                | Description                                                        |
|------|---------------------------|--------------------------------------------------------------------|
| BC-6 | User Administration       | Register users, manage profiles, handle approval workflows         |
| BC-7 | Identity & Access Mgmt    | Authenticate users, manage roles/permissions, token lifecycle      |
| BC-8 | Notification Services     | Email verification, payment confirmations, alerts                  |

### Tier 3 — Infrastructure Capabilities (Platform-level)

| ID    | Capability                | Description                                                        |
|-------|---------------------------|--------------------------------------------------------------------|
| BC-9  | API Routing               | Route external requests to internal services                       |
| BC-10 | Service Discovery         | Dynamic registration and lookup of service instances               |
| BC-11 | Configuration Management  | Externalized, centralized configuration for all services           |
| BC-12 | Observability             | Distributed tracing, logging, metrics, health checks               |
| BC-13 | Security Enforcement      | JWT validation, CSRF protection, authorization rules               |

---

## 3. Service-to-Capability Matrix

| Capability                  | Gateway | Registry | Config | Core Banking | Fund Transfer | Utility Payment | User Service |
|-----------------------------|---------|----------|--------|--------------|---------------|-----------------|--------------|
| BC-1  Account Management    |         |          |        | ✅ **Primary** |               |                 |              |
| BC-2  Fund Transfer         |         |          |        | ✅ Executes    | ✅ **Orchestrates** |            |              |
| BC-3  Payment Processing    |         |          |        | ✅ Executes    |               | ✅ **Orchestrates** |          |
| BC-4  Transaction Management|         |          |        | ✅ **Primary** | ✅ Partial     | ✅ Partial       |              |
| BC-5  Ledger Management     |         |          |        | ✅ **Primary** |               |                 |              |
| BC-6  User Administration   |         |          |        | ✅ User CRUD   |               |                 | ✅ **Primary** |
| BC-7  Identity & Access Mgmt| ✅ JWT  |          |        |              |               |                 | ✅ Keycloak   |
| BC-8  Notification Services |         |          |        |              |               |                 | ⚠️ Stub only  |
| BC-9  API Routing           | ✅ **Primary** |   |        |              |               |                 |              |
| BC-10 Service Discovery     |         | ✅ **Primary** |  |              |               |                 |              |
| BC-11 Config Management     |         |          | ✅ **Primary** |        |               |                 |              |
| BC-12 Observability         | ✅ Partial | ✅ Partial | ✅ Partial | ✅ Partial | ✅ Partial   | ✅ Partial       | ✅ Partial   |
| BC-13 Security Enforcement  | ✅ **Primary** |   |        |              |               |                 |              |

---

## 4. Capability Overlaps

### 4.1 Transaction Management (BC-4) — Fragmented Across 3 Services

**Problem:** Transaction state is managed in THREE separate databases:

| Service               | Table                | Status Values         | What It Tracks                     |
|-----------------------|----------------------|-----------------------|------------------------------------|
| Fund Transfer Service | `fund_transfer`      | PENDING, SUCCESS      | Transfer orchestration state       |
| Utility Payment Svc   | `utility_payment`    | PROCESSING, SUCCESS   | Payment orchestration state        |
| Core Banking Service  | `banking_core_transaction` | —              | Actual ledger transactions         |

**Impact:** There is no single source of truth for transaction history. A customer's full transaction list requires querying three databases. The orchestrator tables duplicate the amount, account references, and status that also exist in the core ledger.

### 4.2 User Data (BC-6) — Split Across Core Banking and User Service

**Problem:** User information is stored in two separate schemas:

| Service             | Table                | Fields                                          |
|---------------------|----------------------|-------------------------------------------------|
| Core Banking        | `banking_core_user`  | id, firstName, lastName, email, identificationNumber |
| User Service        | `user`               | id, authId, identification, status, audit fields |

**Impact:** The user-service depends on core-banking to validate that a user exists before registration. User profile data (name, email) lives in core-banking while auth state (Keycloak authId, approval status) lives in user-service. Updates to user information require coordinating between two services with no synchronization mechanism.

### 4.3 Exception Handling — Duplicated Across 4 Services

**Problem:** `GlobalExceptionHandler`, `ErrorResponse`, `SimpleBankingGlobalException` are copy-pasted across core-banking, fund-transfer, utility-payment, and user services. Each copy is slightly different.

| Class                          | Core | Fund Transfer | Utility Payment | User Service |
|--------------------------------|------|---------------|-----------------|--------------|
| `GlobalExceptionHandler`       | ✅    | ✅             | ✅               | ✅            |
| `ErrorResponse`                | ✅    | ✅             | ✅               | ✅            |
| `SimpleBankingGlobalException` | ✅    | ✅             | ✅               | ✅            |
| `BaseMapper`                   | ✅    | ✅             | ✅               | ✅            |
| `AuditAware`                   | —    | ✅             | ✅               | ✅            |
| `AppAuthUserFilter`            | —    | ✅             | ✅               | ✅            |

**Impact:** Bug fixes must be applied in 4 places. The `banking-common` shared library exists in the repo but these classes have not been extracted into it.

### 4.4 Account Lookup — Duplicated via Feign

Both fund-transfer-service and utility-payment-service define their own Feign client method to look up bank accounts from core-banking:

- `BankingCoreFeignClient.readAccount()` (fund-transfer-service)
- `BankingCoreRestClient.readAccount()` (utility-payment-service)

The DTOs (`AccountResponse`) are also duplicated in each service.

---

## 5. Capability Gaps

### 5.1 Missing Business Capabilities

| # | Missing Capability              | Description                                                       | Business Impact                                      | Priority |
|---|---------------------------------|-------------------------------------------------------------------|------------------------------------------------------|----------|
| 1 | **Payment Scheduling**          | No ability to schedule future-dated payments or recurring transfers| Customers cannot automate regular payments           | 🟠 High  |
| 2 | **Transaction History/Inquiry** | No endpoint to query a customer's consolidated transaction history | Customers cannot view their payment history          | 🟠 High  |
| 3 | **Payment Reversal/Refund**     | No capability to reverse or refund a completed payment            | Disputes require manual database intervention        | 🟠 High  |
| 4 | **Notification/Alerting**       | Email verification stubbed but no transaction notifications        | Customers not informed of debits/credits             | 🟡 Medium|
| 5 | **Audit/Compliance Reporting**  | No audit log beyond basic JPA `@CreatedDate`/`@CreatedBy`         | Cannot produce regulatory compliance reports         | 🟡 Medium|
| 6 | **Fee/Charge Management**       | No fee calculation for transfers or payments                       | Cannot monetize payment services                     | 🟡 Medium|
| 7 | **Currency/FX Management**      | No currency field; no exchange rate lookup                         | Limited to single implicit currency                  | 🟡 Medium|
| 8 | **Beneficiary Management**      | No saved beneficiary list for quick repeat transfers               | Customers must re-enter account details every time   | 🟢 Low  |
| 9 | **Payment Limits Management**   | No configurable per-user or per-account transaction limits         | Cannot enforce risk-based limits                     | 🟠 High  |
| 10| **Reconciliation**              | No end-of-day reconciliation between orchestrator and ledger       | Discrepancies between services go undetected         | 🟡 Medium|

### 5.2 Missing Technical Capabilities

| # | Missing Capability          | Description                                                      | Impact                                                |
|---|-----------------------------|------------------------------------------------------------------|-------------------------------------------------------|
| 1 | **Circuit Breakers**        | No Resilience4j or Hystrix on any Feign client                   | Core-banking failure cascades to all downstream        |
| 2 | **Async Messaging**         | All communication is synchronous REST; no event bus              | Tight coupling; no eventual consistency option         |
| 3 | **Rate Limiting**           | No rate limiting at gateway or service level                     | Vulnerable to abuse and DDoS                           |
| 4 | **Health Check Endpoints**  | Spring Actuator is present but no custom health indicators       | Cannot check database or Keycloak connectivity         |
| 5 | **Structured Logging**      | Log statements are inconsistent and unstructured                 | Hard to search/aggregate in log management tools       |
| 6 | **Contract Testing**        | No Pact or Spring Cloud Contract tests                           | API changes can silently break Feign clients           |
| 7 | **CI/CD Pipeline**          | No pipeline configuration in the repository                      | Manual builds and deployments only                     |

---

## 6. Service Boundary Assessment

### 6.1 Alignment Analysis

| Service                     | Aligned to...          | Assessment                                                          |
|-----------------------------|------------------------|---------------------------------------------------------------------|
| `api-gateway`               | Infrastructure (BC-9)  | ✅ **Well-aligned** — Pure infrastructure concern; single responsibility |
| `service-registry`          | Infrastructure (BC-10) | ✅ **Well-aligned** — Standard Eureka server; no business logic      |
| `config-server`             | Infrastructure (BC-11) | ✅ **Well-aligned** — Standard Spring Cloud Config; no business logic |
| `core-banking-service`      | Multiple (BC-1,2,3,4,5,6) | ❌ **God service** — Contains accounts, users, transactions, ledger, fund transfer execution, and utility payment execution all in one |
| `fund-transfer-service`     | Orchestration only     | ⚠️ **Thin orchestrator** — Saves entity, delegates to core-banking, updates status. Contains almost no business logic. |
| `utility-payment-service`   | Orchestration only     | ⚠️ **Thin orchestrator** — Same pattern as fund-transfer. Near-identical code structure. |
| `user-service`              | BC-6 + BC-7            | ⚠️ **Mixed** — Combines user profile management with Keycloak IAM orchestration |

### 6.2 Boundary Violations

#### 6.2.1 Core Banking Is a Monolith Inside Microservices

The `core-banking-service` is the system of record for:
- Users (CRUD)
- Bank accounts (CRUD + balance management)
- Utility accounts (CRUD)
- Fund transfer execution (debit/credit)
- Utility payment execution (debit)
- Transaction recording

This violates the single-responsibility principle. Every other service depends on it, making it a single point of failure with no independent scalability.

```
                    ┌──────────────────────────────┐
                    │       API Gateway (8082)      │
                    │   JWT Auth + Route Dispatch   │
                    └──────┬───────┬───────┬───────┘
                           │       │       │
              ┌────────────┘       │       └────────────┐
              ▼                    ▼                     ▼
   ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
   │  User Service    │ │  Fund Transfer   │ │ Utility Payment  │
   │  (8083)          │ │  Service (8084)  │ │ Service (8085)   │
   │                  │ │                  │ │                  │
   │  Keycloak IAM ───┤ │  Thin            │ │  Thin            │
   │  User approval   │ │  orchestrator    │ │  orchestrator    │
   └────────┬─────────┘ └────────┬─────────┘ └────────┬─────────┘
            │                    │                     │
            │     ALL three depend on core-banking     │
            │                    │                     │
            └──────────┐         │         ┌───────────┘
                       ▼         ▼         ▼
              ┌─────────────────────────────────────┐
              │       Core Banking Service (8092)    │
              │                                     │
              │  • User CRUD                        │
              │  • Account Management               │
              │  • Fund Transfer Execution          │
              │  • Utility Payment Execution        │
              │  • Transaction Recording            │
              │  • Ledger Management                │
              │  • Balance Updates                  │
              │                                     │
              │        *** GOD SERVICE ***           │
              └──────────────────┬──────────────────┘
                                 │
                        ┌────────┴────────┐
                        │   MySQL (3306)   │
                        │  4 schemas, but  │
                        │  core has ALL    │
                        │  business tables │
                        └─────────────────┘
```

#### 6.2.2 Fund Transfer and Utility Payment Are Nearly Identical

Both services follow the exact same pattern:
1. Accept request → 2. Save entity as PENDING/PROCESSING → 3. Feign call to core-banking → 4. Update entity to SUCCESS

Their codebases are structurally identical:
- Same package structure (`controller`, `service`, `model`, `exception`, `configuration`)
- Same duplicated cross-cutting classes
- Same Feign client pattern
- Different only in the specific DTO fields

This suggests they should be a single **Payment Orchestration Service** or their orchestration logic should be absorbed into core-banking.

#### 6.2.3 User Service Crosses Domain Boundaries

The user-service depends on:
- **Core Banking** (via Feign) to validate user identity and retrieve profile data
- **Keycloak** (via admin API) to manage IAM credentials

This means user registration requires both core-banking AND Keycloak to be available. The user concept is split: profile in core-banking, auth state in user-service, credentials in Keycloak.

---

## 7. Summary Heatmap

```
                 CAPABILITY COVERAGE HEATMAP
                 
Service              │ Core Business │ Supporting │ Infrastructure
─────────────────────┼───────────────┼────────────┼───────────────
API Gateway          │               │            │ ████████████
Service Registry     │               │            │ ████████████
Config Server        │               │            │ ████████████
Core Banking         │ ██████████████│ ████       │
Fund Transfer Svc    │ ████          │            │
Utility Payment Svc  │ ████          │            │
User Service         │               │ ████████   │

Legend: ████ = capability covered    (empty) = not covered

KEY FINDINGS:
  🔴 Core Banking is overloaded (6 capabilities)
  🟡 Fund Transfer & Utility Payment are underloaded (thin orchestrators)
  🟡 Transaction management is fragmented across 3 services
  🔴 5 critical business capabilities are completely missing
  🟠 User data is split across 2 services with no sync mechanism
```
