# Business Capability Map

> **Scope**: All 6 microservices in the Internet Banking platform  
> **Date**: May 2025

---

## 1. Service-to-Capability Mapping

### 1.1 Overview Matrix

| Business Capability | API Gateway | Service Registry | Config Server | Core Banking | Fund Transfer | Utility Payment | User Service |
|---------------------|:-----------:|:----------------:|:-------------:|:------------:|:-------------:|:---------------:|:------------:|
| **API Routing & Edge Security** | **PRIMARY** | | | | | | |
| **Service Discovery** | | **PRIMARY** | | | | | |
| **Configuration Management** | | | **PRIMARY** | | | | |
| **Account Management** | | | | **PRIMARY** | | | |
| **Ledger & Balance Management** | | | | **PRIMARY** | | | |
| **Fund Transfer Processing** | | | | SECONDARY | **PRIMARY** | | |
| **Utility Payment Processing** | | | | SECONDARY | | **PRIMARY** | |
| **Transaction Recording** | | | | **PRIMARY** | SECONDARY | SECONDARY | |
| **User Profile Management** | | | | SECONDARY | | | **PRIMARY** |
| **Identity & Access Management** | SECONDARY | | | | | | **PRIMARY** |
| **Audit Trail** | | | | | SECONDARY | SECONDARY | SECONDARY |
| **Distributed Tracing** | SECONDARY | | | SECONDARY | SECONDARY | SECONDARY | SECONDARY |

**Legend**: **PRIMARY** = owns the capability, SECONDARY = participates but does not own

---

### 1.2 Detailed Capability Descriptions

#### API Routing & Edge Security
**Owner**: `internet-banking-api-gateway` (port 8082)

| Aspect | Details |
|--------|---------|
| **What it does** | Routes all client REST traffic to downstream services; validates OAuth2/JWT tokens via Keycloak; injects `X-Auth-Id` header for downstream identity propagation |
| **Key classes** | `GatewayConfiguration`, `SecurityConfiguration` |
| **Routes** | `/user/**` → user-service, `/fund-transfer/**` → fund-transfer-service, `/payment/**` → utility-payment-service, `/core/**` → core-banking-service |
| **Technology** | Spring Cloud Gateway (reactive), OAuth2 Resource Server |

#### Service Discovery
**Owner**: `internet-banking-service-registry` (port 8081)

| Aspect | Details |
|--------|---------|
| **What it does** | Provides dynamic service registration and lookup via Netflix Eureka. All other services register themselves and resolve peer addresses through this registry. |
| **Key classes** | `InternetBankingServiceRegistryApplication` (single `@EnableEurekaServer` class) |
| **Consumers** | All 5 other services use Eureka client to discover peers |

#### Configuration Management
**Owner**: `internet-banking-config-server` (port 8090)

| Aspect | Details |
|--------|---------|
| **What it does** | Centralizes externalized configuration for all services. Reads YAML config from a GitHub repository and serves it via Spring Cloud Config. Services fetch their `bootstrap.yml`/`application.yml` overrides from here at startup. |
| **Profiles** | `dev`, `docker` |
| **Key classes** | `InternetBankingConfigServerApplication` (single `@EnableConfigServer` class) |

#### Account Management
**Owner**: `core-banking-service` (port 8092)

| Aspect | Details |
|--------|---------|
| **What it does** | CRUD for bank accounts and utility provider accounts. Provides account lookup by number and by provider name. |
| **Key classes** | `AccountService`, `AccountController`, `BankAccountEntity`, `UtilityAccountEntity` |
| **Endpoints** | `GET /api/v1/account/bank-account/{number}`, `GET /api/v1/account/util-account/{name}` |
| **Data** | `banking_core_account` (number, type, status, balances, user_id FK), `banking_core_utility_account` (number, providerName) |

#### Ledger & Balance Management
**Owner**: `core-banking-service`

| Aspect | Details |
|--------|---------|
| **What it does** | Maintains `actualBalance` and `availableBalance` on bank accounts. Debits and credits are applied directly to entity fields within `@Transactional` boundaries. |
| **Key classes** | `TransactionService.internalFundTransfer()`, `TransactionService.utilPayment()` |
| **Known bug** | Double-deduction: `availableBalance = actualBalance - amount` applied after `actualBalance` was already reduced |

#### Fund Transfer Processing
**Owner**: `internet-banking-fund-transfer-service` (port 8084)  
**Participant**: `core-banking-service` (executes the actual balance changes)

| Aspect | Details |
|--------|---------|
| **What it does** | Receives fund transfer requests from clients, persists a `PENDING` record, delegates to core-banking via Feign for balance validation and movement, then updates status to `SUCCESS`. |
| **Key classes** | `FundTransferController`, `FundTransferService`, `FundTransferEntity`, `BankingCoreFeignClient` |
| **Flow** | Client → Gateway → Fund Transfer Service (save PENDING) → Core Banking (validate + debit/credit) → Fund Transfer Service (update SUCCESS) → Client |
| **Endpoints** | `POST /api/v1/transfer`, `GET /api/v1/transfer` |

#### Utility Payment Processing
**Owner**: `internet-banking-utility-payment-service` (port 8085)  
**Participant**: `core-banking-service` (executes the debit and provider account lookup)

| Aspect | Details |
|--------|---------|
| **What it does** | Receives utility bill payment requests, persists a `PROCESSING` record, delegates to core-banking for balance validation and debit, then updates status to `SUCCESS`. |
| **Key classes** | `UtilityPaymentController`, `UtilityPaymentService`, `UtilityPaymentEntity`, `BankingCoreRestClient` |
| **Flow** | Client → Gateway → Utility Payment Service (save PROCESSING) → Core Banking (validate + debit) → Utility Payment Service (update SUCCESS) → Client |
| **Endpoints** | `POST /api/v1/utility-payment`, `GET /api/v1/utility-payment` |

#### Transaction Recording
**Owner**: `core-banking-service`  
**Participants**: `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`

| Aspect | Details |
|--------|---------|
| **What it does** | Records individual debit/credit entries in `banking_core_transaction`. Each fund transfer creates two entries (debit + credit); each utility payment creates one entry (debit). |
| **Overlap** | Both fund-transfer and utility-payment services maintain their own transaction records (`fund_transfer` and `utility_payment` tables) in addition to the core banking transaction log — this is **dual-write** with no consistency guarantee. |

#### User Profile Management
**Owner**: `internet-banking-user-service` (port 8083)  
**Participant**: `core-banking-service` (provides user lookup by NIC)

| Aspect | Details |
|--------|---------|
| **What it does** | Manages internet banking user registration, profile retrieval, and status management (PENDING → APPROVED). Coordinates with core banking for user identity verification and with Keycloak for IAM provisioning. |
| **Key classes** | `UserController`, `UserService`, `UserEntity`, `KeycloakUserService` |
| **Endpoints** | `POST /api/v1/bank-users/register`, `PATCH /api/v1/bank-users/update/{id}`, `GET /api/v1/bank-users`, `GET /api/v1/bank-users/{id}` |

#### Identity & Access Management
**Owner**: `internet-banking-user-service` (Keycloak integration)  
**Participant**: `internet-banking-api-gateway` (JWT validation)

| Aspect | Details |
|--------|---------|
| **What it does** | Creates Keycloak users during registration, sets temporary passwords, manages email verification, enables/disables accounts on admin approval. Gateway validates JWT tokens and extracts principal for downstream propagation. |
| **Key classes** | `KeycloakUserService`, `KeycloakManager`, `KeycloakProperties`, `SecurityConfiguration` (gateway) |

#### Audit Trail
**Participants**: `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`, `internet-banking-user-service`

| Aspect | Details |
|--------|---------|
| **What it does** | Tracks `createdDate`, `createdBy`, `modifiedDate`, `modifiedBy` on entities via Spring Data JPA auditing (`AuditAware` base class). `AuditorAwareConfig` resolves the current user from the `X-Auth-Id` header. |
| **Limitation** | Core banking service entities do NOT extend `AuditAware` — transaction records have no audit metadata. |

#### Distributed Tracing
**Participants**: All services

| Aspect | Details |
|--------|---------|
| **What it does** | Zipkin integration via Micrometer for distributed request tracing across service boundaries. |
| **Limitation** | Configured but observability is passive — no custom spans, no trace ID in API responses. |

---

## 2. Capability Overlaps

### 2.1 Transaction State Management (Dual-Write)

| Capability | Fund Transfer Service | Utility Payment Service | Core Banking Service |
|-----------|:---------------------:|:-----------------------:|:--------------------:|
| Transaction record persistence | `fund_transfer` table (PENDING/SUCCESS) | `utility_payment` table (PROCESSING/SUCCESS) | `banking_core_transaction` table (debit/credit entries) |
| Transaction ID generation | *(receives from core)* | *(receives from core)* | `UUID.randomUUID()` |
| Status tracking | Own status enum | Own status enum | *(no status — append-only ledger)* |

**Problem**: Each payment creates records in **two different databases** with no distributed transaction or saga. If the Feign call succeeds but the response is lost, the orchestrator's record stays PENDING/PROCESSING while core banking has already moved the money. There is no reconciliation mechanism.

### 2.2 Duplicated Cross-Cutting Code

| Component | Fund Transfer | Utility Payment | User Service | Core Banking |
|-----------|:------------:|:---------------:|:------------:|:------------:|
| `BaseMapper` | Yes | Yes | Yes | Yes |
| `AuditAware` | Yes | Yes | Yes | No |
| `GlobalExceptionHandler` | Yes | Yes | Yes | Yes |
| `AppAuthUserFilter` | Yes | Yes | Yes | No |
| `ErrorResponse` | Yes | Yes | Yes | Yes |
| `SimpleBankingGlobalException` | Yes | Yes | Yes | Yes |
| `ApiRequestContext/Holder` | Yes | Yes | Yes | No |
| `AuditorAwareConfig` | Yes | Yes | Yes | No |
| `CustomFeignClientConfiguration` | Yes | Yes | Yes (different pkg) | No |

**9 classes** duplicated across 3-4 services with no shared library. Any bug fix must be applied independently to each copy.

### 2.3 User Identity — Split Ownership

| Aspect | Core Banking | User Service |
|--------|:------------:|:------------:|
| User entity | `banking_core_user` (firstName, lastName, email, NIC) | `user` (authId, identification, status) |
| User lookup | By NIC (`/api/v1/user/{identification}`) | By ID (`/api/v1/bank-users/{id}`) |
| User creation | *(seed data via Flyway)* | Registration flow |

**Problem**: "User" exists in two services with different schemas and no synchronization. Core banking holds personal details; user-service holds authentication state. A user's name change in core banking is invisible to user-service, and vice versa.

---

## 3. Capability Gaps

### 3.1 Missing Business Capabilities

| Missing Capability | Business Impact | Which Service Should Own It |
|---|---|---|
| **Payment Scheduling** | Cannot schedule future-dated payments or recurring transfers | Fund Transfer / Utility Payment |
| **Transaction History & Statements** | No endpoint to query transaction history for a customer account | Core Banking |
| **Notification & Alerts** | No email/SMS/push notification on transfer completion or failure | New: Notification Service |
| **Dispute & Chargeback Management** | No mechanism to reverse or dispute a transaction | New: Dispute Service or Core Banking |
| **Fee & Commission Management** | No fee calculation, no service charges, no commission splitting | Core Banking |
| **FX / Currency Exchange** | No multi-currency support, no exchange rate management | New: FX Service or Core Banking |
| **Payment Beneficiary Management** | No saved beneficiaries, no frequent-payee list | Fund Transfer or User Service |
| **Batch/Bulk Payments** | No bulk payment file upload or batch processing | Fund Transfer |
| **Compliance & AML Screening** | No transaction monitoring, no sanctions screening, no suspicious activity reporting | New: Compliance Service |
| **Reconciliation** | No mechanism to reconcile dual-write transaction records | Core Banking |
| **Rate Limiting & Throttling** | No API rate limiting at the gateway or service level | API Gateway |
| **Approval Workflows** | No maker-checker for high-value transfers | Fund Transfer |

### 3.2 Incomplete Capabilities

| Capability | What's Missing | Business Impact |
|---|---|---|
| **Account Management** | No account creation API, no account closure, no account type management | Accounts must be seed-loaded via SQL |
| **User Management** | No password reset, no profile update, no self-service account recovery | Users locked out if they forget credentials |
| **Error Handling** | All errors return HTTP 400 regardless of cause | Clients cannot implement proper retry/backoff logic |
| **Authorization** | JWT validated only at gateway; downstream services trust `X-Auth-Id` header blindly | Any service-to-service caller can spoof identity |

---

## 4. Service Boundary Assessment

### 4.1 Alignment to Business Capabilities

| Service | Aligned to Business Domain? | Assessment |
|---------|:---------------------------:|------------|
| **API Gateway** | Yes | Clean edge concern — routing + auth. Well-bounded. |
| **Service Registry** | Yes | Pure infrastructure. Well-bounded. |
| **Config Server** | Yes | Pure infrastructure. Well-bounded. |
| **Core Banking** | **Partially** | Overloaded — owns accounts, users, transactions, AND executes payment logic. Functions as both a **system of record** and a **transaction processor**. |
| **Fund Transfer** | **Partially** | Business logic is thin (save-and-delegate). Most real work happens in core banking. This service is primarily an **orchestration shim**. |
| **Utility Payment** | **Partially** | Same pattern as fund transfer — save-and-delegate. Nearly identical code structure. |
| **User Service** | **Partially** | Mixed concern — owns both user profile management AND Keycloak IAM integration. These are distinct capabilities forced into one service. |

### 4.2 Boundary Problems

#### Problem 1: Core Banking is a "God Service"

Core banking handles 4 distinct business capabilities:
1. Account Management (CRUD)
2. Ledger/Balance Management (debit/credit)
3. Transaction Processing (fund transfers + utility payments)
4. User Lookup (by NIC)

This makes it a single point of failure and a deployment bottleneck — any change to account management requires redeploying transaction processing and vice versa.

#### Problem 2: Fund Transfer and Utility Payment Are Too Similar

Both services follow an identical pattern:
1. Accept request → Save entity with initial status → Feign call to core banking → Update entity status → Return response

They share the same cross-cutting classes, the same Feign client pattern, and the same error handling. The business logic that differentiates them (debit-only vs. debit-credit) lives entirely in core banking, not in these services.

#### Problem 3: Technical Layers Masquerading as Service Boundaries

The fund-transfer and utility-payment services are **not** bounded by business capability — they are bounded by **API endpoint**. They contain almost no business logic; they are essentially API facades over core banking. This is a **technical layer decomposition** (controller → service → repository) distributed across the network, not a domain-driven decomposition.

#### Problem 4: Shared-Nothing Data with No Consistency

Each service owns its database schema but there is no mechanism to keep them consistent:
- `fund_transfer.status` can disagree with `banking_core_transaction` records
- `user.identification` can disagree with `banking_core_user.identification_number`
- No saga pattern, no outbox pattern, no event sourcing

---

## 5. Capability Map Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        INTERNET BANKING PLATFORM                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────┐   ┌──────────────────┐   ┌─────────────────────────┐  │
│  │  EDGE SERVICES  │   │  INFRASTRUCTURE  │   │    MISSING CAPABILITIES │  │
│  │                 │   │                  │   │                         │  │
│  │  ● API Routing  │   │  ● Service       │   │  ○ Notifications       │  │
│  │  ● JWT Auth     │   │    Discovery     │   │  ○ Scheduling          │  │
│  │  ● Rate Limit○  │   │  ● Config Mgmt   │   │  ○ Dispute Mgmt       │  │
│  │                 │   │  ● Tracing       │   │  ○ AML/Compliance      │  │
│  └─────────────────┘   └──────────────────┘   │  ○ Fee Management      │  │
│                                                │  ○ FX/Currency         │  │
│  ┌─────────────────────────────────────────┐   │  ○ Batch Payments      │  │
│  │          PAYMENT DOMAIN                 │   │  ○ Beneficiary Mgmt    │  │
│  │                                         │   │  ○ Reconciliation      │  │
│  │  ● Fund Transfer Processing             │   │  ○ Tx History/Stmts    │  │
│  │  ● Utility Payment Processing           │   │  ○ Approval Workflows  │  │
│  │  ● Transaction Recording (OVERLAP)      │   └─────────────────────────┘  │
│  │  ● Balance Management                   │                                │
│  └─────────────────────────────────────────┘                                │
│                                                                             │
│  ┌─────────────────────────────────────────┐                                │
│  │     CUSTOMER & ACCOUNT DOMAIN           │                                │
│  │                                         │                                │
│  │  ● Account Management                   │                                │
│  │  ● User Profile Management              │                                │
│  │  ● Identity & Access (Keycloak)         │                                │
│  │  ● Audit Trail                          │                                │
│  └─────────────────────────────────────────┘                                │
│                                                                             │
│  ● = Implemented    ○ = Missing/Not Implemented                             │
└─────────────────────────────────────────────────────────────────────────────┘
```
