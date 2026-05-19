# Internet Banking Microservices — Application Knowledge Base

## Table of Contents

- [1. Architecture Overview](#1-architecture-overview)
  - [1.1 Services](#11-services)
  - [1.2 Communication Patterns](#12-communication-patterns)
  - [1.3 Infrastructure Components](#13-infrastructure-components)
  - [1.4 Network Topology](#14-network-topology)
- [2. Data Model Documentation](#2-data-model-documentation)
  - [2.1 banking_core_service Schema](#21-banking_core_service-schema)
  - [2.2 banking_core_fund_transfer_service Schema](#22-banking_core_fund_transfer_service-schema)
  - [2.3 banking_core_user_service Schema](#23-banking_core_user_service-schema)
  - [2.4 banking_core_utility_payment_service Schema](#24-banking_core_utility_payment_service-schema)
  - [2.5 Entity Relationship Summary](#25-entity-relationship-summary)
- [3. API Surface Map](#3-api-surface-map)
  - [3.1 API Gateway Routes](#31-api-gateway-routes)
  - [3.2 Core Banking Service](#32-core-banking-service-port-8092)
  - [3.3 Internet Banking User Service](#33-internet-banking-user-service-port-8083)
  - [3.4 Internet Banking Fund Transfer Service](#34-internet-banking-fund-transfer-service-port-8084)
  - [3.5 Internet Banking Utility Payment Service](#35-internet-banking-utility-payment-service-port-8085)
- [4. Key Business Logic Inventory](#4-key-business-logic-inventory)
  - [4.1 Fund Transfer Processing](#41-fund-transfer-processing)
  - [4.2 Utility Payment Processing](#42-utility-payment-processing)
  - [4.3 User Registration and Management](#43-user-registration-and-management)
  - [4.4 Known Business Logic Bugs](#44-known-business-logic-bugs)
- [5. Integration Points](#5-integration-points)
  - [5.1 Keycloak (Identity & Access Management)](#51-keycloak-identity--access-management)
  - [5.2 Spring Cloud Config Server](#52-spring-cloud-config-server)
  - [5.3 Netflix Eureka (Service Discovery)](#53-netflix-eureka-service-discovery)
  - [5.4 Zipkin (Distributed Tracing)](#54-zipkin-distributed-tracing)
  - [5.5 Database Connections](#55-database-connections)
  - [5.6 RabbitMQ (Not Implemented)](#56-rabbitmq-not-implemented)
- [6. Build and Deployment Pipeline Summary](#6-build-and-deployment-pipeline-summary)
  - [6.1 Build System](#61-build-system)
  - [6.2 Docker Configuration](#62-docker-configuration)
  - [6.3 Docker Compose Orchestration](#63-docker-compose-orchestration)
  - [6.4 CI/CD Pipeline](#64-cicd-pipeline)

---

## 1. Architecture Overview

### 1.1 Services

This is a **Java 21 / Spring Boot 3.2.4 / Spring Cloud 2023.0.0** internet banking platform composed of **6 microservices**:

| Service | Port | Role | Framework |
|---------|------|------|-----------|
| `internet-banking-api-gateway` | 8082 | Edge router, OAuth2/JWT validation via Keycloak | Spring Cloud Gateway (WebFlux) |
| `internet-banking-service-registry` | 8081 | Service discovery | Netflix Eureka Server |
| `internet-banking-config-server` | 8090 | Centralized configuration from Git repo | Spring Cloud Config Server |
| `core-banking-service` | 8092 | Core engine: users, accounts, transactions, ledger operations | Spring Boot Web (MVC) |
| `internet-banking-fund-transfer-service` | 8084 | Orchestrates account-to-account fund transfers | Spring Boot Web (MVC) |
| `internet-banking-utility-payment-service` | 8085 | Orchestrates utility bill payments | Spring Boot Web (MVC) |

> **Note:** The `internet-banking-user-service` (port 8083) handles user registration and profile management through Keycloak integration. It is the seventh deployable unit in the Docker Compose but is considered part of the core 6-service architecture.

### 1.2 Communication Patterns

- **Client → Gateway:** All external REST traffic enters through `internet-banking-api-gateway` (port 8082), which acts as the single edge interface.
- **Gateway → Downstream Services:** The gateway routes requests based on path prefix to the appropriate downstream service using Spring Cloud Gateway route definitions. It also injects an `X-Auth-Id` header with the authenticated user's principal name.
- **Service → Service (Synchronous REST via OpenFeign):**
  - `internet-banking-fund-transfer-service` → `core-banking-service` (via `BankingCoreFeignClient`)
  - `internet-banking-utility-payment-service` → `core-banking-service` (via `BankingCoreRestClient` using Feign)
  - `internet-banking-user-service` → `core-banking-service` (via `BankingCoreRestClient` using Feign)
- **Service Discovery:** All services register with Netflix Eureka (`internet-banking-service-registry`) and use Eureka-based service lookup for Feign client resolution.
- **Asynchronous Messaging:** RabbitMQ is mentioned in documentation and architecture diagrams but is **not implemented** in the codebase. No message producers or consumers exist.

### 1.3 Infrastructure Components

| Component | Technology | Version | Purpose |
|-----------|-----------|---------|---------|
| Relational Database | MySQL | 8.4 | Primary data store for all 4 application services |
| Identity Provider | Keycloak | 23.0.7 | OAuth2/OIDC identity and access management |
| Keycloak Database | PostgreSQL | 15 | Keycloak persistence backend |
| Distributed Tracing | Zipkin | 3 | Request tracing across services |
| Tracing Bridge | Micrometer Tracing + Brave | — | Propagates trace/span IDs to Zipkin |
| Schema Migration | Flyway | 10.12.0 | Version-controlled DB migrations (core-banking-service only) |
| Containerization | Docker | — | Each service has its own Dockerfile |
| Orchestration | Docker Compose | v3.6 | Multi-container deployment |

### 1.4 Network Topology

All services run on a custom Docker bridge network `javatodev_ib_network` (subnet `172.25.0.0/16`) with static IP assignments:

| Container | Static IP | Port |
|-----------|-----------|------|
| `openzipkin_server` | 172.25.0.12 | 9411 |
| `keycloak_web` | 172.25.0.11 | 8080 |
| `keycloak_postgre_db` | 172.25.0.10 | 5432 (closed) |
| `mysql_javatodev_app` | 172.25.0.9 | 3306 |
| `internet-banking-config-server` | 172.25.0.8 | 8090 |
| `internet-banking-service-registry` | 172.25.0.7 | 8081 |
| `internet-banking-api-gateway` | 172.25.0.6 | 8082 |
| `internet-banking-user-service` | 172.25.0.5 | 8083 |
| `internet-banking-fund-transfer-service` | 172.25.0.4 | 8084 |
| `internet-banking-utility-payment-service` | 172.25.0.3 | 8085 |
| `core-banking-service` | 172.25.0.2 | 8092 |

---

## 2. Data Model Documentation

The application uses **4 MySQL schemas** on a single MySQL 8.4 instance. The database user is `javatodev_development`, created via `docker-compose/mysql/privileges.sql`.

### 2.1 banking_core_service Schema

Managed by **Flyway migrations** in `core-banking-service/src/main/resources/db/migration/`.

#### `banking_core_user`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Unique identifier |
| `first_name` | VARCHAR(255) | — | User's first name |
| `last_name` | VARCHAR(255) | — | User's last name |
| `email` | VARCHAR(255) | — | Email address |
| `identification_number` | VARCHAR(255) | — | National Identity Card (NIC) number |

#### `banking_core_account`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Unique identifier |
| `number` | VARCHAR(255) | — | Account number (e.g., `100015003000`) |
| `type` | VARCHAR(255) | — | Account type enum: `SAVINGS_ACCOUNT` |
| `status` | VARCHAR(255) | — | Account status enum: `ACTIVE` |
| `available_balance` | DECIMAL(19,2) | — | Available balance for transactions |
| `actual_balance` | DECIMAL(19,2) | — | Actual/ledger balance |
| `user_id` | BIGINT | FK → `banking_core_user.id` | Owner of the account |

#### `banking_core_transaction`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Unique identifier |
| `amount` | DECIMAL(19,2) | — | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | NOT NULL | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | NOT NULL | Reference (target account or bill ref) |
| `transaction_id` | VARCHAR(50) | NOT NULL | UUID transaction identifier |
| `account_id` | BIGINT | FK → `banking_core_account.id` | Associated account |

#### `banking_core_utility_account`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Unique identifier |
| `number` | VARCHAR(255) | — | Utility provider account number |
| `provider_name` | VARCHAR(255) | — | Provider name (e.g., `VODAFONE`, `AIRTEL`) |

### 2.2 banking_core_fund_transfer_service Schema

No Flyway migrations; schema managed by JPA `ddl-auto` (via Spring Cloud Config).

#### `fund_transfer`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Unique identifier |
| `transaction_reference` | VARCHAR(255) | — | UUID from core-banking response |
| `from_account` | VARCHAR(255) | — | Source account number |
| `to_account` | VARCHAR(255) | — | Destination account number |
| `amount` | DECIMAL(19,2) | — | Transfer amount |
| `status` | VARCHAR(255) | — | Enum: `PENDING`, `SUCCESS` |
| `created_date` | TIMESTAMP | — | Audit: creation timestamp |
| `created_by` | VARCHAR(255) | — | Audit: creator (from X-Auth-Id) |
| `modified_date` | TIMESTAMP | — | Audit: last modification timestamp |
| `modified_by` | VARCHAR(255) | — | Audit: last modifier |
| `version` | BIGINT | — | Optimistic locking version |

### 2.3 banking_core_user_service Schema

No Flyway migrations; schema managed by JPA `ddl-auto`.

#### `user`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Unique identifier |
| `auth_id` | VARCHAR(255) | — | Keycloak user UUID |
| `identification` | VARCHAR(255) | — | NIC number linking to core-banking user |
| `status` | VARCHAR(255) | — | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | TIMESTAMP | — | Audit: creation timestamp |
| `created_by` | VARCHAR(255) | — | Audit: creator |
| `modified_date` | TIMESTAMP | — | Audit: last modification timestamp |
| `modified_by` | VARCHAR(255) | — | Audit: last modifier |
| `version` | BIGINT | — | Optimistic locking version |

### 2.4 banking_core_utility_payment_service Schema

No Flyway migrations; schema managed by JPA `ddl-auto`.

#### `utility_payment`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Unique identifier |
| `provider_id` | BIGINT | — | Utility provider ID |
| `amount` | DECIMAL(19,2) | — | Payment amount |
| `reference_number` | VARCHAR(255) | — | Bill reference number |
| `account` | VARCHAR(255) | — | Source bank account number |
| `transaction_id` | VARCHAR(255) | — | UUID from core-banking response |
| `status` | VARCHAR(255) | — | Enum: `PROCESSING`, `SUCCESS` |
| `created_date` | TIMESTAMP | — | Audit: creation timestamp |
| `created_by` | VARCHAR(255) | — | Audit: creator |
| `modified_date` | TIMESTAMP | — | Audit: last modification timestamp |
| `modified_by` | VARCHAR(255) | — | Audit: last modifier |
| `version` | BIGINT | — | Optimistic locking version |

### 2.5 Entity Relationship Summary

```
banking_core_user (1) ──< banking_core_account (many)
banking_core_account (1) ──< banking_core_transaction (many)
banking_core_utility_account (standalone - utility providers)

fund_transfer (standalone - references account numbers as strings)
user (standalone - links to Keycloak via authId, to core-banking via identification)
utility_payment (standalone - references account numbers and provider IDs as strings)
```

> **Note:** The fund-transfer, user, and utility-payment service schemas use **string-based references** (account numbers, identification numbers) rather than foreign keys to the core-banking schema. This is consistent with a microservices pattern where each service owns its own database, but it means referential integrity is not enforced at the database level across services.

---

## 3. API Surface Map

### 3.1 API Gateway Routes

The gateway (`internet-banking-api-gateway`) routes requests based on path prefix:

| Gateway Path Prefix | Target Service | Security |
|---------------------|---------------|----------|
| `/user/**` | `internet-banking-user-service` | JWT required (except `/user/api/v1/bank-users/register`) |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` | JWT required |
| `/payment/**` | `internet-banking-utility-payment-service` | JWT required |
| `/core/**` | `core-banking-service` | JWT required |
| `/actuator/**` | (all services) | Permit all |

### 3.2 Core Banking Service (Port 8092)

#### Account Controller (`/api/v1/account`)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| GET | `/api/v1/account/bank-account/{account_number}` | Get bank account by account number | Path: `account_number` (String) | `BankAccount { number, type, status, availableBalance, actualBalance }` |
| GET | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | Path: `account_name` (String) | `UtilityAccount { id, number, providerName }` |

#### Transaction Controller (`/api/v1/transaction`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| POST | `/api/v1/transaction/fund-transfer` | Process fund transfer | `{ fromAccount: String, toAccount: String, amount: BigDecimal }` | `{ message: String, transactionId: String }` |
| POST | `/api/v1/transaction/util-payment` | Process utility payment | `{ providerId: Long, amount: BigDecimal, referenceNumber: String, account: String }` | `{ message: String, transactionId: String }` |

#### User Controller (`/api/v1/user`)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| GET | `/api/v1/user/{identification}` | Get user by NIC identification number | Path: `identification` (String) | `User { id, firstName, lastName, email, identificationNumber }` |
| GET | `/api/v1/user` | List all users (paginated) | Query: `page`, `size`, `sort` (Pageable) | `List<User>` |

### 3.3 Internet Banking User Service (Port 8083)

#### User Controller (`/api/v1/bank-users`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| POST | `/api/v1/bank-users/register` | Register new user (**PUBLIC**) | `{ email: String, identification: String, password: String }` | `User { id, email, identification, authId, status }` |
| PATCH | `/api/v1/bank-users/update/{id}` | Update user status | `{ status: Enum[PENDING, APPROVED, DISABLED, BLACKLIST] }` | `User` |
| GET | `/api/v1/bank-users` | List all users (paginated) | Query: Pageable | `List<User>` |
| GET | `/api/v1/bank-users/{id}` | Get user by ID | Path: `id` (Long) | `User` |

### 3.4 Internet Banking Fund Transfer Service (Port 8084)

#### Fund Transfer Controller (`/api/v1/transfer`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| POST | `/api/v1/transfer` | Initiate fund transfer | `{ fromAccount: String, toAccount: String, amount: BigDecimal }` | `{ message: String, transactionId: String }` |
| GET | `/api/v1/transfer` | List all fund transfers (paginated) | Query: Pageable | `List<FundTransfer>` |

### 3.5 Internet Banking Utility Payment Service (Port 8085)

#### Utility Payment Controller (`/api/v1/utility-payment`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| POST | `/api/v1/utility-payment` | Process utility payment | `{ providerId: Long, amount: BigDecimal, referenceNumber: String, account: String }` | `{ message: String, transactionId: String }` |
| GET | `/api/v1/utility-payment` | List all utility payments (paginated) | Query: Pageable | `List<UtilityPayment>` |

---

## 4. Key Business Logic Inventory

### 4.1 Fund Transfer Processing

**Flow:** `FundTransferController` → `FundTransferService` → `BankingCoreFeignClient` → `core-banking-service TransactionService`

1. **FundTransferService.fundTransfer():**
   - Creates a `FundTransferEntity` with status `PENDING` and saves to local DB
   - Makes a synchronous Feign call to `core-banking-service` at `POST /api/v1/transaction/fund-transfer`
   - On success: updates entity to `SUCCESS`, sets `transactionReference` from response
   - **No rollback on Feign failure** — entity stays `PENDING` forever

2. **TransactionService.fundTransfer() (core-banking):**
   - Looks up both source and destination `BankAccount` by account number
   - Validates source account has sufficient balance (`actualBalance >= amount`)
   - Calls `internalFundTransfer()`:
     - Debits source: `actualBalance -= amount`, `availableBalance = actualBalance - amount` (**BUG: double deduction**)
     - Credits destination: `actualBalance += amount`, `availableBalance = actualBalance + amount` (**BUG: double addition**)
     - Creates two `TransactionEntity` records (debit and credit)
   - Returns `transactionId` (UUID)

### 4.2 Utility Payment Processing

**Flow:** `UtilityPaymentController` → `UtilityPaymentService` → `BankingCoreRestClient` → `core-banking-service TransactionService`

1. **UtilityPaymentService.utilPayment():**
   - Creates a `UtilityPaymentEntity` with status `PROCESSING` and saves to local DB
   - Makes a synchronous Feign call to `core-banking-service` at `POST /api/v1/transaction/util-payment`
   - On success: updates entity to `SUCCESS`, sets `transactionId` from response
   - **No rollback on Feign failure** — entity stays `PROCESSING` forever

2. **TransactionService.utilPayment() (core-banking):**
   - Looks up source `BankAccount` and validates sufficient balance
   - Looks up `UtilityAccount` by `providerId`
   - Debits source: `actualBalance -= amount`, `availableBalance = actualBalance - amount` (**BUG: double deduction**)
   - Creates one `TransactionEntity` record
   - Returns `transactionId` (UUID)

### 4.3 User Registration and Management

**Flow:** `UserController` → `UserService` → `KeycloakUserService` + `BankingCoreRestClient`

1. **Registration (`createUser`):**
   - Checks if email already exists in Keycloak
   - Looks up user by NIC identification in `core-banking-service`
   - Validates email matches core-banking record
   - Creates Keycloak user with temporary password, email unverified, account disabled
   - On 201 response: looks up newly created Keycloak user to get `authId`
   - Saves `UserEntity` with status `PENDING` to local DB

2. **Approval (`updateUser`):**
   - Admin sends `PATCH` with `status: APPROVED`
   - If approving: enables Keycloak user and marks email as verified
   - Updates local `UserEntity` status

3. **User Listing (`readUsers`):**
   - Reads users from local DB (paginated)
   - For each user, makes a Keycloak API call to enrich with email — **N+1 query problem**

### 4.4 Known Business Logic Bugs

1. **Double-deduction bug in `TransactionService.internalFundTransfer()`:**
   - Line: `fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount))`
   - `actualBalance` has already been decremented, so subtracting `amount` again from it causes a double subtraction on `availableBalance`
   - Same bug exists in `utilPayment()` method
   - Same bug affects the credit side: `toBankAccountEntity.setAvailableBalance(toBankAccountEntity.getActualBalance().add(amount))` — double addition

2. **No compensation/rollback on Feign failure:**
   - Fund transfers stuck in `PENDING` and utility payments stuck in `PROCESSING` indefinitely if the core-banking call fails

3. **No idempotency protection:**
   - Duplicate POST requests to `/api/v1/transfer` or `/api/v1/utility-payment` will process the same transaction multiple times

---

## 5. Integration Points

### 5.1 Keycloak (Identity & Access Management)

- **Version:** 23.0.7
- **Port:** 8080 (Docker static IP: 172.25.0.11)
- **Database:** PostgreSQL 15 (172.25.0.10)
- **Realm:** `javatodev-internet-banking` (imported from `docker-compose/keycloak/realm-export.json`)
- **Client:** `internet-banking-api-client` (confidential client, client-credentials grant)
- **Admin credentials:** `admin` / `password`
- **Integration points:**
  - **API Gateway:** Validates JWT tokens using JWK Set URI from Keycloak
  - **User Service:** Uses Keycloak Admin Client API to create/update/query users
  - **Configuration:** Keycloak server URL, realm, clientId, and client-secret are externalized via Spring Cloud Config (`app.config.keycloak.*`)

### 5.2 Spring Cloud Config Server

- **Port:** 8090
- **Git repository:** `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Search path:** `configuration/`
- **Branch:** `main`
- **Profiles:** `dev` (local development, uses `192.168.1.5:8090`), `docker` (uses container name `internet-banking-config-server:8090`)
- **Bootstrap:** Each service has `bootstrap.yml`, `bootstrap-dev.yml`, and `bootstrap-docker.yml` pointing to the config server

### 5.3 Netflix Eureka (Service Discovery)

- **Port:** 8081 (Docker static IP: 172.25.0.7)
- **All application services** register as Eureka clients
- **Feign clients** use Eureka service names (e.g., `core-banking-service`) for discovery-based routing
- **Configuration:** `eureka.client.service-url.defaultZone` points to `http://localhost:8081/eureka` (overridden in Docker profile)

### 5.4 Zipkin (Distributed Tracing)

- **Version:** 3
- **Port:** 9411 (Docker static IP: 172.25.0.12)
- **Integration:** All services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` dependencies
- **Coverage:** Trace propagation is set up for HTTP requests and Feign calls via `feign-micrometer`

### 5.5 Database Connections

- **MySQL 8.4** (Docker static IP: 172.25.0.9, port 3306)
  - **Root password:** Hardcoded in `docker-compose.yml`
  - **Application user:** `javatodev_development` (created via `privileges.sql`)
  - **Schemas created by init script:**
    - `banking_core_service` — Used by `core-banking-service`
    - `banking_core_fund_transfer_service` — Used by `internet-banking-fund-transfer-service`
    - `banking_core_user_service` — Used by `internet-banking-user-service`
    - `banking_core_utility_payment_service` — Used by `internet-banking-utility-payment-service`
  - **Connection config:** Externalized via Spring Cloud Config Server (datasource URL, credentials)

### 5.6 RabbitMQ (Not Implemented)

- Listed in README and architecture diagrams as a messaging component for notifications
- **No RabbitMQ dependency** in any `build.gradle`
- **No message producers or consumers** in the codebase
- The planned `Notification service` is marked as "PENDING Development" in the README

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build tool:** Gradle 8.6 (per-service Gradle Wrapper — no root-level build file)
- **Java version:** 21 (source compatibility)
- **Each service** is independently built: `cd <service> && ./gradlew clean build`
- **No multi-module Gradle build** — each service has its own `build.gradle`, `gradlew`, `gradle/wrapper/`
- **Dependencies:** Managed via Spring Boot 3.2.4 BOM and Spring Cloud 2023.0.0 BOM
- **Git properties plugin:** `com.gorylenko.gradle-git-properties` v2.4.2 (generates `git.properties` at build time)

### 6.2 Docker Configuration

Each service has its own `Dockerfile` based on `eclipse-temurin:21.0.2_13-jre-alpine`:

```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
EXPOSE <port>
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh
RUN apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

- `wait-for-it.sh` is copied into each image for startup dependency ordering

### 6.3 Docker Compose Orchestration

Two compose files in `docker-compose/`:

1. **`docker-compose-support-apps.yml`:** Infrastructure only (MySQL, Keycloak, PostgreSQL, Zipkin, Config Server, Service Registry)
2. **`docker-compose.yml`:** Full stack (all infrastructure + all application services)

**Startup ordering** uses `wait-for-it.sh` in entrypoint commands with 50-second timeouts:
- Application services wait for: Service Registry (8081) → Config Server (8090) → MySQL (3306)

**MySQL initialization:** Custom `Dockerfile` in `docker-compose/mysql/` that runs `privileges.sql` on first startup to create the application database user and schemas.

**Keycloak initialization:** Realm configuration is imported from `docker-compose/keycloak/realm-export.json` on startup via `--import-realm` flag.

### 6.4 CI/CD Pipeline

**No CI/CD pipeline** is configured in the repository. There are no GitHub Actions workflows, Jenkinsfiles, or other pipeline definitions.

**Test data:** Flyway migration `V1.0.20210427174721__temp_data.sql` seeds the core-banking database with:
- 4 test users with NIC numbers
- 14 savings accounts with pre-set balances
- 6 utility provider accounts (VODAFONE, VERIZON, SINGTEL, HUTCH, AIRTEL, GIO)

**Test credentials:** `ib_admin@javatodev.com` / `5V7huE3G86uB`

**Postman collection:** Available in `postman_collection/` directory for API testing, with `LOCAL_DOCKER_SETUP` environment preset.
