# Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Summary

This is a **Java 21 / Spring Boot 3.2.4 / Spring Cloud 2023.0.0** internet banking application composed of **6 microservices** that communicate via synchronous REST (OpenFeign) through a centralized API Gateway. The system uses Netflix Eureka for service discovery, Spring Cloud Config for centralized configuration, and Keycloak for OAuth 2.0 authentication.

### 1.2 Microservices

| Service | Port | Role |
|---|---|---|
| **internet-banking-config-server** | 8090 | Centralized configuration server; serves config from a remote Git repository |
| **internet-banking-service-registry** | 8081 | Netflix Eureka server for service discovery |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway; single entry point, OAuth2 enforcement, header injection |
| **internet-banking-user-service** | 8083 | User registration, approval workflow, Keycloak integration |
| **internet-banking-fund-transfer-service** | 8084 | Fund transfer orchestration; delegates to core-banking-service via Feign |
| **internet-banking-utility-payment-service** | 8085 | Utility payment orchestration; delegates to core-banking-service via Feign |
| **core-banking-service** | 8092 | Core banking engine: accounts, users, transactions, balance management |

### 1.3 Communication Patterns

```
Client
  │
  ▼
API Gateway (8082)  ──OAuth2/JWT──▶  Keycloak (8080)
  │
  ├──▶ User Service (8083)        ──Feign──▶ Core Banking Service (8092)
  │                                ──REST──▶ Keycloak Admin API
  │
  ├──▶ Fund Transfer Service (8084) ──Feign──▶ Core Banking Service (8092)
  │
  └──▶ Utility Payment Service (8085) ──Feign──▶ Core Banking Service (8092)
```

- **Synchronous REST only** — All inter-service communication uses OpenFeign clients over HTTP.
- **API Gateway** injects an `X-Auth-Id` header (authenticated principal name) into all downstream requests.
- Each downstream service extracts `X-Auth-Id` via `AppAuthUserFilter` and stores it in a `ThreadLocal`-based `ApiRequestContextHolder`.
- **RabbitMQ** is listed in the technology stack and README but is **not implemented** in the current codebase. A Notification Service is marked as "PENDING Development."

### 1.4 Infrastructure Components

| Component | Purpose | Version / Image |
|---|---|---|
| **MySQL** | Primary database for all business services | Custom Dockerfile (mysql base) |
| **PostgreSQL** | Keycloak identity store | postgres:15 |
| **Keycloak** | OAuth 2.0 / OpenID Connect identity provider | quay.io/keycloak/keycloak:23.0.7 |
| **Zipkin** | Distributed tracing collector | openzipkin/zipkin:3 |
| **Netflix Eureka** | Service discovery registry | Embedded (Spring Cloud) |
| **Spring Cloud Config** | Centralized configuration | Embedded; backed by Git repo |

### 1.5 Network Topology (Docker Compose)

All containers run on a custom bridge network `javatodev_ib_network` (subnet `172.25.0.0/16`) with static IP assignments. Services use `wait-for-it.sh` scripts to ensure startup ordering: Config Server and Service Registry must be available before business services start.

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL: `banking_core_service`)

#### `banking_core_user`
| Column | Type | Constraint |
|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `first_name` | VARCHAR(255) | |
| `last_name` | VARCHAR(255) | |
| `email` | VARCHAR(255) | |
| `identification_number` | VARCHAR(255) | National ID / NIC |

#### `banking_core_account`
| Column | Type | Constraint |
|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `number` | VARCHAR(255) | Account number |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `ACTIVE` |
| `actual_balance` | DECIMAL(19,2) | |
| `available_balance` | DECIMAL(19,2) | |
| `user_id` | BIGINT | FK → `banking_core_user.id` |

#### `banking_core_utility_account`
| Column | Type | Constraint |
|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `number` | VARCHAR(255) | Provider account number |
| `provider_name` | VARCHAR(255) | e.g., VODAFONE, AIRTEL |

#### `banking_core_transaction`
| Column | Type | Constraint |
|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `amount` | DECIMAL(19,2) | Signed (+credit / -debit) |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Target account number or reference |
| `transaction_id` | VARCHAR(50) | UUID |
| `account_id` | BIGINT | FK → `banking_core_account.id` |

**Relationships:**
- `UserEntity` 1:N `BankAccountEntity` (bidirectional, `@OneToMany`/`@ManyToOne`)
- `TransactionEntity` 1:1 `BankAccountEntity` (`@OneToOne`, CASCADE ALL)

### 2.2 User Service (MySQL: `banking_core_user_service`)

#### `user` (JPA-managed, extends `AuditAware`)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `auth_id` | VARCHAR | Keycloak user ID |
| `identification` | VARCHAR | NIC / national ID |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | TIMESTAMP | Audit field |
| `created_by` | VARCHAR | Audit field |
| `modified_date` | TIMESTAMP | Audit field |
| `modified_by` | VARCHAR | Audit field |
| `version` | BIGINT | Optimistic locking |

### 2.3 Fund Transfer Service (MySQL: `banking_core_fund_transfer_service`)

#### `fund_transfer` (extends `AuditAware`)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `transaction_reference` | VARCHAR | UUID from core banking |
| `status` | VARCHAR | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| Audit fields | | `created_date`, `created_by`, `modified_date`, `modified_by`, `version` |

### 2.4 Utility Payment Service (MySQL: `banking_core_utility_payment_service`)

#### `utility_payment` (extends `AuditAware`)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Customer reference |
| `account` | VARCHAR | Source bank account number |
| `transaction_id` | VARCHAR | UUID from core banking |
| `status` | VARCHAR | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| Audit fields | | `created_date`, `created_by`, `modified_date`, `modified_by`, `version` |

### 2.5 Database Migrations

Only the **core-banking-service** uses Flyway migrations:
- `V1.0.20210427174638` — Creates `banking_core_user`, `banking_core_account`, `banking_core_utility_account`
- `V1.0.20210427174721` — Inserts seed data (4 users, 14 accounts, 6 utility providers)
- `V1.0.20210429210839` — Creates `banking_core_transaction`

Other services rely on JPA `ddl-auto` (likely `update` via centralized config) for schema management.

---

## 3. API Surface Map

### 3.1 API Gateway Routes

The API Gateway routes requests using Eureka-resolved service names. Based on the security configuration path matchers:

| Gateway Prefix | Target Service |
|---|---|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |
| `/banking-core/**` | core-banking-service |

### 3.2 Core Banking Service (`/api/v1/...`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | — | `BankAccount` (number, type, status, availableBalance, actualBalance) |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | — | `UtilityAccount` (id, number, providerName) |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer | `FundTransferRequest` (fromAccount, toAccount, amount) | `FundTransferResponse` (message, transactionId) |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `UtilityPaymentRequest` (account, providerId, amount, referenceNumber) | `UtilityPaymentResponse` (message, transactionId) |
| `GET` | `/api/v1/user/{identification}` | Get user by NIC | — | `User` (id, firstName, lastName, email, identificationNumber, accounts[]) |
| `GET` | `/api/v1/user` | List users (paginated) | `Pageable` query params | `List<User>` |

### 3.3 User Service (`/api/v1/bank-users/...`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Register new user (public) | `User` (email, identification, password) | `User` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user status | `UserUpdateRequest` (status) | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | `Pageable` query params | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | — | `User` |

### 3.4 Fund Transfer Service (`/api/v1/transfer/...`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `FundTransferRequest` (fromAccount, toAccount, amount) | `FundTransferResponse` (message, transactionId) |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | `Pageable` query params | `List<FundTransfer>` |

### 3.5 Utility Payment Service (`/api/v1/utility-payment/...`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `UtilityPaymentRequest` (providerId, amount, referenceNumber, account) | `UtilityPaymentResponse` (message, transactionId) |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | `Pageable` query params | `List<UtilityPayment>` |

### 3.6 Infrastructure Endpoints (All Services)

| Endpoint | Description |
|---|---|
| `/actuator/**` | Spring Boot Actuator (health, info, metrics, etc.) |

---

## 4. Key Business Logic Inventory

### 4.1 Fund Transfer Rules (`TransactionService.fundTransfer`)

1. Look up source (`fromAccount`) and destination (`toAccount`) bank accounts via `AccountService`
2. **Balance validation**: Source account's `actualBalance` must be ≥ transfer `amount` and > 0; otherwise throws `InsufficientFundsException`
3. Debit source account: `actualBalance -= amount`, `availableBalance = actualBalance - amount` (note: potential double-subtraction bug)
4. Create debit `TransactionEntity` (negative amount) for source account
5. Credit destination account: `actualBalance += amount`, `availableBalance = actualBalance + amount`
6. Create credit `TransactionEntity` (positive amount) for destination account
7. Return UUID `transactionId`

**Orchestration (Fund Transfer Service):**
1. Save `FundTransferEntity` with status `PENDING`
2. Call core banking `/api/v1/transaction/fund-transfer` via Feign
3. Update entity with `transactionReference` and status `SUCCESS`
4. Return response (no error/rollback handling if Feign call fails)

### 4.2 Utility Payment Processing (`TransactionService.utilPayment`)

1. Look up source bank account
2. **Balance validation**: Same rules as fund transfer
3. Look up utility provider account by `providerId`
4. Debit source account (same double-subtraction pattern)
5. Create `TransactionEntity` with type `UTILITY_PAYMENT`
6. Return UUID `transactionId`

**Orchestration (Utility Payment Service):**
1. Save `UtilityPaymentEntity` with status `PROCESSING`
2. Call core banking `/api/v1/transaction/util-payment` via Feign
3. Update entity with `transactionId` and status `SUCCESS`
4. Return response

### 4.3 User Management (`UserService.createUser`)

1. Check if email is already registered in Keycloak; throw `UserAlreadyRegisteredException` if exists
2. Verify user exists in core banking by `identification` (NIC); throw `InvalidBankingUserException` if not found
3. Validate email matches core banking record; throw `InvalidEmailException` if mismatch
4. Create user in Keycloak (disabled, email unverified) with provided password
5. Re-read Keycloak user to get `authId`
6. Save user locally with status `PENDING`

### 4.4 User Approval (`UserService.updateUser`)

1. Look up user entity by ID
2. If new status is `APPROVED`, enable user in Keycloak and mark email as verified
3. Update local user entity status

### 4.5 Enums

| Enum | Values | Used In |
|---|---|---|
| `AccountType` | `SAVINGS_ACCOUNT` | Core Banking |
| `AccountStatus` | `ACTIVE` | Core Banking |
| `TransactionType` | `FUND_TRANSFER`, `UTILITY_PAYMENT` | Core Banking |
| `TransactionStatus` | `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` | Fund Transfer, Utility Payment |
| `Status` | `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` | User Service |

---

## 5. Integration Points

### 5.1 Keycloak (OAuth 2.0 / User Management)

- **API Gateway**: Acts as an OAuth 2.0 Resource Server; validates JWTs against Keycloak's JWK Set URI
- **User Service**: Uses Keycloak Admin Client (`keycloak-admin-client:24.0.4`) for:
  - User creation (`client_credentials` grant)
  - User search by email
  - User enable/disable on approval
- **Configuration**: `app.config.keycloak.server-url`, `realm`, `clientId`, `client-secret` (via Spring Cloud Config)
- **Docker**: Keycloak 23.0.7 with realm import from `docker-compose/keycloak/`

### 5.2 RabbitMQ

- **Status**: Referenced in README and architecture diagram but **not implemented** in code
- **Planned use**: Notification service would consume messages from fund transfer and utility payment services

### 5.3 Zipkin (Distributed Tracing)

- All business services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave`
- Traces are exported to Zipkin server at port 9411
- Feign calls are instrumented via `feign-micrometer`

### 5.4 Database Connections

| Service | Database | Schema |
|---|---|---|
| core-banking-service | MySQL | `banking_core_service` |
| internet-banking-user-service | MySQL | `banking_core_user_service` |
| internet-banking-fund-transfer-service | MySQL | `banking_core_fund_transfer_service` |
| internet-banking-utility-payment-service | MySQL | `banking_core_utility_payment_service` |
| Keycloak | PostgreSQL | `keycloak` |

All services share a single MySQL instance with separate databases (created via `docker-compose/mysql/privileges.sql`). The MySQL user `javatodev_development` has full privileges across all databases.

### 5.5 Spring Cloud Config

- Config Server reads from: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` (branch: `main`, path: `configuration/`)
- All business services bootstrap from Config Server at port 8090
- Docker profile (`-Dspring.profiles.active=docker`) used in containers

### 5.6 OpenFeign Inter-Service Calls

| Caller | Target | Feign Client | Endpoints Called |
|---|---|---|---|
| User Service | Core Banking | `BankingCoreRestClient` | `GET /api/v1/user/{identification}` |
| Fund Transfer Service | Core Banking | `BankingCoreFeignClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/fund-transfer` |
| Utility Payment Service | Core Banking | `BankingCoreRestClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/util-payment` |

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Gradle** (per-service `build.gradle`, no multi-project root build)
- Each service is a standalone Gradle project with `settings.gradle`
- Spring Boot plugin `3.2.4`, Spring Dependency Management `1.1.4`
- Java source compatibility: **21**
- Test framework: JUnit 5 (`useJUnitPlatform()`)

### 6.2 Docker

Each service has a `Dockerfile`:
- Base image: `eclipse-temurin:21.0.2_13-jre-alpine`
- Copies built JAR as `app.jar`
- Includes `wait-for-it.sh` for startup ordering
- Entrypoint activates `docker` Spring profile

### 6.3 Docker Compose

Two compose files in `docker-compose/`:

| File | Purpose |
|---|---|
| `docker-compose.yml` | Full stack (all 6 services + infrastructure) |
| `docker-compose-support-apps.yml` | Infrastructure only (Zipkin, Keycloak, MySQL, Config Server, Service Registry) for local development |

Custom Docker builds:
- `docker-compose/mysql/` — MySQL with initialization SQL (`privileges.sql`)
- `docker-compose/keycloak/` — Keycloak with realm import data

### 6.4 CI/CD

- **No CI/CD pipeline** defined in the repository (`.github/` contains only `FUNDING.yml`)
- No GitHub Actions workflows, Jenkinsfiles, or similar pipeline definitions
- Deployment is manual via `docker-compose up -d`

### 6.5 Postman Collection

A Postman collection and environment file are provided in `postman_collection/`:
- `JAVA_TO_DEV_MICROSERVICES.postman_collection.json`
- `BANKING_CORE_MICROSERVICES_PROJECT.postman_environment.json`
- Environment: `LOCAL_DOCKER_SETUP`
- Test credentials: `ib_admin@javatodev.com / 5V7huE3G86uB`
