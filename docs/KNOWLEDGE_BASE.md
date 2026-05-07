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

### 1.1 System Summary

This is a **Java 21 / Spring Boot 3.2.4** internet banking platform composed of **6 microservices** communicating via REST (OpenFeign) and managed through Spring Cloud infrastructure (Eureka, Config Server, API Gateway). All services are containerized with Docker and orchestrated via Docker Compose on a fixed-IP bridge network (`172.25.0.0/16`).

### 1.2 Microservices Inventory

| Service | Port | IP (Docker) | Purpose |
|---|---|---|---|
| `internet-banking-service-registry` | 8081 | 172.25.0.7 | Netflix Eureka server for service discovery |
| `internet-banking-config-server` | 8090 | 172.25.0.8 | Spring Cloud Config server (Git-backed) |
| `internet-banking-api-gateway` | 8082 | 172.25.0.6 | Spring Cloud Gateway with OAuth2/Keycloak security |
| `internet-banking-user-service` | 8083 | 172.25.0.5 | User registration, profile management, Keycloak integration |
| `internet-banking-fund-transfer-service` | 8084 | 172.25.0.4 | Account-to-account fund transfers |
| `internet-banking-utility-payment-service` | 8085 | 172.25.0.3 | Third-party utility bill payments |
| `core-banking-service` | 8092 | 172.25.0.2 | System of record: accounts, users, transactions, balances |

### 1.3 Infrastructure Components

| Component | Image/Version | Port | IP (Docker) | Purpose |
|---|---|---|---|---|
| MySQL | Custom (Dockerfile in `docker-compose/mysql`) | 3306 | 172.25.0.9 | Primary relational database for all business services |
| PostgreSQL | `postgres:15` | 5432 (closed) | 172.25.0.10 | Keycloak's backing database |
| Keycloak | `quay.io/keycloak/keycloak:23.0.7` | 8080 | 172.25.0.11 | Identity and Access Management (IAM) |
| Zipkin | `openzipkin/zipkin:3` | 9411 | 172.25.0.12 | Distributed tracing |

### 1.4 Communication Patterns

```
                        ┌───────────────┐
       Clients ────────►│  API Gateway  │ (OAuth2 JWT validation via Keycloak)
                        │   :8082       │
                        └──────┬────────┘
                               │ Routes traffic by path prefix
               ┌───────────────┼───────────────┬──────────────────┐
               ▼               ▼               ▼                  ▼
        ┌─────────────┐ ┌─────────────┐ ┌─────────────┐  ┌──────────────┐
        │ User Service│ │Fund Transfer│ │   Utility   │  │ Core Banking │
        │   :8083     │ │  Service    │ │  Payment    │  │   Service    │
        │             │ │   :8084     │ │  Service    │  │    :8092     │
        └──────┬──────┘ └──────┬──────┘ │   :8085     │  └──────────────┘
               │               │        └──────┬──────┘         ▲
               │               │               │                │
               │               └───────────────┴────────────────┘
               │                    OpenFeign REST calls
               │
               ▼
        ┌─────────────┐
        │  Keycloak   │
        │   :8080     │
        └─────────────┘
```

**Synchronous REST (OpenFeign):**
- `internet-banking-user-service` → `core-banking-service` (read user by identification)
- `internet-banking-fund-transfer-service` → `core-banking-service` (read account, process fund transfer)
- `internet-banking-utility-payment-service` → `core-banking-service` (read account, process utility payment)

**Service Discovery:** All services register with Eureka (`internet-banking-service-registry`). Feign clients resolve service names (`core-banking-service`) via Eureka.

**Configuration:** All services fetch configuration from `internet-banking-config-server` at startup using Spring Cloud Config with bootstrap context. Config server pulls from a remote Git repository: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`.

**Security Flow:** API Gateway validates JWT tokens issued by Keycloak. It extracts the principal name and forwards it downstream as an `X-Auth-Id` HTTP header. Downstream services read this header via `AppAuthUserFilter` to identify the authenticated user.

**Distributed Tracing:** All services include Micrometer Tracing Bridge (Brave) and Zipkin Reporter dependencies, sending trace data to Zipkin.

### 1.5 Database Architecture

Each business domain uses a **separate MySQL database** on the same MySQL instance:

| Database | Used By |
|---|---|
| `banking_core_service` | `core-banking-service` |
| `banking_core_fund_transfer_service` | `internet-banking-fund-transfer-service` |
| `banking_core_user_service` | `internet-banking-user-service` |
| `banking_core_utility_payment_service` | `internet-banking-utility-payment-service` |

Schema migrations for `core-banking-service` are managed via **Flyway** (3 migration files). Other services rely on JPA's `hibernate.ddl-auto` for schema management.

---

## 2. Data Model Documentation

### 2.1 Core Banking Service Entities

#### `banking_core_user`
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal user ID |
| `first_name` | VARCHAR(255) | | User's first name |
| `last_name` | VARCHAR(255) | | User's last name |
| `email` | VARCHAR(255) | | User's email address |
| `identification_number` | VARCHAR(255) | | National ID / NIC number |

#### `banking_core_account`
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal account ID |
| `number` | VARCHAR(255) | | Account number (e.g., `100015003000`) |
| `type` | VARCHAR(255) | | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `actual_balance` | DECIMAL(19,2) | | True ledger balance |
| `available_balance` | DECIMAL(19,2) | | Available balance for transactions |
| `user_id` | BIGINT | FK → `banking_core_user.id` | Account owner |

#### `banking_core_transaction`
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal transaction ID |
| `amount` | DECIMAL(19,2) | | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | NOT NULL | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | NOT NULL | Reference (destination account or provider) |
| `transaction_id` | VARCHAR(50) | NOT NULL | UUID-based transaction identifier |
| `account_id` | BIGINT | FK → `banking_core_account.id` | Associated bank account |

#### `banking_core_utility_account`
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal utility account ID |
| `number` | VARCHAR(255) | | Utility provider account number |
| `provider_name` | VARCHAR(255) | | Provider name (e.g., `VODAFONE`, `AIRTEL`) |

### 2.2 User Service Entity

#### `user` (in `banking_core_user_service` database)
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal user ID |
| `auth_id` | VARCHAR | | Keycloak user UUID |
| `identification` | VARCHAR | | NIC / National ID |
| `status` | VARCHAR | | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | TIMESTAMP | | Audit: creation timestamp |
| `created_by` | VARCHAR | | Audit: creator |
| `modified_date` | TIMESTAMP | | Audit: last modification timestamp |
| `modified_by` | VARCHAR | | Audit: last modifier |
| `version` | BIGINT | | Optimistic locking version |

### 2.3 Fund Transfer Service Entity

#### `fund_transfer` (in `banking_core_fund_transfer_service` database)
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal ID |
| `from_account` | VARCHAR | | Source account number |
| `to_account` | VARCHAR | | Destination account number |
| `amount` | DECIMAL | | Transfer amount |
| `transaction_reference` | VARCHAR | | UUID from core banking |
| `status` | VARCHAR | | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` | TIMESTAMP | | Audit field |
| `created_by` | VARCHAR | | Audit field |
| `modified_date` | TIMESTAMP | | Audit field |
| `modified_by` | VARCHAR | | Audit field |
| `version` | BIGINT | | Optimistic locking version |

### 2.4 Utility Payment Service Entity

#### `utility_payment` (in `banking_core_utility_payment_service` database)
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal ID |
| `provider_id` | BIGINT | | Utility provider ID |
| `amount` | DECIMAL | | Payment amount |
| `reference_number` | VARCHAR | | Customer/bill reference |
| `account` | VARCHAR | | Paying bank account number |
| `transaction_id` | VARCHAR | | UUID from core banking |
| `status` | VARCHAR | | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` | TIMESTAMP | | Audit field |
| `created_by` | VARCHAR | | Audit field |
| `modified_date` | TIMESTAMP | | Audit field |
| `modified_by` | VARCHAR | | Audit field |
| `version` | BIGINT | | Optimistic locking version |

### 2.5 Entity Relationships

```
banking_core_user  1 ──── * banking_core_account
banking_core_account  1 ──── * banking_core_transaction
banking_core_utility_account (standalone, no FK relationships)

user (user-service DB, linked to Keycloak via authId)
fund_transfer (fund-transfer-service DB, references account numbers as strings)
utility_payment (utility-payment-service DB, references account numbers as strings)
```

---

## 3. API Surface Map

### 3.1 API Gateway Routes

All client requests enter through the API Gateway (`:8082`), which routes based on path prefix to downstream services. The gateway enforces OAuth2 JWT authentication on all routes except:
- `POST /user/api/v1/bank-users/register` (user registration)
- `/actuator/**` endpoints for all services

### 3.2 Core Banking Service (`:8092`)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | - | `BankAccount` | Get bank account by account number |
| `GET` | `/api/v1/account/util-account/{account_name}` | - | `UtilityAccount` | Get utility account by provider name |
| `POST` | `/api/v1/transaction/fund-transfer` | `FundTransferRequest` | `FundTransferResponse` | Process a fund transfer between accounts |
| `POST` | `/api/v1/transaction/util-payment` | `UtilityPaymentRequest` | `UtilityPaymentResponse` | Process a utility payment |
| `GET` | `/api/v1/user/{identification}` | - | `User` | Get user by identification number |
| `GET` | `/api/v1/user` | `Pageable` params | `List<User>` | Get paginated list of users |

**Request/Response Shapes:**

```json
// FundTransferRequest
{ "fromAccount": "string", "toAccount": "string", "amount": 0.00 }

// FundTransferResponse
{ "message": "string", "transactionId": "string" }

// UtilityPaymentRequest
{ "providerId": 0, "amount": 0.00, "referenceNumber": "string", "account": "string" }

// UtilityPaymentResponse
{ "message": "string", "transactionId": "string" }

// BankAccount
{ "id": 0, "number": "string", "type": "SAVINGS_ACCOUNT|FIXED_DEPOSIT|LOAN_ACCOUNT",
  "status": "PENDING|ACTIVE|DORMANT|BLOCKED", "availableBalance": 0.00,
  "actualBalance": 0.00, "user": { ... } }

// User (core-banking)
{ "id": 0, "firstName": "string", "lastName": "string", "email": "string",
  "identificationNumber": "string", "bankAccounts": [ ... ] }
```

### 3.3 Internet Banking User Service (`:8083`)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | `User` | `User` | Register a new banking user (Keycloak + local DB + core banking validation) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `UserUpdateRequest` | `User` | Update user status (approve/disable/blacklist) |
| `GET` | `/api/v1/bank-users` | `Pageable` params | `List<User>` | List all registered users (enriched from Keycloak) |
| `GET` | `/api/v1/bank-users/{id}` | - | `User` | Get user by internal ID |

**Request/Response Shapes:**

```json
// User (registration request)
{ "email": "string", "identification": "string", "password": "string" }

// User (response)
{ "id": 0, "email": "string", "identification": "string", "authId": "string",
  "status": "PENDING|APPROVED|DISABLED|BLACKLIST", "version": 0 }

// UserUpdateRequest
{ "status": "APPROVED|DISABLED|BLACKLIST" }
```

### 3.4 Internet Banking Fund Transfer Service (`:8084`)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | `FundTransferRequest` | `FundTransferResponse` | Initiate a fund transfer |
| `GET` | `/api/v1/transfer` | `Pageable` params | `List<FundTransfer>` | List fund transfer history |

**Request/Response Shapes:**

```json
// FundTransferRequest
{ "fromAccount": "string", "toAccount": "string", "amount": 0.00 }

// FundTransferResponse
{ "message": "string", "transactionId": "string" }

// FundTransfer (list response)
{ "id": 0, "transactionReference": "string", "status": "string",
  "fromAccount": "string", "toAccount": "string", "amount": 0.00, "version": 0 }
```

### 3.5 Internet Banking Utility Payment Service (`:8085`)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | `UtilityPaymentRequest` | `UtilityPaymentResponse` | Process a utility payment |
| `GET` | `/api/v1/utility-payment` | `Pageable` params | `List<UtilityPayment>` | List utility payment history |

**Request/Response Shapes:**

```json
// UtilityPaymentRequest
{ "providerId": 0, "amount": 0.00, "referenceNumber": "string", "account": "string" }

// UtilityPaymentResponse
{ "message": "string", "transactionId": "string" }
```

### 3.6 Infrastructure Endpoints

| Service | Endpoint | Description |
|---|---|---|
| Service Registry | `GET :8081/eureka` | Eureka dashboard and API |
| Config Server | `GET :8090/{application}/{profile}` | Configuration properties |
| All services | `GET /actuator/**` | Spring Boot Actuator (health, info, metrics) |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

**Location:** `internet-banking-user-service` → `UserService.createUser()`

1. Check if email is already registered in Keycloak; if yes, throw `UserAlreadyRegisteredException`
2. Validate user exists in core banking system via Feign call to `core-banking-service` using identification number
3. Verify email matches the core banking record; if mismatch, throw `InvalidEmailException`
4. Create user in Keycloak with email, first/last name, and password (disabled by default, email not verified)
5. Retrieve Keycloak user ID (`authId`) and persist user in local database with `PENDING` status
6. If user not found in core banking, throw `InvalidBankingUserException`

### 4.2 User Approval Flow

**Location:** `internet-banking-user-service` → `UserService.updateUser()`

1. Admin updates user status via `PATCH /api/v1/bank-users/update/{id}`
2. If status is set to `APPROVED`:
   - Read the user's Keycloak representation
   - Enable the Keycloak account (`setEnabled(true)`)
   - Mark email as verified (`setEmailVerified(true)`)
3. Persist the new status in the local database

### 4.3 Fund Transfer Flow

**Location:** `internet-banking-fund-transfer-service` → `FundTransferService.fundTransfer()` → `core-banking-service` → `TransactionService.fundTransfer()`

**Internet Banking Fund Transfer Service:**
1. Receive transfer request (fromAccount, toAccount, amount)
2. Save a local `FundTransferEntity` with `PENDING` status
3. Call core banking service via Feign to execute the transfer
4. Update local entity with transaction reference and `SUCCESS` status

**Core Banking Service (`TransactionService.fundTransfer()`):**
1. Read both source and destination bank accounts
2. Validate source account has sufficient funds (`actualBalance >= amount`)
3. Debit source account: subtract amount from both `actualBalance` and `availableBalance`
4. Record debit transaction (negative amount)
5. Credit destination account: add amount to both `actualBalance` and `availableBalance`
6. Record credit transaction (positive amount)
7. Return transaction ID

**Balance Bug:** In `internalFundTransfer()`, `availableBalance` is set to `actualBalance - amount` **after** `actualBalance` has already been decremented. This double-subtracts from the available balance. The same bug exists in `utilPayment()`.

### 4.4 Utility Payment Flow

**Location:** `internet-banking-utility-payment-service` → `UtilityPaymentService.utilPayment()` → `core-banking-service` → `TransactionService.utilPayment()`

**Internet Banking Utility Payment Service:**
1. Receive payment request (providerId, amount, referenceNumber, account)
2. Save a local `UtilityPaymentEntity` with `PROCESSING` status
3. Call core banking service via Feign to process the payment
4. Update local entity with transaction ID and `SUCCESS` status

**Core Banking Service (`TransactionService.utilPayment()`):**
1. Read the paying bank account and validate sufficient funds
2. Read the utility provider account by ID
3. Debit the bank account (same double-subtraction bug as fund transfer)
4. Record the transaction

### 4.5 Validation Rules

| Rule | Location | Description |
|---|---|---|
| Insufficient funds | `TransactionService.validateBalance()` | Checks `actualBalance > 0` and `actualBalance >= amount` |
| Duplicate email | `UserService.createUser()` | Checks Keycloak for existing email registration |
| Email mismatch | `UserService.createUser()` | Cross-validates email between request and core banking record |
| User exists in core | `UserService.createUser()` | Validates identification exists in core banking system |
| Entity not found | Multiple services | Throws `EntityNotFoundException` for missing accounts/users |

---

## 5. Integration Points

### 5.1 Keycloak

**Service:** `internet-banking-user-service`
**Library:** `keycloak-admin-client:24.0.4`
**Configuration:** Properties (`app.config.keycloak.*`) loaded from Spring Cloud Config:
- `server-url`: Keycloak base URL
- `realm`: Target realm
- `clientId`: Service client ID
- `client-secret`: Service client secret

**Operations:**
- Create user (`UserRepresentation` with credentials)
- Read user by auth ID
- Search users by email
- Update user (enable/disable, email verification)

**Authentication:** Uses `client_credentials` grant type. Keycloak instance is a static singleton (not thread-safe, not Spring-managed lifecycle).

### 5.2 RabbitMQ

**Status:** Referenced in README as a planned integration for the Notification Service, but **not implemented** in the current codebase. No RabbitMQ dependencies exist in any `build.gradle`. The Notification Service is listed as "PENDING Development."

### 5.3 Zipkin

**All services** include distributed tracing dependencies:
- `io.micrometer:micrometer-tracing-bridge-brave`
- `io.zipkin.reporter2:zipkin-reporter-brave`
- `io.github.openfeign:feign-micrometer`

Trace data is sent to Zipkin at `172.25.0.12:9411`. Configuration is centralized via Spring Cloud Config.

### 5.4 Database Connections

| Service | Database | Driver | Migration |
|---|---|---|---|
| `core-banking-service` | MySQL `banking_core_service` | `mysql-connector-j:8.4.0` | Flyway (`flyway-core:10.12.0`, `flyway-mysql:10.12.0`) |
| `internet-banking-user-service` | MySQL `banking_core_user_service` | `mysql-connector-j:8.4.0` | JPA auto-DDL |
| `internet-banking-fund-transfer-service` | MySQL `banking_core_fund_transfer_service` | `mysql-connector-j:8.4.0` | JPA auto-DDL |
| `internet-banking-utility-payment-service` | MySQL `banking_core_utility_payment_service` | `mysql-connector-j:8.4.0` | JPA auto-DDL |

MySQL is initialized with a custom `privileges.sql` that creates a shared user `javatodev_development` with broad permissions across all databases.

### 5.5 Spring Cloud Config

**Server:** `internet-banking-config-server` (`:8090`)
**Backend:** Git repository at `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` (branch: `main`, path: `configuration`)

All services use **bootstrap context** (`bootstrap.yml` / `bootstrap-docker.yml`) to connect to the config server at startup. The `docker` profile overrides the config server URL to use the Docker container name.

### 5.6 Eureka Service Discovery

**Server:** `internet-banking-service-registry` (`:8081`)
All services register as Eureka clients. Feign clients resolve service names (e.g., `core-banking-service`) via Eureka for inter-service communication.

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build Tool:** Gradle (Wrapper included per service, version 8.6)
- **Java Version:** 21
- **Spring Boot:** 3.2.4
- **Spring Cloud:** 2023.0.0
- **Plugins:** `spring-boot`, `spring-dependency-management`, `gradle-git-properties`

Each service is an independent Gradle project with its own `build.gradle` and Gradle Wrapper. There is **no multi-project Gradle build** (no root `settings.gradle`).

### 6.2 Docker

Each service is expected to be built as a Docker image (e.g., `javatodev/core-banking-service`). Dockerfiles are not present in the repository; images are assumed to be pre-built and published to a registry.

### 6.3 Docker Compose

Two compose files exist:
- **`docker-compose.yml`**: Full stack deployment (all 6 services + infrastructure)
- **`docker-compose-support-apps.yml`**: Infrastructure only (Zipkin, Keycloak, MySQL, Config Server, Service Registry)

**Key Features:**
- Fixed IP addresses on a custom bridge network (`172.25.0.0/16`)
- `wait-for-it.sh` scripts for startup ordering (wait for Service Registry, Config Server, and MySQL before application services start)
- Docker profile activation via `-Dspring.profiles.active=docker`
- Named volumes for data persistence (`postgres_data`, `mysqldata`)
- Keycloak realm import from `docker-compose/keycloak` volume mount

### 6.4 Test Infrastructure

- All services include `spring-boot-starter-test` dependency
- Business services (core, user, fund-transfer, utility-payment) include H2 in-memory database for tests
- Test classes are minimal boilerplate (only `@SpringBootTest` context load tests exist)
- No CI/CD pipeline configuration is present in the repository (GitHub Actions workflows were removed)

### 6.5 OpenAPI / Swagger

- `core-banking-service`, `internet-banking-fund-transfer-service`, `internet-banking-user-service`, and `internet-banking-utility-payment-service` include `springdoc-openapi-starter-webflux-ui:2.1.0`
- Controllers are annotated with `@Tag`, `@Operation` for Swagger documentation
- Swagger UI is available at `/swagger-ui.html` for each service

### 6.6 Postman Collection

A Postman collection is provided at `postman_collection/`:
- `JAVA_TO_DEV_MICROSERVICES.postman_collection.json` - API request templates
- `BANKING_CORE_MICROSERVICES_PROJECT.postman_environment.json` - Environment variables
- Includes a `LOCAL_DOCKER_SETUP` environment for testing against Docker deployment
- Test credentials: `ib_admin@javatodev.com / 5V7huE3G86uB`
