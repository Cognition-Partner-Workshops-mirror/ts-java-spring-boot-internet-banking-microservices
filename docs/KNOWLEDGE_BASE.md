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

The Internet Banking application is a Java 21 / Spring Boot 3.2.4 microservices system using Spring Cloud 2023.0.0. It consists of **6 microservices** and **4 infrastructure components**, all orchestrated via Docker Compose on a fixed-IP bridge network (`172.25.0.0/16`).

### 1.2 Microservices

| Service | Port | IP (Docker) | Purpose |
|---|---|---|---|
| **core-banking-service** | 8092 | 172.25.0.2 | System of record: accounts, users, transactions, balance management |
| **internet-banking-user-service** | 8083 | 172.25.0.5 | User lifecycle: registration, approval, Keycloak integration |
| **internet-banking-fund-transfer-service** | 8084 | 172.25.0.4 | Orchestrates peer-to-peer fund transfers |
| **internet-banking-utility-payment-service** | 8085 | 172.25.0.3 | Orchestrates utility bill payments |
| **internet-banking-api-gateway** | 8082 | 172.25.0.6 | Spring Cloud Gateway with OAuth2/JWT security |
| **internet-banking-service-registry** | 8081 | 172.25.0.7 | Netflix Eureka service discovery server |

### 1.3 Infrastructure Components

| Component | Port | IP (Docker) | Purpose |
|---|---|---|---|
| **internet-banking-config-server** | 8090 | 172.25.0.8 | Spring Cloud Config Server (Git-backed) |
| **MySQL** | 3306 | 172.25.0.9 | Primary database for core-banking, user, fund-transfer, and utility-payment services |
| **Keycloak** (+ PostgreSQL) | 8080 | 172.25.0.11 | Identity and access management (realm import on startup) |
| **Zipkin** | 9411 | 172.25.0.12 | Distributed tracing collector |

### 1.4 Communication Patterns

```
                         +-----------+
       Internet  ------->| API       |
                         | Gateway   |  (OAuth2 Resource Server / JWT validation)
                         | :8082     |
                         +-----+-----+
                               |
               +-------+-------+-------+
               |               |               |
        +------v------+ +------v------+ +------v------+
        | User        | | Fund        | | Utility     |
        | Service     | | Transfer    | | Payment     |
        | :8083       | | Service     | | Service     |
        |             | | :8084       | | :8085       |
        +------+------+ +------+------+ +------+------+
               |               |               |
               |  OpenFeign    |  OpenFeign     |  OpenFeign
               |               |               |
        +------v---------------v---------------v------+
        |              core-banking-service            |
        |              :8092                           |
        +----------------------------------------------+
               |                       |
        +------v------+        +------v------+
        | MySQL       |        | Keycloak    |
        | :3306       |        | :8080       |
        +-------------+        +-------------+
```

**Synchronous (HTTP/REST via OpenFeign):**
- `internet-banking-user-service` -> `core-banking-service` (user validation during registration)
- `internet-banking-fund-transfer-service` -> `core-banking-service` (account reads, fund transfer execution)
- `internet-banking-utility-payment-service` -> `core-banking-service` (account reads, utility payment execution)

**Service Discovery:**
- All services register with Eureka (`internet-banking-service-registry`)
- Feign clients resolve service names (e.g., `core-banking-service`) via Eureka

**Centralized Configuration:**
- All services fetch configuration from `internet-banking-config-server` at bootstrap
- Config server pulls from Git: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`

**Security Flow:**
- API Gateway acts as OAuth2 Resource Server validating JWT tokens from Keycloak
- Gateway injects `X-Auth-Id` header with the authenticated principal name
- Downstream services extract `X-Auth-Id` via `AppAuthUserFilter` servlet filter

**Distributed Tracing:**
- All services use Micrometer Tracing (Brave bridge) to report spans to Zipkin

### 1.5 Startup Order

Docker Compose uses `wait-for-it.sh` scripts to enforce startup dependencies:

1. Infrastructure: MySQL, PostgreSQL (Keycloak DB), Keycloak, Zipkin
2. `internet-banking-config-server` (no dependencies)
3. `internet-banking-service-registry` (no dependencies)
4. Business services wait for both config-server (:8090) and service-registry (:8081) and MySQL (:3306)
5. API Gateway waits for config-server and service-registry

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL: `banking_core_service`)

#### `banking_core_user`
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| `first_name` | VARCHAR(255) | User first name |
| `last_name` | VARCHAR(255) | User last name |
| `email` | VARCHAR(255) | User email address |
| `identification_number` | VARCHAR(255) | National ID (NIC), used as cross-service identifier |

**Relationships:** One-to-Many with `banking_core_account`

#### `banking_core_account`
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| `number` | VARCHAR(255) | Account number (12-digit string) |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `ACTIVE` |
| `actual_balance` | DECIMAL(19,2) | Actual ledger balance |
| `available_balance` | DECIMAL(19,2) | Available balance for withdrawals |
| `user_id` | BIGINT (FK) | References `banking_core_user.id` |

#### `banking_core_utility_account`
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| `number` | VARCHAR(255) | Utility account number |
| `provider_name` | VARCHAR(255) | Provider name (e.g., VODAFONE, AIRTEL) |

#### `banking_core_transaction`
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| `amount` | DECIMAL(19,2) | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Reference (destination account number or utility ref) |
| `transaction_id` | VARCHAR(50) | UUID linking related debit/credit entries |
| `account_id` | BIGINT (FK) | References `banking_core_account.id` |

**Schema Management:** Flyway migrations in `core-banking-service/src/main/resources/db/migration/`

### 2.2 Internet Banking User Service (MySQL)

#### `user`
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| `auth_id` | VARCHAR | Keycloak user ID (UUID) |
| `identification` | VARCHAR | NIC linking to core banking user |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED` |
| Audit fields | | `createdBy`, `createdDate`, `lastModifiedBy`, `lastModifiedDate` (inherited from `AuditAware`) |

**Schema Management:** JPA `ddl-auto` (no Flyway in this service)

### 2.3 Internet Banking Fund Transfer Service (MySQL)

#### `fund_transfer`
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| `transaction_reference` | VARCHAR | UUID from core-banking transaction |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `status` | VARCHAR | Enum: `PENDING`, `SUCCESS` |
| Audit fields | | Inherited from `AuditAware` |

**Schema Management:** JPA `ddl-auto` (no Flyway)

### 2.4 Internet Banking Utility Payment Service (MySQL)

#### `utility_payment`
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Utility reference number |
| `account` | VARCHAR | Source bank account number |
| `transaction_id` | VARCHAR | UUID from core-banking transaction |
| `status` | VARCHAR | Enum: `PROCESSING`, `SUCCESS` |
| Audit fields | | Inherited from `AuditAware` |

**Schema Management:** JPA `ddl-auto` (no Flyway)

---

## 3. API Surface Map

### 3.1 Core Banking Service (`:8092`)

| Method | Path | Request Body | Response | Description |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | - | `BankAccount` | Get bank account by number |
| `GET` | `/api/v1/account/util-account/{account_name}` | - | `UtilityAccount` | Get utility account by provider name |
| `GET` | `/api/v1/user/{identification}` | - | `User` | Get user by NIC |
| `GET` | `/api/v1/user` | Pageable query params | `List<User>` | List users (paginated) |
| `POST` | `/api/v1/transaction/fund-transfer` | `FundTransferRequest` | `FundTransferResponse` | Execute a fund transfer |
| `POST` | `/api/v1/transaction/util-payment` | `UtilityPaymentRequest` | `UtilityPaymentResponse` | Execute a utility payment |

**Key DTOs:**
- `FundTransferRequest`: `{ fromAccount, toAccount, amount }`
- `FundTransferResponse`: `{ message, transactionId }`
- `UtilityPaymentRequest`: `{ providerId, amount, referenceNumber, account }`
- `UtilityPaymentResponse`: `{ message, transactionId }`
- `BankAccount`: `{ id, number, type, status, availableBalance, actualBalance, user }`
- `User`: `{ id, firstName, lastName, email, identificationNumber, bankAccounts }`

### 3.2 Internet Banking User Service (`:8083`)

| Method | Path | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | `User` | `User` | Register a new banking user |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `UserUpdateRequest` | `User` | Update user (e.g., approve) |
| `GET` | `/api/v1/bank-users` | Pageable query params | `List<User>` | List users (paginated) |
| `GET` | `/api/v1/bank-users/{id}` | - | `User` | Get user by ID |

**Key DTOs:**
- `User` (request/response): `{ id, email, identification, password, authId, status }`
- `UserUpdateRequest`: `{ status }` (Enum: `PENDING`, `APPROVED`)

### 3.3 Internet Banking Fund Transfer Service (`:8084`)

| Method | Path | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | `FundTransferRequest` | `FundTransferResponse` | Initiate fund transfer |
| `GET` | `/api/v1/transfer` | Pageable query params | `List<FundTransfer>` | List fund transfers (paginated) |

**Key DTOs:**
- `FundTransferRequest`: `{ fromAccount, toAccount, amount }`
- `FundTransferResponse`: `{ message, transactionId }`

### 3.4 Internet Banking Utility Payment Service (`:8085`)

| Method | Path | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | `UtilityPaymentRequest` | `UtilityPaymentResponse` | Process utility payment |
| `GET` | `/api/v1/utility-payment` | Pageable query params | `List<UtilityPayment>` | List utility payments (paginated) |

**Key DTOs:**
- `UtilityPaymentRequest`: `{ providerId, amount, referenceNumber, account }`
- `UtilityPaymentResponse`: `{ message, transactionId }`

### 3.5 API Gateway Routing (`:8082`)

All requests are routed through the gateway with path prefixes:

| Prefix | Target Service |
|---|---|
| `/user/**` | `internet-banking-user-service` |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` |
| `/banking-core/**` | `core-banking-service` |
| `/utility-payment/**` | `internet-banking-utility-payment-service` |

**Public (unauthenticated) endpoints:**
- `POST /user/api/v1/bank-users/register`
- `/actuator/**` (all services)

**All other endpoints require a valid JWT Bearer token.**

### 3.6 Infrastructure Endpoints

| Service | Path | Description |
|---|---|---|
| Service Registry | `GET :8081/` | Eureka dashboard |
| Config Server | `GET :8090/{app}/{profile}` | Configuration properties |
| All services | `GET /actuator/**` | Spring Boot Actuator (health, info, metrics) |
| Zipkin | `GET :9411/` | Tracing UI |
| Keycloak | `GET :8080/` | Admin console |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

**Service:** `internet-banking-user-service` (`UserService.createUser()`)

1. Check if email already exists in Keycloak (prevent duplicates)
2. Validate user exists in core-banking by NIC (`BankingCoreRestClient.readUser()`)
3. Verify email matches the core-banking record
4. Create user in Keycloak (disabled, email not verified, with password)
5. Retrieve Keycloak auth ID
6. Persist local user record with status `PENDING`

**Failure scenarios:**
- `UserAlreadyRegisteredException` if email exists in Keycloak
- `InvalidEmailException` if email does not match core-banking record
- `InvalidBankingUserException` if NIC not found in core-banking

### 4.2 User Approval Flow

**Service:** `internet-banking-user-service` (`UserService.updateUser()`)

1. Look up user by ID
2. If new status is `APPROVED`: enable Keycloak user and mark email as verified
3. Update local status to `APPROVED`

### 4.3 Fund Transfer Flow

**Service:** `internet-banking-fund-transfer-service` -> `core-banking-service`

1. **Fund Transfer Service** receives request, creates local record with status `PENDING`
2. Calls `core-banking-service` via Feign (`/api/v1/transaction/fund-transfer`)
3. **Core Banking Service** (`TransactionService.fundTransfer()`):
   a. Reads both source and destination bank accounts
   b. Validates source account has sufficient balance (`actualBalance >= amount`)
   c. Executes `internalFundTransfer()` within a `@Transactional` boundary:
      - Debit source: subtract amount from `actualBalance` and `availableBalance`
      - Record debit transaction entry
      - Credit destination: add amount to `actualBalance` and `availableBalance`
      - Record credit transaction entry
      - Both entries share the same `transactionId` (UUID)
4. **Fund Transfer Service** updates local record with transaction reference and status `SUCCESS`

**Validation rules:**
- Source account `actualBalance` must be >= 0
- Source account `actualBalance` must be >= transfer amount
- Throws `InsufficientFundsException` on failure

### 4.4 Utility Payment Flow

**Service:** `internet-banking-utility-payment-service` -> `core-banking-service`

1. **Utility Payment Service** receives request, creates local record with status `PROCESSING`
2. Calls `core-banking-service` via Feign (`/api/v1/transaction/util-payment`)
3. **Core Banking Service** (`TransactionService.utilPayment()`):
   a. Reads the source bank account
   b. Validates sufficient balance
   c. Reads utility account by provider ID
   d. Debits source account (subtracts from both balances)
   e. Records transaction entry with type `UTILITY_PAYMENT`
4. **Utility Payment Service** updates local record with transaction ID and status `SUCCESS`

### 4.5 Authentication and Authorization

- **Keycloak** manages user identities, credentials, and token issuance
- **API Gateway** validates JWT tokens using Keycloak's JWK endpoint
- Gateway injects authenticated user's principal name as `X-Auth-Id` header
- Downstream services extract this header via `AppAuthUserFilter` into `ApiRequestContextHolder` (thread-local)
- Used by `AuditorAwareConfig` for JPA audit fields (`createdBy`, `lastModifiedBy`)

---

## 5. Integration Points

### 5.1 Keycloak (Identity Provider)

- **Version:** 23.0.7
- **Realm:** Imported on startup from `docker-compose/keycloak/` volume mount
- **Integration:** `keycloak-admin-client` (v24.0.4) in user-service
- **Configuration:** `app.config.keycloak.*` properties (server-url, realm, clientId, client-secret)
- **Operations:** Create user, read user by email/ID, update user (enable/verify)
- **Auth flow:** `client_credentials` grant type for admin API access
- **Backing DB:** PostgreSQL 15 at `172.25.0.10:5432`

### 5.2 RabbitMQ (Message Broker)

- **Status:** Referenced in README but **not implemented** in current codebase
- **Intended use:** Push notification messages from fund-transfer and utility-payment services
- **No RabbitMQ dependency** in any `build.gradle` file; no consumer/producer code exists

### 5.3 Zipkin (Distributed Tracing)

- **Version:** Zipkin 3
- **Integration:** Micrometer Tracing with Brave bridge
- **Dependencies:** `micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`, `feign-micrometer`
- **Included in:** All 4 business services + API Gateway
- **Dashboard:** `http://localhost:9411`

### 5.4 MySQL Database

- **Image:** Custom build from `docker-compose/mysql/Dockerfile`
- **Init:** `privileges.sql` for database/user setup
- **Schema:** Flyway migrations (core-banking-service only); JPA auto-DDL for other services
- **Connection:** Configured via Spring Cloud Config Server (externalized)
- **Root password:** Set via `MYSQL_ROOT_PASSWORD` in docker-compose

### 5.5 Spring Cloud Config Server

- **Git repository:** `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch:** `main`, search path: `configuration`
- **Bootstrap:** Services use `bootstrap.yml` / `bootstrap-docker.yml` to connect at startup
- **Local dev:** Points to `http://localhost:8090`
- **Docker:** Overridden to `http://internet-banking-config-server:8090`

### 5.6 Netflix Eureka (Service Discovery)

- **Server:** `internet-banking-service-registry` on port 8081
- **Clients:** All business services + API Gateway register as Eureka clients
- **Feign resolution:** Service names (e.g., `core-banking-service`) resolved via Eureka

### 5.7 OpenAPI / Swagger

- **Library:** `springdoc-openapi-starter-webflux-ui` v2.1.0
- **Included in:** core-banking-service, user-service, fund-transfer-service, utility-payment-service
- **Annotations:** `@Tag` and `@Operation` annotations on all controllers
- **Note:** Uses `webflux-ui` starter even though services are servlet-based (potential mismatch)

---

## 6. Build and Deployment Pipeline

### 6.1 Build System

- **Build tool:** Gradle (each service has its own `build.gradle`, no root `settings.gradle`)
- **Java version:** 21 (source compatibility)
- **Spring Boot:** 3.2.4 via `org.springframework.boot` Gradle plugin
- **Spring Cloud:** 2023.0.0 BOM
- **Lombok:** Used across all services for boilerplate reduction
- **Git properties:** `com.gorylenko.gradle-git-properties` plugin for build metadata

### 6.2 Docker

Each service has its own `Dockerfile`:
- **Base image:** `eclipse-temurin:21.0.2_13-jre-alpine`
- **Pattern:** Copy built JAR as `app.jar`, copy `wait-for-it.sh`, expose port
- **Profile:** `-Dspring.profiles.active=docker` for Docker-specific config
- **wait-for-it.sh:** Bash script ensuring dependent services are available before JVM starts

### 6.3 Docker Compose

Two compose files in `docker-compose/`:

| File | Contents |
|---|---|
| `docker-compose.yml` | Full stack (all services + infrastructure) |
| `docker-compose-support-apps.yml` | Infrastructure only (MySQL, Keycloak, Zipkin, Config Server, Service Registry) |

**Network:** `javatodev_ib_network` bridge network with static IPs (`172.25.0.0/16`)

### 6.4 Build & Run Workflow

```bash
# Build each service individually
cd core-banking-service && ./gradlew build
cd internet-banking-user-service && ./gradlew build
# ... repeat for each service

# Build Docker images
docker build -t javatodev/core-banking-service ./core-banking-service
# ... repeat for each service

# Start everything
cd docker-compose && docker-compose up -d
```

### 6.5 Test Data

Flyway migrations in core-banking-service seed:
- **4 users** (Sam, Guru, Ragu, Randor) with NIC identifiers
- **14 bank accounts** (SAVINGS_ACCOUNT, ACTIVE) with various balances
- **6 utility providers** (VODAFONE, VERIZON, SINGTEL, HUTCH, AIRTEL, GIO)

**Test credentials:** `ib_admin@javatodev.com` / `5V7huE3G86uB`

### 6.6 CI/CD

- **GitHub Actions:** Workflows previously existed but were removed (commit `fa1c445`)
- **No active CI/CD pipeline** in the repository
