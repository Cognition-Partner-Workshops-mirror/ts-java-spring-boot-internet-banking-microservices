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

### 1.1 High-Level Architecture

The system follows a **microservices architecture** built with **Spring Boot 3.2.4** and **Spring Cloud 2023.0.0**, running on **Java 21**. All inter-service communication flows through a centralized API Gateway, and services discover each other via a Netflix Eureka Service Registry.

```
                          +-----------------------+
                          |   Keycloak (AuthN)    |
                          |    (Port 8080)        |
                          +----------+------------+
                                     |
                                     | JWT validation
                                     v
+----------+    +--------------------+--------------------+    +-------------------+
|  Client  |--->|   API Gateway (Spring Cloud Gateway)    |--->| Service Registry  |
|          |    |          (Port 8082)                     |    | (Eureka - 8081)   |
+----------+    +-----+--------+--------+--------+-------+    +-------------------+
                      |        |        |        |                      ^
                      v        v        v        v                      |
               +------+--+ +--+-----+ ++------+ +--------+             |
               |  User   | |  Fund  | |Utility| |  Core  |  (all register)
               | Service | |Transfer| |Payment| |Banking |-------------+
               | (8083)  | |Service | |Service| |Service |
               +---------+ |(8084)  | |(8085) | |(8092)  |
                            +--------+ +-------+ +--------+
                                |         |          ^
                     OpenFeign  |         |          | OpenFeign
                                +---------+----------+
                                          |
                                    +-----+------+
                                    |   MySQL    |
                                    | (Port 3306)|
                                    +------------+
```

### 1.2 Services

| Service | Port | Description |
|---------|------|-------------|
| **internet-banking-config-server** | 8090 | Centralized configuration server. Serves configurations from a remote Git repository to all services. |
| **internet-banking-service-registry** | 8081 | Netflix Eureka service registry. All microservices register here for service discovery. |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway. Single entry point for all client requests. Handles routing, JWT-based authentication via Keycloak, and security filtering. |
| **internet-banking-user-service** | 8083 | Manages user registration, retrieval, and status updates. Integrates with Keycloak for identity management and with core-banking-service for user verification. |
| **internet-banking-fund-transfer-service** | 8084 | Handles fund transfer requests between bank accounts. Delegates actual balance operations to core-banking-service via OpenFeign. |
| **internet-banking-utility-payment-service** | 8085 | Processes utility bill payments. Delegates balance deduction and transaction recording to core-banking-service via OpenFeign. |
| **core-banking-service** | 8092 | Acts as the banking core system. Manages bank accounts, users, utility accounts, and processes the actual financial transactions (balance updates, transaction records). |

### 1.3 Communication Patterns

| Pattern | Technology | Usage |
|---------|-----------|-------|
| **Synchronous REST (inter-service)** | Spring Cloud OpenFeign | Fund Transfer Service -> Core Banking Service; Utility Payment Service -> Core Banking Service; User Service -> Core Banking Service |
| **Synchronous REST (client-facing)** | Spring Cloud Gateway | All client requests route through the API Gateway to downstream services |
| **Service Discovery** | Netflix Eureka | All services register with and discover peers through the Eureka service registry |
| **Centralized Configuration** | Spring Cloud Config | All services pull configuration from the config server backed by a Git repository |
| **Distributed Tracing** | Zipkin + Micrometer Brave | Trace propagation across all services via `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` |
| **Asynchronous Messaging** | RabbitMQ (planned) | Designed for notification delivery from Fund Transfer and Utility Payment services (not yet implemented) |

### 1.4 Infrastructure Components

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Database** | MySQL 8 | Primary data store for all services (4 separate databases) |
| **Identity Provider** | Keycloak 23.0.7 | Authentication and authorization; manages users, realms, and OAuth2/OIDC flows |
| **Keycloak Database** | PostgreSQL 15 | Backing store for Keycloak |
| **Distributed Tracing** | Zipkin 3 | Collects and visualizes distributed traces |
| **Message Broker** | RabbitMQ | Planned for async notifications (pending development) |
| **Containerization** | Docker / Docker Compose | All services and infrastructure run as Docker containers on a custom bridge network (`172.25.0.0/16`) |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service Entities

The core banking service owns the central banking data model, stored in the `banking_core_service` MySQL database.

#### 2.1.1 `banking_core_user`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGINT(20)` | PK, AUTO_INCREMENT | Unique user identifier |
| `first_name` | `VARCHAR(255)` | Nullable | User's first name |
| `last_name` | `VARCHAR(255)` | Nullable | User's last name |
| `email` | `VARCHAR(255)` | Nullable | User's email address |
| `identification_number` | `VARCHAR(255)` | Nullable | National identification number (e.g., NIC) |

**JPA Entity:** `com.javatodev.finance.model.entity.UserEntity`
**Relationships:** One-to-Many with `banking_core_account` (a user has many bank accounts)

#### 2.1.2 `banking_core_account`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGINT(20)` | PK, AUTO_INCREMENT | Unique account identifier |
| `number` | `VARCHAR(255)` | Nullable | Account number (e.g., `100015003000`) |
| `type` | `VARCHAR(255)` | Nullable | Account type enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | `VARCHAR(255)` | Nullable | Account status enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `actual_balance` | `DECIMAL(19,2)` | Nullable | Actual balance in the account |
| `available_balance` | `DECIMAL(19,2)` | Nullable | Available balance for transactions |
| `user_id` | `BIGINT(20)` | FK -> `banking_core_user(id)` | Owner of the account |

**JPA Entity:** `com.javatodev.finance.model.entity.BankAccountEntity`
**Relationships:** Many-to-One with `banking_core_user`

#### 2.1.3 `banking_core_transaction`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGINT(20)` | PK, AUTO_INCREMENT | Unique transaction identifier |
| `amount` | `DECIMAL(19,2)` | Nullable | Transaction amount (negative for debits, positive for credits) |
| `transaction_type` | `VARCHAR(30)` | NOT NULL | Type enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | `VARCHAR(50)` | NOT NULL | Reference (target account number for transfers, provider ref for payments) |
| `transaction_id` | `VARCHAR(50)` | NOT NULL | UUID identifying the transaction |
| `account_id` | `BIGINT(20)` | FK -> `banking_core_account(id)` | Associated bank account |

**JPA Entity:** `com.javatodev.finance.model.entity.TransactionEntity`
**Relationships:** One-to-One with `banking_core_account`

#### 2.1.4 `banking_core_utility_account`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGINT(20)` | PK, AUTO_INCREMENT | Unique utility account identifier |
| `number` | `VARCHAR(255)` | Nullable | Utility account number |
| `provider_name` | `VARCHAR(255)` | Nullable | Utility provider name (e.g., `VODAFONE`, `VERIZON`, `AIRTEL`) |

**JPA Entity:** `com.javatodev.finance.model.entity.UtilityAccountEntity`

### 2.2 Internet Banking User Service Entities

Stored in the `banking_core_user_service` MySQL database.

#### 2.2.1 `user`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGINT` | PK, AUTO_INCREMENT | Unique user identifier |
| `auth_id` | `VARCHAR` | - | Keycloak user ID (links to identity provider) |
| `identification` | `VARCHAR` | - | National identification number |
| `status` | `VARCHAR` | - | User status enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | `INSTANT` | Auto-populated | Record creation timestamp |
| `created_by` | `VARCHAR` | Auto-populated | Creator |
| `modified_date` | `INSTANT` | Auto-populated | Last modification timestamp |
| `modified_by` | `VARCHAR` | Auto-populated | Last modifier |
| `version` | `BIGINT` | Optimistic locking | Row version for concurrency control |

**JPA Entity:** `com.javatodev.finance.model.entity.UserEntity` (extends `AuditAware`)

### 2.3 Fund Transfer Service Entities

Stored in the `banking_core_fund_transfer_service` MySQL database.

#### 2.3.1 `fund_transfer`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGINT` | PK, AUTO_INCREMENT | Unique transfer identifier |
| `from_account` | `VARCHAR` | - | Source account number |
| `to_account` | `VARCHAR` | - | Destination account number |
| `amount` | `DECIMAL` | - | Transfer amount |
| `transaction_reference` | `VARCHAR` | - | Transaction ID returned by core banking service |
| `status` | `VARCHAR` | - | Transaction status enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` | `INSTANT` | Auto-populated | Record creation timestamp |
| `modified_date` | `INSTANT` | Auto-populated | Last modification timestamp |
| `version` | `BIGINT` | Optimistic locking | Row version for concurrency control |

**JPA Entity:** `com.javatodev.finance.model.entity.FundTransferEntity` (extends `AuditAware`)

### 2.4 Utility Payment Service Entities

Stored in the `banking_core_utility_payment_service` MySQL database.

#### 2.4.1 `utility_payment`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGINT` | PK, AUTO_INCREMENT | Unique payment identifier |
| `provider_id` | `BIGINT` | - | Utility provider ID (references `banking_core_utility_account.id`) |
| `amount` | `DECIMAL` | - | Payment amount |
| `reference_number` | `VARCHAR` | - | Customer's reference number for the utility bill |
| `account` | `VARCHAR` | - | Bank account number used for payment |
| `transaction_id` | `VARCHAR` | - | Transaction ID returned by core banking service |
| `status` | `VARCHAR` | - | Transaction status enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` | `INSTANT` | Auto-populated | Record creation timestamp |
| `modified_date` | `INSTANT` | Auto-populated | Last modification timestamp |
| `version` | `BIGINT` | Optimistic locking | Row version for concurrency control |

**JPA Entity:** `com.javatodev.finance.model.entity.UtilityPaymentEntity` (extends `AuditAware`)

### 2.5 Entity Relationship Diagram

```
+---------------------------+       +----------------------------+
| banking_core_user         |       | banking_core_utility_acct  |
|---------------------------|       |----------------------------|
| id (PK)                   |       | id (PK)                    |
| first_name                |       | number                     |
| last_name                 |       | provider_name              |
| email                     |       +----------------------------+
| identification_number     |
+------------+--------------+
             |1
             |
             |*
+------------+--------------+       +----------------------------+
| banking_core_account      |       | banking_core_transaction   |
|---------------------------|       |----------------------------|
| id (PK)                   |1-----1| id (PK)                   |
| number                    |       | amount                     |
| type                      |       | transaction_type           |
| status                    |       | reference_number           |
| actual_balance            |       | transaction_id             |
| available_balance         |       | account_id (FK)            |
| user_id (FK)              |       +----------------------------+
+---------------------------+

+-----------------------+           +----------------------------+
| user (user-service)   |           | fund_transfer              |
|-----------------------|           |----------------------------|
| id (PK)               |           | id (PK)                    |
| auth_id (Keycloak ID) |           | from_account               |
| identification         |           | to_account                 |
| status                 |           | amount                     |
| created_date           |           | transaction_reference      |
| version                |           | status                     |
+-----------------------+           | created_date, version      |
                                    +----------------------------+

                                    +----------------------------+
                                    | utility_payment            |
                                    |----------------------------|
                                    | id (PK)                    |
                                    | provider_id                |
                                    | amount                     |
                                    | reference_number           |
                                    | account                    |
                                    | transaction_id             |
                                    | status                     |
                                    | created_date, version      |
                                    +----------------------------+
```

---

## 3. API Surface Map

All client-facing requests go through the **API Gateway** (port `8082`), which routes to downstream services based on path prefixes. The gateway adds path prefixes during routing (e.g., `/user/**` routes to the user service).

### 3.1 Core Banking Service (Port 8092)

Base path: `/api/v1`

#### Account Controller

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Retrieve bank account details by account number | Path: `account_number` (String) | `BankAccount { id, number, type, status, availableBalance, actualBalance, user }` |
| `GET` | `/api/v1/account/util-account/{account_name}` | Retrieve utility account by provider name | Path: `account_name` (String) | `UtilityAccount { id, number, providerName }` |

#### User Controller

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| `GET` | `/api/v1/user/{identification}` | Retrieve user by identification number | Path: `identification` (String) | `User { id, firstName, lastName, email, identificationNumber, bankAccounts[] }` |
| `GET` | `/api/v1/user` | Retrieve paginated list of users | Query: `page`, `size`, `sort` (Pageable) | `List<User>` |

#### Transaction Controller

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| `POST` | `/api/v1/transaction/fund-transfer` | Process a fund transfer at the core level | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `POST` | `/api/v1/transaction/util-payment` | Process a utility payment at the core level | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |

### 3.2 Internet Banking User Service (Port 8083)

Base path: `/api/v1/bank-users`

| Method | Endpoint | Description | Request Body / Params | Response |
|--------|----------|-------------|----------------------|----------|
| `POST` | `/api/v1/bank-users/register` | Register a new user (public, no auth required) | `{ email, identification, password }` | `User { id, email, identification, authId, status }` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user status (e.g., approve registration) | Path: `id` (Long); Body: `{ status }` (enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST`) | `User { id, email, identification, authId, status }` |
| `GET` | `/api/v1/bank-users` | Retrieve paginated list of registered users | Query: `page`, `size`, `sort` (Pageable) | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Retrieve user by ID | Path: `id` (Long) | `User { id, email, identification, authId, status }` |

### 3.3 Internet Banking Fund Transfer Service (Port 8084)

Base path: `/api/v1/transfer`

| Method | Endpoint | Description | Request Body / Params | Response |
|--------|----------|-------------|----------------------|----------|
| `POST` | `/api/v1/transfer` | Initiate a fund transfer between accounts | `{ fromAccount, toAccount, amount, authID }` | `{ message, transactionId }` |
| `GET` | `/api/v1/transfer` | Retrieve paginated list of fund transfers | Query: `page`, `size`, `sort` (Pageable) | `List<FundTransfer { id, transactionReference, fromAccount, toAccount, amount, status }>` |

### 3.4 Internet Banking Utility Payment Service (Port 8085)

Base path: `/api/v1/utility-payment`

| Method | Endpoint | Description | Request Body / Params | Response |
|--------|----------|-------------|----------------------|----------|
| `POST` | `/api/v1/utility-payment` | Process a utility bill payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/utility-payment` | Retrieve paginated list of utility payments | Query: `page`, `size`, `sort` (Pageable) | `List<UtilityPayment { id, providerId, amount, referenceNumber, account, transactionId, status }>` |

### 3.5 Infrastructure Endpoints

| Service | Endpoint | Description |
|---------|----------|-------------|
| **Service Registry** | `http://localhost:8081/` | Eureka dashboard |
| **Config Server** | `http://localhost:8090/{application}/{profile}` | Fetch configuration for a service |
| **Zipkin** | `http://localhost:9411/` | Distributed tracing UI |
| **Keycloak** | `http://localhost:8080/` | Keycloak admin console |
| **All Services** | `/actuator/**` | Spring Boot Actuator health, info, metrics (permitted without auth at gateway) |

### 3.6 API Gateway Route Prefixes

The API Gateway routes incoming requests to backend services. Requests are routed via Eureka service names:

| Gateway Path Prefix | Target Service (Eureka name) | Example |
|---------------------|------------------------------|---------|
| `/user/**` | `internet-banking-user-service` | `GET /user/api/v1/bank-users` |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` | `POST /fund-transfer/api/v1/transfer` |
| `/utility-payment/**` | `internet-banking-utility-payment-service` | `POST /utility-payment/api/v1/utility-payment` |
| `/banking-core/**` | `core-banking-service` | `GET /banking-core/api/v1/account/bank-account/{num}` |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration and Management

**Service:** `internet-banking-user-service`
**Key Class:** `com.javatodev.finance.service.UserService`

#### Registration Flow (`POST /api/v1/bank-users/register`)

1. **Duplicate check:** Queries Keycloak to check if a user with the same email already exists. Throws `UserAlreadyRegisteredException` (code: `USER-SERVICE-1001`) if found.
2. **Core bank verification:** Calls `core-banking-service` via OpenFeign (`GET /api/v1/user/{identification}`) to verify the user exists in the core banking system by identification number.
3. **Email validation:** Compares the email provided with the email in the core banking system. Throws `InvalidEmailException` (code: `USER-SERVICE-1002`) if mismatched.
4. **Keycloak user creation:** Creates a new user in Keycloak with `enabled=false` and `emailVerified=false`, setting the user's password as a non-temporary credential.
5. **Local record creation:** On successful Keycloak creation (HTTP 201), stores the user in the local `user` table with `status=PENDING` and the Keycloak `authId`.
6. **Fallback:** If core bank user not found, throws `InvalidBankingUserException` (code: `USER-SERVICE-1003`).

#### User Approval Flow (`PATCH /api/v1/bank-users/update/{id}`)

1. Looks up the user in the local database.
2. If the new status is `APPROVED`: enables the user in Keycloak and sets `emailVerified=true`.
3. Updates the local user record with the new status.

### 4.2 Fund Transfer Processing

**Service:** `internet-banking-fund-transfer-service`
**Key Class:** `com.javatodev.finance.service.FundTransferService`

#### Transfer Flow (`POST /api/v1/transfer`)

1. **Record creation:** Saves a `FundTransferEntity` with `status=PENDING` in the local database.
2. **Core bank delegation:** Calls `core-banking-service` via OpenFeign (`POST /api/v1/transaction/fund-transfer`) with the transfer request.
3. **Status update:** On success, updates the local record with `status=SUCCESS` and stores the `transactionReference` (transaction ID from core).
4. **Returns** a success response with the transaction ID.

#### Core Banking Fund Transfer Logic (`TransactionService.fundTransfer()`)

1. **Account lookup:** Reads both source and destination bank accounts by account number.
2. **Balance validation:** Checks that the source account's `actualBalance` is non-negative and >= the transfer amount. Throws `InsufficientFundsException` (code: `BANKING-CORE-SERVICE-1001`) if insufficient.
3. **Debit source:** Subtracts the amount from `actualBalance` and `availableBalance` of the source account.
4. **Record debit transaction:** Saves a `TransactionEntity` with `FUND_TRANSFER` type and negative amount for the source account.
5. **Credit destination:** Adds the amount to `actualBalance` and `availableBalance` of the destination account.
6. **Record credit transaction:** Saves a `TransactionEntity` with `FUND_TRANSFER` type and positive amount for the destination account.
7. **Returns** a UUID transaction ID.
8. The entire operation is **@Transactional**.

### 4.3 Utility Payment Processing

**Service:** `internet-banking-utility-payment-service`
**Key Class:** `com.javatodev.finance.service.UtilityPaymentService`

#### Payment Flow (`POST /api/v1/utility-payment`)

1. **Record creation:** Saves a `UtilityPaymentEntity` with `status=PROCESSING` in the local database.
2. **Core bank delegation:** Calls `core-banking-service` via OpenFeign (`POST /api/v1/transaction/util-payment`) with the payment request.
3. **Status update:** On success, updates the local record with `status=SUCCESS` and stores the `transactionId` from the core response.
4. **Returns** a success response with the transaction ID.

#### Core Banking Utility Payment Logic (`TransactionService.utilPayment()`)

1. **Account lookup:** Reads the payer's bank account by account number.
2. **Balance validation:** Same validation as fund transfers - checks sufficient funds.
3. **Utility account lookup:** Reads the utility provider account by provider ID.
4. **Debit payer:** Subtracts the amount from both `actualBalance` and `availableBalance`.
5. **Record transaction:** Saves a `TransactionEntity` with `UTILITY_PAYMENT` type, negative amount, and the reference number.
6. **Returns** a UUID transaction ID and success message.

### 4.4 Error Handling

Each service implements a `GlobalExceptionHandler` (`@ControllerAdvice`) that catches:
- `SimpleBankingGlobalException` (and subclasses): Returns HTTP 400 with `{ code, message }`.
- Generic `Exception`: Returns HTTP 400 with a descriptive string.

#### Error Codes

| Code | Service | Exception | Description |
|------|---------|-----------|-------------|
| `BANKING-CORE-SERVICE-1000` | Core Banking | `EntityNotFoundException` | Entity not found |
| `BANKING-CORE-SERVICE-1001` | Core Banking | `InsufficientFundsException` | Insufficient funds for transaction |
| `USER-SERVICE-1000` | User Service | `EntityNotFoundException` | Entity not found |
| `USER-SERVICE-1001` | User Service | `UserAlreadyRegisteredException` | Email already registered |
| `USER-SERVICE-1002` | User Service | `InvalidEmailException` | Email does not match core banking records |
| `USER-SERVICE-1003` | User Service | `InvalidBankingUserException` | User not found in core banking system |

---

## 5. Integration Points

### 5.1 Keycloak (Identity and Access Management)

- **Version:** 23.0.7
- **Docker container:** `keycloak_web` at `172.25.0.11:8080`
- **Admin credentials:** `admin` / `password`
- **Backing database:** PostgreSQL 15 at `172.25.0.10:5432` (database: `keycloak`, user: `keycloak`)
- **Realm configuration:** Auto-imported from `docker-compose/keycloak/realm-export.json` on startup via `--import-realm`
- **Authentication flow:** OAuth 2.0 / OIDC with JWT tokens
  - API Gateway validates JWTs using the JWK Set URI configured in `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`
  - User registration endpoint (`/user/api/v1/bank-users/register`) is whitelisted (no auth required)
  - All actuator endpoints are whitelisted
  - All other endpoints require a valid JWT
- **User Service integration:** Uses the `keycloak-admin-client` (v24.0.4) library
  - `KeycloakProperties` configures the connection with `client_credentials` grant type
  - Configuration properties: `app.config.keycloak.server-url`, `app.config.keycloak.realm`, `app.config.keycloak.clientId`, `app.config.keycloak.client-secret`
  - Operations: create user, update user (enable/disable, email verification), search user by email, read user by auth ID
- **Test credentials:** `ib_admin@javatodev.com` / `5V7huE3G86uB`

### 5.2 RabbitMQ (Message Broker)

- **Status:** Planned but **not yet implemented** (notification service is pending development)
- **Intended use:** Fund Transfer and Utility Payment services would publish notification messages to RabbitMQ queues; a dedicated Notification Service would consume these messages and deliver notifications to end users
- **No RabbitMQ container** is currently defined in the Docker Compose files

### 5.3 Zipkin (Distributed Tracing)

- **Version:** 3
- **Docker container:** `openzipkin_server` at `172.25.0.12:9411`
- **UI:** `http://localhost:9411`
- **Integration:** All services include tracing dependencies:
  - `micrometer-tracing-bridge-brave` - Brave tracer bridge for Micrometer
  - `zipkin-reporter-brave` - Reports traces to Zipkin
  - `feign-micrometer` - Propagates trace context through OpenFeign calls
- **Configuration:** Zipkin endpoint URL is provided via the centralized config server

### 5.4 Database Connections

| Service | Database Name | DB Technology | Connection Details |
|---------|--------------|---------------|-------------------|
| Core Banking Service | `banking_core_service` | MySQL 8 | Host: `mysql_javatodev_app` (172.25.0.9:3306) |
| User Service | `banking_core_user_service` | MySQL 8 | Host: `mysql_javatodev_app` (172.25.0.9:3306) |
| Fund Transfer Service | `banking_core_fund_transfer_service` | MySQL 8 | Host: `mysql_javatodev_app` (172.25.0.9:3306) |
| Utility Payment Service | `banking_core_utility_payment_service` | MySQL 8 | Host: `mysql_javatodev_app` (172.25.0.9:3306) |
| Keycloak | `keycloak` | PostgreSQL 15 | Host: `keycloakdb` (172.25.0.10:5432) |

- **MySQL user:** `javatodev_development` / `oPItyPticIAt` (created via `docker-compose/mysql/privileges.sql`)
- **MySQL root password:** configured in Docker Compose
- **Schema management:** Core Banking Service uses **Flyway** for database migrations (`org.flywaydb:flyway-core:10.12.0`, `flyway-mysql:10.12.0`)
  - Migration files located at `core-banking-service/src/main/resources/db/migration/`
  - `V1.0.20210427174638` - Creates base table structure (users, accounts, utility accounts)
  - `V1.0.20210427174721` - Inserts seed/test data (4 users, 14 accounts, 6 utility providers)
  - `V1.0.20210429210839` - Creates transaction table
- **Other services:** Use JPA auto-DDL (Hibernate) for schema generation

### 5.5 OpenFeign Inter-Service Communication

| Caller Service | Feign Client Interface | Target Service (Eureka name) | Endpoints Called |
|---------------|----------------------|------------------------------|-----------------|
| User Service | `BankingCoreRestClient` | `core-banking-service` | `GET /api/v1/user/{identification}` |
| Fund Transfer Service | `BankingCoreFeignClient` | `core-banking-service` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/fund-transfer` |
| Utility Payment Service | `BankingCoreRestClient` | `core-banking-service` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/util-payment` |

- Fund Transfer and Utility Payment services use a `CustomFeignClientConfiguration` for error handling and client customization
- User Service uses a `CustomFeignErrorDecoder` for Feign error response handling

### 5.6 Spring Cloud Config Server

- **Port:** 8090
- **Git backend:** `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
  - Branch: `main`
  - Search path: `configuration`
- **Usage:** All services (except the config server itself) include `spring-cloud-starter-config` and `spring-cloud-starter-bootstrap` to pull configuration on startup
- **Profiles:** Services use `docker` profile when running in containers (set via `-Dspring.profiles.active=docker` in Dockerfiles)

---

## 6. Build and Deployment Pipeline

### 6.1 Build System

- **Build tool:** Gradle (each service has its own `build.gradle`)
- **Java version:** 21 (Eclipse Temurin)
- **Spring Boot version:** 3.2.4
- **Spring Cloud version:** 2023.0.0
- **Common Gradle plugins:**
  - `org.springframework.boot` (v3.2.4)
  - `io.spring.dependency-management` (v1.1.4)
  - `com.gorylenko.gradle-git-properties` (v2.4.2) - Generates `git.properties` for build info
- **Test framework:** JUnit 5 (via `spring-boot-starter-test`)
- **Test database:** H2 in-memory (v2.2.224) for unit tests

### 6.2 Docker Build

Each service has a `Dockerfile` following the same pattern:

```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/{service-name}-0.0.1-SNAPSHOT.jar app.jar
EXPOSE {port}
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh
RUN apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

- Uses **Alpine-based JRE** image for minimal size
- Includes `wait-for-it.sh` for dependency ordering
- Activates `docker` Spring profile

### 6.3 Docker Compose Deployment

Two compose files exist in `docker-compose/`:

#### `docker-compose.yml` (Full Stack)

Deploys all services and infrastructure on a custom bridge network (`172.25.0.0/16`):

| Container | Image | IP | Port | Depends On |
|-----------|-------|----|------|------------|
| `openzipkin_server` | `openzipkin/zipkin:3` | 172.25.0.12 | 9411 | - |
| `keycloak_web` | `quay.io/keycloak/keycloak:23.0.7` | 172.25.0.11 | 8080 | keycloakdb |
| `keycloak_postgre_db` | `postgres:15` | 172.25.0.10 | - | - |
| `mysql_javatodev_app` | Custom (MySQL) | 172.25.0.9 | 3306 | - |
| `internet-banking-config-server` | `javatodev/internet-banking-config-server` | 172.25.0.8 | 8090 | - |
| `internet-banking-service-registry` | `javatodev/internet-banking-service-registry` | 172.25.0.7 | 8081 | - |
| `internet-banking-api-gateway` | `javatodev/internet-banking-api-gateway` | 172.25.0.6 | 8082 | registry, config |
| `internet-banking-user-service` | `javatodev/internet-banking-user-service` | 172.25.0.5 | 8083 | registry, config, mysql |
| `internet-banking-fund-transfer-service` | `javatodev/internet-banking-fund-transfer-service` | 172.25.0.4 | 8084 | registry, config, mysql |
| `internet-banking-utility-payment-service` | `javatodev/internet-banking-utility-payment-service` | 172.25.0.3 | 8085 | registry, config, mysql |
| `core-banking-service` | `javatodev/core-banking-service` | 172.25.0.2 | 8092 | registry, config, mysql |

#### `docker-compose-support-apps.yml` (Infrastructure Only)

Deploys only the infrastructure services (Zipkin, Keycloak, PostgreSQL, MySQL, Config Server, Service Registry) for local development where application services are run from the IDE.

### 6.4 Service Startup Order

Services use `wait-for-it.sh` to enforce startup ordering:

1. **Infrastructure first:** MySQL, PostgreSQL, Keycloak, Zipkin (no ordering constraints)
2. **Config Server** (port 8090) - must be available before any Spring Cloud Config consumers
3. **Service Registry** (port 8081) - must be available before Eureka clients
4. **Application services** (Gateway, User, Fund Transfer, Utility Payment, Core Banking) - wait for both Config Server and Service Registry; database-dependent services also wait for MySQL

### 6.5 Persistent Volumes

| Volume | Mount Point | Purpose |
|--------|------------|---------|
| `postgres_data` | `/var/lib/postgresql/data` | Keycloak PostgreSQL data |
| `mysqldata` | `/var/lib/mysql` | Application MySQL data |

### 6.6 Build and Run Commands

```bash
# Build a specific service
cd {service-directory}
./gradlew clean build

# Build Docker image for a service
cd {service-directory}
./gradlew clean build
docker build -t javatodev/{service-name} .

# Start full stack
cd docker-compose
docker-compose up -d

# Start only infrastructure (for local dev)
cd docker-compose
docker-compose -f docker-compose-support-apps.yml up -d

# Run tests for a service
cd {service-directory}
./gradlew test
```

### 6.7 Seed / Test Data

The core banking service automatically loads test data via Flyway migration `V1.0.20210427174721__temp_data.sql`:

**Users:**

| ID | Name | Email | Identification |
|----|------|-------|---------------|
| 1 | Sam Silva | sam@gmail.com | 808829932V |
| 2 | Guru Darmaraj | guru@gmail.com | 901830556V |
| 3 | Ragu Sivaraj | ragu@gmail.com | 348829932V |
| 4 | Randor Manoon | randor@gmail.com | 842829932V |

**Bank Accounts:** 14 savings accounts distributed across the 4 users with balances ranging from 12,000 to 889,000.33.

**Utility Providers:** VODAFONE, VERIZON, SINGTEL, HUTCH, AIRTEL, GIO
