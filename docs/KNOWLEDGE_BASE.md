# Internet Banking Microservices — Knowledge Base

## Table of Contents
- [1. Architecture Overview](#1-architecture-overview)
- [2. Data Model Documentation](#2-data-model-documentation)
- [3. API Surface Map](#3-api-surface-map)
- [4. Key Business Logic Inventory](#4-key-business-logic-inventory)
- [5. Integration Points](#5-integration-points)
- [6. Build and Deployment Pipeline Summary](#6-build-and-deployment-pipeline-summary)

---

## 1. Architecture Overview

### 1.1 Services

The application consists of **7 microservices** (6 in the original repo, plus infrastructure services):

| Service | Port | Type | Description |
|---|---|---|---|
| **internet-banking-service-registry** | 8081 | Infrastructure | Netflix Eureka server — service discovery for all microservices |
| **internet-banking-config-server** | 8090 | Infrastructure | Spring Cloud Config Server — externalized configuration backed by a Git repository |
| **internet-banking-api-gateway** | 8082 | Infrastructure | Spring Cloud Gateway — single entry point with OAuth2/JWT security and request routing |
| **internet-banking-user-service** | 8083 | Business | User registration, approval, and management; integrates with Keycloak for identity |
| **internet-banking-fund-transfer-service** | 8084 | Business | Orchestrates fund transfers between bank accounts via the core banking service |
| **internet-banking-utility-payment-service** | 8085 | Business | Orchestrates utility bill payments via the core banking service |
| **core-banking-service** | 8092 | Business | Core banking engine — manages accounts, users, transactions, and balance operations |

### 1.2 Communication Patterns

```
                                   ┌──────────────────────┐
                                   │  Keycloak (8080)      │
                                   │  OAuth2 / OIDC        │
                                   └──────────┬───────────┘
                                              │ JWT validation
                                              ▼
┌──────────┐      ┌──────────────────────────────────────────┐
│  Client   │─────▶│  API Gateway (8082)                      │
└──────────┘      │  Routes: /user/**, /fund-transfer/**,    │
                  │          /utility-payment/**,             │
                  │          /banking-core/**                 │
                  └─────┬────────┬────────────┬──────────────┘
                        │        │            │
              ┌─────────▼┐  ┌───▼──────┐  ┌──▼───────────────┐
              │ User Svc  │  │ Fund     │  │ Utility Payment  │
              │ (8083)    │  │ Transfer │  │ Service (8085)   │
              │           │  │ (8084)   │  │                  │
              └─────┬─────┘  └────┬─────┘  └──────┬───────────┘
                    │             │                │
                    │  OpenFeign  │   OpenFeign    │  OpenFeign
                    ▼             ▼                ▼
              ┌─────────────────────────────────────────────┐
              │          Core Banking Service (8092)         │
              │  Accounts, Transactions, Users, Utility Accts│
              └─────────────────────────────────────────────┘
```

- **Synchronous REST (OpenFeign):** All inter-service communication uses OpenFeign clients, discovered through Eureka.
  - `user-service → core-banking-service` (read user by identification)
  - `fund-transfer-service → core-banking-service` (read account, execute fund transfer)
  - `utility-payment-service → core-banking-service` (read account, execute utility payment)
- **Service Discovery:** Netflix Eureka — all services register on startup and discover peers by service name.
- **Centralized Configuration:** Spring Cloud Config Server pulls configuration from a remote Git repository (`internet-banking-microservices-configurations`).
- **Distributed Tracing:** Micrometer Tracing with Brave bridge, reporting to Zipkin (port 9411).
- **Authentication Flow:** API Gateway acts as an OAuth2 Resource Server. It validates JWT tokens issued by Keycloak and injects `X-Auth-Id` header into downstream requests.

### 1.3 Infrastructure Components

| Component | Technology | Purpose |
|---|---|---|
| Service Registry | Netflix Eureka | Service discovery |
| Config Server | Spring Cloud Config | Externalized configuration (Git-backed) |
| API Gateway | Spring Cloud Gateway | Routing, security, header injection |
| Identity Provider | Keycloak 23.0.7 | OAuth2/OIDC, user realm management |
| Database | MySQL 8.x | Persistent storage for all business services |
| Distributed Tracing | Zipkin 3 | Trace collection and visualization |
| Keycloak DB | PostgreSQL 15 | Keycloak's backing store |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (Database: `banking_core_service`)

#### `banking_core_user`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto) | Primary key |
| `first_name` | VARCHAR(255) | User's first name |
| `last_name` | VARCHAR(255) | User's last name |
| `email` | VARCHAR(255) | User's email address |
| `identification_number` | VARCHAR(255) | National identification number |

#### `banking_core_account`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto) | Primary key |
| `number` | VARCHAR(255) | Account number |
| `type` | ENUM(`SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT`) | Account type |
| `status` | ENUM(`PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED`) | Account status |
| `available_balance` | DECIMAL(19,2) | Available balance |
| `actual_balance` | DECIMAL(19,2) | Actual (ledger) balance |
| `user_id` | BIGINT (FK → `banking_core_user.id`) | Owning user |

#### `banking_core_transaction`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto) | Primary key |
| `amount` | DECIMAL(19,2) | Transaction amount (negative = debit) |
| `transaction_type` | ENUM(`FUND_TRANSFER`, `UTILITY_PAYMENT`) | Transaction category |
| `reference_number` | VARCHAR(50) | Reference (destination account or ref number) |
| `transaction_id` | VARCHAR(50) | UUID-based transaction identifier |
| `account_id` | BIGINT (FK → `banking_core_account.id`) | Associated account |

#### `banking_core_utility_account`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto) | Primary key |
| `number` | VARCHAR(255) | Utility provider account number |
| `provider_name` | VARCHAR(255) | Utility provider name (e.g., VODAFONE) |

**Relationships:**
- `banking_core_user` 1──* `banking_core_account` (one user has many accounts)
- `banking_core_account` 1──* `banking_core_transaction` (one account has many transactions)

### 2.2 Fund Transfer Service (Database: `banking_core_fund_transfer_service`)

#### `fund_transfer`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto) | Primary key |
| `from_account` | VARCHAR(255) | Source account number |
| `to_account` | VARCHAR(255) | Destination account number |
| `amount` | DECIMAL(19,2) | Transfer amount |
| `status` | ENUM(`PENDING`, `PROCESSING`, `SUCCESS`, `FAILED`) | Transfer status |
| `transaction_reference` | VARCHAR(255) | Core banking transaction ID |
| `created_date` | TIMESTAMP | Audit: creation time |
| `created_by` | VARCHAR(255) | Audit: creator |
| `modified_date` | TIMESTAMP | Audit: last modification time |
| `modified_by` | VARCHAR(255) | Audit: last modifier |
| `version` | BIGINT | Optimistic lock version |

### 2.3 User Service (Database: `banking_core_user_service`)

#### `user`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto) | Primary key |
| `auth_id` | VARCHAR(255) | Keycloak user ID |
| `identification` | VARCHAR(255) | National identification number |
| `status` | ENUM(`PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST`) | User registration status |
| `created_date` | TIMESTAMP | Audit: creation time |
| `created_by` | VARCHAR(255) | Audit: creator |
| `modified_date` | TIMESTAMP | Audit: last modification time |
| `modified_by` | VARCHAR(255) | Audit: last modifier |
| `version` | BIGINT | Optimistic lock version |

### 2.4 Utility Payment Service (Database: `banking_core_utility_payment_service`)

#### `utility_payment`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto) | Primary key |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL(19,2) | Payment amount |
| `reference_number` | VARCHAR(255) | Customer reference number |
| `account` | VARCHAR(255) | Source bank account number |
| `transaction_id` | VARCHAR(255) | Core banking transaction ID |
| `status` | ENUM(`PENDING`, `PROCESSING`, `SUCCESS`, `FAILED`) | Payment status |
| `created_date` | TIMESTAMP | Audit: creation time |
| `created_by` | VARCHAR(255) | Audit: creator |
| `modified_date` | TIMESTAMP | Audit: last modification time |
| `modified_by` | VARCHAR(255) | Audit: last modifier |
| `version` | BIGINT | Optimistic lock version |

---

## 3. API Surface Map

### 3.1 API Gateway Routes (port 8082)

All requests flow through the gateway. The gateway strips the first path segment and forwards:

| Gateway Path Prefix | Target Service |
|---|---|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |
| `/banking-core/**` | core-banking-service |

### 3.2 Core Banking Service (port 8092)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | — | `BankAccount` | Read bank account by number |
| `GET` | `/api/v1/account/util-account/{account_name}` | — | `UtilityAccount` | Read utility account by provider name |
| `POST` | `/api/v1/transaction/fund-transfer` | `FundTransferRequest` | `FundTransferResponse` | Execute fund transfer in core banking |
| `POST` | `/api/v1/transaction/util-payment` | `UtilityPaymentRequest` | `UtilityPaymentResponse` | Execute utility payment in core banking |
| `GET` | `/api/v1/user/{identification}` | — | `User` | Read user by identification number |
| `GET` | `/api/v1/user` | Pageable params | `List<User>` | Read paginated list of users |

**Request/Response Shapes:**

```
FundTransferRequest: { fromAccount: string, toAccount: string, amount: BigDecimal }
FundTransferResponse: { message: string, transactionId: string }
UtilityPaymentRequest: { providerId: long, amount: BigDecimal, referenceNumber: string, account: string }
UtilityPaymentResponse: { message: string, transactionId: string }
BankAccount: { id: long, number: string, type: AccountType, status: AccountStatus, availableBalance: BigDecimal, actualBalance: BigDecimal, user: User }
User (core): { id: long, firstName: string, lastName: string, email: string, identificationNumber: string, accounts: List<BankAccount> }
```

### 3.3 Internet Banking User Service (port 8083)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | `User` | `User` | Register a new banking user (creates in Keycloak + local DB) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `UserUpdateRequest` | `User` | Update user status (approve/disable) |
| `GET` | `/api/v1/bank-users` | Pageable params | `List<User>` | Read paginated list of users |
| `GET` | `/api/v1/bank-users/{id}` | — | `User` | Read user by ID |

**Request/Response Shapes:**

```
User: { id: long, email: string, identification: string, password: string, authId: string, status: Status }
UserUpdateRequest: { status: Status }
Status: PENDING | APPROVED | DISABLED | BLACKLIST
```

### 3.4 Internet Banking Fund Transfer Service (port 8084)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | `FundTransferRequest` | `FundTransferResponse` | Initiate fund transfer |
| `GET` | `/api/v1/transfer` | Pageable params | `List<FundTransfer>` | Read paginated fund transfers |

**Request/Response Shapes:**

```
FundTransferRequest: { fromAccount: string, toAccount: string, amount: BigDecimal, authID: string }
FundTransferResponse: { message: string, transactionId: string }
```

### 3.5 Internet Banking Utility Payment Service (port 8085)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | `UtilityPaymentRequest` | `UtilityPaymentResponse` | Process utility payment |
| `GET` | `/api/v1/utility-payment` | Pageable params | `List<UtilityPayment>` | Read paginated utility payments |

**Request/Response Shapes:**

```
UtilityPaymentRequest: { providerId: long, amount: BigDecimal, referenceNumber: string, account: string }
UtilityPaymentResponse: { message: string, transactionId: string }
```

### 3.6 Actuator Endpoints (all services)

All business services expose Spring Boot Actuator at `/actuator/**`. The gateway permits unauthenticated access to actuator endpoints.

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

1. Client calls `POST /api/v1/bank-users/register` with email, identification, and password.
2. User service checks Keycloak — if email already registered, throws `UserAlreadyRegisteredException`.
3. Calls core-banking-service to verify the identification number exists as a core banking customer.
4. Validates that the email matches the core banking user's email (throws `InvalidEmailException` if not).
5. Creates a Keycloak user representation (disabled, email unverified) with the provided password.
6. If Keycloak returns 201, saves the user locally with `PENDING` status and Keycloak auth ID.

### 4.2 User Approval Flow

1. Admin calls `PATCH /api/v1/bank-users/update/{id}` with `{ status: "APPROVED" }`.
2. User service reads the Keycloak user, sets `enabled=true` and `emailVerified=true`.
3. Updates local user status to `APPROVED`.

### 4.3 Fund Transfer Flow

1. Client calls `POST /api/v1/transfer` through the API gateway (JWT required).
2. Fund transfer service saves a local record with `PENDING` status.
3. Delegates to core-banking-service via Feign: `POST /api/v1/transaction/fund-transfer`.
4. Core banking service:
   - Reads both source and destination bank accounts.
   - **Validates balance:** source account's `actualBalance` must be ≥ transfer amount (throws `InsufficientFundsException`).
   - Debits the source account, credits the destination account.
   - Creates two `TransactionEntity` records (debit on source, credit on destination).
   - Returns a transaction ID.
5. Fund transfer service updates local record with `SUCCESS` status and transaction reference.

### 4.4 Utility Payment Flow

1. Client calls `POST /api/v1/utility-payment` through the API gateway.
2. Utility payment service saves a local record with `PROCESSING` status.
3. Delegates to core-banking-service via Feign: `POST /api/v1/transaction/util-payment`.
4. Core banking service:
   - Reads the source bank account and validates balance.
   - Reads the utility account by provider ID.
   - Debits the source account.
   - Creates a `TransactionEntity` record.
   - Returns a transaction ID.
5. Utility payment service updates local record with `SUCCESS` and transaction ID.

### 4.5 Balance Validation Rules

- `actualBalance` must be ≥ 0 **and** ≥ the requested transfer/payment amount.
- If validation fails, `InsufficientFundsException` is thrown with code `BANKING-CORE-SERVICE-1001`.

### 4.6 Authentication & Authorization

- API gateway validates JWT tokens from Keycloak.
- The `/user/api/v1/bank-users/register` endpoint is **public** (permitAll).
- All actuator endpoints are public.
- All other endpoints require authentication.
- Gateway injects `X-Auth-Id` header with the authenticated principal's name.
- Downstream services extract this header via `AppAuthUserFilter` for audit purposes.

---

## 5. Integration Points

### 5.1 Keycloak (Identity Provider)

- **Service:** internet-banking-user-service
- **Library:** `keycloak-admin-client:24.0.4`
- **Configuration:** `app.config.keycloak.server-url`, `realm`, `clientId`, `client-secret` (from Config Server)
- **Operations:** Create user, read user by email/ID, update user (enable/disable)
- **Realm:** `javatodev-internet-banking`
- **Grant type:** `client_credentials`

### 5.2 Zipkin (Distributed Tracing)

- **All services** include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave`.
- Traces are exported to Zipkin at port 9411.
- Feign calls are instrumented via `feign-micrometer`.

### 5.3 MySQL (Database)

- **Database server:** Single MySQL 8.x instance shared by all business services.
- **Databases:**
  - `banking_core_service` — core banking entities (users, accounts, transactions, utility accounts)
  - `banking_core_fund_transfer_service` — fund transfer records
  - `banking_core_user_service` — internet banking user registrations
  - `banking_core_utility_payment_service` — utility payment records
- **User:** `javatodev_development` / `oPItyPticIAt`
- **Schema management:** Flyway migrations (core-banking-service only); other services use Hibernate `ddl-auto` (JPA auto-create).

### 5.4 Spring Cloud Config Server (Centralized Configuration)

- **Git Repository:** `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch:** `main`, search path: `configuration`
- All services connect to Config Server on startup via `bootstrap.yml`.
- Profile-specific bootstrap files (`bootstrap-docker.yml`, `bootstrap-dev.yml`) override the config server URI for different environments.

### 5.5 Netflix Eureka (Service Discovery)

- **Server:** internet-banking-service-registry on port 8081.
- All business services and the API gateway register as Eureka clients.
- Feign clients use Eureka for service-name-to-URL resolution (e.g., `core-banking-service`).

### 5.6 RabbitMQ (Message Broker) — Planned

- Mentioned in the README for notification service integration.
- **Not yet implemented** in the current codebase — no RabbitMQ dependencies or configuration exist.

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Gradle 8.6** with per-service wrapper (`gradlew`) — no root build file.
- Each service is an independent Gradle project.
- Shared library: `banking-common` (referenced in blueprint but not in the current repo directory listing).
- **Java 21** with Spring Boot 3.2.4, Spring Cloud 2023.0.0.

### 6.2 Dependencies per Service

| Service | Spring Boot Web | JPA | Eureka Client | OpenFeign | Config | Actuator | Tracing | Security | Swagger |
|---|---|---|---|---|---|---|---|---|---|
| service-registry | ✓ | | Server | | | ✓ | | | |
| config-server | | | | | Server | ✓ | | | |
| api-gateway | | | ✓ | | ✓ | ✓ | ✓ | OAuth2+Security | |
| user-service | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | ✓ |
| fund-transfer-service | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | ✓ |
| utility-payment-service | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | | ✓ |
| core-banking-service | ✓ | ✓ | ✓ | | ✓ | ✓ | ✓ | | ✓ |

### 6.3 Docker & Docker Compose

- Each service has a `Dockerfile` based on `eclipse-temurin:21.0.2_13-jre-alpine`.
- `wait-for-it.sh` scripts ensure startup ordering (service-registry → config-server → MySQL → application).
- **`docker-compose.yml`** orchestrates the full stack:
  - Zipkin, Keycloak + PostgreSQL, MySQL, Config Server, Service Registry, API Gateway, and all 4 business services.
  - Custom bridge network: `172.25.0.0/16` with static IPs.
- **`docker-compose-support-apps.yml`** — a subset for infrastructure only (Zipkin, Keycloak, MySQL, Config Server, Service Registry).
- Docker images are published as `javatodev/<service-name>` on Docker Hub.

### 6.4 Database Migrations

- **Core Banking Service:** Flyway migrations in `src/main/resources/db/migration/`:
  - `V1.0.20210427174638` — Create `banking_core_user`, `banking_core_account`, `banking_core_utility_account` tables.
  - `V1.0.20210427174721` — Seed test data (4 users, 14 accounts, 6 utility accounts).
  - `V1.0.20210429210839` — Create `banking_core_transaction` table.
- **Other services:** Hibernate auto-DDL (no explicit migrations).

### 6.5 Test Infrastructure

- **Test database:** H2 in-memory for all services.
- **Core banking service:** Has 3 unit test classes (AccountServiceTest, TransactionServiceTest, UserServiceTest) with Mockito-based tests.
- **Other services:** Only have empty `ApplicationTests` context-load stubs.
- **Postman collection** included for manual API testing (`postman_collection/`).
