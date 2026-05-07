# Business Capability Map

> **Scope**: All 6 microservices in the Internet Banking Microservices platform
> **Date**: May 2026

---

## Table of Contents

1. [Service Inventory](#1-service-inventory)
2. [Business Capability Mapping](#2-business-capability-mapping)
3. [Capability Overlap Analysis](#3-capability-overlap-analysis)
4. [Capability Gap Analysis](#4-capability-gap-analysis)
5. [Service Boundary Assessment](#5-service-boundary-assessment)

---

## 1. Service Inventory

| # | Service                                   | Port  | Type           | Tech Stack                                      |
|---|-------------------------------------------|-------|----------------|--------------------------------------------------|
| 1 | `internet-banking-api-gateway`            | 8082  | Infrastructure | Spring Cloud Gateway, OAuth2/JWT, WebFlux        |
| 2 | `internet-banking-service-registry`       | 8081  | Infrastructure | Netflix Eureka Server                            |
| 3 | `internet-banking-config-server`          | 8090  | Infrastructure | Spring Cloud Config Server (Git-backed)          |
| 4 | `core-banking-service`                    | 8092  | Business       | Spring Boot MVC, JPA, Flyway, MySQL              |
| 5 | `internet-banking-fund-transfer-service`  | 8084  | Business       | Spring Boot MVC, JPA, OpenFeign, MySQL           |
| 6 | `internet-banking-utility-payment-service`| 8085  | Business       | Spring Boot MVC, JPA, OpenFeign, MySQL           |
| 7 | `internet-banking-user-service`           | 8083  | Business       | Spring Boot MVC, JPA, Keycloak Admin Client, MySQL |

---

## 2. Business Capability Mapping

### Level 1: Business Capability Domains

```
Internet Banking Platform
├── Customer Management
│   ├── User Registration
│   ├── User Authentication & Authorization
│   ├── User Profile Management
│   └── User Status Administration
├── Account Management
│   ├── Bank Account Inquiry
│   ├── Utility Account Inquiry
│   └── Account Balance Management
├── Payment Processing
│   ├── Fund Transfer Orchestration
│   ├── Utility Payment Orchestration
│   ├── Transaction Ledger Management
│   └── Balance Update Execution
├── Platform Infrastructure
│   ├── API Routing & Security
│   ├── Service Discovery
│   └── Centralized Configuration
└── (Gaps - not implemented)
    ├── Notification & Alerts
    ├── Audit & Compliance
    ├── Reporting & Analytics
    └── Dispute Management
```

### Level 2: Service-to-Capability Matrix

| Business Capability                  | Gateway | Registry | Config | Core Banking | Fund Transfer | Utility Payment | User Service |
|--------------------------------------|:-------:|:--------:|:------:|:------------:|:-------------:|:---------------:|:------------:|
| **Customer Management**              |         |          |        |              |               |                 |              |
| User Registration                    |         |          |        |              |               |                 | **PRIMARY**  |
| User Authentication (JWT/OAuth2)     | **PRIMARY** |      |        |              |               |                 |              |
| User Profile Storage (Core)         |         |          |        | **PRIMARY**  |               |                 |              |
| User Profile Storage (Internet)     |         |          |        |              |               |                 | **PRIMARY**  |
| User Status Administration          |         |          |        |              |               |                 | **PRIMARY**  |
| Keycloak Identity Management        |         |          |        |              |               |                 | **PRIMARY**  |
| **Account Management**              |         |          |        |              |               |                 |              |
| Bank Account Inquiry                 |         |          |        | **PRIMARY**  |               |                 |              |
| Utility Account Inquiry              |         |          |        | **PRIMARY**  |               |                 |              |
| Balance Read                         |         |          |        | **PRIMARY**  |               |                 |              |
| Balance Update (Debit/Credit)       |         |          |        | **PRIMARY**  |               |                 |              |
| **Payment Processing**              |         |          |        |              |               |                 |              |
| Fund Transfer Orchestration         |         |          |        |              | **PRIMARY**   |                 |              |
| Fund Transfer Execution             |         |          |        | **PRIMARY**  |               |                 |              |
| Fund Transfer Record Keeping        |         |          |        | SECONDARY    | **PRIMARY**   |                 |              |
| Utility Payment Orchestration       |         |          |        |              |               | **PRIMARY**     |              |
| Utility Payment Execution           |         |          |        | **PRIMARY**  |               |                 |              |
| Utility Payment Record Keeping      |         |          |        | SECONDARY    |               | **PRIMARY**     |              |
| Transaction Ledger (Authoritative)  |         |          |        | **PRIMARY**  |               |                 |              |
| **Platform Infrastructure**         |         |          |        |              |               |                 |              |
| API Routing                         | **PRIMARY** |       |        |              |               |                 |              |
| OAuth2/JWT Security Enforcement     | **PRIMARY** |       |        |              |               |                 |              |
| Service Discovery                   |         | **PRIMARY** |     |              |               |                 |              |
| Centralized Configuration           |         |          | **PRIMARY** |         |               |                 |              |
| Distributed Tracing                 | via Zipkin | via Zipkin | via Zipkin | via Zipkin | via Zipkin | via Zipkin   | via Zipkin   |
| Auth User Context Propagation       | **PRIMARY** |       |        |              | CONSUMER      | CONSUMER        | CONSUMER     |

**Legend**: **PRIMARY** = owns the capability; SECONDARY = has partial/duplicate implementation; CONSUMER = depends on the capability.

---

## 3. Capability Overlap Analysis

### 3.1 User Data Storage (Critical Overlap)

**Services involved**: `core-banking-service`, `internet-banking-user-service`

| Aspect                 | core-banking-service                     | internet-banking-user-service              |
|------------------------|------------------------------------------|--------------------------------------------|
| Table                  | `banking_core_user`                      | `user`                                     |
| Fields                 | id, firstName, lastName, email, identificationNumber | id, authId, identification, status  |
| Source of truth        | Customer identity & profile              | Internet banking enrollment status         |
| Linked to Keycloak     | No                                       | Yes (via `authId`)                         |

**Problem**: User data is split across two services with no synchronization mechanism. The `email` in core-banking and the `email` retrieved from Keycloak can diverge. The `identification` field links them loosely but there is no event-driven sync.

### 3.2 Transaction Record Keeping (Significant Overlap)

**Services involved**: `core-banking-service`, `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`

| Aspect                   | core-banking-service                  | fund-transfer-service           | utility-payment-service          |
|--------------------------|---------------------------------------|---------------------------------|----------------------------------|
| Table                    | `banking_core_transaction`            | `fund_transfer`                 | `utility_payment`                |
| Transaction types        | FUND_TRANSFER, UTILITY_PAYMENT        | Fund transfers only             | Utility payments only            |
| Amount tracking          | Signed amount (debit/credit entries)  | Unsigned amount                 | Unsigned amount                  |
| Status tracking          | None (no status field)                | PENDING/SUCCESS                 | PROCESSING/SUCCESS               |
| Reference to core txn    | `transactionId` (UUID)                | `transactionReference` (UUID)   | `transactionId` (UUID)           |

**Problem**: Every payment creates records in **two** databases — once in the orchestrating service and once in core-banking. These records can become inconsistent if the Feign call fails or if only one write succeeds. There is no eventual consistency mechanism, no outbox pattern, and no saga coordination.

### 3.3 Payment Request/Response DTOs (Code Duplication)

**Services involved**: `core-banking-service`, `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`

| Duplicated Class                     | Copies Found In                                              |
|--------------------------------------|--------------------------------------------------------------|
| `FundTransferRequest`                | core-banking-service, fund-transfer-service                  |
| `FundTransferResponse`               | core-banking-service, fund-transfer-service                  |
| `UtilityPaymentRequest`              | core-banking-service, utility-payment-service, fund-transfer-service |
| `UtilityPaymentResponse`             | core-banking-service, utility-payment-service, fund-transfer-service |
| `AccountResponse`                    | fund-transfer-service, utility-payment-service               |
| `AuditAware`                         | fund-transfer-service, utility-payment-service, user-service |
| `GlobalExceptionHandler`             | core-banking-service, fund-transfer-service, utility-payment-service, user-service |
| `ErrorResponse`                      | core-banking-service, fund-transfer-service, utility-payment-service, user-service |
| `SimpleBankingGlobalException`       | core-banking-service, fund-transfer-service, utility-payment-service, user-service |
| `CustomFeignClientConfiguration`     | fund-transfer-service, utility-payment-service               |
| `AppAuthUserFilter`                  | fund-transfer-service, utility-payment-service, user-service |
| `TransactionStatus` enum             | fund-transfer-service, utility-payment-service               |

**Problem**: 12+ classes are copy-pasted across services. Any bug fix or enhancement must be applied in 2-4 places. There is no shared library or contract module.

### 3.4 Auth User Context Handling (Pattern Overlap)

**Services involved**: `internet-banking-api-gateway`, `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`, `internet-banking-user-service`

The gateway extracts the principal name from the JWT and forwards it as an `X-Auth-Id` header. Each downstream service has its own `AppAuthUserFilter` that reads this header and stores it in a thread-local `ApiRequestContextHolder`. This is:
- **Duplicated**: Same filter class in 3 services
- **Insecure**: Downstream services do not validate the JWT themselves; they trust the spoofable `X-Auth-Id` header
- **Unused for authorization**: The `authID` is captured but never used to verify account ownership in payment operations

---

## 4. Capability Gap Analysis

### 4.1 Missing Business Capabilities

| # | Missing Capability             | Business Impact                                                                | Priority |
|---|-------------------------------|--------------------------------------------------------------------------------|----------|
| 1 | **Notification Service**      | No email/SMS/push notifications for transaction confirmations, alerts, or OTP  | High     |
| 2 | **Audit Trail Service**       | No centralized audit logging for compliance (PCI-DSS, SOX)                    | High     |
| 3 | **Reporting & Analytics**     | No transaction reports, account statements, or dashboards                      | High     |
| 4 | **Dispute Management**        | No mechanism to raise, track, or resolve payment disputes                      | Medium   |
| 5 | **Scheduled Payments**        | No future-dated or recurring payment capability                                | Medium   |
| 6 | **Beneficiary Management**    | No saved payee list for quick transfers                                        | Medium   |
| 7 | **Fee & Charges Engine**      | No transaction fee calculation or application                                  | Medium   |
| 8 | **Account Opening/Closing**   | No self-service account creation or closure                                    | Medium   |
| 9 | **Multi-channel Support**     | No mobile-specific API adaptations, no channel identification                  | Low      |
| 10| **Rate Limiting / Throttling**| No API rate limiting beyond what the gateway may provide                       | Medium   |

### 4.2 Missing Technical Capabilities

| # | Missing Capability              | Impact                                                                   | Priority |
|---|---------------------------------|--------------------------------------------------------------------------|----------|
| 1 | **Circuit Breaker**             | Core-banking failure cascades to all payment services                    | Critical |
| 2 | **Saga / Compensation**         | No distributed transaction management; data inconsistency on failure     | Critical |
| 3 | **Shared Library**              | 12+ duplicated classes; maintenance overhead; inconsistent bug fixes      | High     |
| 4 | **Async Messaging**             | All inter-service calls are synchronous Feign; tight coupling            | High     |
| 5 | **Event Sourcing / Outbox**     | No reliable event publication for cross-service consistency              | High     |
| 6 | **Health Checks (Custom)**      | Only default Spring Boot actuator; no deep health checks                 | Medium   |
| 7 | **API Versioning Strategy**     | Only `/api/v1/`; no strategy for backward-compatible evolution           | Medium   |
| 8 | **Contract Testing**            | No consumer-driven contract tests between Feign clients and providers    | Medium   |

---

## 5. Service Boundary Assessment

### 5.1 Boundary Alignment Score

| Service                        | Aligned to Business Capability? | Aligned to Technical Layer? | Assessment                          |
|--------------------------------|---------------------------------|-----------------------------|-------------------------------------|
| `api-gateway`                  | N/A (infrastructure)            | Yes                         | Correctly scoped as infrastructure  |
| `service-registry`             | N/A (infrastructure)            | Yes                         | Correctly scoped as infrastructure  |
| `config-server`                | N/A (infrastructure)            | Yes                         | Correctly scoped as infrastructure  |
| `core-banking-service`         | **Partially**                   | **Partially**               | **Overloaded** — owns users, accounts, AND transaction execution |
| `fund-transfer-service`        | **Yes**                         | No                          | Good domain boundary but thin orchestrator only |
| `utility-payment-service`      | **Yes**                         | No                          | Good domain boundary but thin orchestrator only |
| `user-service`                 | **Yes**                         | No                          | Well-scoped to user lifecycle management |

### 5.2 Detailed Boundary Issues

#### Issue 1: `core-banking-service` is a Monolith Inside Microservices

The `core-banking-service` contains **three distinct bounded contexts**:

1. **User Management** — `UserController`, `UserService`, `UserEntity`, `UserRepository`
2. **Account Management** — `AccountController`, `AccountService`, `BankAccountEntity`, `UtilityAccountEntity`, repositories
3. **Transaction Execution** — `TransactionController`, `TransactionService`, `TransactionEntity`, `TransactionRepository`

These are separate domains with different change frequencies and scaling requirements. Bundling them in one service means:
- A change to user lookup logic requires redeploying the entire transaction engine
- The transaction engine cannot be scaled independently of account inquiry
- All three share the same database, creating a single point of failure

#### Issue 2: Fund Transfer and Utility Payment Are Structurally Identical

Both services follow the exact same pattern:

```
1. Receive request via REST controller
2. Save entity with initial status (PENDING / PROCESSING)
3. Call core-banking-service via Feign client
4. Update entity with final status (SUCCESS)
5. Return response
```

The only differences are:
- Request/response field names (`fromAccount`/`toAccount` vs. `providerId`/`account`)
- The core-banking endpoint called (`/fund-transfer` vs. `/util-payment`)

They share the same AuditAware base class, the same exception handling, the same Feign configuration, and the same filter chain. This suggests they may be **two implementations of the same Payment Orchestration capability** artificially split by payment type.

#### Issue 3: Orchestration vs. Execution Boundary is Inconsistent

The system has an implicit two-layer architecture:

```
Internet Banking Layer:     fund-transfer-service, utility-payment-service, user-service
Core Banking Layer:         core-banking-service
```

However, the boundary between these layers is inconsistent:
- **Fund Transfer**: Orchestration in fund-transfer-service, execution in core-banking-service
- **Utility Payment**: Orchestration in utility-payment-service, execution in core-banking-service
- **User Management**: Core data in core-banking-service, enrollment in user-service, auth in Keycloak

The orchestration services are extremely thin (one controller, one service class, one Feign client each). They add latency and failure points without adding significant business logic.

### 5.3 Service Coupling Assessment

```
                    ┌─────────────────────┐
                    │     API Gateway      │
                    │   (JWT validation)   │
                    └──────────┬──────────┘
                               │ X-Auth-Id header
              ┌────────────────┼────────────────┐
              │                │                │
    ┌─────────▼──────┐  ┌─────▼────────┐  ┌────▼──────────────┐
    │ user-service   │  │ fund-transfer│  │ utility-payment   │
    │                │  │   service    │  │    service        │
    └───────┬────────┘  └──────┬───────┘  └────────┬──────────┘
            │ Feign            │ Feign              │ Feign
            │                  │                    │
            └──────────────────▼────────────────────┘
                    ┌──────────────────────┐
                    │  core-banking-service │
                    │  (Users, Accounts,   │
                    │   Transactions)      │
                    └──────────┬───────────┘
                               │
                    ┌──────────▼───────────┐
                    │    MySQL (4 schemas)  │
                    └──────────────────────┘
```

**Coupling type**: **Star topology** — all business services depend synchronously on `core-banking-service`. This creates:
- **Single point of failure**: If core-banking-service goes down, ALL operations fail
- **Deployment coupling**: Core-banking API changes require coordinated deployment of all consumers
- **Performance bottleneck**: All requests funnel through one service for execution

### 5.4 Overall Assessment

The service boundaries reflect a **technical layering pattern** (orchestration vs. execution) more than a **business domain decomposition**. The three infrastructure services (gateway, registry, config) are correctly scoped. However, the four business services have significant boundary issues:

- `core-banking-service` is too large (a monolith within the microservices architecture)
- `fund-transfer-service` and `utility-payment-service` are too small and too similar (nearly identical thin orchestrators)
- Cross-cutting concerns (auth context, exception handling, audit) are duplicated rather than shared

The architecture would benefit from a realignment toward **domain-driven service boundaries** with proper shared libraries and asynchronous communication patterns.
