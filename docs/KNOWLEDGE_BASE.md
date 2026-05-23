# Application Knowledge Base

## Table of Contents

- [1. Architecture Overview](#1-architecture-overview)
- [2. Data Model Documentation](#2-data-model-documentation)
- [3. API Surface Map](#3-api-surface-map)
- [4. Key Business Logic Inventory](#4-key-business-logic-inventory)
- [5. Integration Points](#5-integration-points)
- [6. Build and Deployment Pipeline](#6-build-and-deployment-pipeline)

---

## 1. Architecture Overview

### 1.1 System Summary

This is a **Java 21 / Spring Boot 3.2.4** internet banking application built on a microservices architecture using **Spring Cloud 2023.0.0**. The system models a banking platform supporting user registration, fund transfers, and utility payments.

### 1.2 Services

| Service | Port | Description |
|---------|------|-------------|
| **internet-banking-service-registry** | 8081 | Netflix Eureka server for service discovery |
| **internet-banking-config-server** | 8090 | Spring Cloud Config server backed by a Git repository |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway — single entry point, OAuth2/JWT enforcement |
| **internet-banking-user-service** | 8083 | User registration, approval workflow, Keycloak integration |
| **internet-banking-fund-transfer-service** | 8084 | Fund transfer orchestration between bank accounts |
| **internet-banking-utility-payment-service** | 8085 | Utility bill payment orchestration |
| **core-banking-service** | 8092 | Core banking engine — accounts, users, transactions, balances |

> **Note:** The README mentions a Notification service (RabbitMQ consumer) but it has **not been implemented** yet.

### 1.3 Communication Patterns

```
┌─────────────┐        ┌──────────────────┐       ┌──────────────────────────────┐
│   Client     │──JWT──▶│  API Gateway     │──────▶│  User / FundTransfer /       │
│  (Browser /  │        │  (8082)          │       │  UtilityPayment services     │
│   Postman)   │        │  OAuth2 Resource │       │  (8083, 8084, 8085)          │
└─────────────┘        │  Server          │       └────────────┬─────────────────┘
                        └──────────────────┘                    │ OpenFeign (sync HTTP)
                                                                ▼
                                                    ┌──────────────────────┐
                                                    │ core-banking-service │
                                                    │ (8092)               │
                                                    └──────────────────────┘
```

- **Synchronous REST via OpenFeign**: The three application services (`user-service`, `fund-transfer-service`, `utility-payment-service`) call `core-banking-service` using Spring Cloud OpenFeign clients resolved through Eureka.
- **Service Discovery**: All services register with the Eureka service registry and use logical service names for inter-service calls.
- **Centralized Configuration**: Each service fetches its configuration from the Config Server at startup via `bootstrap.yml` (pointing to `http://localhost:8090` or `http://internet-banking-config-server:8090` in Docker).
- **API Gateway Routing**: The gateway routes requests by path prefix to downstream services and injects an `X-Auth-Id` header containing the authenticated principal's name.
- **Auth User Propagation**: A `GlobalFilter` in the gateway extracts the JWT principal and passes it as `X-Auth-Id`. Downstream services read this header via `AppAuthUserFilter` → `ApiRequestContextHolder` for auditing.

### 1.4 Infrastructure Components

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Service Registry | Netflix Eureka | Service discovery |
| Config Server | Spring Cloud Config (Git-backed) | Externalized configuration |
| API Gateway | Spring Cloud Gateway | Request routing, JWT validation |
| Identity Provider | Keycloak 23.0.7 | OAuth2 / OpenID Connect, user management |
| Database | MySQL 8.4.0 | Persistent storage (4 schemas) |
| Distributed Tracing | Zipkin 3 + Micrometer Brave | Request tracing across services |
| Database Migrations | Flyway 10.12.0 | Schema versioning (core-banking-service only) |

---

## 2. Data Model Documentation

### 2.1 core-banking-service (MySQL: `banking_core_service`)

#### `banking_core_user`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Unique user identifier |
| `first_name` | VARCHAR(255) | User's first name |
| `last_name` | VARCHAR(255) | User's last name |
| `email` | VARCHAR(255) | User's email address |
| `identification_number` | VARCHAR(255) | National ID / identification number |

#### `banking_core_account`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Unique account identifier |
| `number` | VARCHAR(255) | Account number |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `available_balance` | DECIMAL(19,2) | Available balance |
| `actual_balance` | DECIMAL(19,2) | Actual balance |
| `user_id` | BIGINT (FK → `banking_core_user.id`) | Account owner |

#### `banking_core_transaction`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Unique transaction identifier |
| `amount` | DECIMAL(19,2) | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Reference (target account number or utility ref) |
| `transaction_id` | VARCHAR(50) | UUID-based transaction identifier |
| `account_id` | BIGINT (FK → `banking_core_account.id`) | Associated account |

#### `banking_core_utility_account`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Unique utility account identifier |
| `number` | VARCHAR(255) | Utility provider account number |
| `provider_name` | VARCHAR(255) | Provider name (e.g., VODAFONE, VERIZON) |

#### Entity Relationships

```
banking_core_user (1) ──── (*) banking_core_account
banking_core_account (1) ──── (1) banking_core_transaction
```

### 2.2 internet-banking-user-service (MySQL: `banking_core_user_service`)

#### `user` (JPA-managed, Hibernate auto-DDL)

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Unique user identifier |
| `auth_id` | VARCHAR | Keycloak user ID |
| `identification` | VARCHAR | NIC / identification number (links to core banking user) |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | TIMESTAMP | Audit: creation timestamp |
| `created_by` | VARCHAR | Audit: created by user |
| `modified_date` | TIMESTAMP | Audit: last modification timestamp |
| `modified_by` | VARCHAR | Audit: modified by user |
| `version` | BIGINT | Optimistic locking version |

### 2.3 internet-banking-fund-transfer-service (MySQL: `banking_core_fund_transfer_service`)

#### `fund_transfer` (JPA-managed, Hibernate auto-DDL)

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Unique transfer identifier |
| `transaction_reference` | VARCHAR | Core banking transaction ID |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `status` | VARCHAR | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` | TIMESTAMP | Audit: creation timestamp |
| `created_by` | VARCHAR | Audit: created by user |
| `modified_date` | TIMESTAMP | Audit: last modification timestamp |
| `modified_by` | VARCHAR | Audit: modified by user |
| `version` | BIGINT | Optimistic locking version |

### 2.4 internet-banking-utility-payment-service (MySQL: `banking_core_utility_payment_service`)

#### `utility_payment` (JPA-managed, Hibernate auto-DDL)

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Unique payment identifier |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Provider reference number |
| `account` | VARCHAR | Source bank account number |
| `transaction_id` | VARCHAR | Core banking transaction ID |
| `status` | VARCHAR | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` | TIMESTAMP | Audit: creation timestamp |
| `created_by` | VARCHAR | Audit: created by user |
| `modified_date` | TIMESTAMP | Audit: last modification timestamp |
| `modified_by` | VARCHAR | Audit: modified by user |
| `version` | BIGINT | Optimistic locking version |

---

## 3. API Surface Map

### 3.1 API Gateway Routes (port 8082)

All requests go through the gateway. The gateway strips the path prefix and forwards to downstream services. Authentication is enforced via OAuth2/JWT except for explicitly permitted paths.

**Public (unauthenticated) endpoints:**
- `POST /user/api/v1/bank-users/register`
- `GET /actuator/**` (all actuator paths)

### 3.2 core-banking-service (port 8092)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | — | `BankAccount { id, number, type, status, availableBalance, actualBalance, user }` |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | — | `UtilityAccount { id, number, providerName }` |
| `GET` | `/api/v1/user/{identification}` | Get user by identification number | — | `User { id, firstName, lastName, email, identificationNumber, bankAccounts[] }` |
| `GET` | `/api/v1/user` | Get paginated list of users | Query: `page`, `size`, `sort` | `List<User>` |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer in core | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment in core | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |

### 3.3 internet-banking-user-service (port 8083)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| `POST` | `/api/v1/bank-users/register` | Register a new internet banking user | `{ email, identification, password }` | `User { id, email, identification, authId, status }` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user (e.g., approve) | `{ status }` | `User { id, email, identification, authId, status }` |
| `GET` | `/api/v1/bank-users` | Get paginated list of users | Query: `page`, `size`, `sort` | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | — | `User { id, email, identification, authId, status }` |

### 3.4 internet-banking-fund-transfer-service (port 8084)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `{ fromAccount, toAccount, amount, authID }` | `{ message, transactionId }` |
| `GET` | `/api/v1/transfer` | Get paginated list of transfers | Query: `page`, `size`, `sort` | `List<FundTransfer>` |

### 3.5 internet-banking-utility-payment-service (port 8085)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/utility-payment` | Get paginated list of payments | Query: `page`, `size`, `sort` | `List<UtilityPayment>` |

### 3.6 Actuator Endpoints (all services)

All services expose Spring Boot Actuator endpoints at `/actuator/**` (health, info, etc.), allowed through the gateway without authentication.

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

1. Client sends `POST /user/api/v1/bank-users/register` with `{ email, identification, password }`.
2. **User Service** checks if email already exists in Keycloak — if so, throws `UserAlreadyRegisteredException`.
3. **User Service** calls **Core Banking Service** via Feign (`GET /api/v1/user/{identification}`) to validate the user exists in the banking core.
4. If the core user's email doesn't match the request email, throws `InvalidEmailException`.
5. Creates a Keycloak user representation (disabled, email unverified) with the provided password.
6. Calls Keycloak Admin API to create the user; if HTTP 201, saves the user in the local database with status `PENDING`.
7. If the user doesn't exist in the banking core, throws `InvalidBankingUserException`.

### 4.2 User Approval Flow

1. Admin sends `PATCH /api/v1/bank-users/update/{id}` with `{ status: "APPROVED" }`.
2. **User Service** reads the user from the local DB.
3. If the new status is `APPROVED`, it updates the Keycloak user: sets `enabled=true` and `emailVerified=true`.
4. Updates the local DB user status.

### 4.3 Fund Transfer Flow

1. Client sends `POST /api/v1/transfer` with `{ fromAccount, toAccount, amount }`.
2. **Fund Transfer Service** saves a `FundTransferEntity` with status `PENDING`.
3. Calls **Core Banking Service** via Feign (`POST /api/v1/transaction/fund-transfer`).
4. **Core Banking Service** (within a `@Transactional` boundary):
   - Reads both bank accounts.
   - Validates the source account has sufficient balance (actual balance ≥ transfer amount and ≥ 0).
   - Generates a UUID transaction ID.
   - Debits the source account (subtracts from both `actualBalance` and `availableBalance`).
   - Records a debit `TransactionEntity` (amount stored as negative) against the source account.
   - Credits the destination account (adds to both `actualBalance` and `availableBalance`).
   - Records a credit `TransactionEntity` (amount stored as positive) against the destination account.
5. **Fund Transfer Service** updates the entity status to `SUCCESS` with the transaction reference.

### 4.4 Utility Payment Flow

1. Client sends `POST /api/v1/utility-payment` with `{ providerId, amount, referenceNumber, account }`.
2. **Utility Payment Service** saves a `UtilityPaymentEntity` with status `PROCESSING`.
3. Calls **Core Banking Service** via Feign (`POST /api/v1/transaction/util-payment`).
4. **Core Banking Service** (within a `@Transactional` boundary):
   - Reads the source bank account.
   - Validates sufficient balance.
   - Reads the utility account by provider ID.
   - Debits the source account.
   - Records a `TransactionEntity` of type `UTILITY_PAYMENT`.
5. **Utility Payment Service** updates the entity status to `SUCCESS` with the transaction ID.

### 4.5 Balance Validation Rules

- The source account's `actualBalance` must be ≥ 0.
- The source account's `actualBalance` must be ≥ the requested transfer/payment `amount`.
- Violation throws `InsufficientFundsException` with code `BANKING-CORE-SERVICE-1001`.

### 4.6 Audit Trail

The `fund-transfer-service`, `user-service`, and `utility-payment-service` use a shared `AuditAware` base class (`@MappedSuperclass` with `@EntityListeners(AuditingEntityListener.class)`) to automatically capture:
- `createdDate` / `createdBy`
- `modifiedDate` / `modifiedBy`
- `version` (optimistic locking via `@Version`)

The `createdBy`/`modifiedBy` values are populated by `AuditorAwareConfig`, which reads the authenticated user ID from `ApiRequestContextHolder` (set by the `AppAuthUserFilter` from the `X-Auth-Id` gateway header).

---

## 5. Integration Points

### 5.1 Keycloak (Identity Provider)

- **Version**: 23.0.7
- **Connection**: User Service → Keycloak Admin Client SDK (`keycloak-admin-client:24.0.4`)
- **Configuration**: Server URL, realm, client ID, and client secret via Spring Cloud Config properties (`app.config.keycloak.*`)
- **Grant Type**: `client_credentials`
- **Usage**:
  - Create user on registration
  - Search user by email (duplicate check)
  - Read user by auth ID
  - Update user (enable on approval)
- **Realm Import**: Docker Compose mounts `docker-compose/keycloak/realm-export.json` for automatic realm provisioning.

### 5.2 RabbitMQ (Message Broker)

- **Status**: Referenced in architecture docs and README but **not implemented** in the current codebase.
- **Intended Use**: The fund-transfer and utility-payment services were designed to push notification messages to a RabbitMQ queue for a Notification service to consume.

### 5.3 Zipkin (Distributed Tracing)

- **Version**: Zipkin 3
- **Port**: 9411
- **Integration**: All services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` dependencies.
- **Configuration**: Tracing configuration is managed via Spring Cloud Config. Traces are sent to the Zipkin collector endpoint.

### 5.4 Database Connections

| Service | Database Schema | Connection Method |
|---------|----------------|-------------------|
| core-banking-service | `banking_core_service` | MySQL 8.4, Flyway migrations |
| internet-banking-user-service | `banking_core_user_service` | MySQL 8.4, Hibernate auto-DDL |
| internet-banking-fund-transfer-service | `banking_core_fund_transfer_service` | MySQL 8.4, Hibernate auto-DDL |
| internet-banking-utility-payment-service | `banking_core_utility_payment_service` | MySQL 8.4, Hibernate auto-DDL |

- **Test Database**: All services use H2 in-memory for testing.
- **MySQL User**: `javatodev_development` / `oPItyPticIAt` (created in `docker-compose/mysql/privileges.sql`)
- **Connection details**: Managed via Spring Cloud Config (externalized from the repository).

### 5.5 Spring Cloud Config (Git-backed)

- **Config Repository**: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch**: `main`
- **Search Path**: `configuration/`
- **How it works**: Each service has a `bootstrap.yml` pointing to the Config Server at `http://localhost:8090` (dev) or `http://internet-banking-config-server:8090` (Docker). The Config Server resolves `{application-name}.yml` from the Git repo.

### 5.6 Eureka Service Registry

- **Port**: 8081
- **Usage**: All application services register as Eureka clients. OpenFeign clients use logical service names (e.g., `core-banking-service`) resolved via Eureka.

---

## 6. Build and Deployment Pipeline

### 6.1 Build System

- **Build Tool**: Gradle 8.6 (per-service Gradle Wrapper — no root build file)
- **Shared Library**: None (no `banking-common` module in the original repo; the blueprint references one that was added later)
- **Java Version**: 21 (source compatibility set in each `build.gradle`)
- **Spring Boot**: 3.2.4 (via Spring Boot Gradle plugin)
- **Spring Cloud**: 2023.0.0 (managed via BOM)

### 6.2 Build Commands

```bash
# Build a single service
cd <service-directory>
./gradlew build

# Build skipping tests
./gradlew build -x test

# Run tests
./gradlew test
```

### 6.3 Docker

Each service has a `Dockerfile` based on `eclipse-temurin:21.0.2_13-jre-alpine`:
1. Copies the built JAR (`build/libs/<service>-0.0.1-SNAPSHOT.jar`) as `app.jar`.
2. Includes `wait-for-it.sh` for startup ordering.
3. Activates the `docker` Spring profile (`-Dspring.profiles.active=docker`).

### 6.4 Docker Compose

Two Compose files in `docker-compose/`:

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Full stack: MySQL, Keycloak, Zipkin, Config Server, Service Registry, Gateway + all 4 app services |
| `docker-compose-support-apps.yml` | Infrastructure only: MySQL, Keycloak, Zipkin, Config Server, Service Registry |

**Custom Docker Network**: `javatodev_ib_network` (bridge, subnet `172.25.0.0/16`) with static IP assignments per container.

**Startup Ordering**: Application service containers use `wait-for-it.sh` to wait for the Service Registry (8081), Config Server (8090), and MySQL (3306) before starting.

### 6.5 Database Initialization

- **MySQL**: Custom Dockerfile in `docker-compose/mysql/` creates user `javatodev_development` and four databases.
- **Flyway**: `core-banking-service` uses Flyway for schema migrations (3 migration scripts: base tables, seed data, transaction table).
- **Other services**: Rely on Hibernate auto-DDL (likely `update` mode via externalized config).

### 6.6 Keycloak Provisioning

- Keycloak runs in `start-dev` mode with `--import-realm`.
- `docker-compose/keycloak/realm-export.json` is mounted as an import volume, providing pre-configured realm, client, and user data.
- Admin credentials: `admin` / `password`.
