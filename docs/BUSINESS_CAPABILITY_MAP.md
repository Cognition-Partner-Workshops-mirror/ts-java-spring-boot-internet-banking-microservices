# Business Capability Map

> **Scope**: All 7 microservices in the Internet Banking platform — API Gateway, Service Registry, Config Server, Core Banking Service, User Service, Fund Transfer Service, and Utility Payment Service.

---

## 1. Service Inventory

| # | Service | Port | Technology Layer | Primary Responsibility |
|---|---|---|---|---|
| 1 | `internet-banking-api-gateway` | 8082 | Edge / Infrastructure | Request routing, OAuth2/JWT validation, auth header injection |
| 2 | `internet-banking-service-registry` | 8081 | Infrastructure | Netflix Eureka — dynamic service discovery |
| 3 | `internet-banking-config-server` | 8090 | Infrastructure | Spring Cloud Config — centralized property management |
| 4 | `core-banking-service` | 8092 | Domain / Backend | System of record: users, accounts, transactions, utility accounts |
| 5 | `internet-banking-user-service` | 8083 | Domain / Orchestration | User registration, Keycloak IAM orchestration, user status management |
| 6 | `internet-banking-fund-transfer-service` | 8084 | Domain / Orchestration | Fund transfer initiation, status tracking, delegation to core banking |
| 7 | `internet-banking-utility-payment-service` | 8085 | Domain / Orchestration | Utility payment initiation, status tracking, delegation to core banking |

---

## 2. Business Capability → Service Mapping

### 2.1 Capability Matrix

| Business Capability | Sub-Capability | Gateway | Registry | Config | Core Banking | User Service | Fund Transfer | Utility Payment |
|---|---|---|---|---|---|---|---|---|
| **API Routing** | Request forwarding | ● | | | | | | |
| | Load balancing | ● | ● | | | | | |
| **Authentication & Authorization** | JWT/OAuth2 validation | ● | | | | | | |
| | User identity extraction | ● | | | | | | |
| | Keycloak user provisioning | | | | | ● | | |
| | Keycloak user activation | | | | | ● | | |
| **Service Discovery** | Service registration | | ● | | | | | |
| | Service lookup | | ● | | | | | |
| **Configuration Management** | Externalized config | | | ● | | | | |
| | Profile-based config | | | ● | | | | |
| **Account Management** | Account creation | | | | ● (data only) | | | |
| | Account inquiry | | | | ● | | | |
| | Account status management | | | | ● (enum only) | | | |
| | Balance management | | | | ● | | | |
| **User Administration** | User registration (orchestration) | | | | | ● | | |
| | User lookup (by NIC) | | | | ● | | | |
| | User status update | | | | | ● | | |
| | User listing | | | | ● | ● | | |
| **Fund Transfer** | Transfer initiation | | | | | | ● | |
| | Transfer execution (debit/credit) | | | | ● | | | |
| | Transfer status tracking | | | | | | ● | |
| | Transfer history | | | | | | ● | |
| **Payment Processing** | Utility payment initiation | | | | | | | ● |
| | Utility payment execution (debit) | | | | ● | | | |
| | Utility payment status tracking | | | | | | | ● |
| | Utility payment history | | | | | | | ● |
| **Utility Provider Management** | Provider account lookup | | | | ● | | | |
| | Provider registration | | | | ● (data only) | | | |
| **Transaction Ledger** | Transaction recording | | | | ● | | | |
| | Transaction querying | | | | (no API exposed) | | | |
| **Observability** | Distributed tracing | ● | | | ● | ● | ● | ● |
| | Health checks (actuator) | ● | ● | ● | ● | ● | ● | ● |

**Legend**: ● = Service implements this capability

---

## 3. Capability Overlaps

### 3.1 Code-Level Duplication

The following classes are duplicated identically (or near-identically) across multiple services with no shared library:

| Duplicated Component | Core Banking | User Service | Fund Transfer | Utility Payment |
|---|---|---|---|---|
| `GlobalExceptionHandler` | ● | ● | ● | ● |
| `ErrorResponse` | ● | ● | ● | ● |
| `SimpleBankingGlobalException` | ● | ● | ● | ● |
| `BaseMapper<E,D>` | ● | ● | ● | ● |
| `AuditAware` (MappedSuperclass) | | ● | ● | ● |
| `AuditConfig` / `AuditorAwareConfig` | | ● | ● | ● |
| `AppAuthUserFilter` / `ApiRequestContext` | | ● | ● | ● |
| `AccountResponse` (DTO) | | ● | ● | ● |

> **Impact**: 8 classes × 3-4 copies = ~28 duplicated files. Any bug fix or enhancement must be applied in multiple places, creating drift risk.

### 3.2 Business Logic Overlaps

| Overlap | Services Involved | Description |
|---|---|---|
| **User data management** | Core Banking + User Service | `banking_core_user` table is in core banking's schema, but user registration/status is managed by User Service. Both services expose user endpoints. |
| **Account balance query** | Core Banking + Fund Transfer + Utility Payment | Fund Transfer and Utility Payment services both define `AccountResponse` DTOs and `BankingCoreFeignClient`/`BankingCoreRestClient` to query account balances. The same Feign method `readAccount()` is duplicated. |
| **Transaction request DTOs** | Core Banking + Fund Transfer + Utility Payment | `FundTransferRequest` exists in both Core Banking and Fund Transfer service. `UtilityPaymentRequest` exists in both Core Banking and Utility Payment service. These are separate classes (not a shared library) that must stay in sync. |
| **Transaction response DTOs** | Core Banking + Fund Transfer + Utility Payment | `FundTransferResponse` and `UtilityPaymentResponse` are duplicated across services. |

---

## 4. Capability Gaps

### 4.1 Business Capabilities Not Covered by Any Service

| Missing Capability | Business Impact | Priority |
|---|---|---|
| **Notification Service** | No email/SMS/push notifications for transaction confirmations, failed transfers, or user registration. The code references Keycloak email verification but has no general notification engine. | **High** |
| **Audit & Compliance Reporting** | No dedicated audit trail beyond `AuditAware` timestamps. No regulatory report generation (AML, CTR, SAR). | **High** |
| **Payment Scheduling** | No future-dated or recurring payment capability. All transactions are immediate. | **Medium** |
| **Reconciliation** | No process to reconcile `fund_transfer` / `utility_payment` status with `banking_core_transaction` records. Orphaned PENDING/PROCESSING records are never cleaned up. | **High** |
| **Customer Statement Generation** | No endpoint or process to generate account statements. Transaction history exists in core banking but is not exposed via API. | **Medium** |
| **Rate Limiting / Throttling** | No API rate limiting at gateway or service level. Vulnerable to abuse. | **Medium** |
| **Fraud Detection** | No velocity checks, unusual-amount detection, or geo-based risk scoring. | **High** |
| **Multi-Currency / FX** | No currency model. All amounts are currency-agnostic. | **Medium** |
| **Batch / Bulk Payments** | No batch processing for payroll, vendor payments, or bulk utility payments. | **Medium** |
| **Dispute / Chargeback Management** | No capability to reverse, dispute, or investigate transactions. | **Medium** |
| **Customer Support / Case Management** | No ticketing or case management for customer issues. | **Low** |

### 4.2 Infrastructure Capability Gaps

| Missing Capability | Impact |
|---|---|
| **Circuit Breakers** | No Resilience4j or Hystrix. Core banking failure cascades to all services. |
| **Async Messaging** | All communication is synchronous REST. No event bus, no CQRS, no eventual consistency. |
| **Secrets Management** | Credentials hardcoded in `docker-compose.yml` and `privileges.sql`. No Vault, no K8s secrets. |
| **CI/CD Pipeline** | No GitHub Actions, Jenkins, or any CI configuration in the repo. |
| **API Versioning Strategy** | All APIs are `v1` with no versioning mechanism beyond URL prefix. |
| **Database Migration Consistency** | Only `core-banking-service` uses Flyway. Other 3 domain services rely on JPA auto-DDL (`spring.jpa.hibernate.ddl-auto`). |

---

## 5. Service Boundary Assessment

### 5.1 Are Boundaries Aligned to Business Capabilities or Technical Layers?

| Assessment | Finding |
|---|---|
| **Fund Transfer Service** | **Partially aligned** — owns transfer orchestration but delegates all financial logic (balance check, debit, credit, ledger) to Core Banking. It is essentially a thin proxy that saves a local status record before and after a Feign call. |
| **Utility Payment Service** | **Partially aligned** — mirrors Fund Transfer Service's pattern. Owns payment orchestration but delegates all financial logic to Core Banking. |
| **Core Banking Service** | **Misaligned — God Service** — owns users, accounts, transactions, utility accounts, balance management, and all financial execution logic. This is a monolith within the microservice architecture. It should be decomposed. |
| **User Service** | **Partially aligned** — owns IAM orchestration (Keycloak) but depends on Core Banking for user identity verification. The `banking_core_user` data it relies on is owned by Core Banking. |
| **API Gateway** | **Well aligned** — clean edge responsibility: routing, auth, header injection. |
| **Service Registry** | **Well aligned** — standard infrastructure concern (Eureka). |
| **Config Server** | **Well aligned** — standard infrastructure concern (Spring Cloud Config). |

### 5.2 Boundary Anti-Patterns Detected

| Anti-Pattern | Description | Affected Services |
|---|---|---|
| **God Service** | Core Banking owns too many business domains (user, account, transaction, utility provider). It is the single point of failure for all business operations. | `core-banking-service` |
| **Anemic Orchestrators** | Fund Transfer and Utility Payment services contain minimal business logic — just save-status-before, Feign-call, save-status-after. They add network hops and complexity without meaningful domain encapsulation. | `fund-transfer-service`, `utility-payment-service` |
| **Shared Database (Logical)** | All 4 MySQL schemas are on the same MySQL instance. While logically separated, they share connection credentials and host. A MySQL outage kills everything. | All domain services |
| **Distributed Monolith** | Services are tightly coupled via synchronous Feign calls. There is no fallback, no async path, and no data autonomy. Removing any service breaks the chain. | All domain services |
| **Feature Envy** | Fund Transfer and Utility Payment services define DTOs (`FundTransferRequest`, `AccountResponse`) that mirror Core Banking's DTOs exactly, because they pass requests straight through. | `fund-transfer-service`, `utility-payment-service` |

---

## 6. Dependency Graph (Current State)

```
                           ┌─────────────────┐
                           │   API Gateway    │
                           │    (8082)        │
                           └──┬──┬──┬──┬─────┘
                              │  │  │  │
              ┌───────────────┘  │  │  └───────────────┐
              │                  │  │                   │
              ▼                  ▼  ▼                   ▼
   ┌──────────────────┐  ┌─────────────┐  ┌─────────────────────┐
   │   User Service   │  │Fund Transfer│  │  Utility Payment    │
   │     (8083)       │  │  Service    │  │    Service          │
   │                  │  │  (8084)     │  │    (8085)           │
   └────────┬─────────┘  └──────┬──────┘  └──────────┬──────────┘
            │                   │                     │
            │ Feign             │ Feign               │ Feign
            ▼                   ▼                     ▼
   ┌────────────────────────────────────────────────────────────┐
   │                    Core Banking Service                     │
   │                        (8092)                               │
   │  ┌──────────┐ ┌───────────┐ ┌─────────────┐ ┌───────────┐│
   │  │  Users   │ │ Accounts  │ │Transactions │ │ Utility   ││
   │  │          │ │           │ │             │ │ Accounts  ││
   │  └──────────┘ └───────────┘ └─────────────┘ └───────────┘│
   └────────────────────────────────────────────────────────────┘
            │                   │                     │
            ▼                   ▼                     ▼
   ┌────────────────────────────────────────────────────────────┐
   │               MySQL (4 schemas, single instance)            │
   └────────────────────────────────────────────────────────────┘

   Infrastructure:
   ┌─────────────────┐  ┌────────────────┐  ┌─────────┐
   │ Service Registry│  │ Config Server  │  │ Keycloak│
   │   (Eureka)      │  │  (8090)        │  │ (8080)  │
   │   (8081)        │  │                │  │         │
   └─────────────────┘  └────────────────┘  └─────────┘
           ▲                    ▲               ▲
           │                    │               │
    All services register   All services    User Service
    & discover via Eureka   fetch config    manages users
```

---

## 7. Key Findings Summary

1. **Core Banking is a God Service** — it bundles 4+ distinct business domains (User, Account, Transaction, Utility Provider) into one deployable. All other services depend on it.

2. **Fund Transfer and Utility Payment are anemic orchestrators** — they add a persistence layer for status tracking but contain almost no business logic. Their value proposition is minimal given the network overhead.

3. **8 classes are copy-pasted across 3-4 services** — no shared library exists. This creates maintenance burden and bug propagation risk.

4. **Critical business capabilities are entirely missing** — notification, reconciliation, fraud detection, audit reporting, and dispute management have no implementation.

5. **The architecture is a distributed monolith** — synchronous coupling, shared database infrastructure, and no async communication mean the system has the complexity of microservices without the resilience benefits.
