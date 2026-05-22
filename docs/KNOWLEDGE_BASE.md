# Application Knowledge Base

## Table of Contents

- [1. Architecture Overview](#1-architecture-overview)
- [2. Data Model Documentation](#2-data-model-documentation)
- [3. API Surface Map](#3-api-surface-map)
- [4. Key Business Logic Inventory](#4-key-business-logic-inventory)
- [5. Integration Points](#5-integration-points)
- [6. Build and Deployment Pipeline Summary](#6-build-and-deployment-pipeline-summary)

---

## 1. Architecture Overview

### 1.1 High-Level Architecture

The application follows a **Spring Cloud microservices** pattern with six independently deployable services communicating through synchronous REST (OpenFeign) calls. All external traffic enters through a single API Gateway that enforces OAuth 2.0 (Keycloak) authentication before routing to downstream services discovered via Eureka.

```
                        ┌──────────────────┐
                        │   Keycloak (IdP)  │ ◄── OAuth2 JWT tokens
                        └────────┬─────────┘
                                 │
                    ┌────────────▼────────────┐
  Clients ────────► │   API Gateway (:8082)   │ ◄── Spring Cloud Gateway + OAuth2 Resource Server
                    └────────────┬────────────┘
                                 │  X-Auth-Id header injection
                    ┌────────────▼────────────┐
                    │  Service Registry (:8081)│ ◄── Netflix Eureka
                    └────────────┬────────────┘
                ┌───────────┬────┴────┬───────────┐
                ▼           ▼         ▼           ▼
         ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐
         │  User    │ │  Fund    │ │ Utility  │ │ Core Banking │
         │ Service  │ │ Transfer │ │ Payment  │ │   Service    │
         │ (:8083)  │ │ (:8084)  │ │ (:8085)  │ │   (:8092)    │
         └────┬─────┘ └────┬─────┘ └────┬─────┘ └──────────────┘
              │  Feign      │  Feign     │  Feign       ▲
              └─────────────┴────────────┴──────────────┘
                              │
                    ┌─────────▼─────────┐     ┌────────────┐
                    │   MySQL (:3306)   │     │ Zipkin     │
                    └───────────────────┘     │ (:9411)    │
                                              └────────────┘
                    ┌───────────────────┐
                    │ Config Server     │ ◄── Git-backed (GitHub)
                    │ (:8090)           │
                    └───────────────────┘
```

### 1.2 Services Summary

| Service | Port | Role | Database |
|---------|------|------|----------|
| **internet-banking-service-registry** | 8081 | Netflix Eureka service discovery server | None |
| **internet-banking-config-server** | 8090 | Spring Cloud Config Server (Git-backed) | None |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway with OAuth2/Keycloak security | None |
| **internet-banking-user-service** | 8083 | User registration, management, Keycloak integration | `banking_core_user_service` (MySQL) |
| **internet-banking-fund-transfer-service** | 8084 | Fund transfer orchestration between accounts | `banking_core_fund_transfer_service` (MySQL) |
| **internet-banking-utility-payment-service** | 8085 | Utility bill payment processing | `banking_core_utility_payment_service` (MySQL) |
| **core-banking-service** | 8092 | Core banking engine: accounts, users, transactions | `banking_core_service` (MySQL) |

### 1.3 Communication Patterns

| Pattern | Technology | Usage |
|---------|-----------|-------|
| **Synchronous REST** | OpenFeign | All inter-service communication (fund-transfer→core-banking, utility-payment→core-banking, user-service→core-banking) |
| **Service Discovery** | Netflix Eureka | Services register and discover each other via Eureka; Feign clients resolve by service name |
| **API Gateway Routing** | Spring Cloud Gateway | Routes prefixed paths (`/user/**`, `/fund-transfer/**`, `/utility-payment/**`, `/banking-core/**`) to respective services |
| **Centralized Config** | Spring Cloud Config Server | Config fetched from Git repo at `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` |
| **Auth Header Propagation** | Custom `GlobalFilter` | Gateway extracts JWT principal name → sets `X-Auth-Id` header → downstream `AppAuthUserFilter` captures for auditing |
| **Distributed Tracing** | Micrometer Tracing + Brave + Zipkin | Trace context propagated across all services; spans exported to Zipkin |

### 1.4 Infrastructure Components

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Database** | MySQL 8.4.0 | Persistent storage for all application services (4 separate databases) |
| **Identity Provider** | Keycloak 23.0.7 (PostgreSQL 15 backend) | OAuth 2.0 / OpenID Connect authentication; realm `javatodev-internet-banking` |
| **Distributed Tracing** | Zipkin 3 | Collects and visualizes distributed traces |
| **Service Registry** | Netflix Eureka | Service discovery and registration |
| **Configuration Store** | Git repository (GitHub) | Externalized configuration for all services |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service

The core banking service owns the primary banking domain entities.

#### `banking_core_user`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGINT` | PK, AUTO_INCREMENT | Surrogate primary key |
| `email` | `VARCHAR(255)` | | User email address |
| `first_name` | `VARCHAR(255)` | | User first name |
| `last_name` | `VARCHAR(255)` | | User last name |
| `identification_number` | `VARCHAR(255)` | | National ID or identification number |

#### `banking_core_account`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGINT` | PK, AUTO_INCREMENT | Surrogate primary key |
| `number` | `VARCHAR(255)` | | Account number (12-digit string) |
| `type` | `VARCHAR(255)` | ENUM: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` | Account type |
| `status` | `VARCHAR(255)` | ENUM: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` | Account status |
| `actual_balance` | `DECIMAL(19,2)` | | Current ledger balance |
| `available_balance` | `DECIMAL(19,2)` | | Available withdrawal balance |
| `user_id` | `BIGINT` | FK → `banking_core_user.id` | Account owner |

#### `banking_core_transaction`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGINT` | PK, AUTO_INCREMENT | Surrogate primary key |
| `amount` | `DECIMAL(19,2)` | | Transaction amount (negative for debits) |
| `transaction_type` | `VARCHAR(30)` | NOT NULL; ENUM: `FUND_TRANSFER`, `UTILITY_PAYMENT` | Type of transaction |
| `reference_number` | `VARCHAR(50)` | NOT NULL | Reference (target account or utility ref) |
| `transaction_id` | `VARCHAR(50)` | NOT NULL | UUID-based unique transaction identifier |
| `account_id` | `BIGINT` | FK → `banking_core_account.id` | Associated account |

#### `banking_core_utility_account`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGINT` | PK, AUTO_INCREMENT | Surrogate primary key |
| `number` | `VARCHAR(255)` | | Utility provider account number |
| `provider_name` | `VARCHAR(255)` | | Utility provider name (e.g., VODAFONE, AIRTEL) |

#### Relationships

```
banking_core_user (1) ───< (N) banking_core_account (1) ───< (N) banking_core_transaction
banking_core_utility_account (standalone, referenced by providerId in payment requests)
```

### 2.2 Internet Banking User Service

#### `user`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGINT` | PK, AUTO_INCREMENT | Surrogate primary key |
| `auth_id` | `VARCHAR(255)` | | Keycloak user ID (UUID) |
| `identification` | `VARCHAR(255)` | | Links to `banking_core_user.identification_number` |
| `status` | `VARCHAR(255)` | ENUM: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` | User approval status |
| `created_date` | `TIMESTAMP` | Audit | Record creation timestamp |
| `created_by` | `VARCHAR(255)` | Audit | Creator identifier |
| `modified_date` | `TIMESTAMP` | Audit | Last modification timestamp |
| `modified_by` | `VARCHAR(255)` | Audit | Last modifier identifier |
| `version` | `BIGINT` | Optimistic lock | JPA `@Version` for concurrency |

### 2.3 Internet Banking Fund Transfer Service

#### `fund_transfer`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGINT` | PK, AUTO_INCREMENT | Surrogate primary key |
| `from_account` | `VARCHAR(255)` | | Source account number |
| `to_account` | `VARCHAR(255)` | | Destination account number |
| `amount` | `DECIMAL(19,2)` | | Transfer amount |
| `transaction_reference` | `VARCHAR(255)` | | Transaction ID returned from core banking |
| `status` | `VARCHAR(255)` | ENUM: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` | Transfer status |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | | Audit fields | Inherited from `AuditAware` |

### 2.4 Internet Banking Utility Payment Service

#### `utility_payment`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGINT` | PK, AUTO_INCREMENT | Surrogate primary key |
| `provider_id` | `BIGINT` | | Utility provider reference ID |
| `amount` | `DECIMAL(19,2)` | | Payment amount |
| `reference_number` | `VARCHAR(255)` | | Customer reference number |
| `account` | `VARCHAR(255)` | | Source bank account number |
| `transaction_id` | `VARCHAR(255)` | | Transaction ID from core banking |
| `status` | `VARCHAR(255)` | ENUM: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` | Payment status |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | | Audit fields | Inherited from `AuditAware` |

---

## 3. API Surface Map

### 3.1 API Gateway Routes

All external requests are routed through the API Gateway (`:8082`) with the following path prefixes:

| Gateway Path Prefix | Target Service | Auth Required |
|---------------------|---------------|---------------|
| `/user/**` | internet-banking-user-service | Yes (except `/user/api/v1/bank-users/register`) |
| `/fund-transfer/**` | internet-banking-fund-transfer-service | Yes |
| `/utility-payment/**` | internet-banking-utility-payment-service | Yes |
| `/banking-core/**` | core-banking-service | Yes |
| `/actuator/**` | (all services) | No |

### 3.2 Internet Banking User Service (`:8083`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/bank-users/register` | Register a new internet banking user | `User { email, identification, password }` | `User { id, email, identification, authId, status }` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user status (approve/disable) | `UserUpdateRequest { status }` | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | — | `User` |

### 3.3 Internet Banking Fund Transfer Service (`:8084`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/transfer` | Initiate a fund transfer | `FundTransferRequest { fromAccount, toAccount, amount }` | `FundTransferResponse { message, transactionId }` |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | Query: `page`, `size`, `sort` | `List<FundTransfer>` |

### 3.4 Internet Banking Utility Payment Service (`:8085`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/utility-payment` | Process a utility payment | `UtilityPaymentRequest { providerId, amount, referenceNumber, account }` | `UtilityPaymentResponse { message, transactionId }` |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | Query: `page`, `size`, `sort` | `List<UtilityPayment>` |

### 3.5 Core Banking Service (`:8092`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by account number | — | `BankAccount { id, number, type, status, availableBalance, actualBalance }` |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | — | `UtilityAccount { id, number, providerName }` |
| `POST` | `/api/v1/transaction/fund-transfer` | Process a fund transfer (core) | `FundTransferRequest { fromAccount, toAccount, amount }` | `FundTransferResponse { message, transactionId }` |
| `POST` | `/api/v1/transaction/util-payment` | Process a utility payment (core) | `UtilityPaymentRequest { providerId, amount, referenceNumber, account }` | `UtilityPaymentResponse { message, transactionId }` |
| `GET` | `/api/v1/user/{identification}` | Get user by identification number | — | `User { id, firstName, lastName, email, identificationNumber, accounts }` |
| `GET` | `/api/v1/user` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |

### 3.6 Feign Client Calls (Inter-Service)

| Calling Service | Target Service | Feign Method | Target Endpoint |
|----------------|---------------|--------------|-----------------|
| Fund Transfer Service | Core Banking | `readAccount(accountNumber)` | `GET /api/v1/account/bank-account/{account_number}` |
| Fund Transfer Service | Core Banking | `fundTransfer(request)` | `POST /api/v1/transaction/fund-transfer` |
| Utility Payment Service | Core Banking | `readAccount(accountNumber)` | `GET /api/v1/account/bank-account/{account_number}` |
| Utility Payment Service | Core Banking | `utilityPayment(request)` | `POST /api/v1/transaction/util-payment` |
| User Service | Core Banking | `readUser(identification)` | `GET /api/v1/user/{identification}` |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

**Location:** `internet-banking-user-service` → `UserService.createUser()`

1. Check if email is already registered in Keycloak → throw `UserAlreadyRegisteredException` if exists
2. Fetch user from core banking service via Feign by identification number
3. Validate that the email in the request matches the core banking record → throw `InvalidEmailException` if mismatch
4. Create a Keycloak user representation (disabled, email unverified) with provided password
5. Call Keycloak Admin API to create user; verify 201 status
6. Retrieve the created Keycloak user to get the `authId`
7. Save to local `user` table with status `PENDING`

### 4.2 User Approval Flow

**Location:** `internet-banking-user-service` → `UserService.updateUser()`

1. Find user entity by ID → throw `EntityNotFoundException` if not found
2. If new status is `APPROVED`: enable the Keycloak user and mark email as verified
3. Update the user entity's status in the local database

### 4.3 Fund Transfer Flow

**Location:** `internet-banking-fund-transfer-service` → `FundTransferService.fundTransfer()`

1. Create a `FundTransferEntity` from the request with status `PENDING`
2. Save to `fund_transfer` table
3. Call core banking service via Feign to execute the transfer
4. Update entity with transaction reference and status `SUCCESS`
5. Return success response

**Core Banking Execution:** `core-banking-service` → `TransactionService.fundTransfer()`

1. Look up both from/to bank accounts by account number
2. Validate sufficient balance in the source account
3. Debit source account: subtract amount from `actualBalance` and `availableBalance`
4. Record debit transaction entry
5. Credit destination account: add amount to `actualBalance` and `availableBalance`
6. Record credit transaction entry
7. Return transaction ID

### 4.4 Utility Payment Flow

**Location:** `internet-banking-utility-payment-service` → `UtilityPaymentService.utilPayment()`

1. Create a `UtilityPaymentEntity` from the request with status `PROCESSING`
2. Save to `utility_payment` table
3. Call core banking service via Feign to execute the payment
4. Update entity with transaction ID and status `SUCCESS`
5. Return success response

**Core Banking Execution:** `core-banking-service` → `TransactionService.utilPayment()`

1. Look up source bank account by account number
2. Validate sufficient balance
3. Look up utility account by provider ID
4. Debit source account balance
5. Record transaction entry with type `UTILITY_PAYMENT`
6. Return transaction ID

### 4.5 Balance Validation Rules

**Location:** `core-banking-service` → `TransactionService.validateBalance()`

- Source account `actualBalance` must be ≥ 0
- Source account `actualBalance` must be ≥ transfer/payment amount
- Throws `InsufficientFundsException` with code `BANKING-CORE-SERVICE-1001` on failure

### 4.6 Audit Trail

**Location:** Fund transfer, utility payment, and user services

- All mutable entities extend `AuditAware`, which provides:
  - `createdDate` / `createdBy` — set automatically on insert
  - `modifiedDate` / `modifiedBy` — set automatically on update
  - `version` — `@Version` for optimistic locking
- Auditor identity comes from the `X-Auth-Id` HTTP header (propagated by the API Gateway)

---

## 5. Integration Points

### 5.1 Keycloak (Identity Provider)

| Aspect | Detail |
|--------|--------|
| **Version** | 23.0.7 |
| **URL** | `http://keycloak_web:8080` (Docker) / configurable via `app.config.keycloak.server-url` |
| **Realm** | `javatodev-internet-banking` |
| **Grant Type** | `client_credentials` (admin API) |
| **Client** | `internet-banking-api-client` |
| **Integration Point** | `KeycloakUserService` in user-service (via `keycloak-admin-client:24.0.4`) |
| **Operations** | Create user, update user, search by email, read by authId |
| **JWT Validation** | API Gateway validates JWT tokens via `jwk-set-uri` endpoint |

### 5.2 MySQL

| Aspect | Detail |
|--------|--------|
| **Version** | 8.4.0 |
| **Host** | `mysql_javatodev_app` (Docker) / `172.25.0.9` |
| **Port** | 3306 |
| **Databases** | `banking_core_service`, `banking_core_fund_transfer_service`, `banking_core_user_service`, `banking_core_utility_payment_service` |
| **User** | `javatodev_development` / `oPItyPticIAt` |
| **Schema Migration** | Flyway (core-banking-service only, 3 migration scripts) |
| **Driver** | `com.mysql:mysql-connector-j:8.4.0` |
| **Test DB** | H2 in-memory (all services use H2 for tests) |

### 5.3 Zipkin (Distributed Tracing)

| Aspect | Detail |
|--------|--------|
| **Version** | 3 |
| **URL** | `http://172.25.0.12:9411` |
| **Integration** | `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` in all services |
| **Coverage** | All 4 application services + API gateway + config server |

### 5.4 RabbitMQ (Planned)

| Aspect | Detail |
|--------|--------|
| **Status** | **Not implemented** — mentioned in README for notification service |
| **Planned Use** | Fund transfer and utility payment services to push notification messages |
| **Notification Service** | Listed as "PENDING Development" |

### 5.5 Spring Cloud Config Server

| Aspect | Detail |
|--------|--------|
| **Git URI** | `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` |
| **Branch** | `main` |
| **Search Path** | `configuration` |
| **Port** | 8090 |
| **Consumers** | All application services (via `spring-cloud-starter-config` + `spring-cloud-starter-bootstrap`) |

### 5.6 Netflix Eureka (Service Discovery)

| Aspect | Detail |
|--------|--------|
| **Port** | 8081 |
| **Self-Registration** | Disabled (`register-with-eureka: false`) |
| **Consumers** | All services register as Eureka clients |
| **Usage** | Feign clients resolve service names to instances via Eureka |

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

| Aspect | Detail |
|--------|--------|
| **Build Tool** | Gradle 8.6 (per-service wrapper — no root build file) |
| **Java Version** | 21 (OpenJDK, source compatibility 21) |
| **Spring Boot** | 3.2.4 |
| **Spring Cloud** | 2023.0.0 |
| **Shared Library** | None in current repo (each service is fully self-contained) |
| **Test Framework** | JUnit 5 (via `spring-boot-starter-test`) |

### 6.2 Docker

Each service has its own `Dockerfile`:

- **Base image:** `eclipse-temurin:21.0.2_13-jre-alpine`
- **Pattern:** Copy fat JAR → set `ENTRYPOINT` with `-Dspring.profiles.active=docker`
- **Orchestration script:** `wait-for-it.sh` ensures service registry and config server are available before startup

### 6.3 Docker Compose

Two compose files in `docker-compose/`:

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Full stack: all 7 services + MySQL + Keycloak + PostgreSQL + Zipkin |
| `docker-compose-support-apps.yml` | Infrastructure only: MySQL + Keycloak + PostgreSQL + Zipkin + Config Server + Service Registry |

**Network:** Custom bridge `javatodev_ib_network` (`172.25.0.0/16`) with static IPs per container.

### 6.4 Startup Order

Managed via `wait-for-it.sh` in Docker entrypoints:

1. **MySQL** + **Keycloak DB** (PostgreSQL) — no dependencies
2. **Zipkin** — no dependencies
3. **Config Server** — no dependencies
4. **Service Registry** — no dependencies
5. **API Gateway** — waits for Service Registry + Config Server
6. **Application Services** (user, fund-transfer, utility-payment, core-banking) — wait for Service Registry + Config Server + MySQL

### 6.5 CI/CD

- No CI/CD pipeline configuration detected in the repository
- `.github/FUNDING.yml` exists (sponsorship configuration only)
- No GitHub Actions, Jenkins, or other CI pipeline definitions present

### 6.6 Pre-populated Test Data

The core banking service includes Flyway migration `V1.0.20210427174721__temp_data.sql` with:

- **4 users** (Sam, Guru, Ragu, Randor)
- **14 savings accounts** distributed across users
- **6 utility providers** (Vodafone, Verizon, Singtel, Hutch, Airtel, GIO)

Keycloak realm is pre-imported via volume mount (`docker-compose/keycloak/realm-export.json`).
