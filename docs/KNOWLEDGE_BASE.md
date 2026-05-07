# Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Summary

This is a **Java 21 / Spring Boot 3.2.4 / Spring Cloud 2023.0.0** internet banking application composed of **6 microservices** that together provide user management, fund transfer, and utility payment capabilities through a unified API gateway.

### 1.2 Service Inventory

| Service | Port | Type | Purpose |
|---|---|---|---|
| `internet-banking-config-server` | 8090 | Infrastructure | Centralized configuration via Spring Cloud Config (backed by Git) |
| `internet-banking-service-registry` | 8081 | Infrastructure | Service discovery via Netflix Eureka Server |
| `internet-banking-api-gateway` | 8082 | Infrastructure | API routing, OAuth2/JWT security via Spring Cloud Gateway |
| `internet-banking-user-service` | 8083 | Business | User registration, approval, and management (integrates with Keycloak) |
| `internet-banking-fund-transfer-service` | 8084 | Business | Inter-account fund transfers |
| `internet-banking-utility-payment-service` | 8085 | Business | Utility bill payments (telecom, etc.) |
| `core-banking-service` | 8092 | Business | Core banking engine: accounts, users, transactions, balance management |

### 1.3 Communication Patterns

```
                        ┌──────────────────┐
                        │   Keycloak       │
                        │  (Auth Server)   │
                        │  Port 8080       │
                        └────────┬─────────┘
                                 │ OAuth2 / JWT
                                 ▼
┌─────────┐    HTTP    ┌──────────────────┐    Routes    ┌────────────────────┐
│  Client  │──────────▶│  API Gateway     │─────────────▶│ User Service       │
│          │           │  (8082)          │              │ (8083)             │
└─────────┘            │                  │              └────────┬───────────┘
                       │                  │                       │ OpenFeign
                       │                  │              ┌────────▼───────────┐
                       │                  │─────────────▶│ Fund Transfer Svc  │
                       │                  │              │ (8084)             │
                       │                  │              └────────┬───────────┘
                       │                  │                       │ OpenFeign
                       │                  │              ┌────────▼───────────┐
                       │                  │─────────────▶│ Utility Payment Svc│
                       │                  │              │ (8085)             │
                       │                  │              └────────┬───────────┘
                       │                  │                       │ OpenFeign
                       │                  │              ┌────────▼───────────┐
                       │                  │─────────────▶│ Core Banking Svc   │
                       └──────────────────┘              │ (8092)             │
                                                         └────────┬───────────┘
                       ┌──────────────────┐                       │
                       │  Config Server   │◀──────────────────────┘
                       │  (8090)          │  Bootstrap config
                       └──────────────────┘
                       ┌──────────────────┐
                       │  Service Registry│◀─── All services register here
                       │  Eureka (8081)   │
                       └──────────────────┘
```

**Inter-service communication:** Synchronous HTTP via **Spring Cloud OpenFeign** clients. Services discover each other through Eureka. The API Gateway propagates the authenticated user identity via the `X-Auth-Id` HTTP header to downstream services.

**Configuration:** All services bootstrap from the **Config Server**, which fetches configuration from a remote Git repository (`https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`). Profile-based bootstrap files (`bootstrap.yml`, `bootstrap-docker.yml`, `bootstrap-dev.yml`) point to the config server at different URLs per environment.

### 1.4 Infrastructure Components

| Component | Technology | Purpose |
|---|---|---|
| Database | MySQL 8.4.0 | Primary data store for all business services |
| Identity Provider | Keycloak 23.0.7 (PostgreSQL 15 backend) | OAuth2/OIDC authentication and user management |
| Distributed Tracing | Zipkin 3 + Micrometer Tracing (Brave) | Request tracing across services |
| Service Discovery | Netflix Eureka | Service registration and discovery |
| Config Management | Spring Cloud Config Server | Centralized, Git-backed configuration |
| API Gateway | Spring Cloud Gateway | Routing, security, header propagation |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL: `banking_core_service`)

#### `banking_core_user`
| Column | Type | Description |
|---|---|---|
| `id` | bigint (PK, auto-increment) | User ID |
| `first_name` | varchar(255) | First name |
| `last_name` | varchar(255) | Last name |
| `email` | varchar(255) | Email address |
| `identification_number` | varchar(255) | National ID / NIC number |

#### `banking_core_account`
| Column | Type | Description |
|---|---|---|
| `id` | bigint (PK, auto-increment) | Account ID |
| `number` | varchar(255) | Account number (e.g., `100015003000`) |
| `type` | varchar(255) | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | varchar(255) | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `available_balance` | decimal(19,2) | Available balance |
| `actual_balance` | decimal(19,2) | Actual/ledger balance |
| `user_id` | bigint (FK → `banking_core_user.id`) | Owner |

#### `banking_core_transaction`
| Column | Type | Description |
|---|---|---|
| `id` | bigint (PK, auto-increment) | Transaction ID |
| `amount` | decimal(19,2) | Transaction amount (negative for debits) |
| `transaction_type` | varchar(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | varchar(50) | Target account number or reference |
| `transaction_id` | varchar(50) | UUID transaction identifier |
| `account_id` | bigint (FK → `banking_core_account.id`) | Associated account |

#### `banking_core_utility_account`
| Column | Type | Description |
|---|---|---|
| `id` | bigint (PK, auto-increment) | Utility account ID |
| `number` | varchar(255) | Utility account number |
| `provider_name` | varchar(255) | Provider name (e.g., `VODAFONE`, `VERIZON`) |

**Relationships:**
- `banking_core_user` 1:N `banking_core_account` (via `user_id` FK)
- `banking_core_transaction` N:1 `banking_core_account` (via `account_id` FK)
- `banking_core_utility_account` is standalone (no FK relationships)

### 2.2 Fund Transfer Service (MySQL: `banking_core_fund_transfer_service`)

#### `fund_transfer`
| Column | Type | Description |
|---|---|---|
| `id` | bigint (PK, auto-increment) | Transfer ID |
| `from_account` | varchar | Source account number |
| `to_account` | varchar | Destination account number |
| `amount` | decimal | Transfer amount |
| `transaction_reference` | varchar | UUID from core banking response |
| `status` | varchar | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` | timestamp | Audit: creation time |
| `created_by` | varchar | Audit: creator (from X-Auth-Id) |
| `modified_date` | timestamp | Audit: last modified time |
| `modified_by` | varchar | Audit: modifier |
| `version` | bigint | Optimistic locking version |

### 2.3 User Service (MySQL: `banking_core_user_service`)

#### `user`
| Column | Type | Description |
|---|---|---|
| `id` | bigint (PK, auto-increment) | Internal user ID |
| `auth_id` | varchar | Keycloak user ID (UUID) |
| `identification` | varchar | National ID / NIC |
| `status` | varchar | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | (audit fields) | JPA auditing fields |

### 2.4 Utility Payment Service (MySQL: `banking_core_utility_payment_service`)

#### `utility_payment`
| Column | Type | Description |
|---|---|---|
| `id` | bigint (PK, auto-increment) | Payment ID |
| `provider_id` | bigint | Utility provider ID |
| `amount` | decimal | Payment amount |
| `reference_number` | varchar | Customer reference (e.g., phone number) |
| `account` | varchar | Source bank account number |
| `transaction_id` | varchar | UUID from core banking response |
| `status` | varchar | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | (audit fields) | JPA auditing fields |

### 2.5 Database Provisioning

The MySQL Docker image runs `privileges.sql` at initialization, which:
1. Creates user `javatodev_development` with password `oPItyPticIAt`
2. Grants DDL/DML privileges on all databases
3. Creates four databases: `banking_core_service`, `banking_core_fund_transfer_service`, `banking_core_user_service`, `banking_core_utility_payment_service`

Schema migrations for `banking_core_service` are managed via **Flyway** (3 migration scripts in `core-banking-service/src/main/resources/db/migration/`). Other services rely on JPA auto-DDL (no Flyway migrations present).

---

## 3. API Surface Map

All APIs are routed through the **API Gateway** at `http://localhost:8082`. The gateway strips the service prefix and forwards to the registered Eureka service.

### 3.1 Core Banking Service (`/core/...` via gateway)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | - | `BankAccount` (id, number, type, status, availableBalance, actualBalance, user) |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | - | `UtilityAccount` (id, number, providerName) |
| `GET` | `/api/v1/user/{identification}` | Get user by identification number | - | `User` (id, firstName, lastName, email, identificationNumber, bankAccounts[]) |
| `GET` | `/api/v1/user` | List users (paginated) | Pageable params | `List<User>` |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer | `{fromAccount, toAccount, amount}` | `{message, transactionId}` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` |

### 3.2 User Service (`/user/...` via gateway)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Register new user (public) | `{email, identification, password}` | `User` (id, email, identification, authId, status) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user status | `{status}` (APPROVED/DISABLED/etc.) | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | Pageable params | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | - | `User` |

### 3.3 Fund Transfer Service (`/fund-transfer/...` via gateway)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `{fromAccount, toAccount, amount}` | `{message, transactionId}` |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | Pageable params | `List<FundTransfer>` |

### 3.4 Utility Payment Service (`/payment/...` via gateway)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` |
| `GET` | `/api/v1/utility-payment` | List payments (paginated) | Pageable params | `List<UtilityPayment>` |

### 3.5 Health/Actuator Endpoints (all services)

All services expose Spring Boot Actuator at `/actuator/**`. The gateway permits these without authentication:
- `/user/actuator/**`
- `/fund-transfer/actuator/**`
- `/banking-core/actuator/**`
- `/utility-payment/actuator/**`

---

## 4. Key Business Logic Inventory

### 4.1 Fund Transfer (Core Banking)

**Location:** `core-banking-service/.../service/TransactionService.java`

1. Look up source (`fromAccount`) and destination (`toAccount`) bank accounts
2. **Validate balance:** `actualBalance >= 0 AND actualBalance >= transferAmount`; throws `InsufficientFundsException` if violated
3. Debit source account: `actualBalance -= amount`, `availableBalance = actualBalance - amount`
4. Credit destination account: `actualBalance += amount`, `availableBalance = actualBalance + amount`
5. Create two `TransactionEntity` records (debit + credit) with a shared `transactionId` (UUID)
6. Entire operation is `@Transactional`

**Bug note:** Available balance calculation has a double-subtraction issue — `availableBalance` is set to `actualBalance.subtract(amount)` *after* `actualBalance` was already reduced, causing an over-deduction.

### 4.2 Fund Transfer (Fund Transfer Service - Orchestrator)

**Location:** `internet-banking-fund-transfer-service/.../service/FundTransferService.java`

1. Save a `FundTransferEntity` with status `PENDING`
2. Call Core Banking Service via Feign: `POST /api/v1/transaction/fund-transfer`
3. On success, update entity status to `SUCCESS` and store the `transactionReference`
4. No failure handling / rollback if the Feign call fails (status remains `PENDING`)

### 4.3 Utility Payment (Core Banking)

**Location:** `core-banking-service/.../service/TransactionService.java`

1. Look up the source bank account
2. Validate balance (same rules as fund transfer)
3. Look up the utility provider account by `providerId`
4. Debit the source account (same double-subtraction bug)
5. Create a `TransactionEntity` for the debit
6. Return `{message, transactionId}`

### 4.4 Utility Payment (Payment Service - Orchestrator)

**Location:** `internet-banking-utility-payment-service/.../service/UtilityPaymentService.java`

1. Save a `UtilityPaymentEntity` with status `PROCESSING`
2. Call Core Banking Service via Feign: `POST /api/v1/transaction/util-payment`
3. On success, update entity status to `SUCCESS` and store the `transactionId`
4. No failure handling / rollback

### 4.5 User Registration

**Location:** `internet-banking-user-service/.../service/UserService.java`

1. Check if email already exists in Keycloak → throw `UserAlreadyRegisteredException`
2. Look up the user in Core Banking by NIC (`identification`) via Feign
3. Verify the provided email matches the core banking record → throw `InvalidEmailException` if mismatch
4. Create user in Keycloak (disabled, email unverified, with provided password)
5. Save local `UserEntity` with Keycloak `authId`, status = `PENDING`

### 4.6 User Approval

When updating a user's status to `APPROVED`:
1. Fetch the Keycloak `UserRepresentation` by `authId`
2. Set `enabled = true` and `emailVerified = true` in Keycloak
3. Update local entity status

---

## 5. Integration Points

### 5.1 Keycloak (Identity Provider)

- **Version:** 23.0.7
- **Realm:** `javatodev-internet-banking`
- **Client ID:** `internet-banking-core-client`
- **Integration method:** `keycloak-admin-client` SDK (v24.0.4) used by User Service for user CRUD
- **OAuth2 flow:** API Gateway validates JWT tokens using the Keycloak JWK endpoint (`spring.security.oauth2.resourceserver.jwt.jwk-set-uri`)
- **Realm import:** Pre-configured realm exported as `realm-export.json` and imported at container startup
- **Database:** PostgreSQL 15 (container `keycloak_postgre_db`)

### 5.2 RabbitMQ

- **Status:** Referenced in README as a planned integration for the Notification Service
- **Current state:** Not implemented. No RabbitMQ dependency in any `build.gradle`. No message producers or consumers exist in the codebase
- The Notification Service itself is listed as "PENDING Development"

### 5.3 Zipkin (Distributed Tracing)

- **Version:** Zipkin 3 (Docker container)
- **Port:** 9411
- **Integration:** All business services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` dependencies
- **Configuration:** Trace reporting is configured via the centralized config server (not visible in local config files)

### 5.4 Database Connections

| Service | Database | Connection Method |
|---|---|---|
| Core Banking | `banking_core_service` (MySQL) | Spring Data JPA + Flyway migrations |
| Fund Transfer | `banking_core_fund_transfer_service` (MySQL) | Spring Data JPA (auto-DDL) |
| User Service | `banking_core_user_service` (MySQL) | Spring Data JPA (auto-DDL) |
| Utility Payment | `banking_core_utility_payment_service` (MySQL) | Spring Data JPA (auto-DDL) |
| Keycloak | `keycloak` (PostgreSQL) | Internal Keycloak management |

All MySQL services connect via `com.mysql:mysql-connector-j:8.4.0`. Database credentials are managed through the centralized config server.

### 5.5 Inter-Service Feign Clients

| Source Service | Target Service | Feign Client | Endpoints Called |
|---|---|---|---|
| User Service | Core Banking | `BankingCoreRestClient` | `GET /api/v1/user/{identification}` |
| Fund Transfer Service | Core Banking | `BankingCoreFeignClient` | `GET /api/v1/account/bank-account/{number}`, `POST /api/v1/transaction/fund-transfer` |
| Utility Payment Service | Core Banking | `BankingCoreRestClient` | `GET /api/v1/account/bank-account/{number}`, `POST /api/v1/transaction/util-payment` |

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build tool:** Gradle 8.6 (per service, each has its own `build.gradle` and Gradle wrapper)
- **No multi-module build:** Each service is an independent Gradle project (no root `settings.gradle`)
- **Plugins:** `org.springframework.boot` 3.2.4, `io.spring.dependency-management` 1.1.4, `com.gorylenko.gradle-git-properties` 2.4.2
- **Java:** Source compatibility set to Java 21
- **Test framework:** JUnit 5 (via `spring-boot-starter-test`)

### 6.2 Docker

Each service has its own `Dockerfile`:
- **Base image:** `eclipse-temurin:21.0.2_13-jre-alpine`
- **Pattern:** Copy pre-built JAR (`build/libs/*.jar`) as `app.jar`, include `wait-for-it.sh` for startup ordering
- **Profiles:** Containers start with `-Dspring.profiles.active=docker`

Support infrastructure:
- **MySQL:** Custom Dockerfile from `mysql:8.4.0` with init SQL
- **Keycloak:** Custom Dockerfile from `quay.io/keycloak/keycloak:23.0.7` with realm import

### 6.3 Docker Compose

Two compose files in `docker-compose/`:

1. **`docker-compose.yml`** — Full stack (all 6 services + MySQL + Keycloak + PostgreSQL + Zipkin)
2. **`docker-compose-support-apps.yml`** — Infrastructure only (Config Server, Service Registry, MySQL, Keycloak, PostgreSQL, Zipkin)

**Network:** Custom bridge network `javatodev_ib_network` (`172.25.0.0/16`) with static IPs per container.

**Startup ordering:** Services use `wait-for-it.sh` to wait for dependencies (Service Registry → Config Server → MySQL) before starting.

### 6.4 CI/CD

- **GitHub Actions:** Previously had workflows, but they were removed (commit `fa1c445`: "Remove GH Actions workflows (PAT scope compatibility)")
- **No active CI/CD pipeline** in the repository
- Only a `.github/FUNDING.yml` file remains

### 6.5 Configuration Repository

Centralized configuration is stored externally at:
`https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`

This repository contains environment-specific configuration (database URLs, Keycloak settings, Eureka config, Zipkin endpoints, etc.) that the Config Server serves to all services at startup.
