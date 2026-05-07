# Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Summary

This is a **Java 21 / Spring Boot 3.2.4** internet banking application built on a microservices architecture using **Spring Cloud 2023.0.0**. The system models a simplified banking domain with user registration, account management, fund transfers, and utility payments.

### 1.2 Microservices Inventory

| Service | Port | Purpose | Database |
|---|---|---|---|
| **core-banking-service** | 8092 | Core banking engine: accounts, users, transactions | MySQL (`banking_core_service`) |
| **internet-banking-user-service** | 8083 | User registration, approval, Keycloak integration | MySQL (`banking_core_user_service`) |
| **internet-banking-fund-transfer-service** | 8084 | Orchestrates fund transfers via core banking | MySQL (`banking_core_fund_transfer_service`) |
| **internet-banking-utility-payment-service** | 8085 | Orchestrates utility payments via core banking | MySQL (`banking_core_utility_payment_service`) |
| **internet-banking-api-gateway** | 8082 | API Gateway with OAuth2/JWT security | None |
| **internet-banking-service-registry** | 8081 | Eureka service discovery server | None |
| **internet-banking-config-server** | 8090 | Centralized configuration (Git-backed) | None |

### 1.3 Communication Patterns

```
                    ┌───────────────┐
                    │   Keycloak    │ (AuthN/AuthZ - OAuth2/OIDC)
                    │   :8080       │
                    └───────┬───────┘
                            │ JWT validation
                            ▼
┌──────────┐    ┌───────────────────────┐
│  Client   │───▶│   API Gateway (:8082) │
└──────────┘    │  (Spring Cloud GW)    │
                └──────┬───┬───┬────────┘
           ┌───────────┘   │   └───────────┐
           ▼               ▼               ▼
┌──────────────┐ ┌──────────────┐ ┌────────────────────┐
│ User Service │ │ Fund Transfer│ │ Utility Payment    │
│   (:8083)    │ │   (:8084)    │ │   (:8085)          │
└──────┬───────┘ └──────┬───────┘ └────────┬───────────┘
       │                │                   │
       │   OpenFeign    │   OpenFeign       │  OpenFeign
       ▼                ▼                   ▼
       ┌────────────────────────────────────┐
       │     Core Banking Service (:8092)   │
       │     (Accounts, Transactions, Users)│
       └────────────────────────────────────┘
```

**Inter-service communication:** Synchronous REST via **Spring Cloud OpenFeign** with service discovery through **Netflix Eureka**. All business services register with the Service Registry and discover each other by service name.

**Gateway routing:** The API Gateway routes requests using path prefixes (e.g., `/user/**` → user-service, `/fund-transfer/**` → fund-transfer-service). It injects an `X-Auth-Id` header with the authenticated user's principal name into all proxied requests.

**Configuration:** All services pull their configuration from the **Config Server**, which reads from a Git repository (`https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`).

### 1.4 Infrastructure Components

| Component | Image/Technology | Purpose |
|---|---|---|
| **MySQL** | Custom image (based on mysql) | Primary database for all business services |
| **Keycloak** | `quay.io/keycloak/keycloak:23.0.7` | Identity and access management (OAuth2/OIDC) |
| **PostgreSQL** | `postgres:15` | Keycloak's backing database |
| **Zipkin** | `openzipkin/zipkin:3` | Distributed tracing |
| **Eureka** | Spring Cloud Netflix Eureka Server | Service discovery |
| **Spring Cloud Config** | Git-backed config server | Centralized configuration |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service

#### `banking_core_user`
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto) | Internal user ID |
| `email` | VARCHAR(255) | User email address |
| `first_name` | VARCHAR(255) | First name |
| `last_name` | VARCHAR(255) | Last name |
| `identification_number` | VARCHAR(255) | National identification number |

#### `banking_core_account`
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto) | Internal account ID |
| `number` | VARCHAR(255) | Account number |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `available_balance` | DECIMAL(19,2) | Available balance |
| `actual_balance` | DECIMAL(19,2) | Actual/ledger balance |
| `user_id` | BIGINT (FK → `banking_core_user.id`) | Account owner |

#### `banking_core_utility_account`
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto) | Internal ID |
| `number` | VARCHAR(255) | Utility provider account number |
| `provider_name` | VARCHAR(255) | Provider name (e.g., VODAFONE, VERIZON) |

#### `banking_core_transaction`
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto) | Internal transaction ID |
| `amount` | DECIMAL(19,2) | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Reference (destination account or utility ref) |
| `transaction_id` | VARCHAR(50) | UUID-based transaction identifier |
| `account_id` | BIGINT (FK → `banking_core_account.id`) | Associated account |

**Relationships:**
- `banking_core_user` 1:N `banking_core_account` (one user, many accounts)
- `banking_core_account` 1:N `banking_core_transaction` (one account, many transactions)

### 2.2 Internet Banking User Service

#### `user` (with JPA auditing)
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto) | Internal ID |
| `auth_id` | VARCHAR | Keycloak user ID |
| `identification` | VARCHAR | National identification (links to core banking) |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | TIMESTAMP | Audit: creation timestamp |
| `created_by` | VARCHAR | Audit: creator |
| `modified_date` | TIMESTAMP | Audit: last modification timestamp |
| `modified_by` | VARCHAR | Audit: last modifier |
| `version` | BIGINT | Optimistic locking version |

### 2.3 Internet Banking Fund Transfer Service

#### `fund_transfer` (with JPA auditing)
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto) | Internal ID |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `transaction_reference` | VARCHAR | Transaction ID from core banking |
| `status` | VARCHAR | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| Audit fields | Various | `created_date`, `created_by`, `modified_date`, `modified_by`, `version` |

### 2.4 Internet Banking Utility Payment Service

#### `utility_payment` (with JPA auditing)
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto) | Internal ID |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Payment reference |
| `account` | VARCHAR | Source account number |
| `transaction_id` | VARCHAR | Transaction ID from core banking |
| `status` | VARCHAR | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| Audit fields | Various | `created_date`, `created_by`, `modified_date`, `modified_by`, `version` |

---

## 3. API Surface Map

### 3.1 Core Banking Service (`:8092`)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `GET` | `/api/v1/user/{identification}` | — | `User` (id, firstName, lastName, email, identificationNumber, accounts[]) | Read user by identification number |
| `GET` | `/api/v1/user` | Pageable params | `List<User>` | List users (paginated) |
| `GET` | `/api/v1/account/bank-account/{account_number}` | — | `BankAccount` (number, type, status, availableBalance, actualBalance) | Get bank account by number |
| `GET` | `/api/v1/account/util-account/{account_name}` | — | `UtilityAccount` (id, number, providerName) | Get utility account by provider name |
| `POST` | `/api/v1/transaction/fund-transfer` | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` | Process fund transfer |
| `POST` | `/api/v1/transaction/util-payment` | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` | Process utility payment |

### 3.2 Internet Banking User Service (`:8083`)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | `{ email, identification, password }` | `User` (id, email, identification, authId, status) | Register new banking user |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `{ status }` | `User` | Update user status (approve/disable) |
| `GET` | `/api/v1/bank-users` | Pageable params | `List<User>` | List registered users (paginated) |
| `GET` | `/api/v1/bank-users/{id}` | — | `User` | Get user by ID |

### 3.3 Internet Banking Fund Transfer Service (`:8084`)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | `{ fromAccount, toAccount, amount, authID }` | `{ message, transactionId }` | Initiate fund transfer |
| `GET` | `/api/v1/transfer` | Pageable params | `List<FundTransfer>` | List fund transfers (paginated) |

### 3.4 Internet Banking Utility Payment Service (`:8085`)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` | Process utility payment |
| `GET` | `/api/v1/utility-payment` | Pageable params | `List<UtilityPayment>` | List utility payments (paginated) |

### 3.5 API Gateway Route Prefixes (`:8082`)

All external requests go through the gateway. Routes are prefixed:
- `/user/**` → `internet-banking-user-service`
- `/fund-transfer/**` → `internet-banking-fund-transfer-service`
- `/utility-payment/**` → `internet-banking-utility-payment-service`
- `/banking-core/**` → `core-banking-service`

### 3.6 Infrastructure Endpoints

| Service | Endpoint | Description |
|---|---|---|
| Service Registry | `GET :8081/eureka` | Eureka dashboard |
| Config Server | `GET :8090/{application}/{profile}` | Fetch configuration |
| Zipkin | `GET :9411` | Tracing UI |
| Keycloak | `GET :8080` | Keycloak admin console |
| All services | `GET /actuator/**` | Spring Boot Actuator health/info/metrics |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

1. Client sends `POST /user/api/v1/bank-users/register` with `{ email, identification, password }`
2. **User Service** checks if email is already registered in Keycloak
3. Calls **Core Banking Service** via Feign to validate identification number exists
4. Validates that the email matches the core banking record
5. Creates a Keycloak user (disabled, email unverified) with the provided password
6. Stores local user record with `status=PENDING` and the Keycloak `authId`
7. Admin must later call `PATCH /update/{id}` with `status=APPROVED` to enable the Keycloak account

### 4.2 Fund Transfer Flow

1. Client sends `POST /fund-transfer/api/v1/transfer` with `{ fromAccount, toAccount, amount }`
2. **Fund Transfer Service** creates a local `FundTransferEntity` with `status=PENDING`
3. Calls **Core Banking Service** via Feign `POST /api/v1/transaction/fund-transfer`
4. Core Banking validates source account has sufficient funds (`actualBalance >= amount`)
5. Core Banking debits source account, credits destination account, creates transaction records for both
6. Returns transaction ID to Fund Transfer Service
7. Fund Transfer Service updates local record with `status=SUCCESS` and transaction reference

**Balance validation rule:** `actualBalance >= 0 AND actualBalance >= transferAmount`

### 4.3 Utility Payment Flow

1. Client sends `POST /utility-payment/api/v1/utility-payment` with `{ providerId, amount, referenceNumber, account }`
2. **Utility Payment Service** creates a local `UtilityPaymentEntity` with `status=PROCESSING`
3. Calls **Core Banking Service** via Feign `POST /api/v1/transaction/util-payment`
4. Core Banking validates source account has sufficient funds
5. Core Banking debits source account, creates a transaction record
6. Returns transaction ID to Utility Payment Service
7. Utility Payment Service updates local record with `status=SUCCESS` and transaction ID

### 4.4 Balance Calculation

- **Fund Transfer (debit side):** `actualBalance = actualBalance - amount`, `availableBalance = (new actualBalance) - amount` (Note: this double-subtracts from availableBalance — likely a bug)
- **Fund Transfer (credit side):** `actualBalance = actualBalance + amount`, `availableBalance = (new actualBalance) + amount` (double-adds — likely a bug)
- **Utility Payment:** Same double-subtraction pattern for the debit side

### 4.5 Transaction Recording

Each fund transfer creates **two** transaction records:
- Debit record on source account (negative amount)
- Credit record on destination account (positive amount)

Both records share the same `transactionId` (UUID) and `referenceNumber` (destination account number).

---

## 5. Integration Points

### 5.1 Keycloak (Identity Provider)

- **Version:** 23.0.7
- **Protocol:** OAuth2/OIDC, admin REST API
- **Used by:** User Service (admin client for user CRUD), API Gateway (JWT validation)
- **Configuration:**
  - Server URL, realm, clientId, clientSecret configured via `app.config.keycloak.*` properties
  - Uses `client_credentials` grant type for admin operations
  - Realm data imported on startup from `docker-compose/keycloak/realm-export.json`
- **Singleton pattern:** `KeycloakProperties` uses a static singleton for the Keycloak client instance (thread-safety concern)

### 5.2 RabbitMQ

- **Mentioned in README** as part of the architecture for notification service
- **Not implemented:** No RabbitMQ dependencies in any `build.gradle`, no message producers or consumers in the codebase
- **Status:** The notification service is listed as "PENDING Development"

### 5.3 Zipkin (Distributed Tracing)

- **Version:** openzipkin/zipkin:3
- **Integration:** Via Micrometer Tracing Bridge Brave (`micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`)
- **Coverage:** All business services include tracing dependencies
- **Port:** 9411

### 5.4 Database Connections

- **Engine:** MySQL (custom Docker image with initialization scripts)
- **Databases:** 4 separate databases for service isolation:
  - `banking_core_service`
  - `banking_core_fund_transfer_service`
  - `banking_core_user_service`
  - `banking_core_utility_payment_service`
- **Migrations:** Core Banking Service uses **Flyway** for schema management (3 migration files)
- **Other services:** Use JPA auto-DDL (no explicit migration strategy)
- **Connection credentials:** Configured via Spring Cloud Config (Git-backed)

### 5.5 Service Discovery (Eureka)

- **Server:** `internet-banking-service-registry` on port 8081
- **Clients:** All business services register as Eureka clients
- **Resolution:** Feign clients use service names (e.g., `core-banking-service`) resolved via Eureka

### 5.6 Centralized Configuration

- **Server:** `internet-banking-config-server` on port 8090
- **Source:** Git repository at `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Profiles:** `default`, `dev`, `docker`
- **Bootstrap:** Each service has `bootstrap.yml` pointing to `http://localhost:8090` and profile-specific variants (`bootstrap-dev.yml`, `bootstrap-docker.yml`)

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build tool:** Gradle (per-service, no multi-project build)
- **Gradle wrapper:** 8.6 (each service has its own `gradlew`)
- **Java:** 21 (source compatibility)
- **Spring Boot:** 3.2.4 (via plugin)
- **Spring Cloud:** 2023.0.0 (via BOM)
- **Key plugins:**
  - `org.springframework.boot` (3.2.4)
  - `io.spring.dependency-management` (1.1.4)
  - `com.gorylenko.gradle-git-properties` (2.4.2) — in most services
- **Test framework:** JUnit 5 (Jupiter) via `spring-boot-starter-test`

### 6.2 Docker

Each business service has a `Dockerfile` and a `wait-for-it.sh` script for startup ordering.

**Docker Compose files:**
- `docker-compose/docker-compose.yml` — Full stack (all services + infrastructure)
- `docker-compose/docker-compose-support-apps.yml` — Infrastructure only (MySQL, Keycloak, Zipkin, Config Server, Service Registry)

**Startup order:** Services wait for:
1. Service Registry (`:8081`)
2. Config Server (`:8090`)
3. MySQL (`:3306`) — for database-backed services

**Network:** Custom bridge network `javatodev_ib_network` (subnet `172.25.0.0/16`) with static IP assignments.

### 6.3 CI/CD

- **GitHub Actions:** Previously configured but workflows have been removed (commit message: "Remove GH Actions workflows")
- **No active CI/CD pipeline** in the repository

### 6.4 Test Infrastructure

- **Core Banking Service:** Has unit tests for `UserService`, `AccountService`, `TransactionService` (using Mockito, H2 in-memory DB for test profile)
- **Other services:** Only have empty Spring Boot application context tests (`*ApplicationTests.java`)
- **Test database:** H2 in-memory with Flyway disabled for core banking service tests

### 6.5 Shared Dependencies Across Services

| Dependency | Used In |
|---|---|
| Spring Boot Starter Web | core-banking, user, fund-transfer, utility-payment, service-registry |
| Spring Boot Starter Data JPA | core-banking, user, fund-transfer, utility-payment |
| Spring Cloud Eureka Client | core-banking, user, fund-transfer, utility-payment, api-gateway |
| Spring Cloud OpenFeign | user, fund-transfer, utility-payment |
| Spring Boot Actuator | All services |
| Micrometer Tracing (Brave/Zipkin) | All business services |
| Spring Cloud Config Client | core-banking, user, fund-transfer, utility-payment, api-gateway |
| SpringDoc OpenAPI | core-banking, user, fund-transfer, utility-payment |
| Lombok | core-banking, user, fund-transfer, utility-payment |
| MySQL Connector | core-banking, user, fund-transfer, utility-payment |
| H2 (test only) | core-banking, fund-transfer, utility-payment |
