# Business Capability Map

## 1. Overview

This document maps each of the six microservices in the Internet Banking application to business capabilities, identifies overlaps and gaps, and assesses whether service boundaries align with business domains or technical layers.

---

## 2. Microservice Inventory

| # | Service | Port | Primary Technology |
|---|---------|------|--------------------|
| 1 | `core-banking-service` | 8092 | Spring Boot, JPA, MySQL |
| 2 | `internet-banking-fund-transfer-service` | 8084 | Spring Boot, JPA, MySQL, OpenFeign |
| 3 | `internet-banking-utility-payment-service` | 8085 | Spring Boot, JPA, MySQL, OpenFeign |
| 4 | `internet-banking-user-service` | 8083 | Spring Boot, JPA, PostgreSQL, Keycloak, OpenFeign |
| 5 | `internet-banking-api-gateway` | 8082 | Spring Cloud Gateway, OAuth2/JWT |
| 6 | `internet-banking-service-registry` | 8081 | Netflix Eureka |
| 7 | `internet-banking-config-server` | 8090 | Spring Cloud Config |

> **Note:** The README lists 6 microservices. Counting the config server separately brings the total to 7 deployable units, but the config server is infrastructure, not a business service. A planned **Notification Service** is referenced in the README but not yet implemented.

---

## 3. Business Capability Map

### 3.1 Capability-to-Service Matrix

| Business Capability | core-banking | fund-transfer | utility-payment | user-service | api-gateway | service-registry / config-server |
|---------------------|:---:|:---:|:---:|:---:|:---:|:---:|
| **Account Management** | ● Primary | | | | | |
| **Account Inquiry** | ● Primary | ○ Proxy (Feign readAccount) | | | | |
| **Fund Transfer Processing** | ● Execution | ● Orchestration | | | | |
| **Utility Payment Processing** | ● Execution | | ● Orchestration | | | |
| **Transaction Recording** | ● Primary | ● Local copy | ● Local copy | | | |
| **Balance Management** | ● Primary | | | | | |
| **User Administration** | ○ Read-only (user lookup) | | | ● Primary | | |
| **Identity & Access Management** | | | | ● Keycloak integration | ● JWT validation | |
| **User Registration** | | | | ● Primary | | |
| **User Approval Workflow** | | | | ● Primary | | |
| **API Routing** | | | | | ● Primary | |
| **Rate Limiting / Throttling** | | | | | ○ Not implemented | |
| **Service Discovery** | | | | | | ● Primary (Eureka) |
| **Centralized Configuration** | | | | | | ● Primary (Config Server) |
| **Audit Trail** | ○ Entity-level (`TransactionEntity`) | ○ JPA auditing (created/modified) | ○ JPA auditing (created/modified) | ○ JPA auditing | | |
| **Notification / Messaging** | | ○ Planned (RabbitMQ) | ○ Planned (RabbitMQ) | | | |
| **Reporting & Analytics** | | | | | | |
| **Fraud Detection** | | | | | | |
| **Compliance / AML** | | | | | | |

**Legend:** ● = owns/implements the capability; ○ = partial, proxy, or planned

---

### 3.2 Detailed Capability Descriptions

#### Account Management (core-banking-service)
- `AccountController` exposes GET endpoints for bank accounts and utility accounts
- `AccountService` reads from `BankAccountRepository` and `UtilityAccountRepository`
- No create/update/delete account endpoints — account lifecycle management is absent from the API

#### Fund Transfer Processing
- **Orchestration** (`fund-transfer-service`): Receives request → persists local `FundTransferEntity` (PENDING) → calls core-banking via Feign → updates status to SUCCESS
- **Execution** (`core-banking-service`): `TransactionService.fundTransfer()` validates balance → debits sender → credits receiver → records two `TransactionEntity` rows

#### Utility Payment Processing
- **Orchestration** (`utility-payment-service`): Receives request → persists local `UtilityPaymentEntity` (PROCESSING) → calls core-banking via Feign → updates status to SUCCESS
- **Execution** (`core-banking-service`): `TransactionService.utilPayment()` validates balance → debits account → records `TransactionEntity`

#### User Administration
- **core-banking-service**: `UserController` + `UserService` — read-only access to `banking_core_user` table (lookup by identification number)
- **user-service**: Full CRUD — register, update, read users. Integrates with Keycloak for identity management. Calls core-banking to validate user identification during registration

#### Identity & Access Management
- **user-service**: Creates users in Keycloak, manages approval workflow (PENDING → APPROVED → DISABLED/BLACKLIST)
- **api-gateway**: Validates JWT tokens from Keycloak via OAuth2 resource server. Injects `X-Auth-Id` header into downstream requests

#### API Routing (api-gateway)
- Routes requests to downstream services based on path prefixes: `/user/**`, `/fund-transfer/**`, `/utility-payment/**`, `/banking-core/**`
- Applies OAuth2/JWT security; permits unauthenticated access to registration endpoint and actuator endpoints

---

## 4. Capability Overlaps

### 4.1 Transaction Recording (Triple Write)

**Severity: HIGH**

The same transaction is recorded in three different places:

1. **fund-transfer-service**: `FundTransferEntity` (own database) — stores `fromAccount`, `toAccount`, `amount`, `status`, `transactionReference`
2. **core-banking-service**: `TransactionEntity` (core database) — stores two rows per fund transfer (debit + credit) with `transactionId`, `amount`, `referenceNumber`
3. For utility payments: `UtilityPaymentEntity` (utility-payment-service DB) + `TransactionEntity` (core-banking DB)

**Risk:** Data inconsistency between databases. If the Feign call succeeds but the status update in the orchestrating service fails, the databases will diverge. There is no reconciliation mechanism.

### 4.2 Payment Request DTOs (Duplicated Classes)

**Severity: MEDIUM**

`FundTransferRequest` is defined in three places:
- `internet-banking-fund-transfer-service/.../model/dto/request/FundTransferRequest.java` (has `authID` field)
- `core-banking-service/.../model/dto/request/FundTransferRequest.java` (does NOT have `authID` field)
- `internet-banking-fund-transfer-service/.../model/dto/request/UtilityPaymentRequest.java` (unused in this service)

`UtilityPaymentRequest` is defined in three places:
- `internet-banking-utility-payment-service/.../model/rest/request/UtilityPaymentRequest.java`
- `core-banking-service/.../model/dto/request/UtilityPaymentRequest.java`
- `internet-banking-fund-transfer-service/.../model/dto/request/UtilityPaymentRequest.java` (dead code)

**Risk:** Field drift between copies. The `authID` field already diverges between fund-transfer and core-banking copies of `FundTransferRequest`.

### 4.3 User Management (Split Across Two Services)

**Severity: MEDIUM**

User data lives in two places:
- `core-banking-service`: `banking_core_user` table (first name, last name, email, identification number, bank accounts)
- `user-service`: own user table (email, identification, authId, status) + Keycloak user store

During registration, the user-service calls core-banking to validate the user's identification number, then creates the user in Keycloak and its own database. This means user identity is fragmented across three stores (core DB, user-service DB, Keycloak).

### 4.4 Exception Handling (Duplicated Boilerplate)

**Severity: LOW**

`GlobalExceptionHandler`, `SimpleBankingGlobalException`, `ErrorResponse`, and `EntityNotFoundException` are copy-pasted across `core-banking-service`, `fund-transfer-service`, and `utility-payment-service`. They are not shared as a library.

### 4.5 Audit Infrastructure (Duplicated)

**Severity: LOW**

`AuditAware`, `AuditConfig`, `AuditorAwareConfig`, `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`, and `CustomFeignClientConfiguration` are duplicated across fund-transfer-service and utility-payment-service.

---

## 5. Capability Gaps

### 5.1 Notification Service — NOT IMPLEMENTED

**Impact: HIGH**

The README mentions a Notification Service that should consume RabbitMQ messages and push notifications. Neither fund-transfer-service nor utility-payment-service currently publishes messages to RabbitMQ, and no notification service exists in the codebase.

### 5.2 Fraud Detection — NOT IMPLEMENTED

**Impact: HIGH**

No fraud detection, velocity checks, or suspicious-activity monitoring exists. Combined with the lack of amount limits and ownership checks, this is a significant business risk.

### 5.3 Compliance / AML / KYC — NOT IMPLEMENTED

**Impact: HIGH**

No anti-money laundering checks, no Know Your Customer verification beyond basic Keycloak registration, no sanctions screening, no regulatory reporting.

### 5.4 Reporting & Analytics — NOT IMPLEMENTED

**Impact: MEDIUM**

No endpoints for transaction history, account statements, or aggregate reporting. Core-banking stores transactions but exposes no read APIs for them.

### 5.5 Account Lifecycle Management — NOT IMPLEMENTED

**Impact: MEDIUM**

No API to create, close, freeze, or update bank accounts. Accounts appear to be pre-seeded in the database.

### 5.6 Payment Scheduling — NOT IMPLEMENTED

**Impact: MEDIUM**

No support for future-dated or recurring payments.

### 5.7 Payment Reversal / Refund — NOT IMPLEMENTED

**Impact: MEDIUM**

No mechanism to reverse a completed fund transfer or utility payment.

### 5.8 Rate Limiting / Throttling — NOT IMPLEMENTED

**Impact: MEDIUM**

The API gateway performs authentication but no rate limiting or request throttling.

### 5.9 Transaction History / Statement API — NOT IMPLEMENTED

**Impact: MEDIUM**

Fund-transfer and utility-payment services expose paginated list endpoints for their own records, but core-banking-service has no transaction history API. End users cannot retrieve a consolidated view of their transaction history.

---

## 6. Service Boundary Assessment

### Current Alignment

| Criterion | Assessment |
|-----------|-----------|
| **Business domain alignment** | **MIXED** — Fund-transfer and utility-payment are separated by payment type (good domain separation), but core-banking is a monolithic "god service" handling accounts, transactions, and users |
| **Data ownership** | **POOR** — Transaction data is duplicated across service boundaries without a clear system of record |
| **Team autonomy** | **MODERATE** — Each service can be independently deployed, but shared DTO definitions create tight coupling |
| **Independent deployability** | **MODERATE** — Services are independently deployable but not independently releasable due to coupled DTOs and Feign contracts |
| **Bounded context clarity** | **LOW** — `core-banking-service` spans multiple bounded contexts (Accounts, Transactions, Users) |

### Boundary Anti-Patterns Detected

1. **God Service**: `core-banking-service` handles account management, transaction processing, and user management — three distinct bounded contexts in a single service.
2. **Orchestration Anemia**: Fund-transfer and utility-payment services are thin orchestration layers with almost no business logic of their own — the actual processing logic lives in core-banking.
3. **Shared-Nothing Duplication**: Instead of a shared library, DTOs, exceptions, and infrastructure code are copy-pasted, leading to drift.
4. **Two-Phase Commit Without Saga**: The orchestration pattern (save local → call remote → update local) has no compensating transactions or outbox pattern.

---

## 7. Summary Diagram

```
┌─────────────────────────────────────────────────────────┐
│                    API GATEWAY (8082)                     │
│         JWT/OAuth2 · Routing · X-Auth-Id Header          │
└──────┬──────────────┬──────────────┬────────────────────┘
       │              │              │
       ▼              ▼              ▼
┌──────────┐  ┌──────────────┐  ┌──────────────────┐
│  USER    │  │ FUND         │  │ UTILITY          │
│ SERVICE  │  │ TRANSFER     │  │ PAYMENT          │
│ (8083)   │  │ SERVICE      │  │ SERVICE          │
│          │  │ (8084)       │  │ (8085)           │
│ Keycloak │  │              │  │                  │
│ Integr.  │  │ Orchestrate  │  │ Orchestrate      │
└────┬─────┘  └──────┬───────┘  └────────┬─────────┘
     │               │                   │
     │      Feign    │         Feign     │
     ▼               ▼                   ▼
┌────────────────────────────────────────────────────────┐
│              CORE BANKING SERVICE (8092)                 │
│                                                         │
│   AccountService  ·  TransactionService  ·  UserService │
│   BankAccountRepo ·  TransactionRepo     ·  UserRepo    │
│   UtilityAccountRepo                                    │
│                                                         │
│                    MySQL Database                        │
└─────────────────────────────────────────────────────────┘

┌──────────────────┐  ┌──────────────────┐
│ SERVICE REGISTRY │  │  CONFIG SERVER   │
│ Eureka (8081)    │  │     (8090)       │
└──────────────────┘  └──────────────────┘

[NOT IMPLEMENTED]
┌──────────────────┐
│  NOTIFICATION    │
│  SERVICE         │
│  (RabbitMQ)      │
└──────────────────┘
```
