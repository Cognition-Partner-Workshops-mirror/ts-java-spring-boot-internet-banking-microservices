# Application Knowledge Base

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

This is a **Java 21 / Spring Boot 3.2.4** internet banking application built on a microservices architecture using **Spring Cloud 2023.0.0**. It models a simplified banking system with user registration, fund transfers, and utility payments.

### 1.2 Services

| # | Service | Port | Purpose |
|---|---------|------|---------|
| 1 | **internet-banking-config-server** | 8090 | Centralized configuration via Spring Cloud Config; fetches from a remote Git repository |
| 2 | **internet-banking-service-registry** | 8081 | Netflix Eureka server for service discovery |
| 3 | **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway; single entry point, OAuth2/JWT security, request routing |
| 4 | **internet-banking-user-service** | 8083 | User registration, profile management, Keycloak integration |
| 5 | **internet-banking-fund-transfer-service** | 8084 | Account-to-account fund transfers; delegates to core-banking-service via Feign |
| 6 | **internet-banking-utility-payment-service** | 8085 | Utility bill payments; delegates to core-banking-service via Feign |
| 7 | **core-banking-service** | 8092 | System of record: accounts, users, transactions, balance management |

### 1.3 Communication Patterns

```
┌──────────┐      OAuth2/JWT       ┌──────────────────────┐
│  Client   │ ──────────────────►  │   API Gateway (:8082)│
└──────────┘                       └──────┬───────────────┘
                                          │  Routes via Eureka
                    ┌─────────────────────┼─────────────────────┐
                    ▼                     ▼                     ▼
          ┌─────────────────┐  ┌──────────────────┐  ┌──────────────────┐
          │ User Service    │  │ Fund Transfer    │  │ Utility Payment  │
          │ (:8083)         │  │ Service (:8084)  │  │ Service (:8085)  │
          └────────┬────────┘  └────────┬─────────┘  └────────┬─────────┘
                   │ Feign              │ Feign                │ Feign
                   ▼                    ▼                      ▼
              ┌──────────────────────────────────────────────────────┐
              │              Core Banking Service (:8092)            │
              │        (Accounts, Transactions, Users, Ledger)       │
              └──────────────────────────────────────────────────────┘
                                        │
                                        ▼
                                  ┌───────────┐
                                  │   MySQL    │
                                  │  (:3306)   │
                                  └───────────┘
```

- **Synchronous REST (OpenFeign):** All inter-service calls use Spring Cloud OpenFeign with Eureka-based service discovery. Services reference each other by Eureka service name (e.g., `core-banking-service`).
- **API Gateway Routing:** The gateway routes requests to downstream services using path prefixes (`/user/**`, `/fund-transfer/**`, `/utility-payment/**`, `/banking-core/**`).
- **Auth Propagation:** The gateway extracts the authenticated principal name from the JWT and forwards it as an `X-Auth-Id` header to downstream services via a `GlobalFilter`.
- **Config Server:** All services (except the config server and service registry) fetch their configuration at startup from the config server, which reads from a Git repository.

### 1.4 Infrastructure Components

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Database | MySQL 8.4.0 | Persistent storage for all business services (4 databases) |
| Identity Provider | Keycloak 23.0.7 | OAuth2/OIDC authentication and user management |
| Keycloak DB | PostgreSQL 15 | Persistent storage for Keycloak realm data |
| Service Registry | Netflix Eureka | Dynamic service discovery |
| Config Server | Spring Cloud Config | Centralized externalized configuration |
| Distributed Tracing | Zipkin 3 (via Micrometer/Brave) | Request tracing across service boundaries |
| API Gateway | Spring Cloud Gateway | Routing, security enforcement, header propagation |

### 1.5 Network Topology (Docker)

All containers run on a custom bridge network (`javatodev_ib_network`, subnet `172.25.0.0/16`) with static IP assignments for predictable inter-service communication. A `wait-for-it.sh` script is used in each service's Docker entrypoint to ensure dependencies (config server, service registry, MySQL) are available before the application starts.

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (Database: `banking_core_service`)

Managed via **Flyway** migrations.

#### `banking_core_user`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `first_name` | VARCHAR | |
| `last_name` | VARCHAR | |
| `email` | VARCHAR | |
| `identification_number` | VARCHAR | National ID / NIC; unique business key |

**Relationships:** One-to-many with `banking_core_account`.

#### `banking_core_account`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `number` | VARCHAR | Account number; unique lookup key |
| `type` | ENUM | `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | ENUM | `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `available_balance` | DECIMAL | |
| `actual_balance` | DECIMAL | |
| `user_id` | BIGINT (FK) | References `banking_core_user.id` |

#### `banking_core_transaction`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `amount` | DECIMAL | Negative for debits, positive for credits |
| `transaction_type` | ENUM | `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR | Target account number or utility reference |
| `transaction_id` | VARCHAR (UUID) | Unique transaction identifier |
| `account_id` | BIGINT (FK) | References `banking_core_account.id` (OneToOne with CascadeType.ALL) |

#### `banking_core_utility_account`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `number` | VARCHAR | Utility provider account number |
| `provider_name` | VARCHAR | Utility company name; unique lookup key |

### 2.2 Fund Transfer Service (Database: `banking_core_fund_transfer_service`)

#### `fund_transfer`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `transaction_reference` | VARCHAR | UUID from core banking response |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | |
| `status` | ENUM | `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` | TIMESTAMP | Audit field |
| `created_by` | VARCHAR | Audit field (from X-Auth-Id header) |
| `modified_date` | TIMESTAMP | Audit field |
| `modified_by` | VARCHAR | Audit field |
| `version` | BIGINT | Optimistic locking |

### 2.3 User Service (Database: `banking_core_user_service`)

#### `user`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `auth_id` | VARCHAR | Keycloak user UUID; links to Keycloak identity |
| `identification` | VARCHAR | National ID / NIC |
| `status` | ENUM | `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | TIMESTAMP | Audit field |
| `created_by` | VARCHAR | Audit field |
| `modified_date` | TIMESTAMP | Audit field |
| `modified_by` | VARCHAR | Audit field |
| `version` | BIGINT | Optimistic locking |

### 2.4 Utility Payment Service (Database: `banking_core_utility_payment_service`)

#### `utility_payment`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | |
| `reference_number` | VARCHAR | |
| `account` | VARCHAR | Source bank account number |
| `transaction_id` | VARCHAR | UUID from core banking response |
| `status` | ENUM | `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` | TIMESTAMP | Audit field |
| `created_by` | VARCHAR | Audit field |
| `modified_date` | TIMESTAMP | Audit field |
| `modified_by` | VARCHAR | Audit field |
| `version` | BIGINT | Optimistic locking |

---

## 3. API Surface Map

### 3.1 API Gateway Routes

All external traffic enters through the gateway at `:8082`. Routes are defined via Spring Cloud Config and resolve downstream services via Eureka.

| Gateway Path Prefix | Target Service | Notes |
|---------------------|---------------|-------|
| `/user/**` | internet-banking-user-service | |
| `/fund-transfer/**` | internet-banking-fund-transfer-service | |
| `/utility-payment/**` | internet-banking-utility-payment-service | |
| `/banking-core/**` | core-banking-service | |

**Security:** All endpoints require a valid JWT token except:
- `POST /user/api/v1/bank-users/register` (public)
- `/actuator/**` on all services (public)

### 3.2 Core Banking Service (`:8092`)

Base path: `/api/v1`

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | — | `BankAccount` (id, number, type, status, availableBalance, actualBalance, user) | Lookup bank account by number |
| `GET` | `/api/v1/account/util-account/{account_name}` | — | `UtilityAccount` (id, number, providerName) | Lookup utility account by provider name |
| `POST` | `/api/v1/transaction/fund-transfer` | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` | Execute fund transfer between two accounts |
| `POST` | `/api/v1/transaction/util-payment` | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` | Execute utility payment |
| `GET` | `/api/v1/user/{identification}` | — | `User` (id, firstName, lastName, email, identificationNumber, bankAccounts[]) | Read user by identification number |
| `GET` | `/api/v1/user` | Query: `page`, `size`, `sort` | `List<User>` | Read all users (paginated) |

### 3.3 Internet Banking User Service (`:8083`)

Base path: `/api/v1/bank-users`

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `POST` | `/api/v1/bank-users/register` | `{ email, identification, password }` | `User` (id, email, identification, authId, status) | Register new banking user (creates in Keycloak + local DB) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `{ status }` | `User` | Update user status (APPROVED enables Keycloak account) |
| `GET` | `/api/v1/bank-users` | Query: `page`, `size`, `sort` | `List<User>` | List all registered users (paginated) |
| `GET` | `/api/v1/bank-users/{id}` | — | `User` | Read user by internal DB ID |

### 3.4 Internet Banking Fund Transfer Service (`:8084`)

Base path: `/api/v1/transfer`

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `POST` | `/api/v1/transfer` | `{ fromAccount, toAccount, amount, authID }` | `{ message, transactionId }` | Initiate fund transfer (saves locally, delegates to core) |
| `GET` | `/api/v1/transfer` | Query: `page`, `size`, `sort` | `List<FundTransfer>` | List all fund transfers (paginated) |

### 3.5 Internet Banking Utility Payment Service (`:8085`)

Base path: `/api/v1/utility-payment`

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `POST` | `/api/v1/utility-payment` | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` | Process utility payment (saves locally, delegates to core) |
| `GET` | `/api/v1/utility-payment` | Query: `page`, `size`, `sort` | `List<UtilityPayment>` | List all utility payments (paginated) |

### 3.6 Infrastructure Endpoints

| Service | Endpoint | Description |
|---------|----------|-------------|
| All services | `/actuator/health` | Health check |
| All services | `/actuator/info` | Git commit info |
| Service Registry | `:8081/` | Eureka dashboard |
| Config Server | `:8090/{app}/{profile}` | Fetch configuration |
| Zipkin | `:9411/` | Tracing dashboard |

---

## 4. Key Business Logic Inventory

### 4.1 Fund Transfer Rules

**Location:** `core-banking-service` → `TransactionService.fundTransfer()`

1. **Account Lookup:** Both `fromAccount` and `toAccount` are resolved by account number. If either is not found, `EntityNotFoundException` is thrown.
2. **Balance Validation:** The source account's `actualBalance` must be >= the transfer `amount` and must be > 0. Otherwise, `InsufficientFundsException` is thrown.
3. **Debit/Credit:** The source account is debited (both `actualBalance` and `availableBalance` are reduced). The destination account is credited (both fields are increased).
4. **Transaction Records:** Two `TransactionEntity` records are created — one debit (negative amount) on the source account, one credit (positive amount) on the destination. Both share the same `transactionId` (UUID).
5. **Orchestration:** The fund-transfer-service saves a local `FundTransferEntity` with status `PENDING`, calls core-banking via Feign, then updates the local record to `SUCCESS` with the core transaction reference.

> **Bug Note:** In `internalFundTransfer()`, the `availableBalance` is set to `actualBalance.subtract(amount)` *after* `actualBalance` has already been subtracted, resulting in a double-subtraction for the source and double-addition for the destination.

### 4.2 Utility Payment Processing

**Location:** `core-banking-service` → `TransactionService.utilPayment()`

1. **Account Lookup:** The paying account is resolved by number. The utility provider is resolved by `providerId`.
2. **Balance Validation:** Same rules as fund transfer.
3. **Debit:** The paying account's `actualBalance` and `availableBalance` are both reduced.
4. **Transaction Record:** A single `TransactionEntity` is created with `UTILITY_PAYMENT` type and negative amount.
5. **Orchestration:** The utility-payment-service saves a local `UtilityPaymentEntity` with status `PROCESSING`, calls core-banking via Feign, then updates to `SUCCESS`.

> **Bug Note:** Same double-subtraction bug as fund transfers — `availableBalance` is set to `actualBalance.subtract(amount)` after `actualBalance` was already subtracted.

### 4.3 User Registration Flow

**Location:** `internet-banking-user-service` → `UserService.createUser()`

1. **Duplicate Check:** Search Keycloak for existing users with the same email. If found, throw `UserAlreadyRegisteredException`.
2. **Core Banking Lookup:** Call core-banking-service to verify the user exists by identification number. If the email doesn't match core banking records, throw `InvalidEmailException`.
3. **Keycloak Registration:** Create a `UserRepresentation` with the user's details and password. The account is created as disabled (`enabled=false`, `emailVerified=false`).
4. **Local Persistence:** If Keycloak returns HTTP 201, fetch the newly created Keycloak user to get the `authId` (UUID), then save a local `UserEntity` with status `PENDING`.
5. **Approval Flow:** An admin can later `PATCH /update/{id}` with `status: APPROVED`, which enables the Keycloak account and sets `emailVerified=true`.

### 4.4 Authentication & Authorization

**Location:** `internet-banking-api-gateway` → `SecurityConfiguration`

1. The API gateway enforces OAuth2 JWT validation using Keycloak's JWK endpoint.
2. The user registration endpoint is publicly accessible.
3. All actuator endpoints are publicly accessible.
4. All other endpoints require a valid JWT.
5. The gateway extracts the principal name from the JWT and forwards it as `X-Auth-Id` to downstream services.
6. Downstream services extract `X-Auth-Id` via `AppAuthUserFilter` and store it in a `ThreadLocal` context for JPA auditing.

---

## 5. Integration Points

### 5.1 Keycloak (Identity & Access Management)

- **Version:** 23.0.7
- **Connection:** User service connects via `keycloak-admin-client` (v24.0.4) using client credentials grant
- **Configuration Properties:**
  - `app.config.keycloak.server-url` — Keycloak server URL
  - `app.config.keycloak.realm` — Target realm name
  - `app.config.keycloak.clientId` — Client ID for admin operations
  - `app.config.keycloak.client-secret` — Client secret
- **Operations:** Create user, update user (enable/disable), search by email, read user by ID
- **Realm Import:** Docker Compose mounts a realm export JSON for automatic initialization
- **JWT Validation:** API gateway validates JWTs using Keycloak's `jwk-set-uri`
- **Keycloak Database:** PostgreSQL 15 at `172.25.0.10:5432` (credentials: `keycloak/password`)

### 5.2 RabbitMQ (Message Broker)

- **Status:** Referenced in README as an integration point for notification service, but **not currently implemented** in the codebase.
- **Intended Use:** Fund transfer and utility payment services would publish notification messages to RabbitMQ for consumption by a notification service.

### 5.3 Zipkin (Distributed Tracing)

- **Version:** Zipkin 3
- **Connection:** All services include Micrometer Tracing with Brave bridge and Zipkin reporter
- **Dashboard:** `http://localhost:9411`
- **Configuration:** Tracing settings are managed via Spring Cloud Config (sampling probability, endpoint URL)

### 5.4 Database Connections

| Service | Database Name | Database | Driver |
|---------|--------------|----------|--------|
| core-banking-service | `banking_core_service` | MySQL 8.4.0 | `mysql-connector-j:8.4.0` |
| internet-banking-fund-transfer-service | `banking_core_fund_transfer_service` | MySQL 8.4.0 | `mysql-connector-j:8.4.0` |
| internet-banking-user-service | `banking_core_user_service` | MySQL 8.4.0 | `mysql-connector-j:8.4.0` |
| internet-banking-utility-payment-service | `banking_core_utility_payment_service` | MySQL 8.4.0 | `mysql-connector-j:8.4.0` |

- **MySQL Credentials:** `javatodev_development / oPItyPticIAt` (created via `privileges.sql` init script)
- **MySQL Root Password:** `woVERANKliGharym`
- **Schema Management:** Core banking service uses Flyway for migrations; other services rely on Hibernate auto-DDL

### 5.5 Spring Cloud Config (External Configuration)

- **Config Repository:** `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch:** `main`
- **Search Path:** `configuration/`
- **Bootstrap:** Each service uses `bootstrap.yml` (local), `bootstrap-dev.yml` (dev), or `bootstrap-docker.yml` (Docker) to connect to the config server

### 5.6 Inter-Service Feign Clients

| Source Service | Target Service | Feign Client Interface | Endpoints Called |
|---------------|---------------|----------------------|-----------------|
| internet-banking-user-service | core-banking-service | `BankingCoreRestClient` | `GET /api/v1/user/{identification}` |
| internet-banking-fund-transfer-service | core-banking-service | `BankingCoreFeignClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/fund-transfer` |
| internet-banking-utility-payment-service | core-banking-service | `BankingCoreRestClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/util-payment` |

---

## 6. Build and Deployment Pipeline

### 6.1 Build System

- **Build Tool:** Gradle (wrapper version 8.6)
- **Java Version:** 21 (Eclipse Temurin 21.0.2)
- **Spring Boot:** 3.2.4 with Spring Cloud 2023.0.0
- **Build Command:** `./gradlew build` (per-service; no top-level multi-project build)
- **Artifact:** Each service produces a fat JAR in `build/libs/`

### 6.2 Docker

- **Base Image:** `eclipse-temurin:21.0.2_13-jre-alpine`
- **Build Pattern:** Each service has its own `Dockerfile` that copies the pre-built JAR
- **Profile:** Docker containers run with `-Dspring.profiles.active=docker`
- **Wait Script:** `wait-for-it.sh` ensures dependencies are healthy before starting

### 6.3 Docker Compose

Two compose files in `docker-compose/`:

1. **`docker-compose.yml`** — Full stack: all 7 services + MySQL + Keycloak + PostgreSQL + Zipkin
2. **`docker-compose-support-apps.yml`** — Infrastructure only: MySQL, Keycloak, PostgreSQL, Zipkin, Config Server, Service Registry

**Startup Order:** Services use `wait-for-it.sh` in their entrypoints to wait for:
1. Service Registry (`:8081`)
2. Config Server (`:8090`)
3. MySQL (`:3306`)

### 6.4 Databases Initialization

- **MySQL:** Custom Dockerfile runs `privileges.sql` on first start to create the app user and the 4 databases
- **Keycloak:** Realm configuration is imported from `keycloak/realm-export.json` via `--import-realm` command
- **Core Banking Schema:** Managed by Flyway migrations (migration files in config repo)

### 6.5 Configuration Profiles

| Profile | Config Server URL | Use Case |
|---------|------------------|----------|
| `default` | `http://localhost:8090` | Local development |
| `dev` | `http://192.168.1.5:8090` | Development server |
| `docker` | `http://internet-banking-config-server:8090` | Docker Compose deployment |

### 6.6 Notable Build Plugins

| Plugin | Purpose |
|--------|---------|
| `org.springframework.boot` (3.2.4) | Spring Boot packaging |
| `io.spring.dependency-management` (1.1.4) | BOM-based dependency management |
| `com.gorylenko.gradle-git-properties` (2.4.2) | Git info in `/actuator/info` |
