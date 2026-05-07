# Internet Banking Microservices - Application Knowledge Base

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Data Model Documentation](#2-data-model-documentation)
3. [API Surface Map](#3-api-surface-map)
4. [Key Business Logic Inventory](#4-key-business-logic-inventory)
5. [Integration Points](#5-integration-points)
6. [Build and Deployment Pipeline](#6-build-and-deployment-pipeline)

---

## 1. Architecture Overview

### 1.1 System Summary

A Java 21 / Spring Boot 3.2.4 internet banking platform composed of **6 microservices** communicating via REST (OpenFeign) and managed through Spring Cloud infrastructure. The system models a banking core with account management, inter-account fund transfers, and utility bill payments.

**Spring Cloud Version:** 2023.0.0

### 1.2 Microservices

| Service | Port | Description |
|---|---|---|
| **core-banking-service** | 8092 | Central banking ledger. Manages users, bank accounts, utility accounts, and executes transactions (fund transfers and utility payments) directly against the database. |
| **internet-banking-user-service** | 8083 | Internet banking user registration and lifecycle management. Integrates with Keycloak for identity management and delegates to core-banking-service for user verification. |
| **internet-banking-fund-transfer-service** | 8084 | Orchestrates fund transfer requests. Persists transfer records locally and delegates actual balance operations to core-banking-service via Feign. |
| **internet-banking-utility-payment-service** | 8085 | Orchestrates utility bill payments. Persists payment records locally and delegates actual balance operations to core-banking-service via Feign. |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway. Single entry point for all client traffic. Routes requests to downstream services, enforces OAuth2/JWT authentication via Keycloak, and injects `X-Auth-Id` header. |
| **internet-banking-service-registry** | 8081 | Netflix Eureka Server. All services register here for service discovery. |
| **internet-banking-config-server** | 8090 | Spring Cloud Config Server. Serves externalized configuration from a Git repository to all services at startup. |

### 1.3 Communication Patterns

#### Synchronous REST (OpenFeign)

All inter-service communication is synchronous HTTP via Spring Cloud OpenFeign clients, resolved through Eureka service discovery.

| Caller | Callee | Feign Client | Operations |
|---|---|---|---|
| internet-banking-user-service | core-banking-service | `BankingCoreRestClient` | `GET /api/v1/user/{identification}` |
| internet-banking-fund-transfer-service | core-banking-service | `BankingCoreFeignClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/fund-transfer` |
| internet-banking-utility-payment-service | core-banking-service | `BankingCoreRestClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/util-payment` |

#### API Gateway Routing

The API Gateway routes external requests to internal services using Spring Cloud Gateway with Eureka-based service discovery. The gateway prefixes are:

- `/user/**` -> internet-banking-user-service
- `/fund-transfer/**` -> internet-banking-fund-transfer-service
- `/banking-core/**` -> core-banking-service
- `/utility-payment/**` -> internet-banking-utility-payment-service

#### Authentication Flow

1. Client authenticates with Keycloak (port 8080) to obtain a JWT access token.
2. Client sends requests to the API Gateway (port 8082) with the JWT in the `Authorization` header.
3. The gateway validates the JWT using Keycloak's JWK set URI (`spring.security.oauth2.resourceserver.jwt.jwk-set-uri`).
4. A `GlobalFilter` extracts the authenticated principal name and injects it as the `X-Auth-Id` HTTP header into the proxied request.
5. Downstream services read `X-Auth-Id` via `AppAuthUserFilter` and store it in a thread-local `ApiRequestContextHolder` for audit purposes.

**Public (unauthenticated) endpoints:**
- `POST /user/api/v1/bank-users/register`
- All `/actuator/**` endpoints across services

#### Centralized Configuration

All services (except the config server and service registry) fetch their configuration from the Config Server at startup via Spring Cloud Bootstrap (`bootstrap.yml`). The Config Server reads from a remote Git repository:
- **Git URI:** `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Search Path:** `configuration`
- **Branch:** `main`

Each service has `bootstrap-docker.yml` and `bootstrap-dev.yml` profiles for Docker and local development environments respectively, overriding the config server URI as needed.

### 1.4 Infrastructure Components

| Component | Technology | Port | Purpose |
|---|---|---|---|
| **Service Registry** | Netflix Eureka | 8081 | Service discovery and registration |
| **Config Server** | Spring Cloud Config | 8090 | Externalized configuration from Git |
| **API Gateway** | Spring Cloud Gateway | 8082 | Request routing, JWT authentication, header injection |
| **Identity Provider** | Keycloak 23.0.7 | 8080 | OAuth2/OpenID Connect authentication, user realm management |
| **Application Database** | MySQL | 3306 | Persistent storage for all business services |
| **Keycloak Database** | PostgreSQL 15 | 5432 (internal) | Keycloak's identity store |
| **Distributed Tracing** | Zipkin 3 | 9411 | Trace collection and visualization |
| **Tracing Bridge** | Micrometer Tracing + Brave | - | In-process trace instrumentation forwarded to Zipkin |
| **Monitoring** | Spring Boot Actuator | per-service | Health checks, metrics, info endpoints |
| **API Documentation** | SpringDoc OpenAPI (WebFlux UI) 2.1.0 | per-service | Swagger UI for each service |

### 1.5 Build & Deployment

- **Build Tool:** Gradle (per-service `build.gradle`, no multi-project root build)
- **Java:** 21 (sourceCompatibility)
- **Containerization:** Each service has a `Dockerfile`; orchestrated via `docker-compose/docker-compose.yml`
- **Startup Orchestration:** Services use `wait-for-it.sh` scripts in Docker entrypoints to wait for the service registry, config server, and MySQL before starting.
- **Database Migrations:** Flyway (core-banking-service only) manages schema evolution.
- **Git Properties:** `com.gorylenko.gradle-git-properties` plugin generates git metadata for Actuator `/info` endpoint.

### 1.6 Docker Network Topology

All containers run on a custom bridge network (`javatodev_ib_network`, subnet `172.25.0.0/16`):

| Container | IP |
|---|---|
| core-banking-service | 172.25.0.2 |
| internet-banking-utility-payment-service | 172.25.0.3 |
| internet-banking-fund-transfer-service | 172.25.0.4 |
| internet-banking-user-service | 172.25.0.5 |
| internet-banking-api-gateway | 172.25.0.6 |
| internet-banking-service-registry | 172.25.0.7 |
| internet-banking-config-server | 172.25.0.8 |
| mysql_javatodev_app | 172.25.0.9 |
| keycloak_postgre_db | 172.25.0.10 |
| keycloak_web | 172.25.0.11 |
| openzipkin_server | 172.25.0.12 |

---

## 2. Data Model Documentation

### 2.1 Database Schema Overview

The system uses **4 separate MySQL databases** (created by `docker-compose/mysql/privileges.sql`):

| Database | Owning Service |
|---|---|
| `banking_core_service` | core-banking-service |
| `banking_core_fund_transfer_service` | internet-banking-fund-transfer-service |
| `banking_core_user_service` | internet-banking-user-service |
| `banking_core_utility_payment_service` | internet-banking-utility-payment-service |

### 2.2 core-banking-service Entities

#### `banking_core_user`

Central bank customer record.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal user ID |
| `first_name` | VARCHAR(255) | nullable | Customer first name |
| `last_name` | VARCHAR(255) | nullable | Customer last name |
| `email` | VARCHAR(255) | nullable | Customer email address |
| `identification_number` | VARCHAR(255) | nullable | National identification (e.g., NIC) |

**JPA Entity:** `UserEntity` (`com.javatodev.finance.model.entity.UserEntity`)
**Relationships:** One-to-Many -> `BankAccountEntity` (mapped by `user`, LAZY fetch, CASCADE ALL)

#### `banking_core_account`

Bank account ledger.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal account ID |
| `number` | VARCHAR(255) | nullable | Account number (e.g., `100015003000`) |
| `type` | VARCHAR(255) | nullable | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | nullable | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `available_balance` | DECIMAL(19,2) | nullable | Balance available for transactions |
| `actual_balance` | DECIMAL(19,2) | nullable | Ledger balance |
| `user_id` | BIGINT | FK -> `banking_core_user.id` | Account owner |

**JPA Entity:** `BankAccountEntity` (`com.javatodev.finance.model.entity.BankAccountEntity`)
**Relationships:** Many-to-One -> `UserEntity` (via `user_id` join column)

#### `banking_core_utility_account`

Utility service provider accounts (telecom, energy, etc.).

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal ID |
| `number` | VARCHAR(255) | nullable | Utility provider account number |
| `provider_name` | VARCHAR(255) | nullable | Provider name (e.g., `VODAFONE`, `AIRTEL`) |

**JPA Entity:** `UtilityAccountEntity` (`com.javatodev.finance.model.entity.UtilityAccountEntity`)
**Relationships:** None (standalone reference data)

#### `banking_core_transaction`

Transaction log for all fund movements.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal transaction ID |
| `amount` | DECIMAL(19,2) | nullable | Transaction amount (negative for debits, positive for credits) |
| `transaction_type` | VARCHAR(30) | NOT NULL | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | NOT NULL | Counterparty account number or utility reference |
| `transaction_id` | VARCHAR(50) | NOT NULL | UUID correlating debit/credit entries |
| `account_id` | BIGINT | FK -> `banking_core_account.id` | Account the transaction belongs to |

**JPA Entity:** `TransactionEntity` (`com.javatodev.finance.model.entity.TransactionEntity`)
**Relationships:** One-to-One -> `BankAccountEntity` (via `account_id`, CASCADE ALL)

### 2.3 internet-banking-user-service Entities

#### `user`

Internet banking user registration record (not the core bank customer - this is the IB-layer identity).

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal ID |
| `auth_id` | VARCHAR(255) | nullable | Keycloak user UUID |
| `identification` | VARCHAR(255) | nullable | Links to core banking user's `identification_number` |
| `status` | VARCHAR(255) | nullable | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | TIMESTAMP | auto | Audit: creation timestamp |
| `created_by` | VARCHAR(255) | auto | Audit: creator |
| `modified_date` | TIMESTAMP | auto | Audit: last modification timestamp |
| `modified_by` | VARCHAR(255) | auto | Audit: last modifier |
| `version` | BIGINT | optimistic lock | JPA `@Version` for optimistic concurrency |

**JPA Entity:** `UserEntity` extends `AuditAware` (`com.javatodev.finance.model.entity.UserEntity`)
**Relationships:** None (uses Feign to resolve core-banking user data)

### 2.4 internet-banking-fund-transfer-service Entities

#### `fund_transfer`

Fund transfer request tracking.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal ID |
| `from_account` | VARCHAR(255) | nullable | Source account number |
| `to_account` | VARCHAR(255) | nullable | Destination account number |
| `amount` | DECIMAL(19,2) | nullable | Transfer amount |
| `transaction_reference` | VARCHAR(255) | nullable | UUID from core-banking-service after execution |
| `status` | VARCHAR(255) | nullable | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` | TIMESTAMP | auto | Audit: creation timestamp |
| `created_by` | VARCHAR(255) | auto | Audit: creator |
| `modified_date` | TIMESTAMP | auto | Audit: last modification timestamp |
| `modified_by` | VARCHAR(255) | auto | Audit: last modifier |
| `version` | BIGINT | optimistic lock | JPA `@Version` for optimistic concurrency |

**JPA Entity:** `FundTransferEntity` extends `AuditAware` (`com.javatodev.finance.model.entity.FundTransferEntity`)

### 2.5 internet-banking-utility-payment-service Entities

#### `utility_payment`

Utility payment request tracking.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal ID |
| `provider_id` | BIGINT | nullable | Utility provider ID (FK to `banking_core_utility_account.id` conceptually) |
| `amount` | DECIMAL(19,2) | nullable | Payment amount |
| `reference_number` | VARCHAR(255) | nullable | Bill/customer reference number |
| `account` | VARCHAR(255) | nullable | Source bank account number |
| `transaction_id` | VARCHAR(255) | nullable | UUID from core-banking-service after execution |
| `status` | VARCHAR(255) | nullable | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` | TIMESTAMP | auto | Audit: creation timestamp |
| `created_by` | VARCHAR(255) | auto | Audit: creator |
| `modified_date` | TIMESTAMP | auto | Audit: last modification timestamp |
| `modified_by` | VARCHAR(255) | auto | Audit: last modifier |
| `version` | BIGINT | optimistic lock | JPA `@Version` for optimistic concurrency |

**JPA Entity:** `UtilityPaymentEntity` extends `AuditAware` (`com.javatodev.finance.model.entity.UtilityPaymentEntity`)

### 2.6 Shared Audit Superclass

`AuditAware` (`@MappedSuperclass`) is used by the internet-banking services (user, fund-transfer, utility-payment) to provide:

| Field | Annotation | Description |
|---|---|---|
| `createdDate` | `@CreatedDate` | Auto-set on insert |
| `createdBy` | `@CreatedBy` | Auto-set from `AuditorAwareConfig` (reads `X-Auth-Id` from request context) |
| `modifiedDate` | `@LastModifiedDate` | Auto-set on update |
| `modifiedBy` | `@LastModifiedBy` | Auto-set from `AuditorAwareConfig` |
| `version` | `@Version` | Optimistic locking counter |

### 2.7 Entity Relationship Diagram (Textual)

```
CORE BANKING SERVICE DATABASE (banking_core_service)
=====================================================

  banking_core_user
  +-----------------------+
  | id (PK)               |
  | first_name            |
  | last_name             |
  | email                 |
  | identification_number |
  +----------+------------+
             |
             | 1:N
             v
  banking_core_account
  +-----------------------+
  | id (PK)               |
  | number                |
  | type                  |
  | status                |
  | available_balance     |
  | actual_balance        |
  | user_id (FK) ---------+---> banking_core_user.id
  +----------+------------+
             |
             | 1:1
             v
  banking_core_transaction
  +-----------------------+
  | id (PK)               |
  | amount                |
  | transaction_type      |
  | reference_number      |
  | transaction_id        |
  | account_id (FK) ------+---> banking_core_account.id
  +-----------------------+

  banking_core_utility_account
  +-----------------------+
  | id (PK)               |
  | number                |
  | provider_name         |
  +-----------------------+


FUND TRANSFER SERVICE DATABASE (banking_core_fund_transfer_service)
===================================================================

  fund_transfer
  +-----------------------+
  | id (PK)               |
  | from_account          |
  | to_account            |
  | amount                |
  | transaction_reference |
  | status                |
  | + AuditAware fields   |
  +-----------------------+


USER SERVICE DATABASE (banking_core_user_service)
==================================================

  user
  +-----------------------+
  | id (PK)               |
  | auth_id               |  ----> Keycloak user UUID
  | identification        |  ----> core banking_core_user.identification_number
  | status                |
  | + AuditAware fields   |
  +-----------------------+


UTILITY PAYMENT SERVICE DATABASE (banking_core_utility_payment_service)
=======================================================================

  utility_payment
  +-----------------------+
  | id (PK)               |
  | provider_id           |  ----> core banking_core_utility_account.id (logical)
  | amount                |
  | reference_number      |
  | account               |
  | transaction_id        |
  | status                |
  | + AuditAware fields   |
  +-----------------------+
```

### 2.8 Seed / Test Data

Flyway migration `V1.0.20210427174721__temp_data.sql` inserts:

- **4 users**: Sam Silva, Guru Darmaraj, Ragu Sivaraj, Randor Manoon
- **14 savings accounts** distributed across the 4 users with balances ranging from 12,000 to 889,000.33
- **6 utility providers**: VODAFONE, VERIZON, SINGTEL, HUTCH, AIRTEL, GIO

---

## 3. API Surface Map

### 3.1 API Gateway (Port 8082) - External Entry Point

All external requests go through the gateway. The gateway strips the service prefix and forwards to the target service. JWT authentication is required for all endpoints except those explicitly permitted.

### 3.2 core-banking-service (Port 8092)

Base path: `/api/v1`

#### Account Controller (`/api/v1/account`)

| Method | Endpoint | Description | Request | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by account number | Path: `account_number` (String) | `BankAccount { id, number, type, status, availableBalance, actualBalance, user }` |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | Path: `account_name` (String) | `UtilityAccount { id, number, providerName }` |

#### User Controller (`/api/v1/user`)

| Method | Endpoint | Description | Request | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/user/{identification}` | Get user by identification number | Path: `identification` (String) | `User { id, firstName, lastName, email, identificationNumber, bankAccounts[] }` |
| `GET` | `/api/v1/user` | Get paginated list of users | Query: `page`, `size`, `sort` (Spring Pageable) | `List<User>` |

#### Transaction Controller (`/api/v1/transaction`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transaction/fund-transfer` | Execute a fund transfer | `FundTransferRequest { fromAccount, toAccount, amount }` | `FundTransferResponse { message, transactionId }` |
| `POST` | `/api/v1/transaction/util-payment` | Execute a utility payment | `UtilityPaymentRequest { providerId, amount, referenceNumber, account }` | `UtilityPaymentResponse { message, transactionId }` |

### 3.3 internet-banking-user-service (Port 8083)

Base path: `/api/v1/bank-users`

| Method | Endpoint | Description | Request Body / Params | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Register a new internet banking user | `User { email, identification, password }` | `User { id, email, identification, authId, status }` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user status (e.g., approve) | Path: `id` (Long); Body: `UserUpdateRequest { status }` | `User { id, email, identification, authId, status }` |
| `GET` | `/api/v1/bank-users` | List all registered IB users (paginated) | Query: `page`, `size`, `sort` (Spring Pageable) | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get a specific IB user by ID | Path: `id` (Long) | `User { id, email, identification, authId, status }` |

### 3.4 internet-banking-fund-transfer-service (Port 8084)

Base path: `/api/v1/transfer`

| Method | Endpoint | Description | Request Body / Params | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate a fund transfer | `FundTransferRequest { fromAccount, toAccount, amount, authID }` | `FundTransferResponse { message, transactionId }` |
| `GET` | `/api/v1/transfer` | List all fund transfers (paginated) | Query: `page`, `size`, `sort` (Spring Pageable) | `List<FundTransfer { id, transactionReference, status, fromAccount, toAccount, amount }>` |

### 3.5 internet-banking-utility-payment-service (Port 8085)

Base path: `/api/v1/utility-payment`

| Method | Endpoint | Description | Request Body / Params | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process a utility payment | `UtilityPaymentRequest { providerId, amount, referenceNumber, account }` | `UtilityPaymentResponse { message, transactionId }` |
| `GET` | `/api/v1/utility-payment` | List all utility payments (paginated) | Query: `page`, `size`, `sort` (Spring Pageable) | `List<UtilityPayment { providerId, amount, referenceNumber, account, status }>` |

### 3.6 internet-banking-service-registry (Port 8081)

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/` | Eureka dashboard (web UI) |
| `GET` | `/eureka/apps` | List registered service instances (Eureka REST API) |

### 3.7 internet-banking-config-server (Port 8090)

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/{application}/{profile}` | Fetch configuration for a given application and profile |
| `GET` | `/{application}/{profile}/{label}` | Fetch configuration for a given application, profile, and Git label |

### 3.8 Cross-Service Actuator Endpoints

Every service exposes Spring Boot Actuator endpoints (unauthenticated through the gateway):

| Endpoint | Description |
|---|---|
| `/actuator/health` | Health check |
| `/actuator/info` | Application info (includes Git properties) |
| `/actuator/metrics` | Micrometer metrics |
| `/actuator/prometheus` | Prometheus-format metrics (if enabled) |

### 3.9 Request/Response DTO Reference

#### Core Banking DTOs

```
BankAccount {
    id: Long
    number: String
    type: AccountType (SAVINGS_ACCOUNT | FIXED_DEPOSIT | LOAN_ACCOUNT)
    status: AccountStatus (PENDING | ACTIVE | DORMANT | BLOCKED)
    availableBalance: BigDecimal
    actualBalance: BigDecimal
    user: User
}

User (core) {
    id: Long
    firstName: String
    lastName: String
    email: String
    identificationNumber: String
    bankAccounts: List<BankAccount>
}

UtilityAccount {
    id: Long
    number: String
    providerName: String
}

Transaction {
    id: Long
    amount: BigDecimal
    bankAccount: BankAccount
    referenceNumber: String
}

FundTransferRequest (core) {
    fromAccount: String
    toAccount: String
    amount: BigDecimal
}

FundTransferResponse {
    message: String
    transactionId: String
}

UtilityPaymentRequest (core) {
    providerId: Long
    amount: BigDecimal
    referenceNumber: String
    account: String
}

UtilityPaymentResponse {
    message: String
    transactionId: String
}
```

#### Internet Banking User Service DTOs

```
User (IB) {
    id: Long
    email: String
    identification: String
    password: String          (input only, for registration)
    authId: String            (Keycloak UUID)
    status: Status (PENDING | APPROVED | DISABLED | BLACKLIST)
}

UserUpdateRequest {
    status: Status
}

UserResponse (from core, via Feign) {
    id: Integer
    firstName: String
    lastName: String
    email: String
    identificationNumber: String
    bankAccounts: List<AccountResponse>
}

AccountResponse {
    id: Integer
    number: String
    type: String
    status: String
    actualBalance: BigDecimal
    availableBalance: BigDecimal
}
```

#### Internet Banking Fund Transfer Service DTOs

```
FundTransferRequest {
    fromAccount: String
    toAccount: String
    amount: BigDecimal
    authID: String
}

FundTransfer {
    id: Long
    transactionReference: String
    status: String
    fromAccount: String
    toAccount: String
    amount: BigDecimal
}
```

#### Internet Banking Utility Payment Service DTOs

```
UtilityPaymentRequest {
    providerId: Long
    amount: BigDecimal
    referenceNumber: String
    account: String
}

UtilityPayment {
    providerId: Long
    amount: BigDecimal
    referenceNumber: String
    account: String
    status: TransactionStatus (PENDING | PROCESSING | SUCCESS | FAILED)
}
```

---

## 4. Key Business Logic Inventory

### 4.1 Fund Transfer Processing

**Flow:** Client -> API Gateway -> Fund Transfer Service -> Core Banking Service

**Location:** `internet-banking-fund-transfer-service` orchestrates; `core-banking-service` executes.

#### Step-by-step (Fund Transfer Service - `FundTransferService.fundTransfer()`):

1. Receive `FundTransferRequest` (fromAccount, toAccount, amount, authID).
2. Create a `FundTransferEntity` with status `PENDING` and persist it.
3. Call core-banking-service via Feign: `POST /api/v1/transaction/fund-transfer`.
4. On success, update the local entity with the `transactionReference` (UUID) and set status to `SUCCESS`.
5. Return `FundTransferResponse` with message and transaction ID.

#### Step-by-step (Core Banking Service - `TransactionService.fundTransfer()`):

1. Look up both the `fromAccount` and `toAccount` by account number (throws `EntityNotFoundException` if not found).
2. **Balance validation:** Verify `fromAccount.actualBalance >= 0` AND `fromAccount.actualBalance >= transferAmount`. Throws `InsufficientFundsException` if either fails.
3. Generate a UUID `transactionId`.
4. **Debit source account:** Subtract `amount` from `actualBalance` and `availableBalance`.
5. Record a debit `TransactionEntity` (type=`FUND_TRANSFER`, amount=negative, referenceNumber=destination account).
6. **Credit destination account:** Add `amount` to `actualBalance` and `availableBalance`.
7. Record a credit `TransactionEntity` (type=`FUND_TRANSFER`, amount=positive, referenceNumber=destination account).
8. Return `FundTransferResponse` with `transactionId`.

**Transactional boundary:** The entire core-banking `TransactionService` is annotated `@Transactional`, ensuring debit and credit are atomic.

#### Business Rules:

- Both source and destination accounts must exist.
- Source account must have sufficient actual balance (>= 0 and >= transfer amount).
- Each fund transfer creates exactly 2 transaction records in core (debit + credit).
- The fund transfer service maintains its own audit trail independently of the core ledger.

### 4.2 Utility Payment Processing

**Flow:** Client -> API Gateway -> Utility Payment Service -> Core Banking Service

**Location:** `internet-banking-utility-payment-service` orchestrates; `core-banking-service` executes.

#### Step-by-step (Utility Payment Service - `UtilityPaymentService.utilPayment()`):

1. Receive `UtilityPaymentRequest` (providerId, amount, referenceNumber, account).
2. Create a `UtilityPaymentEntity` with status `PROCESSING` and persist it.
3. Call core-banking-service via Feign: `POST /api/v1/transaction/util-payment`.
4. On success, update the local entity with `transactionId` and set status to `SUCCESS`.
5. Return `UtilityPaymentResponse` with message and transaction ID.

#### Step-by-step (Core Banking Service - `TransactionService.utilPayment()`):

1. Generate a UUID `transactionId`.
2. Look up the source bank account by account number.
3. **Balance validation:** Same as fund transfer - checks `actualBalance >= 0` and `actualBalance >= paymentAmount`.
4. Look up the utility account by `providerId`.
5. **Debit source account:** Subtract `amount` from both `actualBalance` and `availableBalance`.
6. Record a `TransactionEntity` (type=`UTILITY_PAYMENT`, amount=negative, referenceNumber from request).
7. Return `UtilityPaymentResponse` with `transactionId`.

#### Business Rules:

- Source bank account must exist.
- Utility provider account must exist (by ID).
- Source account must have sufficient balance.
- Only the source account is debited (no credit to a utility account balance - assumes external settlement).
- Single transaction record created (debit only).

### 4.3 User Registration & Management

**Flow:** Client -> API Gateway -> User Service -> Keycloak + Core Banking Service

**Location:** `internet-banking-user-service`

#### User Registration (`UserService.createUser()`):

1. Check if the email is already registered in Keycloak. If so, throw `UserAlreadyRegisteredException`.
2. Call core-banking-service via Feign: `GET /api/v1/user/{identification}` to verify the user exists in the banking core.
3. Validate that the provided email matches the email on record in core banking. If not, throw `InvalidEmailException`.
4. Create a Keycloak `UserRepresentation`:
   - Set email, username (=email), firstName, lastName from core banking data.
   - Set `emailVerified=false`, `enabled=false`.
   - Set credentials with the user-provided password.
5. Call Keycloak Admin API to create the user.
6. If Keycloak returns HTTP 201:
   - Re-read the user from Keycloak to get the `authId` (Keycloak UUID).
   - Persist a local `UserEntity` with status `PENDING`.
7. If the identification doesn't match any core banking user, throw `InvalidBankingUserException`.

#### User Approval (`UserService.updateUser()`):

1. Look up the IB user entity by ID.
2. If the new status is `APPROVED`:
   - Read the user from Keycloak by `authId`.
   - Set `enabled=true` and `emailVerified=true` in Keycloak.
   - Update the Keycloak user representation.
3. Update the local entity status and persist.

#### Business Rules:

- A user must exist in the core banking system before they can register for internet banking.
- Email must match the core banking record exactly.
- Users are created in Keycloak with `enabled=false` - they cannot log in until approved.
- Approval (status -> `APPROVED`) enables the Keycloak account, granting login access.
- Status lifecycle: `PENDING` -> `APPROVED` -> `DISABLED` / `BLACKLIST` (managed via PATCH endpoint).

### 4.4 Authentication & Authorization

**Location:** `internet-banking-api-gateway`

#### Security Configuration:

- OAuth2 Resource Server with JWT validation against Keycloak's JWK Set URI.
- CSRF disabled (stateless API).
- All endpoints require authentication except:
  - `POST /user/api/v1/bank-users/register` (public registration)
  - `/actuator/**` on all services (operational endpoints)

#### Authenticated User Propagation:

1. The `GatewayConfiguration.customGlobalFilter()` extracts `Principal.getName()` from the JWT.
2. Injects it as the `X-Auth-Id` HTTP header on proxied requests.
3. Defaults to `"SYSTEM USER"` if no principal is found.
4. Downstream services' `AppAuthUserFilter` reads `X-Auth-Id` and stores it in `ApiRequestContextHolder` (thread-local).
5. `AuditorAwareConfig` uses this context to populate `createdBy` and `modifiedBy` audit fields.

### 4.5 Error Handling

All services implement a `GlobalExceptionHandler` (`@RestControllerAdvice`) that maps custom exceptions to standardized `ErrorResponse` objects.

#### Custom Exceptions:

| Exception | Service(s) | HTTP Status | Error Code |
|---|---|---|---|
| `EntityNotFoundException` | All | Mapped via handler | Entity not found |
| `InsufficientFundsException` | core-banking | Mapped via handler | `INSUFFICIENT_FUNDS` |
| `UserAlreadyRegisteredException` | user-service | Mapped via handler | `ERROR_EMAIL_REGISTERED` |
| `InvalidEmailException` | user-service | Mapped via handler | `ERROR_INVALID_EMAIL` |
| `InvalidBankingUserException` | user-service | Mapped via handler | `ERROR_USER_NOT_FOUND_UNDER_NIC` |
| `SimpleBankingGlobalException` | All | Mapped via handler | General banking error |

#### Feign Error Handling:

The user-service and fund-transfer-service implement `CustomFeignErrorDecoder` / `CustomFeignClientConfiguration` to translate HTTP errors from core-banking-service into appropriate local exceptions.

#### Error Response Shape:

```
ErrorResponse {
    code: String       (e.g., "INSUFFICIENT_FUNDS")
    message: String    (human-readable description)
}
```

### 4.6 Distributed Tracing

All services are instrumented with:
- **Micrometer Tracing** with Brave bridge for trace/span propagation.
- **Zipkin Reporter** (Brave) for exporting traces to the Zipkin server (port 9411).
- **Feign Micrometer** integration for automatic tracing of Feign client calls.

Trace context is propagated automatically across Feign calls, providing end-to-end visibility from gateway through to core banking operations.

---

## 5. Integration Points

### 5.1 Keycloak (Identity Provider)

| Aspect | Detail |
|---|---|
| **Version** | 23.0.7 |
| **Container** | `keycloak_web` (port 8080) |
| **Backend DB** | PostgreSQL 15 (`keycloak_postgre_db`, port 5432 internal only) |
| **Admin Credentials** | `admin` / `password` |
| **Realm Import** | Auto-imported on startup from `docker-compose/keycloak/` volume mount |
| **Grant Type** | `client_credentials` (service-to-Keycloak communication) |

#### Integration Touchpoints

1. **API Gateway (OAuth2 Resource Server)**
   - Validates JWT tokens using Keycloak's JWK Set URI (`spring.security.oauth2.resourceserver.jwt.jwk-set-uri`).
   - Configuration sourced from Spring Cloud Config (externalized).
   - Uses `spring-boot-starter-oauth2-resource-server` and `spring-boot-starter-security`.

2. **User Service (Keycloak Admin Client)**
   - Library: `keycloak-admin-client:24.0.4`
   - Connects via `KeycloakProperties` using externalized config (`app.config.keycloak.*`):
     - `server-url`: Keycloak base URL
     - `realm`: Target realm name
     - `clientId`: Service client ID
     - `client-secret`: Client secret for `client_credentials` grant
   - Operations: Create user, update user (enable/verify email), search by email, read by auth ID.
   - Singleton `Keycloak` instance (lazy-initialized, not thread-safe).

3. **Client Authentication Flow**
   - Clients obtain JWT tokens by authenticating against Keycloak's token endpoint.
   - Test credentials: `ib_admin@javatodev.com` / `5V7huE3G86uB`

### 5.2 RabbitMQ (Message Broker)

| Aspect | Detail |
|---|---|
| **Status** | Referenced in architecture documentation but **not implemented in code** |
| **Intended Use** | Notification service would consume messages from fund-transfer and utility-payment services |
| **Current State** | No RabbitMQ dependencies in any `build.gradle`, no AMQP configuration, no message producers or consumers |
| **Docker** | No RabbitMQ container in `docker-compose.yml` |

RabbitMQ is part of the planned architecture (mentioned in README for notification service) but has not been implemented. The notification service itself is marked as "PENDING Development."

### 5.3 Zipkin (Distributed Tracing)

| Aspect | Detail |
|---|---|
| **Version** | Zipkin 3 (Docker image: `openzipkin/zipkin:3`) |
| **Container** | `openzipkin_server` (port 9411) |
| **Dashboard** | `http://localhost:9411` |

#### Integration Touchpoints

All 6 microservices (except config server and service registry) include tracing dependencies:

| Dependency | Purpose |
|---|---|
| `io.micrometer:micrometer-tracing-bridge-brave` | Bridges Micrometer Tracing API to Brave tracer |
| `io.zipkin.reporter2:zipkin-reporter-brave` | Exports Brave spans to Zipkin over HTTP |
| `io.github.openfeign:feign-micrometer` | Instruments OpenFeign calls with trace context propagation |

- Trace/span IDs are automatically propagated across Feign client calls.
- Zipkin URL is configured via externalized configuration (Spring Cloud Config).
- The API gateway uses WebFlux tracing (reactive stack).

### 5.4 MySQL (Application Database)

| Aspect | Detail |
|---|---|
| **Container** | `mysql_javatodev_app` (port 3306) |
| **Root Password** | `woVERANKliGharym` |
| **App User** | `javatodev_development` / `oPItyPticIAt` |
| **Custom Image** | Built from `docker-compose/mysql/` (includes `privileges.sql` for DB/user creation) |

#### Database Connections

| Service | Database | Driver | ORM |
|---|---|---|---|
| core-banking-service | `banking_core_service` | `com.mysql:mysql-connector-j:8.4.0` | Spring Data JPA + Hibernate |
| internet-banking-user-service | `banking_core_user_service` | `com.mysql:mysql-connector-j:8.4.0` | Spring Data JPA + Hibernate |
| internet-banking-fund-transfer-service | `banking_core_fund_transfer_service` | `com.mysql:mysql-connector-j:8.4.0` | Spring Data JPA + Hibernate |
| internet-banking-utility-payment-service | `banking_core_utility_payment_service` | `com.mysql:mysql-connector-j:8.4.0` | Spring Data JPA + Hibernate |

- JDBC URLs, credentials, and JPA settings are externalized via Spring Cloud Config.
- Only `core-banking-service` uses Flyway for schema migrations. Other services rely on `hibernate.ddl-auto` (configured externally).
- Test profiles use H2 in-memory database (`com.h2database:h2:2.2.224`).

### 5.5 Netflix Eureka (Service Discovery)

| Aspect | Detail |
|---|---|
| **Server** | `internet-banking-service-registry` (port 8081) |
| **Dashboard** | `http://localhost:8081` |

#### Registered Services

All business services and the API gateway register as Eureka clients:
- `core-banking-service`
- `internet-banking-user-service`
- `internet-banking-fund-transfer-service`
- `internet-banking-utility-payment-service`
- `internet-banking-api-gateway`

Eureka client configuration (service URL, prefer-ip-address, etc.) is sourced from Spring Cloud Config. The service registry itself does **not** register with itself (`register-with-eureka: false`, `fetch-registry: false`).

### 5.6 Spring Cloud Config Server (Centralized Configuration)

| Aspect | Detail |
|---|---|
| **Service** | `internet-banking-config-server` (port 8090) |
| **Backend** | Git repository |
| **Git URI** | `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` |
| **Search Path** | `configuration` |
| **Branch** | `main` |

#### Configuration Consumers

Every service (except the config server itself) connects to the config server at startup via `bootstrap.yml`:

```yaml
spring:
  cloud:
    config:
      uri: http://localhost:8090   # overridden per profile (docker, dev)
```

Configuration includes: database connection strings, Eureka client settings, Keycloak properties, Zipkin URLs, server ports, logging levels, and API gateway route definitions.

### 5.7 Integration Dependency Matrix

| Service | Keycloak | MySQL | Eureka | Config Server | Zipkin | Feign (to core-banking) |
|---|---|---|---|---|---|---|
| core-banking-service | - | Yes | Client | Yes | Yes | - |
| internet-banking-user-service | Admin Client | Yes | Client | Yes | Yes | Yes |
| internet-banking-fund-transfer-service | - | Yes | Client | Yes | Yes | Yes |
| internet-banking-utility-payment-service | - | Yes | Client | Yes | Yes | Yes |
| internet-banking-api-gateway | JWT Validation | - | Client | Yes | Yes | - |
| internet-banking-service-registry | - | - | Server | - | - | - |
| internet-banking-config-server | - | - | - | Self | - | - |

---

## 6. Build and Deployment Pipeline

### 6.1 Build System

| Aspect | Detail |
|---|---|
| **Build Tool** | Gradle (per-service, no multi-project root build file) |
| **Gradle Wrapper** | Each service includes `gradlew` / `gradlew.bat` |
| **Spring Boot Plugin** | `org.springframework.boot` 3.2.4 |
| **Dependency Management Plugin** | `io.spring.dependency-management` 1.1.4 |
| **Java Compatibility** | sourceCompatibility = 21 |
| **Artifact Format** | Executable JAR (Spring Boot fat JAR) |
| **Test Framework** | JUnit 5 (Jupiter) via `useJUnitPlatform()` |

#### Build Command (per service)

```bash
cd <service-directory>
./gradlew clean build
```

The build output JAR lands in `build/libs/<service-name>-0.0.1-SNAPSHOT.jar`.

#### Notable Plugins

| Plugin | Services | Purpose |
|---|---|---|
| `com.gorylenko.gradle-git-properties` 2.4.2 | All except service-registry | Generates `git.properties` for Actuator `/info` endpoint |

### 6.2 Containerization

#### Dockerfile Pattern

All services follow an identical Dockerfile pattern:

```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
LABEL maintainer="chinthaka@javatodev.com"
VOLUME /main-app
ADD build/libs/<service-name>-0.0.1-SNAPSHOT.jar app.jar
EXPOSE <port>
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh
RUN apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

**Key characteristics:**
- **Base image:** Eclipse Temurin 21 JRE on Alpine Linux (minimal footprint).
- **Pre-built JAR:** Dockerfile expects the JAR to already be built (`build/libs/`). It is NOT a multi-stage build.
- **Profile activation:** Docker containers run with `-Dspring.profiles.active=docker`, which triggers `bootstrap-docker.yml` for Docker-specific config server URIs.
- **Startup dependency:** `wait-for-it.sh` is included for orchestrating startup order.

#### Container Images

| Service | Image Name | Port |
|---|---|---|
| core-banking-service | `javatodev/core-banking-service` | 8092 |
| internet-banking-user-service | `javatodev/internet-banking-user-service` | 8083 |
| internet-banking-fund-transfer-service | `javatodev/internet-banking-fund-transfer-service` | 8084 |
| internet-banking-utility-payment-service | `javatodev/internet-banking-utility-payment-service` | 8085 |
| internet-banking-api-gateway | `javatodev/internet-banking-api-gateway` | 8082 |
| internet-banking-service-registry | `javatodev/internet-banking-service-registry` | 8081 |
| internet-banking-config-server | `javatodev/internet-banking-config-server` | 8090 |

### 6.3 Docker Compose Orchestration

Two compose files are provided in `docker-compose/`:

| File | Purpose |
|---|---|
| `docker-compose.yml` | Full stack: all 6 microservices + MySQL + Keycloak + PostgreSQL + Zipkin |
| `docker-compose-support-apps.yml` | Infrastructure only: MySQL + Keycloak + PostgreSQL + Zipkin + Config Server + Service Registry |

#### Startup Order

The `docker-compose.yml` uses `wait-for-it.sh` in entrypoints to enforce dependency ordering:

```
1. MySQL, Keycloak DB, Zipkin (no dependencies)
2. Keycloak (depends_on: keycloakdb)
3. Config Server (no wait-for-it, starts independently)
4. Service Registry (no wait-for-it, starts independently)
5. API Gateway (waits for: service-registry:8081, config-server:8090)
6. Business services (waits for: service-registry:8081, config-server:8090, mysql:3306)
```

#### Deployment Commands

```bash
# Start full stack
cd docker-compose
docker-compose up -d

# Start infrastructure only (for local development)
docker-compose -f docker-compose-support-apps.yml up -d

# Rebuild a specific service
cd <service-directory>
./gradlew clean build
docker build -t javatodev/<service-name> .

# View logs
docker-compose logs -f <service-name>
```

### 6.4 Database Migration Pipeline

| Aspect | Detail |
|---|---|
| **Tool** | Flyway 10.12.0 (core-banking-service only) |
| **Migration Location** | `core-banking-service/src/main/resources/db/migration/` |
| **Naming Convention** | `V{version}_{timestamp}__{description}.sql` |

#### Migration Files

| File | Description |
|---|---|
| `V1.0.20210427174638__create_base_table_structure.sql` | Creates `banking_core_user`, `banking_core_account`, `banking_core_utility_account` tables |
| `V1.0.20210427174721__temp_data.sql` | Inserts seed data (4 users, 14 accounts, 6 utility providers) |
| `V1.0.20210429210839__create_transaction_table.sql` | Creates `banking_core_transaction` table |

**Note:** The other three services (user, fund-transfer, utility-payment) do **not** use Flyway. Their schemas are likely managed by Hibernate's `ddl-auto` setting (configured externally via Spring Cloud Config), which is not recommended for production.

### 6.5 Testing Infrastructure

| Aspect | Detail |
|---|---|
| **Test Framework** | JUnit 5 (Jupiter) |
| **Mocking** | Mockito |
| **Test DB** | H2 in-memory (for core-banking-service tests) |

#### Test Coverage by Service

| Service | Test Classes | Test Methods | Scope |
|---|---|---|---|
| core-banking-service | `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest` | ~16 | Unit tests for service layer with mocked repositories |
| internet-banking-user-service | `InternetBankingUserServiceApplicationTests` | 1 | Spring context load test only |
| internet-banking-fund-transfer-service | `InternetBankingFundTransferServiceApplicationTests` | 1 | Spring context load test only |
| internet-banking-utility-payment-service | `InternetBankingUtilityPaymentServiceApplicationTests` | 1 | Spring context load test only |
| internet-banking-api-gateway | `InternetBankingApiGatewayApplicationTests` | 1 | Spring context load test only |
| internet-banking-config-server | `InternetBankingConfigServerApplicationTests` | 1 | Spring context load test only |
| internet-banking-service-registry | `InternetBankingServiceRegistryApplicationTests` | 1 | Spring context load test only |

### 6.6 API Testing

- **Postman Collection:** Available in `postman_collection/JAVA_TO_DEV_MICROSERVICES.postman_collection.json` with environment file `BANKING_CORE_MICROSERVICES_PROJECT.postman_environment.json`.
- **HTTP Test File:** `internet-banking-api-gateway/api_test.http` with basic actuator endpoint checks.
- **Environment:** Switch to `LOCAL_DOCKER_SETUP` environment in Postman for local testing.

### 6.7 CI/CD Pipeline

**Current state:** No CI/CD pipeline is configured. There are no GitHub Actions workflows, Jenkinsfiles, or other CI configuration files in the repository. Builds and deployments are entirely manual.
