# Internet Banking Microservices - Application Knowledge Base

## Table of Contents

- [1. Architecture Overview](#1-architecture-overview)
- [2. Data Model Documentation](#2-data-model-documentation)
- [3. API Surface Map](#3-api-surface-map)
- [4. Key Business Logic Inventory](#4-key-business-logic-inventory)
- [5. Integration Points](#5-integration-points)
- [6. Build and Deployment Pipeline Summary](#6-build-and-deployment-pipeline-summary)

---

## 1. Architecture Overview

### Technology Stack

| Component          | Technology                        |
|--------------------|-----------------------------------|
| Language           | Java 21                           |
| Framework          | Spring Boot 3.2.4                 |
| Cloud Framework    | Spring Cloud 2023.0.0             |
| Service Discovery  | Netflix Eureka                    |
| API Gateway        | Spring Cloud Gateway              |
| Config Management  | Spring Cloud Config Server        |
| IAM                | Keycloak 23.0.7                   |
| Database           | MySQL 8.4 (4 schemas)             |
| DB Migration       | Flyway 10.12.0 (core-banking only)|
| Tracing            | Zipkin 3 + Micrometer Brave       |
| Inter-service Comm | OpenFeign (synchronous REST)      |
| Build Tool         | Gradle (per-service, no root)     |
| Containerization   | Docker (eclipse-temurin:21-jre-alpine) |
| Orchestration      | Docker Compose                    |

### Service Inventory

| Service                                  | Port  | Type            | Description                                         |
|------------------------------------------|-------|-----------------|-----------------------------------------------------|
| `internet-banking-api-gateway`           | 8082  | Infrastructure  | Central entry point; OAuth2/JWT validation via Keycloak; routes to downstream services |
| `internet-banking-service-registry`      | 8081  | Infrastructure  | Netflix Eureka server for service discovery          |
| `internet-banking-config-server`         | 8090  | Infrastructure  | Spring Cloud Config; reads from external GitHub repo |
| `core-banking-service`                   | 8092  | Business        | Core engine: users, bank accounts, utility accounts, transactions |
| `internet-banking-fund-transfer-service` | 8084  | Business        | Orchestrates fund transfers between bank accounts    |
| `internet-banking-utility-payment-service`| 8085 | Business        | Orchestrates utility bill payments                   |
| `internet-banking-user-service`          | 8083  | Business        | User registration, Keycloak integration, approval workflow |

### Communication Patterns

```
                    +-----------+
                    | Keycloak  |
                    | (8080)    |
                    +-----+-----+
                          |
  Client --> [API Gateway (8082)] --JWT validation--> routes to:
                  |         |         |          |
                  v         v         v          v
            user-service  fund-transfer  utility-payment  core-banking
             (8083)        (8084)         (8085)           (8092)
                  |         |              |
                  +----+----+--------------+
                       |
                  OpenFeign (sync REST)
                       |
                       v
                core-banking-service (8092)
```

- **Synchronous REST via OpenFeign**: `fund-transfer-service`, `utility-payment-service`, and `user-service` all call `core-banking-service` through Feign clients, resolved via Eureka service discovery.
- **No async messaging**: RabbitMQ is referenced in documentation but is **not implemented** in the codebase. All communication is synchronous.
- **Gateway auth propagation**: The API Gateway extracts the JWT principal name and forwards it as an `X-Auth-Id` HTTP header to downstream services. Downstream services read this header via a servlet filter (`AppAuthUserFilter`).

### Infrastructure Components

| Component       | Image / Technology        | Static IP    | Purpose                          |
|-----------------|---------------------------|--------------|----------------------------------|
| MySQL           | mysql:8.4.0 (custom)      | 172.25.0.9   | Persistence for all 4 business services |
| Keycloak        | keycloak:23.0.7           | 172.25.0.11  | Identity and access management   |
| PostgreSQL      | postgres:15               | 172.25.0.10  | Keycloak's backing store         |
| Zipkin          | openzipkin/zipkin:3        | 172.25.0.12  | Distributed tracing UI           |

---

## 2. Data Model Documentation

### 2.1 `banking_core_service` Schema (core-banking-service)

Managed by **Flyway** migrations. Contains the system of record for users, accounts, and transactions.

#### `banking_core_user`

| Column                 | Type         | Constraints     | Description                     |
|------------------------|--------------|-----------------|---------------------------------|
| `id`                   | BIGINT       | PK, AUTO_INCREMENT | Surrogate primary key         |
| `first_name`           | VARCHAR(255) |                 | User's first name               |
| `last_name`            | VARCHAR(255) |                 | User's last name                |
| `email`                | VARCHAR(255) |                 | User's email address            |
| `identification_number`| VARCHAR(255) |                 | National ID (NIC) - unique business key |

#### `banking_core_account`

| Column             | Type          | Constraints         | Description                      |
|--------------------|---------------|----------------------|----------------------------------|
| `id`               | BIGINT        | PK, AUTO_INCREMENT   | Surrogate primary key            |
| `number`           | VARCHAR(255)  |                      | Account number (business key)    |
| `type`             | VARCHAR(255)  | ENUM(STRING)         | `SAVINGS_ACCOUNT`                |
| `status`           | VARCHAR(255)  | ENUM(STRING)         | `ACTIVE`                         |
| `available_balance`| DECIMAL(19,2) |                      | Balance available for transactions|
| `actual_balance`   | DECIMAL(19,2) |                      | Ledger balance                   |
| `user_id`          | BIGINT        | FK -> banking_core_user.id | Account owner              |

**Relationships**: Many accounts to one user (`@ManyToOne`).

#### `banking_core_transaction`

| Column            | Type          | Constraints                  | Description                     |
|-------------------|---------------|-------------------------------|---------------------------------|
| `id`              | BIGINT        | PK, AUTO_INCREMENT            | Surrogate primary key           |
| `amount`          | DECIMAL(19,2) |                               | Transaction amount (negative=debit)|
| `transaction_type`| VARCHAR(30)   | NOT NULL, ENUM(STRING)        | `FUND_TRANSFER` or `UTILITY_PAYMENT`|
| `reference_number`| VARCHAR(50)   | NOT NULL                      | Related account or reference    |
| `transaction_id`  | VARCHAR(50)   | NOT NULL                      | UUID linking related entries    |
| `account_id`      | BIGINT        | FK -> banking_core_account.id | Account involved                |

**Relationships**: One-to-one with account (`@OneToOne` with `CascadeType.ALL` -- problematic, see Gap Analysis).

#### `banking_core_utility_account`

| Column         | Type         | Constraints     | Description                    |
|----------------|--------------|-----------------|--------------------------------|
| `id`           | BIGINT       | PK, AUTO_INCREMENT | Surrogate primary key       |
| `number`       | VARCHAR(255) |                 | Provider's account number      |
| `provider_name`| VARCHAR(255) |                 | Utility provider name (e.g., VODAFONE)|

### 2.2 `banking_core_fund_transfer_service` Schema

JPA auto-DDL (no Flyway).

#### `fund_transfer`

| Column                | Type          | Constraints         | Description                       |
|-----------------------|---------------|----------------------|-----------------------------------|
| `id`                  | BIGINT        | PK, AUTO_INCREMENT   | Surrogate primary key             |
| `transaction_reference`| VARCHAR(255) |                      | UUID from core-banking response   |
| `from_account`        | VARCHAR(255)  |                      | Source account number             |
| `to_account`          | VARCHAR(255)  |                      | Destination account number        |
| `amount`              | DECIMAL(19,2) |                      | Transfer amount                   |
| `status`              | VARCHAR(255)  | ENUM(STRING)         | `PENDING` -> `SUCCESS`            |
| `created_at`          | TIMESTAMP     | (AuditAware)         | Record creation time              |
| `updated_at`          | TIMESTAMP     | (AuditAware)         | Last update time                  |

### 2.3 `banking_core_user_service` Schema

JPA auto-DDL (no Flyway).

#### `user`

| Column           | Type         | Constraints         | Description                        |
|------------------|--------------|----------------------|------------------------------------|
| `id`             | BIGINT       | PK, AUTO_INCREMENT   | Surrogate primary key              |
| `auth_id`        | VARCHAR(255) |                      | Keycloak user UUID                 |
| `identification` | VARCHAR(255) |                      | NIC linking to core-banking user   |
| `status`         | VARCHAR(255) | ENUM(STRING)         | `PENDING` -> `APPROVED`            |
| `created_at`     | TIMESTAMP    | (AuditAware)         | Record creation time               |
| `updated_at`     | TIMESTAMP    | (AuditAware)         | Last update time                   |

### 2.4 `banking_core_utility_payment_service` Schema

JPA auto-DDL (no Flyway).

#### `utility_payment`

| Column            | Type          | Constraints         | Description                      |
|-------------------|---------------|----------------------|----------------------------------|
| `id`              | BIGINT        | PK, AUTO_INCREMENT   | Surrogate primary key            |
| `provider_id`     | BIGINT        |                      | Utility provider reference       |
| `amount`          | DECIMAL(19,2) |                      | Payment amount                   |
| `reference_number`| VARCHAR(255)  |                      | Customer's reference number      |
| `account`         | VARCHAR(255)  |                      | Payer's bank account number      |
| `transaction_id`  | VARCHAR(255)  |                      | UUID from core-banking response  |
| `status`          | VARCHAR(255)  | ENUM(STRING)         | `PROCESSING` -> `SUCCESS`        |
| `created_at`      | TIMESTAMP     | (AuditAware)         | Record creation time             |
| `updated_at`      | TIMESTAMP     | (AuditAware)         | Last update time                 |

### Entity Relationship Diagram (Conceptual)

```
banking_core_service:
  banking_core_user 1──* banking_core_account 1──1 banking_core_transaction
  banking_core_utility_account (standalone)

banking_core_fund_transfer_service:
  fund_transfer (references account numbers as strings, no FK)

banking_core_user_service:
  user (references Keycloak authId and core-banking NIC as strings)

banking_core_utility_payment_service:
  utility_payment (references provider and account as strings, no FK)
```

---

## 3. API Surface Map

### 3.1 API Gateway Routes

| Gateway Path Prefix    | Target Service                      |
|------------------------|-------------------------------------|
| `/user/**`             | `internet-banking-user-service`     |
| `/fund-transfer/**`    | `internet-banking-fund-transfer-service` |
| `/payment/**`          | `internet-banking-utility-payment-service` |
| `/core/**`             | `core-banking-service`              |

Public (unauthenticated) endpoint: `POST /user/api/v1/bank-users/register`

### 3.2 Core Banking Service (port 8092)

| Method | Endpoint                                    | Description                  | Request Body                              | Response                                |
|--------|---------------------------------------------|------------------------------|-------------------------------------------|-----------------------------------------|
| GET    | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | -                                         | `BankAccount` (number, type, status, availableBalance, actualBalance) |
| GET    | `/api/v1/account/util-account/{account_name}`   | Get utility account by provider | -                                     | `UtilityAccount` (id, number, providerName) |
| POST   | `/api/v1/transaction/fund-transfer`              | Process fund transfer       | `{ fromAccount, toAccount, amount }`      | `{ message, transactionId }`            |
| POST   | `/api/v1/transaction/util-payment`               | Process utility payment     | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| GET    | `/api/v1/user/{identification}`                  | Get user by NIC             | -                                         | `User` (id, firstName, lastName, email, identificationNumber, accounts[]) |
| GET    | `/api/v1/user`                                   | List users (paginated)      | Query: `page`, `size`, `sort`             | `User[]`                                |

### 3.3 Internet Banking User Service (port 8083)

| Method | Endpoint                             | Description                      | Request Body                                  | Response                              |
|--------|--------------------------------------|----------------------------------|-----------------------------------------------|---------------------------------------|
| POST   | `/api/v1/bank-users/register`        | Register new user (PUBLIC)       | `{ email, identification, password }`         | `User` (id, authId, identification, status) |
| PATCH  | `/api/v1/bank-users/update/{id}`     | Update user status (admin)       | `{ status: "APPROVED" \| "PENDING" }`         | `User`                                |
| GET    | `/api/v1/bank-users`                 | List all users (paginated)       | Query: `page`, `size`, `sort`                 | `User[]`                              |
| GET    | `/api/v1/bank-users/{id}`            | Get user by ID                   | -                                             | `User`                                |

### 3.4 Internet Banking Fund Transfer Service (port 8084)

| Method | Endpoint              | Description                     | Request Body                                         | Response                             |
|--------|-----------------------|---------------------------------|------------------------------------------------------|--------------------------------------|
| POST   | `/api/v1/transfer`    | Initiate fund transfer          | `{ fromAccount, toAccount, amount }`                 | `{ message, transactionId }`         |
| GET    | `/api/v1/transfer`    | List transfers (paginated)      | Query: `page`, `size`, `sort`                        | `FundTransfer[]` (id, transactionReference, fromAccount, toAccount, amount, status, createdAt, updatedAt) |

### 3.5 Internet Banking Utility Payment Service (port 8085)

| Method | Endpoint                  | Description                    | Request Body                                              | Response                             |
|--------|---------------------------|--------------------------------|-----------------------------------------------------------|--------------------------------------|
| POST   | `/api/v1/utility-payment` | Process utility payment        | `{ providerId, amount, referenceNumber, account }`        | `{ message, transactionId }`         |
| GET    | `/api/v1/utility-payment` | List payments (paginated)      | Query: `page`, `size`, `sort`                             | `UtilityPayment[]` (id, providerId, amount, referenceNumber, account, transactionId, status, createdAt, updatedAt) |

### 3.6 Actuator Endpoints (all services)

All services expose Spring Boot Actuator at `/actuator/**`. These are whitelisted in the API Gateway security configuration.

---

## 4. Key Business Logic Inventory

### 4.1 Fund Transfer Flow

```
Client -> Gateway -> fund-transfer-service -> core-banking-service
```

1. **fund-transfer-service** receives `FundTransferRequest` (fromAccount, toAccount, amount)
2. Creates `FundTransferEntity` with status `PENDING` and saves to DB
3. Calls `core-banking-service` via Feign: `POST /api/v1/transaction/fund-transfer`
4. **core-banking-service** `TransactionService.fundTransfer()`:
   - Looks up both bank accounts by number
   - Validates sender has sufficient `actualBalance`
   - Calls `internalFundTransfer()`:
     - Debits sender: `actualBalance -= amount`, `availableBalance = actualBalance - amount`
     - Credits receiver: `actualBalance += amount`, `availableBalance = actualBalance + amount`
     - Creates two `TransactionEntity` records (debit + credit) with shared `transactionId`
   - Returns `transactionId`
5. **fund-transfer-service** updates entity with `transactionReference` and status `SUCCESS`

**Known Bug**: Double-deduction in `availableBalance`. Line 91: `availableBalance` is set to `actualBalance - amount`, but `actualBalance` was already debited, causing double subtraction. Same pattern on the credit side (line 100) causes double addition.

### 4.2 Utility Payment Flow

```
Client -> Gateway -> utility-payment-service -> core-banking-service
```

1. **utility-payment-service** receives `UtilityPaymentRequest` (providerId, amount, referenceNumber, account)
2. Creates `UtilityPaymentEntity` with status `PROCESSING` and saves to DB
3. Calls `core-banking-service` via Feign: `POST /api/v1/transaction/util-payment`
4. **core-banking-service** `TransactionService.utilPayment()`:
   - Looks up bank account and utility account
   - Validates sufficient balance
   - Debits sender account
   - Creates `TransactionEntity` record
   - Returns `transactionId`
5. **utility-payment-service** updates entity with `transactionId` and status `SUCCESS`

**Known Bug**: Same double-deduction bug as fund transfer (line 63-64 in `TransactionService`).

### 4.3 User Registration Flow

```
Client -> Gateway (PUBLIC) -> user-service -> Keycloak + core-banking-service
```

1. **user-service** receives `User` (email, identification, password)
2. Checks Keycloak for existing user with same email (rejects if found)
3. Calls `core-banking-service` via Feign: `GET /api/v1/user/{identification}` to verify NIC exists
4. Validates that the email matches the core-banking record
5. Creates Keycloak user via admin API:
   - Sets email, name, username (= email)
   - Sets temporary password
   - User is created as **disabled** with **unverified email**
6. If Keycloak returns 201, saves `UserEntity` with status `PENDING`
7. Admin later calls `PATCH /api/v1/bank-users/update/{id}` with `{ status: "APPROVED" }`
   - This enables the Keycloak user and marks email as verified

### 4.4 Balance Validation Rules

- Fund transfer: `actualBalance` must be >= 0 AND >= transfer `amount`
- Utility payment: Same validation as fund transfer
- No minimum balance enforcement
- No daily/transaction limits
- No overdraft protection

### 4.5 Transaction Status State Machine

```
Fund Transfer:     PENDING  --> SUCCESS  (no FAILED state)
Utility Payment:   PROCESSING --> SUCCESS (no FAILED state)
User:              PENDING  --> APPROVED  (no REJECTED state)
```

---

## 5. Integration Points

### 5.1 Keycloak (IAM)

| Aspect               | Detail                                                    |
|----------------------|-----------------------------------------------------------|
| Server               | Keycloak 23.0.7 (dev mode)                               |
| Realm                | Configured via `app.config.keycloak.realm` property       |
| Auth flow            | `client_credentials` grant (service account)              |
| Client library       | `keycloak-admin-client:24.0.4`                            |
| Used by              | `internet-banking-user-service` (user CRUD)               |
| Gateway integration  | OAuth2 Resource Server with JWT; JWK Set URI configured   |
| Realm import         | Docker volume mount from `docker-compose/keycloak/`       |

### 5.2 Zipkin (Distributed Tracing)

| Aspect               | Detail                                                    |
|----------------------|-----------------------------------------------------------|
| Server               | Zipkin 3 (port 9411)                                      |
| Client libraries     | `micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`|
| Feign integration    | `feign-micrometer` for propagating trace context           |
| Coverage             | All 6 services include tracing dependencies               |
| Config               | Externalized via Spring Cloud Config Server               |

### 5.3 Database Connections

| Service                        | Schema                                  | Driver          | Migration     |
|--------------------------------|-----------------------------------------|-----------------|---------------|
| core-banking-service           | `banking_core_service`                  | mysql-connector-j:8.4.0 | Flyway 10.12.0 |
| fund-transfer-service          | `banking_core_fund_transfer_service`    | mysql-connector-j:8.4.0 | JPA auto-DDL  |
| user-service                   | `banking_core_user_service`             | mysql-connector-j:8.4.0 | JPA auto-DDL  |
| utility-payment-service        | `banking_core_utility_payment_service`  | mysql-connector-j:8.4.0 | JPA auto-DDL  |

All four schemas reside on the same MySQL 8.4 instance. Connection credentials: `javatodev_development` / `oPItyPticIAt` (hardcoded in `privileges.sql`).

### 5.4 Spring Cloud Config Server

| Aspect              | Detail                                                       |
|---------------------|--------------------------------------------------------------|
| Config repo         | `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` |
| Branch              | `main`                                                       |
| Search path         | `configuration/`                                             |
| Profiles            | `dev` (local), `docker` (containerized)                      |
| Bootstrap mechanism | `bootstrap.yml` + `spring-cloud-starter-bootstrap`           |

### 5.5 RabbitMQ (NOT IMPLEMENTED)

RabbitMQ is mentioned in the project's architecture diagram but is **not present** in any `build.gradle`, configuration file, or source code. All inter-service communication is synchronous via Feign.

---

## 6. Build and Deployment Pipeline Summary

### Build Structure

Each service has an **independent Gradle build** (no multi-module root project). There is no shared library module.

```
ts-java-spring-boot-internet-banking-microservices/
  core-banking-service/              build.gradle, Dockerfile
  internet-banking-api-gateway/      build.gradle, Dockerfile
  internet-banking-config-server/    build.gradle, Dockerfile
  internet-banking-fund-transfer-service/  build.gradle, Dockerfile
  internet-banking-service-registry/ build.gradle, Dockerfile
  internet-banking-user-service/     build.gradle, Dockerfile
  internet-banking-utility-payment-service/ build.gradle, Dockerfile
```

### Build Commands

```bash
# Per-service build
cd <service-directory>
./gradlew clean build

# Docker image build (per-service)
docker build -t javatodev/<service-name> .
```

### Docker Compose

Two compose files in `docker-compose/`:

| File                              | Contents                                          |
|-----------------------------------|---------------------------------------------------|
| `docker-compose-support-apps.yml` | MySQL, Keycloak, PostgreSQL, Zipkin, Config Server, Service Registry |
| `docker-compose.yml`             | All of the above + all 6 application services      |

### Networking

- Custom bridge network: `javatodev_ib_network` (172.25.0.0/16)
- Static IP assignments for all containers
- `wait-for-it.sh` for startup ordering (50s timeout per dependency)
- Service startup order: MySQL -> Config Server -> Service Registry -> Business Services

### Port Mapping

| Service                | Host Port | Container Port |
|------------------------|-----------|----------------|
| API Gateway            | 8082      | 8082           |
| Service Registry       | 8081      | 8081           |
| Config Server          | 8090      | 8090           |
| Core Banking           | 8092      | 8092           |
| User Service           | 8083      | 8083           |
| Fund Transfer          | 8084      | 8084           |
| Utility Payment        | 8085      | 8085           |
| MySQL                  | 3306      | 3306           |
| Keycloak               | 8080      | 8080           |
| Zipkin                 | 9411      | 9411           |

### CI/CD

**No CI/CD pipeline is configured** in the repository. There are no GitHub Actions workflows, Jenkinsfiles, or other pipeline definitions.

### Seed Data

Flyway migration `V1.0.20210427174721__temp_data.sql` inserts:
- 4 test users (Sam, Guru, Ragu, Randor)
- 14 bank accounts (SAVINGS_ACCOUNT, ACTIVE) with balances ranging from 12,000 to 889,000.33
- 6 utility providers (VODAFONE, VERIZON, SINGTEL, HUTCH, AIRTEL, GIO)
