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

### 4.3 Missing Business Capabilities — Detailed Mapping

Each missing capability is mapped to the existing services that would own or consume it, along with proposed API surface and data model.

#### 4.3.1 Notification Service

| Aspect                 | Details                                                                                       |
|------------------------|-----------------------------------------------------------------------------------------------|
| **Owning Service**     | New: `internet-banking-notification-service`                                                  |
| **Consuming Services** | `fund-transfer-service` (transfer confirmations), `utility-payment-service` (payment receipts), `user-service` (registration/approval emails) |
| **Trigger Mechanism**  | Event-driven: listens to `PaymentCompleted`, `PaymentFailed`, `UserRegistered`, `UserApproved` events from RabbitMQ |
| **Proposed Endpoints** | `POST /api/v1/notifications/send` (internal), `GET /api/v1/notifications/{userId}` (user history), `PATCH /api/v1/notifications/{id}/read` |
| **Data Model**         | `notification` table: id, userId, channel (EMAIL/SMS/PUSH), templateId, payload (JSON), status (PENDING/SENT/FAILED), sentAt, createdDate |
| **Channels**           | Email (SMTP/AWS SES), SMS (AWS SNS/Twilio), Push (FCM/APNs)                                  |
| **Templates Needed**   | Fund transfer confirmation, utility payment receipt, registration welcome, account approval, failed transaction alert, low balance warning |
| **Current Gap in Code**| `UserService.createUser()` sets `emailVerified=false` but never triggers an actual verification email. Keycloak can send emails but is not configured to do so. |

#### 4.3.2 Audit Trail Service

| Aspect                 | Details                                                                                       |
|------------------------|-----------------------------------------------------------------------------------------------|
| **Owning Service**     | New: `internet-banking-audit-service`                                                         |
| **Consuming Services** | All 4 business services generate auditable events                                              |
| **Trigger Mechanism**  | Event-driven: subscribes to all domain events; also consumes HTTP access logs from the gateway  |
| **Proposed Endpoints** | `GET /api/v1/audit/events?userId=&type=&from=&to=` (admin query), `GET /api/v1/audit/events/{entityId}` (entity history) |
| **Data Model**         | `audit_event` table: id, eventType, entityType, entityId, userId, action (CREATE/UPDATE/DELETE), previousState (JSON), newState (JSON), ipAddress, userAgent, timestamp |
| **Regulatory Mapping** | PCI-DSS Req 10 (track access to cardholder data), SOX Section 302/404 (financial controls), GDPR Art 30 (records of processing) |
| **Current Gap in Code**| `AuditAware` tracks `createdBy`/`modifiedBy` fields but these are always null — no `AuditorAware<String>` bean is registered to supply the current user. The `X-Auth-Id` header value is captured by `AppAuthUserFilter` but never wired into the JPA auditing framework. |

#### 4.3.3 Reporting & Analytics

| Aspect                 | Details                                                                                       |
|------------------------|-----------------------------------------------------------------------------------------------|
| **Owning Service**     | New: `internet-banking-reporting-service`                                                     |
| **Consuming Services** | Reads from `core-banking-service` (transaction ledger, account balances), `fund-transfer-service`, `utility-payment-service` |
| **Proposed Endpoints** | `GET /api/v1/reports/account-statement/{accountNumber}?from=&to=` (statement), `GET /api/v1/reports/transaction-summary?period=` (admin dashboard), `GET /api/v1/reports/export?format=PDF|CSV` |
| **Data Model**         | Read-only views or materialized tables; no separate write model needed. May use CQRS with a read-replica database. |
| **Reports Needed**     | Account statement (date range), transaction summary (by type, status, period), user activity report, failed transaction report, daily/monthly volume report |
| **Current Gap in Code**| The `GET /api/v1/transfer` and `GET /api/v1/utility-payment` endpoints return raw paginated entity lists with no filtering, date range, or aggregation. No statement generation. No export format support. |

#### 4.3.4 Dispute Management

| Aspect                 | Details                                                                                       |
|------------------------|-----------------------------------------------------------------------------------------------|
| **Owning Service**     | New: `internet-banking-dispute-service`                                                       |
| **Consuming Services** | `fund-transfer-service` (transaction reference lookup), `utility-payment-service` (payment reference lookup), `notification-service` (status updates to customer) |
| **Proposed Endpoints** | `POST /api/v1/disputes` (raise dispute), `GET /api/v1/disputes/{id}` (status check), `PATCH /api/v1/disputes/{id}` (admin resolution), `GET /api/v1/disputes?userId=&status=` (list) |
| **Data Model**         | `dispute` table: id, transactionReference, disputeType (UNAUTHORIZED/INCORRECT_AMOUNT/NOT_RECEIVED/DUPLICATE), description, status (OPEN/INVESTIGATING/RESOLVED/REJECTED), resolution, userId, assignedTo, createdDate, resolvedDate |
| **Workflow**           | Customer raises dispute -> auto-link to transaction -> assign to agent -> investigate -> resolve/reject -> notify customer |
| **Current Gap in Code**| No dispute endpoint, no dispute entity, no mechanism to reverse or hold funds pending investigation. The `TransactionStatus` enum has no DISPUTED or REVERSED value. |

#### 4.3.5 Scheduled Payments

| Aspect                 | Details                                                                                       |
|------------------------|-----------------------------------------------------------------------------------------------|
| **Owning Service**     | Extend: `internet-banking-payment-service` (or current fund-transfer / utility-payment services) |
| **Consuming Services** | `core-banking-service` (executes the payment when scheduled time arrives)                      |
| **Proposed Endpoints** | `POST /api/v1/payments/scheduled` (create), `GET /api/v1/payments/scheduled` (list), `DELETE /api/v1/payments/scheduled/{id}` (cancel), `PATCH /api/v1/payments/scheduled/{id}` (modify) |
| **Data Model**         | `scheduled_payment` table: id, paymentType (TRANSFER/UTILITY), payload (JSON), scheduledDate, recurrence (ONCE/DAILY/WEEKLY/MONTHLY), status (ACTIVE/PAUSED/COMPLETED/CANCELLED), nextExecutionDate, lastExecutionDate, userId |
| **Infrastructure**     | Requires a job scheduler (Spring Scheduler, Quartz, or a dedicated cron service) to poll for due payments and trigger execution |
| **Current Gap in Code**| `FundTransferRequest` and `UtilityPaymentRequest` have no `scheduledDate` or `recurrence` field. All payments execute immediately on POST. No job scheduler configured. |

#### 4.3.6 Beneficiary Management

| Aspect                 | Details                                                                                       |
|------------------------|-----------------------------------------------------------------------------------------------|
| **Owning Service**     | Extend: `internet-banking-user-service` or new `internet-banking-beneficiary-service`          |
| **Consuming Services** | `fund-transfer-service` (auto-populate destination account), `utility-payment-service` (auto-populate provider/reference) |
| **Proposed Endpoints** | `POST /api/v1/beneficiaries` (add), `GET /api/v1/beneficiaries` (list by user), `DELETE /api/v1/beneficiaries/{id}` (remove), `PATCH /api/v1/beneficiaries/{id}` (update nickname) |
| **Data Model**         | `beneficiary` table: id, userId, nickname, accountNumber, accountHolderName, bankName, beneficiaryType (INDIVIDUAL/UTILITY), providerId (nullable), isVerified, createdDate |
| **Current Gap in Code**| Every fund transfer requires the user to type `fromAccount` and `toAccount` manually. No saved payee list. No quick-transfer capability. No account name verification against the destination bank. |

#### 4.3.7 Fee & Charges Engine

| Aspect                 | Details                                                                                       |
|------------------------|-----------------------------------------------------------------------------------------------|
| **Owning Service**     | Extend: `core-banking-service` (or new `fee-service` after decomposition)                      |
| **Consuming Services** | `fund-transfer-service` (transfer fees), `utility-payment-service` (service charges)           |
| **Proposed Endpoints** | `GET /api/v1/fees/calculate?type=&amount=&currency=` (fee quote), `GET /api/v1/fees/schedule` (admin: list fee rules) |
| **Data Model**         | `fee_rule` table: id, transactionType, minAmount, maxAmount, feeType (FLAT/PERCENTAGE/TIERED), feeValue, currency, effectiveFrom, effectiveTo. `fee_transaction` table: id, originalTransactionId, feeAmount, feeType |
| **Current Gap in Code**| `TransactionService.internalFundTransfer()` transfers the exact `amount` with no fee calculation. `utilPayment()` debits only the payment amount. No fee deduction anywhere. No fee disclosure to the user before confirming payment. |

#### 4.3.8 Account Opening/Closing

| Aspect                 | Details                                                                                       |
|------------------------|-----------------------------------------------------------------------------------------------|
| **Owning Service**     | Extend: `core-banking-service` (Account Management bounded context)                            |
| **Consuming Services** | `user-service` (trigger account opening after user approval), `notification-service` (confirmation) |
| **Proposed Endpoints** | `POST /api/v1/accounts` (open), `PATCH /api/v1/accounts/{id}/close` (close), `GET /api/v1/accounts?userId=` (list user accounts) |
| **Data Model**         | Extend `banking_core_account`: add `openedDate`, `closedDate`, `closureReason`. New `account_application` table: id, userId, accountType, status (SUBMITTED/APPROVED/REJECTED), kycDocumentIds |
| **Current Gap in Code**| `BankAccountEntity` exists but there is no REST endpoint to create or close an account. Accounts must be seeded via Flyway SQL scripts or direct DB inserts. The `AccountController` only has GET endpoints. |

#### 4.3.9 Multi-channel Support

| Aspect                 | Details                                                                                       |
|------------------------|-----------------------------------------------------------------------------------------------|
| **Owning Service**     | Extend: `internet-banking-api-gateway`                                                         |
| **Impact**             | All downstream services                                                                        |
| **Proposed Changes**   | Add `X-Channel` header (WEB/MOBILE/API) at gateway. Per-channel rate limits, response shaping (e.g., mobile gets lighter payloads), and channel-specific security rules (e.g., mobile requires device binding). |
| **Data Model**         | `device` table: id, userId, deviceId, platform (iOS/Android/Web), pushToken, lastActiveDate. Add `channel` column to `audit_event` and transaction tables. |
| **Current Gap in Code**| No channel identification anywhere. The gateway passes `X-Auth-Id` but no `X-Channel`. All clients receive identical responses regardless of channel. No device registration. |

#### 4.3.10 Rate Limiting / Throttling

| Aspect                 | Details                                                                                       |
|------------------------|-----------------------------------------------------------------------------------------------|
| **Owning Service**     | Extend: `internet-banking-api-gateway`                                                         |
| **Impact**             | Protects all downstream services from abuse                                                    |
| **Proposed Changes**   | Add Spring Cloud Gateway `RequestRateLimiter` filter backed by Redis. Configure per-user and per-endpoint limits. Add `429 Too Many Requests` response handling. |
| **Configuration**      | Transfer endpoints: 10 req/min per user. Read endpoints: 60 req/min per user. Registration: 3 req/hour per IP. Admin endpoints: 30 req/min per user. |
| **Current Gap in Code**| `SecurityConfiguration` permits all actuator endpoints and secures everything else via JWT, but has no rate limiting. Spring Cloud Gateway supports `RequestRateLimiter` natively but it is not configured. No Redis dependency present. |

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
