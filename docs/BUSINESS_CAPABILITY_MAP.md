# Business Capability Map

> **Scope**: All 6 microservices in the internet banking platform  
> **Date**: 2026-05-13

---

## 1. Service Inventory

| # | Service                                      | Port | Technology Stack              | Primary Role                                    |
|---|----------------------------------------------|------|-------------------------------|------------------------------------------------|
| 1 | `internet-banking-api-gateway`               | 8082 | Spring Cloud Gateway, WebFlux | Edge routing, OAuth2/JWT validation              |
| 2 | `internet-banking-service-registry`          | 8081 | Netflix Eureka                | Service discovery                                |
| 3 | `internet-banking-config-server`             | 8090 | Spring Cloud Config           | Centralized externalized configuration           |
| 4 | `core-banking-service`                       | 8092 | Spring Boot MVC, JPA, Flyway  | System of record: accounts, users, transactions  |
| 5 | `internet-banking-fund-transfer-service`     | 8084 | Spring Boot MVC, JPA, Feign   | Fund transfer orchestration                      |
| 6 | `internet-banking-utility-payment-service`   | 8085 | Spring Boot MVC, JPA, Feign   | Utility payment orchestration                    |
| 7 | `internet-banking-user-service`              | 8083 | Spring Boot MVC, JPA, Feign, Keycloak SDK | User registration and IAM orchestration |

---

## 2. Business Capability Definitions

| ID    | Capability                    | Description                                                                          |
|-------|-------------------------------|--------------------------------------------------------------------------------------|
| BC-01 | **Account Management**        | Creating, reading, updating, and closing bank accounts; balance inquiry                |
| BC-02 | **Fund Transfer**             | Account-to-account money movement within the same institution                          |
| BC-03 | **Payment Processing**        | Bill payments to external utility/service providers                                    |
| BC-04 | **Transaction Ledger**        | Recording, storing, and querying all financial transactions                             |
| BC-05 | **User Administration**       | Customer registration, profile management, status approval                             |
| BC-06 | **Identity & Access Mgmt**    | Authentication (Keycloak OIDC), authorization (JWT), user provisioning in IAM           |
| BC-07 | **Service Discovery**         | Runtime service-instance registration and lookup                                       |
| BC-08 | **API Routing & Security**    | Edge gateway: request routing, rate limiting, JWT validation, header enrichment         |
| BC-09 | **Configuration Management**  | Externalized property management across all services and environments                   |
| BC-10 | **Observability**             | Distributed tracing (Zipkin), logging, health checks                                   |
| BC-11 | **Utility Provider Mgmt**     | Maintaining the registry of external utility/bill payment providers                     |

---

## 3. Service-to-Capability Mapping

The matrix below shows which services own (●), participate in (◐), or have no involvement (○) with each business capability.

| Capability                  | API Gateway | Service Registry | Config Server | Core Banking | Fund Transfer | Utility Payment | User Service |
|-----------------------------|:-----------:|:----------------:|:-------------:|:------------:|:-------------:|:---------------:|:------------:|
| BC-01 Account Management    | ○           | ○                | ○             | ●            | ◐             | ◐               | ○            |
| BC-02 Fund Transfer         | ○           | ○                | ○             | ◐            | ●             | ○               | ○            |
| BC-03 Payment Processing    | ○           | ○                | ○             | ◐            | ○             | ●               | ○            |
| BC-04 Transaction Ledger    | ○           | ○                | ○             | ●            | ◐             | ◐               | ○            |
| BC-05 User Administration   | ○           | ○                | ○             | ◐            | ○             | ○               | ●            |
| BC-06 Identity & Access Mgmt| ◐           | ○                | ○             | ○            | ○             | ○               | ◐            |
| BC-07 Service Discovery     | ◐           | ●                | ○             | ◐            | ◐             | ◐               | ◐            |
| BC-08 API Routing & Security| ●           | ○                | ○             | ○            | ○             | ○               | ○            |
| BC-09 Configuration Mgmt    | ◐           | ○                | ●             | ◐            | ◐             | ◐               | ◐            |
| BC-10 Observability         | ◐           | ○                | ○             | ◐            | ◐             | ◐               | ◐            |
| BC-11 Utility Provider Mgmt | ○           | ○                | ○             | ●            | ○             | ○               | ○            |

**Legend**: ● = Owns / primary, ◐ = Participates / consumes, ○ = Not involved

---

## 4. Capability Overlaps

### 4.1 Code Duplication Across Services

The following classes are **copy-pasted identically** (or near-identically) across multiple services with no shared library:

| Duplicated Class              | Services                                            | Impact                                              |
|-------------------------------|-----------------------------------------------------|-----------------------------------------------------|
| `BaseMapper<E, D>`           | Core Banking, Fund Transfer, Utility Payment, User  | Generic mapper interface duplicated 4×               |
| `AuditAware`                 | Fund Transfer, Utility Payment, User                | Audit base entity duplicated 3×                      |
| `GlobalExceptionHandler`     | Core Banking, Fund Transfer, Utility Payment, User  | Error handling logic duplicated 4× (slightly different implementations) |
| `AppAuthUserFilter`          | Fund Transfer, Utility Payment, User                | Auth header extraction filter duplicated 3×          |
| `ApiRequestContext`          | Fund Transfer, Utility Payment, User                | Request context holder duplicated 3×                 |
| `ApiRequestContextHolder`    | Fund Transfer, Utility Payment, User                | Thread-local context duplicated 3×                   |
| `AuditConfig` + `AuditorAwareConfig` | Fund Transfer, Utility Payment, User       | JPA auditing configuration duplicated 3×             |
| `CustomFeignClientConfiguration` | Fund Transfer, Utility Payment                  | Feign logging config duplicated 2×                   |
| `ErrorResponse`              | Core Banking, Fund Transfer, Utility Payment, User  | Error DTO duplicated 4×                              |
| `SimpleBankingGlobalException`| Core Banking, Fund Transfer, Utility Payment, User  | Base exception class duplicated 4×                   |
| `TransactionStatus` enum     | Fund Transfer, Utility Payment                      | Status enum duplicated 2×                            |
| `FundTransferRequest` DTO    | Core Banking, Fund Transfer                         | Request DTO duplicated 2× (slightly different — core version lacks `authID`) |
| `UtilityPaymentRequest` DTO  | Core Banking, Fund Transfer, Utility Payment        | Request DTO duplicated 3×                            |

**Risk**: Bug fixes or changes must be applied to every copy. The `GlobalExceptionHandler` already has slight variations between services (builder vs constructor pattern), indicating drift.

### 4.2 Business Logic Overlaps

| Overlap Area                      | Services Involved                          | Details                                                              |
|-----------------------------------|--------------------------------------------|----------------------------------------------------------------------|
| Balance validation                | Core Banking                               | Only exists in one place — but should also exist at orchestration layer for fail-fast |
| User lookup by identification     | Core Banking + User Service                | Core banking stores `banking_core_user`; user service stores separate `user` table. Two sources of truth for user data. |
| Account read                      | Core Banking (primary), Fund Transfer + Utility Payment (Feign callers) | Fund Transfer has `BankingCoreFeignClient.readAccount()` defined but never called in the happy path — dead code. |

---

## 5. Capability Gaps

These are business functions that are **not covered by any service** in the current system:

| # | Missing Capability                        | Business Impact                                                                     | Priority   |
|---|------------------------------------------|-------------------------------------------------------------------------------------|------------|
| 1 | **Payment Scheduling**                   | No future-dated or recurring payments; limits consumer and corporate banking use     | HIGH       |
| 2 | **Transaction History / Statement**      | No API to list transactions by account; core `TransactionEntity` exists but has no read endpoint | HIGH       |
| 3 | **Notification / Alerts**                | No email, SMS, or push notifications for transaction confirmations or failures       | HIGH       |
| 4 | **Currency / FX Management**             | No currency on accounts or transactions; no exchange rate engine                     | CRITICAL   |
| 5 | **Fraud Detection / AML Screening**      | No transaction monitoring, velocity checks, or suspicious activity flagging           | HIGH       |
| 6 | **Payment Reversal / Refund**            | No mechanism to reverse or refund a completed transaction                             | MEDIUM     |
| 7 | **Dispute / Chargeback Management**      | No customer dispute workflow                                                         | MEDIUM     |
| 8 | **Account Lifecycle Management**         | No APIs to create, close, block, or unblock accounts — only read is supported         | MEDIUM     |
| 9 | **Beneficiary Management**               | No saved beneficiaries / payee lists for quick re-transfers                           | LOW        |
| 10| **Audit Trail / Compliance Reporting**   | Audit timestamps exist but no query API; no compliance report generation              | HIGH       |
| 11| **Rate Limiting / Throttling**           | Gateway has no rate limiting configuration; DDoS exposure                             | MEDIUM     |
| 12| **Reconciliation**                       | No mechanism to reconcile orchestration-layer records with core ledger                | HIGH       |
| 13| **Batch Payment Processing**             | No bulk/batch payment support (salary runs, vendor payments)                          | MEDIUM     |
| 14| **Inter-Bank Transfer (SWIFT/RTGS)**     | Only intra-bank transfers supported; no external clearing integration                 | LOW        |

---

## 6. Service Boundary Assessment

### 6.1 Current Boundary Model

```
                          ┌──────────────────────┐
                          │    API Gateway        │
                          │  (routing + JWT)      │
                          └──────────┬───────────┘
                                     │
                 ┌───────────────────┼───────────────────┐
                 │                   │                    │
          ┌──────▼──────┐    ┌──────▼──────┐    ┌───────▼───────┐
          │ User Service│    │Fund Transfer │    │Utility Payment│
          │(registration│    │  Service     │    │   Service     │
          │  + status)  │    │(orchestrate) │    │ (orchestrate) │
          └──────┬──────┘    └──────┬──────┘    └───────┬───────┘
                 │                  │                    │
                 │           ┌──────▼──────────────────▼──────┐
                 └──────────►│       Core Banking Service      │
                             │  (accounts + users + tx ledger  │
                             │   + utility accounts)           │
                             └─────────────────────────────────┘
```

### 6.2 Assessment

| Criterion                              | Assessment          | Details                                                                                               |
|----------------------------------------|---------------------|-------------------------------------------------------------------------------------------------------|
| **Aligned to business capabilities?**  | **PARTIALLY**       | Fund Transfer and Utility Payment each own one capability. But Core Banking is a **monolith** housing accounts, users, transactions, and utility providers — 4+ capabilities in one service. |
| **Aligned to bounded contexts (DDD)?** | **NO**              | Core Banking mixes Account context, Transaction context, User context, and Utility Provider context with shared entities and no aggregate boundaries. |
| **Technical vs business boundaries?**  | **MIXED**           | Infrastructure services (Gateway, Registry, Config) are correctly separated by technical concern. But Fund Transfer and Utility Payment are separated by **transaction type** (a technical distinction) rather than by business domain — both are "Payment Processing." |
| **Data ownership clear?**             | **NO**              | User data exists in both `banking_core_user` (core banking) and `user` (user service) with no synchronization mechanism. This creates dual sources of truth. |
| **Service autonomy**                   | **LOW**             | Fund Transfer and Utility Payment are fully dependent on Core Banking for every operation via synchronous Feign calls. They cannot function independently. |
| **Appropriate granularity?**           | **UNBALANCED**      | Core Banking is too coarse (god service). Fund Transfer and Utility Payment are too granular (thin orchestration wrappers with <50 lines of business logic each). |
| **Communication pattern**              | **BRITTLE**         | 100% synchronous REST. No event-driven patterns. Core Banking failure cascades instantly to all dependent services. |

### 6.3 Key Findings

1. **Core Banking is a "God Service"**: It owns accounts, users, transactions, and utility provider data. All business logic (balance checking, debit/credit, transaction recording) lives here. The orchestration services are thin proxies.

2. **Fund Transfer ≈ Utility Payment**: These two services have nearly identical structure — persist a local entity, call core banking, update status. The only difference is the specific core-banking endpoint called and the DTO shape. Separating them by payment type creates unnecessary operational overhead (2 databases, 2 deployments, 2 codebases).

3. **User Service has split-brain user data**: `core-banking-service` owns `banking_core_user` (name, email, NIC). `user-service` owns `user` (authId, identification, status). Neither service has a complete picture of the user. No synchronization mechanism exists.

4. **Infrastructure services are correct**: The API Gateway, Service Registry, and Config Server follow standard Spring Cloud patterns and are appropriately scoped to their technical concerns.

---

## 7. Summary Matrix

| Service                    | Capabilities Owned | Assessment                                   |
|----------------------------|-------------------|----------------------------------------------|
| API Gateway                | 1 (Routing)       | Well-scoped ✓                                |
| Service Registry           | 1 (Discovery)     | Well-scoped ✓                                |
| Config Server              | 1 (Config)        | Well-scoped ✓                                |
| Core Banking               | 4+ (Accounts, Tx Ledger, Users, Util Providers) | **Over-scoped — monolith risk** |
| Fund Transfer              | 1 (partial)       | **Under-scoped — thin orchestration proxy**   |
| Utility Payment            | 1 (partial)       | **Under-scoped — thin orchestration proxy**   |
| User Service               | 1 (IAM orch.)     | Appropriately scoped but has data ownership conflict with Core Banking |
