# Application Knowledge Base

## Internet Banking Microservices Platform

> **Tech Stack:** Java 21 | Spring Boot 3.2.4 | Spring Cloud 2023.0.0 | MySQL 8.4 | Keycloak 23.0.7 | Docker Compose

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Data Model Documentation](#2-data-model-documentation)
3. [API Surface Map](#3-api-surface-map)
4. [Key Business Logic Inventory](#4-key-business-logic-inventory)
5. [Integration Points](#5-integration-points)
6. [Build and Deployment Summary](#6-build-and-deployment-summary)

---

## 1. Architecture Overview

### 1.1 Service Inventory

The platform comprises **6 Spring Boot microservices** plus **4 infrastructure components**:

| Service | Port | Role | Spring Boot Type |
|---|---|---|---|
| `internet-banking-api-gateway` | 8082 | Edge router, OAuth2/JWT enforcement | WebFlux (reactive) |
| `internet-banking-service-registry` | 8081 | Netflix Eureka discovery server | Web |
| `internet-banking-config-server` | 8090 | Spring Cloud Config (GitHub-backed) | Web |
| `core-banking-service` | 8092 | System of record: users, accounts, transactions | Web (MVC) |
| `internet-banking-fund-transfer-service` | 8084 | Fund transfer orchestration | Web (MVC) |
| `internet-banking-utility-payment-service` | 8085 | Utility bill payment orchestration | Web (MVC) |

| Infrastructure Component | Port | Purpose |
|---|---|---|
| MySQL 8.4 | 3306 | Primary relational store (4 schemas) |
| Keycloak 23.0.7 | 8080 | Identity and access management (backed by PostgreSQL 15) |
| PostgreSQL 15 | 5432 | Keycloak persistence |
| Zipkin 3 | 9411 | Distributed tracing collector |

### 1.2 High-Level Architecture Diagram (Textual)

```
                          ┌──────────────────────────────┐
                          │      External Clients         │
                          └──────────────┬───────────────┘
                                         │  HTTPS
                                         ▼
                          ┌──────────────────────────────┐
                          │  internet-banking-api-gateway │
                          │  (8082) — OAuth2 + JWT        │
                          │  Spring Cloud Gateway          │
                          └──┬───────┬───────┬───────────┘
                             │       │       │
              ┌──────────────┘       │       └──────────────┐
              ▼                      ▼                      ▼
 ┌────────────────────┐ ┌────────────────────┐ ┌─────────────────────────┐
 │  user-service       │ │ fund-transfer-svc  │ │  utility-payment-svc    │
 │  (8083)             │ │ (8084)             │ │  (8085)                 │
 └────────┬───────────┘ └────────┬───────────┘ └────────┬────────────────┘
          │  Feign                │  Feign                │  Feign
          ▼                      ▼                        ▼
 ┌─────────────────────────────────────────────────────────────┐
 │                   core-banking-service (8092)                │
 │          Users  |  Accounts  |  Transactions                 │
 └───────────────────────────┬─────────────────────────────────┘
                             │  JPA / Flyway
                             ▼
                     ┌───────────────┐
                     │   MySQL 8.4   │
                     │  (4 schemas)  │
                     └───────────────┘

 ┌───────────────────────┐     ┌───────────────┐
 │  Keycloak 23.0.7      │◄────│ PostgreSQL 15 │
 │  (IAM / OAuth2)       │     └───────────────┘
 └───────────────────────┘

 ┌────────────────────────────────┐     ┌──────────────────────┐
 │  service-registry (Eureka)     │     │  config-server       │
 │  (8081)                        │     │  (8090, GitHub-backed)│
 └────────────────────────────────┘     └──────────────────────┘
```

### 1.3 Communication Patterns

| Pattern | Implementation | Details |
|---|---|---|
| **Synchronous REST** | Spring Cloud OpenFeign | All inter-service calls are synchronous HTTP via Feign clients resolved through Eureka |
| **Service Discovery** | Netflix Eureka | All 4 business services register with the Eureka server at startup |
| **Centralized Config** | Spring Cloud Config Server | Each service uses `bootstrap.yml` to fetch config from a GitHub repo; profiles: `dev`, `docker` |
| **API Gateway** | Spring Cloud Gateway (WebFlux) | Single entry point; routes prefixed by service name to downstream services |
| **Identity Propagation** | Custom `X-Auth-Id` header | Gateway extracts JWT principal name and forwards it as `X-Auth-Id`; downstream services read it via `AppAuthUserFilter` |
| **Distributed Tracing** | Micrometer Tracing + Brave + Zipkin | Trace IDs propagated across Feign calls; spans exported to Zipkin |
| **Async Messaging** | _Not implemented_ | RabbitMQ is referenced in documentation but is not present in code or Docker Compose |

### 1.4 Security Architecture

| Layer | Mechanism | Details |
|---|---|---|
| **Edge Authentication** | OAuth2 Resource Server (JWT) | API Gateway validates JWTs against Keycloak's JWK Set URI |
| **Public Endpoints** | `permitAll()` | `/user/api/v1/bank-users/register` and all `/actuator/**` paths bypass auth |
| **Identity Propagation** | `X-Auth-Id` header | Gateway's `GatewayConfiguration` GlobalFilter extracts `Principal.getName()` and injects it into proxied requests |
| **Downstream Auth** | _None_ | Business services do not validate JWTs; they trust the `X-Auth-Id` header from the Gateway |
| **IAM Provider** | Keycloak 23.0.7 | Manages user lifecycle (registration, credentials, email verification, enable/disable) via Admin Client SDK |
| **Keycloak Integration** | `keycloak-admin-client:24.0.4` | User service uses client-credentials grant to manage realm users programmatically |
| **CSRF** | Disabled | `ServerHttpSecurity.CsrfSpec::disable` in Gateway's `SecurityConfiguration` |

---

## 2. Data Model Documentation

### 2.1 Database Schema Overview

All data is stored in a single MySQL 8.4 instance with **4 separate schemas**:

| Schema | Service Owner | Migration Strategy |
|---|---|---|
| `banking_core_service` | core-banking-service | Flyway (3 versioned migrations) |
| `banking_core_fund_transfer_service` | internet-banking-fund-transfer-service | JPA auto-DDL (Hibernate) |
| `banking_core_user_service` | internet-banking-user-service | JPA auto-DDL (Hibernate) |
| `banking_core_utility_payment_service` | internet-banking-utility-payment-service | JPA auto-DDL (Hibernate) |

DB user: `javatodev_development` with full DDL/DML privileges on all schemas.

### 2.2 Schema: `banking_core_service`

#### Table: `banking_core_user`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT(20)` | PK, AUTO_INCREMENT | Surrogate key |
| `first_name` | `VARCHAR(255)` | Nullable | User's first name |
| `last_name` | `VARCHAR(255)` | Nullable | User's last name |
| `email` | `VARCHAR(255)` | Nullable | Email address |
| `identification_number` | `VARCHAR(255)` | Nullable | National ID Card (NIC) number; used as cross-service identifier |

**JPA Entity:** `com.javatodev.finance.model.entity.UserEntity` (core-banking-service)

#### Table: `banking_core_account`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT(20)` | PK, AUTO_INCREMENT | Surrogate key |
| `number` | `VARCHAR(255)` | Nullable | Account number (e.g., `100015003000`) |
| `type` | `VARCHAR(255)` | Nullable | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | `VARCHAR(255)` | Nullable | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `available_balance` | `DECIMAL(19,2)` | Nullable | Balance available for transactions |
| `actual_balance` | `DECIMAL(19,2)` | Nullable | Ledger balance |
| `user_id` | `BIGINT(20)` | FK -> `banking_core_user.id` | Account owner |

**JPA Entity:** `com.javatodev.finance.model.entity.BankAccountEntity`
**Relationship:** Many-to-One -> `UserEntity` (bidirectional; `UserEntity.accounts` is OneToMany LAZY with CASCADE ALL)

#### Table: `banking_core_transaction`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT(20)` | PK, AUTO_INCREMENT | Surrogate key |
| `amount` | `DECIMAL(19,2)` | Nullable | Transaction amount (negative for debits) |
| `transaction_type` | `VARCHAR(30)` | NOT NULL | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | `VARCHAR(50)` | NOT NULL | For fund transfers: destination account number; for utility: provider reference |
| `transaction_id` | `VARCHAR(50)` | NOT NULL | UUID linking debit/credit entries of the same transfer |
| `account_id` | `BIGINT(20)` | FK -> `banking_core_account.id` | Account this transaction belongs to |

**JPA Entity:** `com.javatodev.finance.model.entity.TransactionEntity`
**Relationship:** OneToOne -> `BankAccountEntity` (CASCADE ALL)

#### Table: `banking_core_utility_account`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT(20)` | PK, AUTO_INCREMENT | Surrogate key |
| `number` | `VARCHAR(255)` | Nullable | Utility provider account number |
| `provider_name` | `VARCHAR(255)` | Nullable | Provider name (e.g., `VODAFONE`, `VERIZON`, `AIRTEL`) |

**JPA Entity:** `com.javatodev.finance.model.entity.UtilityAccountEntity`

#### Entity Relationship Diagram (Core Banking)

```
 ┌──────────────────┐       ┌──────────────────────┐       ┌──────────────────────────┐
 │ banking_core_user │──1:N──│ banking_core_account  │──1:1──│ banking_core_transaction  │
 │                    │       │                        │       │                            │
 │ id (PK)            │       │ id (PK)                │       │ id (PK)                    │
 │ first_name         │       │ number                 │       │ amount                     │
 │ last_name          │       │ type                   │       │ transaction_type            │
 │ email              │       │ status                 │       │ reference_number            │
 │ identification_num │       │ available_balance      │       │ transaction_id              │
 └──────────────────┘       │ actual_balance         │       │ account_id (FK)             │
                             │ user_id (FK)           │       └──────────────────────────┘
                             └──────────────────────┘

 ┌────────────────────────────┐
 │ banking_core_utility_acct   │  (standalone, no FKs)
 │ id (PK)                     │
 │ number                      │
 │ provider_name               │
 └────────────────────────────┘
```

### 2.3 Schema: `banking_core_fund_transfer_service`

#### Table: `fund_transfer`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT(20)` | PK, AUTO_INCREMENT | Surrogate key |
| `transaction_reference` | `VARCHAR(255)` | Nullable | UUID from core-banking after successful processing |
| `from_account` | `VARCHAR(255)` | Nullable | Source account number |
| `to_account` | `VARCHAR(255)` | Nullable | Destination account number |
| `amount` | `DECIMAL(19,2)` | Nullable | Transfer amount |
| `status` | `VARCHAR(255)` | Nullable | Enum: `PENDING`, `SUCCESS` (never set to `FAILED`) |
| `created_date` | `TIMESTAMP` | Auto (audit) | Creation timestamp |
| `created_by` | `VARCHAR(255)` | Auto (audit) | Creator identity |
| `modified_date` | `TIMESTAMP` | Auto (audit) | Last modification timestamp |
| `modified_by` | `VARCHAR(255)` | Auto (audit) | Last modifier identity |
| `version` | `BIGINT` | Optimistic lock | JPA `@Version` for optimistic concurrency |

**JPA Entity:** `com.javatodev.finance.model.entity.FundTransferEntity` (extends `AuditAware`)

### 2.4 Schema: `banking_core_user_service`

#### Table: `user`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT(20)` | PK, AUTO_INCREMENT | Surrogate key |
| `auth_id` | `VARCHAR(255)` | Nullable | Keycloak user UUID |
| `identification` | `VARCHAR(255)` | Nullable | NIC number (links to core-banking `identification_number`) |
| `status` | `VARCHAR(255)` | Nullable | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | `TIMESTAMP` | Auto (audit) | Creation timestamp |
| `created_by` | `VARCHAR(255)` | Auto (audit) | Creator identity |
| `modified_date` | `TIMESTAMP` | Auto (audit) | Last modification timestamp |
| `modified_by` | `VARCHAR(255)` | Auto (audit) | Last modifier identity |
| `version` | `BIGINT` | Optimistic lock | JPA `@Version` |

**JPA Entity:** `com.javatodev.finance.model.entity.UserEntity` (extends `AuditAware`, internet-banking-user-service)

### 2.5 Schema: `banking_core_utility_payment_service`

#### Table: `utility_payment`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT(20)` | PK, AUTO_INCREMENT | Surrogate key |
| `provider_id` | `BIGINT` | Nullable | Utility provider ID (FK to core `banking_core_utility_account.id` conceptually, not enforced) |
| `amount` | `DECIMAL(19,2)` | Nullable | Payment amount |
| `reference_number` | `VARCHAR(255)` | Nullable | Provider-specific reference |
| `account` | `VARCHAR(255)` | Nullable | Source bank account number |
| `transaction_id` | `VARCHAR(255)` | Nullable | UUID from core-banking after processing |
| `status` | `VARCHAR(255)` | Nullable | Enum: `PROCESSING`, `SUCCESS` (never set to `FAILED`) |
| `created_date` | `TIMESTAMP` | Auto (audit) | Creation timestamp |
| `created_by` | `VARCHAR(255)` | Auto (audit) | Creator identity |
| `modified_date` | `TIMESTAMP` | Auto (audit) | Last modification timestamp |
| `modified_by` | `VARCHAR(255)` | Auto (audit) | Last modifier identity |
| `version` | `BIGINT` | Optimistic lock | JPA `@Version` |

**JPA Entity:** `com.javatodev.finance.model.entity.UtilityPaymentEntity` (extends `AuditAware`)

### 2.6 Seed Data (Flyway Migration V1.0.20210427174721)

The core-banking schema is seeded with:
- **4 users** (Sam Silva, Guru Darmaraj, Ragu Sivaraj, Randor Manoon)
- **14 savings accounts** across the 4 users (balances ranging from 12,000 to 889,000.33)
- **6 utility providers** (Vodafone, Verizon, Singtel, Hutch, Airtel, GIO)

---

## 3. API Surface Map

### 3.1 Gateway Routing

All requests enter through the API Gateway (port 8082). The gateway strips the service prefix and routes to the downstream service via Eureka:

| Gateway Path Prefix | Target Service | Downstream Base Path |
|---|---|---|
| `/user/**` | internet-banking-user-service | `/api/v1/bank-users/**` |
| `/fund-transfer/**` | internet-banking-fund-transfer-service | `/api/v1/transfer/**` |
| `/payment/**` | internet-banking-utility-payment-service | `/api/v1/utility-payment/**` |
| `/core/**` | core-banking-service | `/api/v1/**` |

### 3.2 Core Banking Service (port 8092)

#### Account Controller — `/api/v1/account`

| Method | Path | Auth | Description | Request | Response |
|---|---|---|---|---|---|
| `GET` | `/bank-account/{account_number}` | Internal | Get bank account by account number | Path: `account_number` (String) | `BankAccount` { id, number, type, status, availableBalance, actualBalance, user } |
| `GET` | `/util-account/{account_name}` | Internal | Get utility account by provider name | Path: `account_name` (String) | `UtilityAccount` { id, number, providerName } |

#### User Controller — `/api/v1/user`

| Method | Path | Auth | Description | Request | Response |
|---|---|---|---|---|---|
| `GET` | `/{identification}` | Internal | Get user by NIC identification number | Path: `identification` (String) | `User` { id, firstName, lastName, email, identificationNumber, bankAccounts[] } |
| `GET` | `/` | Internal | List users (paginated) | Query: `page`, `size`, `sort` (Spring Pageable) | `List<User>` |

#### Transaction Controller — `/api/v1/transaction`

| Method | Path | Auth | Description | Request Body | Response |
|---|---|---|---|---|---|
| `POST` | `/fund-transfer` | Internal | Process a fund transfer | `FundTransferRequest` { fromAccount (String), toAccount (String), amount (BigDecimal) } | `FundTransferResponse` { message (String), transactionId (String) } |
| `POST` | `/util-payment` | Internal | Process a utility payment | `UtilityPaymentRequest` { providerId (Long), amount (BigDecimal), referenceNumber (String), account (String) } | `UtilityPaymentResponse` { message (String), transactionId (String) } |

### 3.3 Internet Banking User Service (port 8083)

#### User Controller — `/api/v1/bank-users`

| Method | Path | Auth | Description | Request Body | Response |
|---|---|---|---|---|---|
| `POST` | `/register` | **Public** | Register a new banking user | `User` { email (String), identification (String), password (String) } | `User` { id, email, identification, authId, status, version } |
| `PATCH` | `/update/{id}` | JWT (via Gateway) | Update user status (approve/disable) | `UserUpdateRequest` { status (Enum: PENDING, APPROVED, DISABLED, BLACKLIST) } | `User` |
| `GET` | `/` | JWT (via Gateway) | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |
| `GET` | `/{id}` | JWT (via Gateway) | Get user by database ID | Path: `id` (Long) | `User` |

### 3.4 Internet Banking Fund Transfer Service (port 8084)

#### Fund Transfer Controller — `/api/v1/transfer`

| Method | Path | Auth | Description | Request Body | Response |
|---|---|---|---|---|---|
| `POST` | `/` | JWT (via Gateway) | Initiate a fund transfer | `FundTransferRequest` { fromAccount (String), toAccount (String), amount (BigDecimal), authID (String) } | `FundTransferResponse` { message (String), transactionId (String) } |
| `GET` | `/` | JWT (via Gateway) | List fund transfers (paginated) | Query: `page`, `size`, `sort` | `List<FundTransfer>` { id, transactionReference, status, fromAccount, toAccount, amount, version } |

### 3.5 Internet Banking Utility Payment Service (port 8085)

#### Utility Payment Controller — `/api/v1/utility-payment`

| Method | Path | Auth | Description | Request Body | Response |
|---|---|---|---|---|---|
| `POST` | `/` | JWT (via Gateway) | Process a utility payment | `UtilityPaymentRequest` { providerId (Long), amount (BigDecimal), referenceNumber (String), account (String) } | `UtilityPaymentResponse` { message (String), transactionId (String) } |
| `GET` | `/` | JWT (via Gateway) | List utility payments (paginated) | Query: `page`, `size`, `sort` | `List<UtilityPayment>` { providerId, amount, referenceNumber, account, status, version } |

### 3.6 Feign Client Contracts (Inter-Service)

| Source Service | Target Service | Feign Interface | Endpoints Called |
|---|---|---|---|
| fund-transfer-service | core-banking-service | `BankingCoreFeignClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/fund-transfer` |
| utility-payment-service | core-banking-service | `BankingCoreRestClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/util-payment` |
| user-service | core-banking-service | `BankingCoreRestClient` | `GET /api/v1/user/{identification}` |

### 3.7 API Summary Count

| Service | GET | POST | PATCH | Total |
|---|---|---|---|---|
| core-banking-service | 4 | 2 | 0 | **6** |
| user-service | 2 | 1 | 1 | **4** |
| fund-transfer-service | 1 | 1 | 0 | **2** |
| utility-payment-service | 1 | 1 | 0 | **2** |
| **Total** | **8** | **5** | **1** | **14** |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

**Service:** `internet-banking-user-service` -> `UserService.createUser()`

```
1. Check if email already exists in Keycloak
   └─ If exists: throw UserAlreadyRegisteredException

2. Fetch user from core-banking by NIC identification
   └─ Feign GET /api/v1/user/{identification}

3. Validate email matches core-banking record
   └─ If mismatch: throw InvalidEmailException

4. Create Keycloak user representation
   ├─ Set email, username (= email), firstName, lastName
   ├─ Set emailVerified = false
   ├─ Set enabled = false (account disabled until admin approval)
   └─ Set temporary password from request

5. Call Keycloak Admin API to create user
   └─ If HTTP 201: proceed; otherwise: throw InvalidBankingUserException

6. Retrieve created Keycloak user to get authId (UUID)

7. Save UserEntity to local DB
   ├─ status = PENDING
   ├─ authId = Keycloak UUID
   └─ identification = NIC from core-banking

8. Return User DTO
```

**Key Rules:**
- User must exist in core-banking system before self-registration (NIC-based lookup)
- Email must match the core-banking record exactly
- Account starts as `PENDING` and `disabled` in Keycloak until admin approval
- Password is set as non-temporary during Keycloak creation

### 4.2 User Approval Flow

**Service:** `internet-banking-user-service` -> `UserService.updateUser()`

```
1. Look up UserEntity by ID
   └─ If not found: throw EntityNotFoundException

2. If new status == APPROVED:
   ├─ Fetch Keycloak UserRepresentation by authId
   ├─ Set enabled = true
   ├─ Set emailVerified = true
   └─ Update Keycloak user via Admin API

3. Update local entity status
4. Save and return
```

**Key Rules:**
- Only the `APPROVED` status triggers Keycloak enablement
- Other status transitions (`DISABLED`, `BLACKLIST`) only update the local database; they do not disable the Keycloak account

### 4.3 Fund Transfer Flow

**Service:** `internet-banking-fund-transfer-service` -> `FundTransferService.fundTransfer()`
**Downstream:** `core-banking-service` -> `TransactionService.fundTransfer()` + `internalFundTransfer()`

```
=== Fund Transfer Service (Orchestrator) ===

1. Create FundTransferEntity with status = PENDING
2. Save to local DB (banking_core_fund_transfer_service schema)
3. Call core-banking via Feign: POST /api/v1/transaction/fund-transfer
4. On success:
   ├─ Set transactionReference = response.transactionId
   ├─ Set status = SUCCESS
   └─ Save updated entity
5. Return FundTransferResponse

=== Core Banking Service (Processor) ===

1. Read source bank account by account number
2. Read destination bank account by account number
3. Validate source account balance >= transfer amount
   └─ If insufficient: throw InsufficientFundsException
4. Execute internalFundTransfer():
   a. Generate UUID transactionId
   b. Debit source account:
      ├─ actualBalance = actualBalance - amount
      ├─ availableBalance = actualBalance - amount  ⚠️ BUG: double deduction
      └─ Save account
   c. Record debit transaction (amount negative)
   d. Credit destination account:
      ├─ actualBalance = actualBalance + amount
      ├─ availableBalance = actualBalance + amount  ⚠️ BUG: double addition
      └─ Save account
   e. Record credit transaction (amount positive)
5. Return FundTransferResponse with transactionId
```

**Key Rules:**
- Balance validation uses `actualBalance` (not `availableBalance`)
- Both `actualBalance` and `availableBalance` must be >= 0 and >= transfer amount
- The entire core-banking operation runs in a single `@Transactional` scope
- Fund transfer creates TWO transaction records (debit + credit) linked by the same `transactionId`
- **Known Bug:** `availableBalance` is set from the already-modified `actualBalance`, causing a double deduction/addition

### 4.4 Utility Payment Flow

**Service:** `internet-banking-utility-payment-service` -> `UtilityPaymentService.utilPayment()`
**Downstream:** `core-banking-service` -> `TransactionService.utilPayment()`

```
=== Utility Payment Service (Orchestrator) ===

1. Create UtilityPaymentEntity with status = PROCESSING
2. Save to local DB (banking_core_utility_payment_service schema)
3. Call core-banking via Feign: POST /api/v1/transaction/util-payment
4. On success:
   ├─ Set status = SUCCESS
   ├─ Set transactionId = response.transactionId
   └─ Save updated entity
5. Return UtilityPaymentResponse

=== Core Banking Service (Processor) ===

1. Generate UUID transactionId
2. Read source bank account by account number
3. Validate balance >= payment amount
   └─ If insufficient: throw InsufficientFundsException
4. Read utility account by provider ID (name-based lookup)
5. Fetch BankAccountEntity for direct JPA manipulation
6. Debit source account:
   ├─ actualBalance = actualBalance - amount
   └─ availableBalance = actualBalance - amount  ⚠️ BUG: double deduction
7. Record transaction (amount negative, type = UTILITY_PAYMENT)
8. Return UtilityPaymentResponse
```

**Key Rules:**
- Utility payments are debit-only (no credit to a bank account; utility provider is external)
- Provider is validated by looking up `banking_core_utility_account` by `providerName`
- Single transaction record created (debit only)
- **Known Bug:** Same double-deduction issue as fund transfers

### 4.5 Balance Validation Rules

**Location:** `core-banking-service` -> `TransactionService.validateBalance()`

```java
if (actualBalance < 0 || actualBalance < transferAmount) {
    throw InsufficientFundsException
}
```

- Validation checks `actualBalance` only (not `availableBalance`)
- No minimum balance enforcement
- No account status check (transfers from `DORMANT` or `BLOCKED` accounts are allowed)
- No daily/per-transaction limits
- No self-transfer prevention (same source and destination allowed)

### 4.6 Auditing

Three services (`fund-transfer`, `user`, `utility-payment`) use a shared `AuditAware` base class with:

| Field | Annotation | Description |
|---|---|---|
| `createdDate` | `@CreatedDate` | Timestamp of entity creation |
| `createdBy` | `@CreatedBy` | Identity of creator (from `AuditorAwareConfig`) |
| `modifiedDate` | `@LastModifiedDate` | Timestamp of last update |
| `modifiedBy` | `@LastModifiedBy` | Identity of last modifier |
| `version` | `@Version` | Optimistic locking counter |

The `AuditorAwareConfig` provides the current user from `ApiRequestContextHolder`, which is populated by `AppAuthUserFilter` reading the `X-Auth-Id` header.

**Note:** The core-banking-service does NOT use `AuditAware`; its entities have no audit fields.

### 4.7 Error Handling

**Core Banking Service** uses a centralized `GlobalExceptionHandler`:

| Exception | HTTP Status | Response Format |
|---|---|---|
| `SimpleBankingGlobalException` (and subclasses) | 400 Bad Request | `ErrorResponse` { code (String), message (String) } |
| `EntityNotFoundException` | 400 Bad Request | `ErrorResponse` |
| `InsufficientFundsException` | 400 Bad Request | `ErrorResponse` |
| Any other `Exception` | 400 Bad Request | Plain text: `"Exception occur inside API " + e` |

**Global Error Codes** (core-banking):

| Code | Meaning |
|---|---|
| `ENTITY_NOT_FOUND` | Requested entity does not exist |
| `INSUFFICIENT_FUNDS` | Account balance too low |

**User Service Error Codes:**

| Code | Meaning |
|---|---|
| `ERROR_EMAIL_REGISTERED` | Email already registered in Keycloak |
| `ERROR_INVALID_EMAIL` | Email doesn't match core-banking record |
| `ERROR_USER_NOT_FOUND_UNDER_NIC` | NIC not found in core-banking |

**Note:** All errors return HTTP 400 regardless of the actual error type (including 404-type errors).

---

## 5. Integration Points

### 5.1 Keycloak Integration

| Aspect | Detail |
|---|---|
| **Service** | internet-banking-user-service |
| **Library** | `keycloak-admin-client:24.0.4` |
| **Auth Method** | Client credentials grant |
| **Configuration** | `app.config.keycloak.server-url`, `app.config.keycloak.realm`, `app.config.keycloak.clientId`, `app.config.keycloak.client-secret` (externalized via Config Server) |
| **Operations** | Create user, update user (enable/disable, email verification), search users by email, read user by ID |
| **Realm Import** | `docker-compose/keycloak/realm-export.json` auto-imported on Keycloak startup |
| **Singleton Pattern** | `KeycloakProperties.getInstance()` uses a static singleton (not thread-safe) |

### 5.2 Database Connections

| Service | Schema | Connection | ORM | Migration |
|---|---|---|---|---|
| core-banking-service | `banking_core_service` | MySQL via `mysql-connector-j:8.4.0` | Spring Data JPA (Hibernate) | Flyway `10.12.0` |
| fund-transfer-service | `banking_core_fund_transfer_service` | MySQL via `mysql-connector-j:8.4.0` | Spring Data JPA (Hibernate) | Auto-DDL |
| user-service | `banking_core_user_service` | MySQL via `mysql-connector-j:8.4.0` | Spring Data JPA (Hibernate) | Auto-DDL |
| utility-payment-service | `banking_core_utility_payment_service` | MySQL via `mysql-connector-j:8.4.0` | Spring Data JPA (Hibernate) | Auto-DDL |

### 5.3 Distributed Tracing

| Aspect | Detail |
|---|---|
| **Libraries** | `micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`, `feign-micrometer` |
| **Collector** | Zipkin 3 (port 9411) |
| **Propagation** | B3 format via Brave; automatically propagated across Feign calls |
| **Services Instrumented** | All 6 services include tracing dependencies |

### 5.4 Service Discovery

| Aspect | Detail |
|---|---|
| **Server** | Netflix Eureka (internet-banking-service-registry, port 8081) |
| **Clients** | All 4 business services + API Gateway register as Eureka clients |
| **Feign Resolution** | `@FeignClient(name = "core-banking-service")` resolves via Eureka |
| **Health Check** | Default Eureka heartbeat (30s interval) |

### 5.5 Configuration Management

| Aspect | Detail |
|---|---|
| **Server** | Spring Cloud Config Server (port 8090) |
| **Backend** | GitHub repository (URL externalized) |
| **Bootstrap** | Each service has `bootstrap.yml` pointing to `http://localhost:8090` (dev) or `http://internet-banking-config-server:8090` (docker) |
| **Profiles** | `dev` (local development), `docker` (container deployment) |
| **Refresh** | No Spring Cloud Bus or webhook refresh mechanism configured |

---

## 6. Build and Deployment Summary

### 6.1 Build System

| Aspect | Detail |
|---|---|
| **Build Tool** | Gradle (per-service, no multi-module root build) |
| **Java Version** | 21 (`sourceCompatibility = '21'`) |
| **Spring Boot** | 3.2.4 |
| **Spring Cloud** | 2023.0.0 |
| **Gradle Plugins** | `spring-boot`, `spring-dependency-management`, `gradle-git-properties` |
| **Test Framework** | JUnit 5 (via `spring-boot-starter-test`) |
| **Test DB** | H2 in-memory (`com.h2database:h2:2.2.224`) |

Each service is an independent Gradle project — there is no root `build.gradle` or `settings.gradle` for multi-module builds.

### 6.2 Docker

**Base Image:** `eclipse-temurin:21-jre-alpine` (per service Dockerfile)

Each service has:
- Its own `Dockerfile`
- A `wait-for-it.sh` script for startup ordering

**Docker Compose Files:**
- `docker-compose/docker-compose-support-apps.yml` — Infrastructure only (MySQL, Keycloak, PostgreSQL, Zipkin, Config Server, Service Registry)
- `docker-compose/docker-compose.yml` — Full stack (all 6 services + infrastructure)

### 6.3 Network Topology (Docker)

All containers run on a custom bridge network `javatodev_ib_network` (`172.25.0.0/16`) with static IPs:

| IP | Service |
|---|---|
| `172.25.0.2` | core-banking-service |
| `172.25.0.3` | internet-banking-utility-payment-service |
| `172.25.0.4` | internet-banking-fund-transfer-service |
| `172.25.0.5` | internet-banking-user-service |
| `172.25.0.6` | internet-banking-api-gateway |
| `172.25.0.7` | internet-banking-service-registry |
| `172.25.0.8` | internet-banking-config-server |
| `172.25.0.9` | MySQL |
| `172.25.0.10` | PostgreSQL (Keycloak DB) |
| `172.25.0.11` | Keycloak |
| `172.25.0.12` | Zipkin |

### 6.4 Startup Order

Services use `wait-for-it.sh` with 50-second timeouts to enforce startup dependencies:

```
MySQL, PostgreSQL, Keycloak, Zipkin  (start independently)
         │
         ▼
 Config Server  (no wait-for-it)
 Service Registry  (no wait-for-it)
         │
         ▼
 API Gateway  (waits for: Service Registry, Config Server)
 Core Banking  (waits for: Service Registry, Config Server, MySQL)
 User Service  (waits for: Service Registry, Config Server, MySQL)
 Fund Transfer  (waits for: Service Registry, Config Server, MySQL)
 Utility Payment  (waits for: Service Registry, Config Server, MySQL)
```

### 6.5 Key Dependencies (per service)

| Dependency | Services Using It | Purpose |
|---|---|---|
| `spring-boot-starter-web` | core, fund-transfer, user, utility-payment | REST controllers (Spring MVC) |
| `spring-cloud-starter-gateway` | api-gateway | Reactive HTTP routing |
| `spring-boot-starter-data-jpa` | core, fund-transfer, user, utility-payment | JPA/Hibernate ORM |
| `spring-cloud-starter-openfeign` | fund-transfer, user, utility-payment | Declarative HTTP clients |
| `spring-cloud-starter-netflix-eureka-client` | all 6 services | Service discovery |
| `spring-cloud-starter-config` | all 6 services | Centralized configuration |
| `spring-boot-starter-oauth2-resource-server` | api-gateway | JWT validation |
| `keycloak-admin-client:24.0.4` | user | Keycloak management API |
| `flyway-core:10.12.0` + `flyway-mysql:10.12.0` | core | Database migrations |
| `springdoc-openapi-starter-webflux-ui:2.1.0` | core, fund-transfer, user, utility-payment | Swagger/OpenAPI docs |
| `micrometer-tracing-bridge-brave` | all 6 services | Distributed tracing |
| `zipkin-reporter-brave` | all 6 services | Zipkin span export |

### 6.6 CI/CD

No CI/CD pipeline is configured in the repository. There are no GitHub Actions workflows, Jenkinsfiles, or other pipeline definitions.

### 6.7 OpenAPI Documentation

All four MVC-based services include `springdoc-openapi-starter-webflux-ui:2.1.0` and annotate controllers with `@Tag` and `@Operation`. However, the dependency is incorrect — MVC services should use `springdoc-openapi-starter-webmvc-ui` instead of the WebFlux variant. Swagger UI may not function correctly at runtime due to this mismatch.
