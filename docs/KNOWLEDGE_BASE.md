# Application Knowledge Base

## 1. Architecture Overview

### 1.1 Service Inventory

| # | Service | Port | Role |
|---|---------|------|------|
| 1 | **internet-banking-config-server** | 8090 | Centralized configuration via Spring Cloud Config; backs onto a Git repo (`internet-banking-microservices-configurations`) |
| 2 | **internet-banking-service-registry** | 8081 | Netflix Eureka server for service discovery |
| 3 | **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway; single entry point for all client traffic; enforces OAuth 2.0 / JWT auth via Keycloak |
| 4 | **internet-banking-user-service** | 8083 | User registration, approval, and profile management; integrates with Keycloak Admin API and core-banking-service |
| 5 | **internet-banking-fund-transfer-service** | 8084 | Orchestrates fund transfers between bank accounts; delegates actual balance mutations to core-banking-service |
| 6 | **internet-banking-utility-payment-service** | 8085 | Orchestrates utility bill payments (electricity, telecom, etc.); delegates balance mutations to core-banking-service |
| 7 | **core-banking-service** | 8092 | The "banking core" -- owns accounts, users, transactions, and balance ledger; processes fund transfers and utility payments at the DB level |

> **Note:** A Notification Service is mentioned in the README as "PENDING Development" and is not implemented.

### 1.2 Communication Patterns

```
                         +-----------+
                         | Keycloak  |
                         | (AuthN)   |
                         +-----+-----+
                               |
                       JWT validation
                               |
Client ----HTTPS----> [API Gateway :8082]
                         |        |        |
               +---------+--------+--------+---------+
               |                  |                   |
     [user-service :8083] [fund-transfer :8084] [utility-payment :8085]
               |                  |                   |
               +--------> [core-banking :8092] <------+
                          (OpenFeign / Eureka)
```

| Pattern | Technology | Details |
|---------|-----------|---------|
| **Service discovery** | Netflix Eureka | All services register with Eureka; FeignClients resolve service names at runtime |
| **Synchronous REST** | Spring Cloud OpenFeign | `fund-transfer-service` and `utility-payment-service` call `core-banking-service` via Feign; `user-service` calls `core-banking-service` via Feign |
| **API Gateway routing** | Spring Cloud Gateway | Routes prefixed `/user/**`, `/fund-transfer/**`, `/utility-payment/**`, `/banking-core/**` to respective services |
| **Centralized config** | Spring Cloud Config Server | All services bootstrap from Config Server (Git-backed) |
| **Auth propagation** | Custom `X-Auth-Id` header | Gateway extracts the authenticated principal name and adds it as `X-Auth-Id` header; downstream services read it via `AppAuthUserFilter` |
| **Async messaging** | RabbitMQ (planned) | README references RabbitMQ for notification messages; **not yet implemented** in code |

### 1.3 Infrastructure Components

| Component | Image / Version | Purpose |
|-----------|----------------|---------|
| **MySQL** | Custom build (`docker-compose/mysql`) | Primary datastore for core-banking-service, fund-transfer, utility-payment, and user services |
| **Keycloak** | `quay.io/keycloak/keycloak:23.0.7` | Identity provider; OAuth 2.0 / OpenID Connect; realm and client data imported on startup |
| **PostgreSQL** | `postgres:15` | Backs the Keycloak database |
| **Zipkin** | `openzipkin/zipkin:3` | Distributed tracing collector |
| **Eureka** | Embedded (Spring Cloud Netflix) | Service registry |
| **Spring Cloud Config** | Embedded | Externalized configuration from Git |

---

## 2. Data Model Documentation

### 2.1 core-banking-service

Managed via **Flyway** migrations (`src/main/resources/db/migration/`).

#### `banking_core_user`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `first_name` | VARCHAR(255) | |
| `last_name` | VARCHAR(255) | |
| `email` | VARCHAR(255) | |
| `identification_number` | VARCHAR(255) | National ID / NIC |

#### `banking_core_account`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `number` | VARCHAR(255) | Account number (12-digit string in seed data) |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT`, `CURRENT_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `ACTIVE`, `INACTIVE` |
| `actual_balance` | DECIMAL(19,2) | |
| `available_balance` | DECIMAL(19,2) | |
| `user_id` | BIGINT (FK -> `banking_core_user.id`) | |

#### `banking_core_utility_account`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `number` | VARCHAR(255) | Provider account number |
| `provider_name` | VARCHAR(255) | e.g., VODAFONE, VERIZON, SINGTEL |

#### `banking_core_transaction`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `amount` | DECIMAL(19,2) | Negative for debits, positive for credits |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Destination account number or reference |
| `transaction_id` | VARCHAR(50) | UUID string grouping debit/credit entries |
| `account_id` | BIGINT (FK -> `banking_core_account.id`) | |

#### Entity Relationships

```
banking_core_user  1──*  banking_core_account  1──1  banking_core_transaction
                                                      (via OneToOne, see note below)
banking_core_utility_account  (standalone, no FK)
```

> **Design note:** `TransactionEntity.account` is mapped as `@OneToOne(cascade = CascadeType.ALL)`, which is semantically incorrect -- an account has many transactions. This is a known issue.

### 2.2 internet-banking-fund-transfer-service

Schema managed by **JPA auto-DDL** (no Flyway migrations).

#### `fund_transfer`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `transaction_reference` | VARCHAR | UUID from core-banking-service |
| `status` | VARCHAR | Enum: `PENDING`, `SUCCESS`, `FAILED`, `PROCESSING` |
| `created_by` | VARCHAR | Audit field (from `AuditAware`) |
| `created_at` | TIMESTAMP | Audit field |
| `updated_by` | VARCHAR | Audit field |
| `updated_at` | TIMESTAMP | Audit field |

### 2.3 internet-banking-utility-payment-service

#### `utility_payment`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Customer reference |
| `account` | VARCHAR | Source bank account number |
| `transaction_id` | VARCHAR | UUID from core-banking-service |
| `status` | VARCHAR | Enum: `PROCESSING`, `SUCCESS`, `FAILED` |
| Audit columns | | `created_by`, `created_at`, `updated_by`, `updated_at` |

### 2.4 internet-banking-user-service

#### `user`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `auth_id` | VARCHAR | Keycloak user UUID |
| `identification` | VARCHAR | National ID / NIC (links to core-banking-service user) |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED`, `REJECTED` |
| Audit columns | | `created_by`, `created_at`, `updated_by`, `updated_at` |

---

## 3. API Surface Map

### 3.1 core-banking-service (`:8092`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | -- | `BankAccount` (id, number, type, status, actualBalance, availableBalance) |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | -- | `UtilityAccount` (id, number, providerName) |
| `GET` | `/api/v1/user/{identification}` | Get user by identification number | -- | `User` (id, firstName, lastName, email, identificationNumber, accounts[]) |
| `GET` | `/api/v1/user` | List users (paginated) | -- | `List<User>` |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer | `FundTransferRequest` (fromAccount, toAccount, amount) | `FundTransferResponse` (message, transactionId) |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `UtilityPaymentRequest` (account, providerId, amount, referenceNumber) | `UtilityPaymentResponse` (message, transactionId) |

### 3.2 internet-banking-user-service (`:8083`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| `POST` | `/api/v1/bank-users/register` | Register new user (creates Keycloak user + local record) | `User` (email, password, identification) | `User` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user (approve/reject) | `UserUpdateRequest` (status) | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | -- | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | -- | `User` |

### 3.3 internet-banking-fund-transfer-service (`:8084`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `FundTransferRequest` (fromAccount, toAccount, amount) | `FundTransferResponse` (message, transactionId) |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | -- | `List<FundTransfer>` |

### 3.4 internet-banking-utility-payment-service (`:8085`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `UtilityPaymentRequest` (providerId, amount, referenceNumber, account) | `UtilityPaymentResponse` (message, transactionId) |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | -- | `List<UtilityPayment>` |

### 3.5 API Gateway Route Prefixes

All services are accessed through the gateway at `:8082`. Routes are configured via Spring Cloud Config:

| Gateway Path Prefix | Target Service |
|---------------------|---------------|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |
| `/banking-core/**` | core-banking-service |

### 3.6 Actuator Endpoints

All services expose Spring Boot Actuator at `/actuator/**` (permitted without authentication at the gateway level).

---

## 4. Key Business Logic Inventory

### 4.1 Fund Transfer Flow

1. **Client** sends `POST /fund-transfer/api/v1/transfer` through the API Gateway.
2. **fund-transfer-service** saves a `FundTransferEntity` with status `PENDING`.
3. **fund-transfer-service** calls `core-banking-service` via Feign: `POST /api/v1/transaction/fund-transfer`.
4. **core-banking-service** `TransactionService.fundTransfer()`:
   - Looks up source (`fromAccount`) and destination (`toAccount`) bank accounts.
   - **Validates balance**: if `actualBalance < 0` or `actualBalance < transferAmount`, throws `InsufficientFundsException`.
   - Calls `internalFundTransfer()`:
     - Debits source: `actualBalance -= amount`, `availableBalance = actualBalance - amount`.
     - Credits destination: `actualBalance += amount`, `availableBalance = actualBalance + amount`.
     - Creates two `TransactionEntity` records (debit + credit) with a shared `transactionId` (UUID).
5. **fund-transfer-service** updates its entity with the returned `transactionReference` and sets status to `SUCCESS`.

### 4.2 Utility Payment Flow

1. **Client** sends `POST /utility-payment/api/v1/utility-payment` through the API Gateway.
2. **utility-payment-service** saves a `UtilityPaymentEntity` with status `PROCESSING`.
3. **utility-payment-service** calls `core-banking-service` via Feign: `POST /api/v1/transaction/util-payment`.
4. **core-banking-service** `TransactionService.utilPayment()`:
   - Looks up source bank account and utility account (by provider ID).
   - **Validates balance** (same check as fund transfer).
   - Debits source account.
   - Creates one `TransactionEntity` record with type `UTILITY_PAYMENT`.
   - Returns a `UtilityPaymentResponse` with a UUID `transactionId`.
5. **utility-payment-service** updates its entity with status `SUCCESS` and the `transactionId`.

### 4.3 User Registration Flow

1. **Client** sends `POST /user/api/v1/bank-users/register` (no auth required at gateway).
2. **user-service** `UserService.createUser()`:
   - Checks Keycloak for existing user with the same email; throws `UserAlreadyRegisteredException` if found.
   - Calls `core-banking-service` via Feign (`GET /api/v1/user/{identification}`) to validate the banking identity.
   - Validates the email matches the core banking record; throws `InvalidEmailException` on mismatch.
   - Creates a Keycloak user (disabled, email unverified) with the provided password.
   - Saves a local `UserEntity` with status `PENDING` and the Keycloak `authId`.
3. **Admin approval** via `PATCH /api/v1/bank-users/update/{id}` with status `APPROVED`:
   - Enables the Keycloak user and marks email as verified.
   - Updates local status to `APPROVED`.

### 4.4 Balance Calculation Bug

In `TransactionService.utilPayment()` and `internalFundTransfer()`, the `availableBalance` is computed as `actualBalance - amount` **after** `actualBalance` has already been decremented. This causes a double-deduction of the transfer amount from `availableBalance`. Example: if balance is 1000 and transfer is 100, `actualBalance` becomes 900 but `availableBalance` becomes 800.

---

## 5. Integration Points

### 5.1 Keycloak

- **Version:** 23.0.7
- **Usage:** OAuth 2.0 Resource Server at the API Gateway; Admin Client API in user-service for user CRUD.
- **Configuration:** `app.config.keycloak.server-url`, `realm`, `clientId`, `client-secret` (injected from Spring Cloud Config).
- **Realm data** imported automatically via Docker volume mount (`docker-compose/keycloak/`).
- **Gateway** validates JWT tokens using `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`.

### 5.2 RabbitMQ

- **Status:** Referenced in README / architecture diagram but **not implemented** in any service code.
- **Planned use:** Notification service would consume messages for sending user notifications after fund transfers and utility payments.

### 5.3 Zipkin

- **Version:** Zipkin 3 (via Docker)
- **Port:** 9411
- **Integration:** All services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` dependencies for distributed tracing.

### 5.4 Database Connections

| Service | Database | Connection |
|---------|----------|-----------|
| core-banking-service | MySQL (`banking_core_service`) | Via Spring Cloud Config; Flyway migrations |
| fund-transfer-service | MySQL | Via Spring Cloud Config; JPA auto-DDL |
| utility-payment-service | MySQL | Via Spring Cloud Config; JPA auto-DDL |
| user-service | MySQL | Via Spring Cloud Config; JPA auto-DDL |
| Keycloak | PostgreSQL (`keycloak`) | Dedicated Postgres container |

### 5.5 OpenFeign Service-to-Service Calls

| Caller | Target | Feign Client | Endpoints Called |
|--------|--------|-------------|-----------------|
| fund-transfer-service | core-banking-service | `BankingCoreFeignClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/fund-transfer` |
| utility-payment-service | core-banking-service | `BankingCoreRestClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/util-payment` |
| user-service | core-banking-service | `BankingCoreRestClient` | `GET /api/v1/user/{identification}` |

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build tool:** Gradle (per-service `build.gradle`, no root multi-project build)
- **Java version:** 21 (Temurin)
- **Spring Boot version:** 3.2.4
- **Spring Cloud version:** 2023.0.0
- **Plugins:** `spring-boot`, `spring-dependency-management`, `gradle-git-properties`

### 6.2 Docker

Each service has its own `Dockerfile`:
- Base image: `eclipse-temurin:21.0.2_13-jre-alpine`
- Copies the built JAR and a `wait-for-it.sh` script for startup ordering.
- Entrypoint activates the `docker` Spring profile.

### 6.3 Docker Compose

Located in `docker-compose/docker-compose.yml`. Defines:
- **Infrastructure:** MySQL, Keycloak + PostgreSQL, Zipkin
- **Application services:** All 6 microservices with fixed IP addresses on a custom bridge network (`172.25.0.0/16`).
- **Startup ordering:** `wait-for-it.sh` scripts ensure services wait for Config Server, Service Registry, and MySQL before starting.
- A secondary `docker-compose-support-apps.yml` starts only infrastructure components (useful for local development).

### 6.4 Configuration Management

- Externalized to a separate Git repository: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- Spring Cloud Config Server serves configurations; services bootstrap from it.
- Three profiles: default (local), `dev`, `docker`.

### 6.5 CI/CD

- No CI/CD pipeline (GitHub Actions, Jenkins, etc.) is present in the repository.
- GitHub Actions workflows were explicitly removed per the latest commit.
