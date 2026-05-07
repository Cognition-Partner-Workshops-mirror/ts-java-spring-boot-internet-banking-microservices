# Application Knowledge Base

## Table of Contents

- [1. Architecture Overview](#1-architecture-overview)
  - [1.1 Services](#11-services)
  - [1.2 Communication Patterns](#12-communication-patterns)
  - [1.3 Infrastructure Components](#13-infrastructure-components)
  - [1.4 Network Topology](#14-network-topology)
- [2. Data Model Documentation](#2-data-model-documentation)
  - [2.1 Core Banking Service](#21-core-banking-service)
  - [2.2 Internet Banking User Service](#22-internet-banking-user-service)
  - [2.3 Internet Banking Fund Transfer Service](#23-internet-banking-fund-transfer-service)
  - [2.4 Internet Banking Utility Payment Service](#24-internet-banking-utility-payment-service)
- [3. API Surface Map](#3-api-surface-map)
  - [3.1 API Gateway Routes](#31-api-gateway-routes)
  - [3.2 Core Banking Service Endpoints](#32-core-banking-service-endpoints)
  - [3.3 User Service Endpoints](#33-user-service-endpoints)
  - [3.4 Fund Transfer Service Endpoints](#34-fund-transfer-service-endpoints)
  - [3.5 Utility Payment Service Endpoints](#35-utility-payment-service-endpoints)
  - [3.6 Inter-Service Feign Client Calls](#36-inter-service-feign-client-calls)
- [4. Key Business Logic Inventory](#4-key-business-logic-inventory)
  - [4.1 Fund Transfer Rules](#41-fund-transfer-rules)
  - [4.2 Utility Payment Processing](#42-utility-payment-processing)
  - [4.3 User Management](#43-user-management)
- [5. Integration Points](#5-integration-points)
  - [5.1 Keycloak](#51-keycloak)
  - [5.2 RabbitMQ](#52-rabbitmq)
  - [5.3 Zipkin](#53-zipkin)
  - [5.4 Database Connections](#54-database-connections)
  - [5.5 Spring Cloud Config Server](#55-spring-cloud-config-server)
  - [5.6 Netflix Eureka Service Registry](#56-netflix-eureka-service-registry)
- [6. Build and Deployment Pipeline Summary](#6-build-and-deployment-pipeline-summary)
  - [6.1 Build System (Gradle)](#61-build-system-gradle)
  - [6.2 Docker Compose](#62-docker-compose)
  - [6.3 Startup Ordering](#63-startup-ordering)

---

## 1. Architecture Overview

### 1.1 Services

This application consists of **6 microservices** built with Java 21 and Spring Boot 3.2.4 (Spring Cloud 2023.0.0):

| # | Service | Port | Purpose |
|---|---------|------|---------|
| 1 | **core-banking-service** | 8092 | System of record for users, bank accounts, utility accounts, and transaction ledger. Acts as the "core bank" backend. |
| 2 | **internet-banking-user-service** | 8083 | Internet banking user registration, approval workflow, and Keycloak identity management. |
| 3 | **internet-banking-fund-transfer-service** | 8084 | Orchestrates account-to-account fund transfers by delegating to the core banking service. |
| 4 | **internet-banking-utility-payment-service** | 8085 | Orchestrates utility bill payments (electricity, water, telecom) by delegating to the core banking service. |
| 5 | **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway; single entry point for all client traffic; OAuth2/JWT enforcement via Keycloak; injects `X-Auth-Id` header. |
| 6 | **internet-banking-service-registry** | 8081 | Netflix Eureka server for dynamic service discovery. |

Additionally, the **internet-banking-config-server** (port 8090) provides centralized configuration sourced from a Git repository (`internet-banking-microservices-configurations`).

### 1.2 Communication Patterns

```
                  ┌─────────────────────┐
   Client ──────► │   API Gateway       │ (OAuth2 JWT validation)
                  │   :8082             │
                  └────┬───┬───┬────────┘
                       │   │   │
        ┌──────────────┘   │   └──────────────┐
        ▼                  ▼                   ▼
 ┌──────────────┐  ┌──────────────┐  ┌────────────────┐
 │ User Service │  │ Fund Transfer│  │ Utility Payment│
 │ :8083        │  │ :8084        │  │ :8085          │
 └──────┬───────┘  └──────┬───────┘  └──────┬─────────┘
        │  OpenFeign       │  OpenFeign      │  OpenFeign
        ▼                  ▼                 ▼
 ┌─────────────────────────────────────────────────────┐
 │              Core Banking Service :8092              │
 │         (Accounts, Users, Transactions)             │
 └─────────────────────────────────────────────────────┘
```

- **Client → API Gateway**: HTTP/REST over the gateway. JWT bearer token required for all endpoints except `/user/api/v1/bank-users/register` and `/actuator/**`.
- **Service-to-Service**: Synchronous REST via **Spring Cloud OpenFeign** clients, with service discovery through Eureka. Each downstream service calls `core-banking-service` by its Eureka-registered name.
- **Authentication Header Propagation**: The API Gateway extracts the authenticated principal name and injects it as `X-Auth-Id` HTTP header. Downstream services read this header via `AppAuthUserFilter` and store it in a `ThreadLocal` (`ApiRequestContextHolder`) for audit purposes.
- **Message Queue** (RabbitMQ): Referenced in the README as planned for notification messages from fund-transfer and utility-payment services. **Not yet implemented** in the current codebase.

### 1.3 Infrastructure Components

| Component | Image/Technology | Purpose |
|-----------|-----------------|---------|
| MySQL 8.4.0 | Custom Dockerfile (`docker-compose/mysql/`) | Persistent storage for all 4 business services (4 databases) |
| PostgreSQL 15 | `postgres:15` | Keycloak identity data store |
| Keycloak 23.0.7 | `quay.io/keycloak/keycloak:23.0.7` | OAuth2/OpenID Connect identity provider |
| Zipkin 3 | `openzipkin/zipkin:3` | Distributed tracing collector and UI |
| Spring Cloud Config Server | Custom Spring Boot app | Centralized externalized configuration from Git |
| Netflix Eureka | Embedded in `service-registry` | Service discovery |

### 1.4 Network Topology

All services run on a dedicated Docker bridge network (`javatodev_ib_network`, subnet `172.25.0.0/16`) with **fixed IP assignments**:

| IP Address | Service |
|------------|---------|
| 172.25.0.2 | core-banking-service |
| 172.25.0.3 | internet-banking-utility-payment-service |
| 172.25.0.4 | internet-banking-fund-transfer-service |
| 172.25.0.5 | internet-banking-user-service |
| 172.25.0.6 | internet-banking-api-gateway |
| 172.25.0.7 | internet-banking-service-registry |
| 172.25.0.8 | internet-banking-config-server |
| 172.25.0.9 | mysql_javatodev_app |
| 172.25.0.10 | keycloak_postgre_db |
| 172.25.0.11 | keycloak_web |
| 172.25.0.12 | openzipkin_server |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service

**Database**: `banking_core_service` (MySQL)
**Schema managed by**: Flyway migrations (`src/main/resources/db/migration/`)

#### `banking_core_user`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal user ID |
| `first_name` | VARCHAR(255) | | User's first name |
| `last_name` | VARCHAR(255) | | User's last name |
| `email` | VARCHAR(255) | | User's email address |
| `identification_number` | VARCHAR(255) | | National ID / NIC number |

**Relationships**: One-to-Many with `banking_core_account` (a user owns multiple accounts).

#### `banking_core_account`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal account ID |
| `number` | VARCHAR(255) | | Account number (e.g., `100015003000`) |
| `type` | VARCHAR(255) | | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `available_balance` | DECIMAL(19,2) | | Available balance for transactions |
| `actual_balance` | DECIMAL(19,2) | | Ledger balance |
| `user_id` | BIGINT | FK → `banking_core_user.id` | Owning user |

#### `banking_core_utility_account`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal ID |
| `number` | VARCHAR(255) | | Utility account number |
| `provider_name` | VARCHAR(255) | | Provider name (e.g., `VODAFONE`, `AIRTEL`) |

#### `banking_core_transaction`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal ID |
| `amount` | DECIMAL(19,2) | | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | NOT NULL | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | NOT NULL | Counterparty account or reference |
| `transaction_id` | VARCHAR(50) | NOT NULL | UUID for the transaction |
| `account_id` | BIGINT | FK → `banking_core_account.id` | Associated account |

**Seed Data**: 4 users, 14 savings accounts, 6 utility providers pre-loaded via Flyway migration.

### 2.2 Internet Banking User Service

**Database**: `banking_core_user_service` (MySQL)
**Schema managed by**: JPA auto-DDL (Hibernate)

#### `user`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal user ID |
| `auth_id` | VARCHAR(255) | | Keycloak user UUID |
| `identification` | VARCHAR(255) | | National ID linking to core banking user |
| `status` | VARCHAR(255) | | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | TIMESTAMP | Audit | Auto-populated creation timestamp |
| `created_by` | VARCHAR(255) | Audit | Auto-populated creator |
| `modified_date` | TIMESTAMP | Audit | Auto-populated last modification timestamp |
| `modified_by` | VARCHAR(255) | Audit | Auto-populated modifier |
| `version` | BIGINT | Optimistic locking | JPA `@Version` field |

### 2.3 Internet Banking Fund Transfer Service

**Database**: `banking_core_fund_transfer_service` (MySQL)
**Schema managed by**: JPA auto-DDL (Hibernate)

#### `fund_transfer`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal ID |
| `from_account` | VARCHAR(255) | | Source account number |
| `to_account` | VARCHAR(255) | | Destination account number |
| `amount` | DECIMAL(19,2) | | Transfer amount |
| `transaction_reference` | VARCHAR(255) | | Transaction ID from core banking |
| `status` | VARCHAR(255) | | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | (Audit) | | Inherited from `AuditAware` |

### 2.4 Internet Banking Utility Payment Service

**Database**: `banking_core_utility_payment_service` (MySQL)
**Schema managed by**: JPA auto-DDL (Hibernate)

#### `utility_payment`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal ID |
| `provider_id` | BIGINT | | Utility provider ID from core banking |
| `amount` | DECIMAL(19,2) | | Payment amount |
| `reference_number` | VARCHAR(255) | | Customer reference number |
| `account` | VARCHAR(255) | | Source bank account number |
| `transaction_id` | VARCHAR(255) | | Transaction ID from core banking |
| `status` | VARCHAR(255) | | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | (Audit) | | Inherited from `AuditAware` |

---

## 3. API Surface Map

### 3.1 API Gateway Routes

The API Gateway uses Eureka-based discovery routing. Routes are configured via the external Spring Cloud Config Server. The gateway prefixes map to backend services:

| Gateway Path Prefix | Backend Service |
|---------------------|----------------|
| `/user/**` | `internet-banking-user-service` |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` |
| `/utility-payment/**` | `internet-banking-utility-payment-service` |
| `/banking-core/**` | `core-banking-service` |

**Security rules** (defined in `SecurityConfiguration`):
- `permitAll()`: `/user/api/v1/bank-users/register`, `/actuator/**`, `/{service}/actuator/**`
- All other routes: `authenticated` (JWT bearer token required)

### 3.2 Core Banking Service Endpoints

Base URL: `http://localhost:8092`

#### Account Controller (`/api/v1/account`)

| Method | Path | Description | Request | Response |
|--------|------|-------------|---------|----------|
| `GET` | `/bank-account/{account_number}` | Get bank account by number | Path: `account_number` (String) | `BankAccount { id, number, type, status, availableBalance, actualBalance, user }` |
| `GET` | `/util-account/{account_name}` | Get utility account by provider name | Path: `account_name` (String) | `UtilityAccount { id, number, providerName }` |

#### User Controller (`/api/v1/user`)

| Method | Path | Description | Request | Response |
|--------|------|-------------|---------|----------|
| `GET` | `/{identification}` | Get user by identification number | Path: `identification` (String) | `User { id, firstName, lastName, email, identificationNumber, bankAccounts[] }` |
| `GET` | `/` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |

#### Transaction Controller (`/api/v1/transaction`)

| Method | Path | Description | Request | Response |
|--------|------|-------------|---------|----------|
| `POST` | `/fund-transfer` | Process internal fund transfer | Body: `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `POST` | `/util-payment` | Process utility payment | Body: `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |

### 3.3 User Service Endpoints

Base URL: `http://localhost:8083`  
Gateway: `http://localhost:8082/user`

#### User Controller (`/api/v1/bank-users`)

| Method | Path | Description | Request | Response |
|--------|------|-------------|---------|----------|
| `POST` | `/register` | Register a new internet banking user | Body: `{ email, identification, password }` | `User { id, email, identification, authId, status }` |
| `PATCH` | `/update/{id}` | Update user (e.g., approve registration) | Path: `id` (Long); Body: `{ status }` | `User` |
| `GET` | `/` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |
| `GET` | `/{id}` | Get user by ID | Path: `id` (Long) | `User` |

### 3.4 Fund Transfer Service Endpoints

Base URL: `http://localhost:8084`  
Gateway: `http://localhost:8082/fund-transfer`

#### Fund Transfer Controller (`/api/v1/transfer`)

| Method | Path | Description | Request | Response |
|--------|------|-------------|---------|----------|
| `POST` | `/` | Initiate fund transfer | Body: `{ fromAccount, toAccount, amount, authID }` | `{ message, transactionId }` |
| `GET` | `/` | List all fund transfers (paginated) | Query: `page`, `size`, `sort` | `List<FundTransfer>` |

### 3.5 Utility Payment Service Endpoints

Base URL: `http://localhost:8085`  
Gateway: `http://localhost:8082/utility-payment`

#### Utility Payment Controller (`/api/v1/utility-payment`)

| Method | Path | Description | Request | Response |
|--------|------|-------------|---------|----------|
| `POST` | `/` | Process utility payment | Body: `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/` | List all utility payments (paginated) | Query: `page`, `size`, `sort` | `List<UtilityPayment>` |

### 3.6 Inter-Service Feign Client Calls

| Caller Service | Target Service | Feign Method | HTTP Call |
|---------------|---------------|-------------|-----------|
| User Service | Core Banking | `readUser(identification)` | `GET /api/v1/user/{identification}` |
| Fund Transfer Service | Core Banking | `readAccount(accountNumber)` | `GET /api/v1/account/bank-account/{account_number}` |
| Fund Transfer Service | Core Banking | `fundTransfer(request)` | `POST /api/v1/transaction/fund-transfer` |
| Utility Payment Service | Core Banking | `readAccount(accountNumber)` | `GET /api/v1/account/bank-account/{account_number}` |
| Utility Payment Service | Core Banking | `utilityPayment(request)` | `POST /api/v1/transaction/util-payment` |

---

## 4. Key Business Logic Inventory

### 4.1 Fund Transfer Rules

**Flow**: Client → API Gateway → Fund Transfer Service → Core Banking Service

1. **Fund Transfer Service** (`FundTransferService.fundTransfer()`):
   - Creates a `FundTransferEntity` with status `PENDING` and persists it locally.
   - Delegates the actual transfer to Core Banking via Feign (`bankingCoreFeignClient.fundTransfer(request)`).
   - On success, updates local entity with `transactionReference` and status `SUCCESS`.
   - **No failure handling**: If the Feign call fails, the local entity remains in `PENDING` state with no retry or compensation logic.

2. **Core Banking Service** (`TransactionService.fundTransfer()`):
   - Reads both source (`fromAccount`) and destination (`toAccount`) bank accounts.
   - **Balance validation**: Checks `actualBalance >= 0` AND `actualBalance >= transferAmount`. Throws `InsufficientFundsException` if insufficient.
   - Calls `internalFundTransfer()` which:
     - Debits the source account: `actualBalance -= amount`, `availableBalance = actualBalance - amount` (note: double subtraction bug).
     - Saves a debit `TransactionEntity` (amount is negated).
     - Credits the destination account: `actualBalance += amount`, `availableBalance = actualBalance + amount` (note: double addition bug).
     - Saves a credit `TransactionEntity`.
   - Returns a `FundTransferResponse` with UUID transaction ID.

**Known Bug**: The `availableBalance` calculation uses the already-updated `actualBalance`, resulting in an off-by-amount error. For example, transferring 100 from a 200-balance account sets `actualBalance=100` then `availableBalance=100-100=0` instead of `100`.

### 4.2 Utility Payment Processing

**Flow**: Client → API Gateway → Utility Payment Service → Core Banking Service

1. **Utility Payment Service** (`UtilityPaymentService.utilPayment()`):
   - Creates a `UtilityPaymentEntity` with status `PROCESSING`.
   - Delegates to Core Banking via Feign (`bankingCoreRestClient.utilityPayment(request)`).
   - On success, updates local entity with `transactionId` and status `SUCCESS`.

2. **Core Banking Service** (`TransactionService.utilPayment()`):
   - Reads the source bank account and validates the balance.
   - Reads the utility account by provider ID.
   - Debits the source account (same double-subtraction bug as fund transfer).
   - Records a `UTILITY_PAYMENT` transaction.
   - **No actual third-party payment integration** — commented placeholder: "we can call third party API to process UTIL payment."

### 4.3 User Management

**Flow**: Client → API Gateway → User Service → (Keycloak + Core Banking Service)

1. **Registration** (`UserService.createUser()`):
   - Checks Keycloak for existing user with the same email (duplicate prevention).
   - Validates the user exists in the Core Banking system (by identification/NIC number) via Feign.
   - Validates email matches between the registration request and core banking record.
   - Creates a Keycloak user (disabled, email unverified) with the provided password.
   - Reads back the Keycloak user to get the `authId` (Keycloak UUID).
   - Persists a local `UserEntity` with status `PENDING`.

2. **Approval** (`UserService.updateUser()`):
   - When status changes to `APPROVED`, enables the Keycloak user and marks email as verified.
   - Updates local entity status.

3. **User Listing** (`UserService.readUsers()`):
   - Reads paginated users from local DB.
   - For each user, enriches with Keycloak data (email, etc.) — **N+1 query pattern** to Keycloak.

---

## 5. Integration Points

### 5.1 Keycloak

- **Version**: 23.0.7
- **Purpose**: OAuth2/OpenID Connect identity provider
- **Integration in API Gateway**: JWT validation using `spring-boot-starter-oauth2-resource-server`. JWK set URI configured via `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`.
- **Integration in User Service**: Uses `keycloak-admin-client:24.0.4` for programmatic user management (create, update, search). Configured via:
  - `app.config.keycloak.server-url`
  - `app.config.keycloak.realm`
  - `app.config.keycloak.clientId`
  - `app.config.keycloak.client-secret`
- **Realm**: Pre-configured with realm export in `docker-compose/keycloak/`.
- **Grant type**: `client_credentials` for admin API access.
- **Admin credentials**: `admin` / `password` (dev mode).

### 5.2 RabbitMQ

- **Status**: **Not implemented** in the current codebase.
- **Planned use**: Fund Transfer and Utility Payment services were intended to push notification messages to RabbitMQ for a Notification Service to consume.
- **No RabbitMQ dependency** exists in any `build.gradle`.
- **No RabbitMQ container** is defined in Docker Compose.

### 5.3 Zipkin

- **Version**: Zipkin 3 (via `openzipkin/zipkin:3`)
- **Port**: 9411
- **Integration**: All business services include:
  - `io.micrometer:micrometer-tracing-bridge-brave`
  - `io.zipkin.reporter2:zipkin-reporter-brave`
  - `io.github.openfeign:feign-micrometer`
- **Configuration**: Tracing endpoint configured via Spring Cloud Config Server properties.

### 5.4 Database Connections

All services connect to a single MySQL 8.4.0 instance with **4 separate databases**:

| Database | Used By |
|----------|---------|
| `banking_core_service` | core-banking-service |
| `banking_core_user_service` | internet-banking-user-service |
| `banking_core_fund_transfer_service` | internet-banking-fund-transfer-service |
| `banking_core_utility_payment_service` | internet-banking-utility-payment-service |

**Connection details** (from `docker-compose/mysql/privileges.sql`):
- User: `javatodev_development`
- Password: `oPItyPticIAt` (hardcoded in SQL init script)
- Root password: `woVERANKliGharym` (hardcoded in Docker Compose)

Keycloak uses a **separate PostgreSQL 15** instance with database `keycloak` (`keycloak` / `password`).

### 5.5 Spring Cloud Config Server

- **Port**: 8090
- **Configuration source**: Git repository at `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` (branch: `main`, search path: `configuration`).
- **Client configuration**: All services use `bootstrap.yml` to resolve the config server URI. Profile-specific variants:
  - `bootstrap.yml`: `http://localhost:8090` (default/local)
  - `bootstrap-dev.yml`: `http://192.168.1.5:8090` (dev network)
  - `bootstrap-docker.yml`: `http://internet-banking-config-server:8090` (Docker)

### 5.6 Netflix Eureka Service Registry

- **Port**: 8081
- **Mode**: Standalone (does not register with itself, does not fetch registry).
- **All business services and the API gateway** register as Eureka clients.
- **Feign clients** use Eureka service names (e.g., `core-banking-service`) for client-side load balancing.

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System (Gradle)

Each microservice is an independent Gradle project (no multi-project build). Common setup:

| Property | Value |
|----------|-------|
| Java version | 21 |
| Spring Boot | 3.2.4 |
| Spring Cloud | 2023.0.0 |
| Lombok | Annotation processor |
| Testing | JUnit 5 (JUnit Platform) |
| Git properties | `com.gorylenko.gradle-git-properties:2.4.2` (all except service-registry) |

**Key dependencies by service**:

| Service | Unique Dependencies |
|---------|-------------------|
| core-banking-service | `flyway-core:10.12.0`, `flyway-mysql:10.12.0`, `springdoc-openapi-starter-webflux-ui:2.1.0` |
| user-service | `keycloak-admin-client:24.0.4`, `spring-cloud-starter-openfeign`, `feign-okhttp:13.2.1`, `springdoc-openapi` |
| fund-transfer-service | `spring-cloud-starter-openfeign`, `springdoc-openapi` |
| utility-payment-service | `spring-cloud-starter-openfeign`, `springdoc-openapi` |
| api-gateway | `spring-cloud-starter-gateway`, `spring-boot-starter-oauth2-client/resource-server`, `spring-boot-starter-security` |
| service-registry | `spring-cloud-starter-netflix-eureka-server` |
| config-server | `spring-cloud-config-server` |

**Test dependencies**: All services use `spring-boot-starter-test`. Core banking, user, fund-transfer, and utility-payment services include `h2:2.2.224` for in-memory test DB.

### 6.2 Docker Compose

Two compose files exist:

1. **`docker-compose.yml`**: Full stack — all infrastructure + all microservices.
2. **`docker-compose-support-apps.yml`**: Infrastructure only (Zipkin, Keycloak, PostgreSQL, MySQL, Config Server, Service Registry) — for local development where services run from IDE.

**Docker images**: Each service builds a Docker image using a `Dockerfile` based on `eclipse-temurin:21.0.2_13-jre-alpine`. The image:
- Copies the Gradle-built fat JAR as `app.jar`.
- Copies `wait-for-it.sh` for startup ordering.
- Installs `bash` (required by `wait-for-it.sh` on Alpine).
- Sets active profile to `docker`.

### 6.3 Startup Ordering

Services use `wait-for-it.sh` scripts in their Docker entrypoints to ensure dependencies are ready:

```
1. MySQL, PostgreSQL, Zipkin, Keycloak  (no dependencies)
2. Config Server                        (no dependencies)
3. Service Registry                     (no dependencies)
4. API Gateway                          (waits for: Service Registry, Config Server)
5. User Service                         (waits for: Service Registry, Config Server, MySQL)
6. Fund Transfer Service                (waits for: Service Registry, Config Server, MySQL)
7. Utility Payment Service              (waits for: Service Registry, Config Server, MySQL)
8. Core Banking Service                 (waits for: Service Registry, Config Server, MySQL)
```

**Test credentials**: `ib_admin@javatodev.com` / `5V7huE3G86uB`
