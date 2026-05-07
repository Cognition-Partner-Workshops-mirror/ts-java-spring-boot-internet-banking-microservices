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

### High-Level Architecture

The Internet Banking system is a Java 21 / Spring Boot 3.2.4 microservices application built on Spring Cloud 2023.0.0. It consists of **6 independently deployable services** organized around banking domain capabilities.

```
                        ┌──────────────────────┐
                        │   Keycloak (IAM)     │
                        │   Port 8080          │
                        └──────────┬───────────┘
                                   │ OAuth2/OIDC
                                   ▼
┌───────────┐    ┌─────────────────────────────────┐    ┌──────────────────┐
│  Clients  │───▶│  API Gateway (Port 8082)         │───▶│  Config Server   │
│           │    │  Spring Cloud Gateway + Security │    │  Port 8090       │
└───────────┘    └──────┬──────────┬───────────┬────┘    └──────────────────┘
                        │          │           │
              ┌─────────▼──┐  ┌───▼────────┐ ┌▼─────────────────┐
              │ User Svc   │  │ Fund Xfer  │ │ Utility Payment  │
              │ Port 8083  │  │ Svc 8084   │ │ Svc 8085         │
              └──────┬─────┘  └─────┬──────┘ └──────┬───────────┘
                     │  Feign       │ Feign          │ Feign
                     ▼              ▼                ▼
              ┌────────────────────────────────────────────┐
              │         Core Banking Service               │
              │         Port 8092                          │
              └─────────────────┬──────────────────────────┘
                                │
                                ▼
              ┌──────────────────────────────────┐
              │   MySQL 8.4 (4 schemas)          │
              │   Port 3306                      │
              └──────────────────────────────────┘

         ┌──────────────────┐    ┌──────────────────┐
         │ Service Registry │    │     Zipkin        │
         │ Eureka 8081      │    │     Port 9411     │
         └──────────────────┘    └──────────────────┘
```

### Services

| # | Service | Port | Purpose | Key Dependencies |
|---|---------|------|---------|------------------|
| 1 | **internet-banking-config-server** | 8090 | Centralized configuration via Spring Cloud Config (Git-backed) | Spring Cloud Config Server |
| 2 | **internet-banking-service-registry** | 8081 | Service discovery via Netflix Eureka Server | Spring Cloud Netflix Eureka Server |
| 3 | **internet-banking-api-gateway** | 8082 | Single entry point, routing, OAuth2/JWT security | Spring Cloud Gateway, Spring Security OAuth2 |
| 4 | **internet-banking-user-service** | 8083 | User registration, management, Keycloak integration | OpenFeign, Keycloak Admin Client, JPA/MySQL |
| 5 | **internet-banking-fund-transfer-service** | 8084 | Fund transfer processing between bank accounts | OpenFeign, JPA/MySQL |
| 6 | **internet-banking-utility-payment-service** | 8085 | Utility bill payment processing | OpenFeign, JPA/MySQL |
| 7 | **core-banking-service** | 8092 | Core banking engine: accounts, users, transactions | JPA/MySQL, Flyway |

### Communication Patterns

| Pattern | Technology | Usage |
|---------|-----------|-------|
| **Synchronous REST** | Spring Cloud OpenFeign | All inter-service calls (User Svc → Core Banking, Fund Transfer → Core Banking, Utility Payment → Core Banking) |
| **Service Discovery** | Netflix Eureka | All services register with Eureka; Feign clients resolve service names automatically |
| **API Gateway Routing** | Spring Cloud Gateway | Routes external requests to downstream services via path prefixes (`/user/**`, `/fund-transfer/**`, `/banking-core/**`, `/utility-payment/**`) |
| **Centralized Config** | Spring Cloud Config Server | All services (except Config Server and Service Registry) fetch configuration from a Git-backed config server |
| **Distributed Tracing** | Micrometer Tracing + Zipkin | All services export traces to Zipkin via Brave bridge |
| **Authentication** | OAuth2 / JWT via Keycloak | API Gateway validates JWT tokens; propagates `X-Auth-Id` header to downstream services |

### Infrastructure Components

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Identity Provider** | Keycloak 23.0.7 (PostgreSQL 15 backend) | User authentication, OAuth2 token issuance, realm/client management |
| **Database** | MySQL 8.4.0 | Primary data store for all business services |
| **Tracing** | Zipkin 3 | Distributed trace collection and visualization |
| **Service Registry** | Netflix Eureka | Service discovery and registration |
| **Config Store** | Git repository (GitHub) | Externalized configuration for all services |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL schema: `banking_core_service`)

#### Entities

**`banking_core_user`**

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `bigint(20)` | PK, AUTO_INCREMENT | User ID |
| `email` | `varchar(255)` | | User email address |
| `first_name` | `varchar(255)` | | First name |
| `last_name` | `varchar(255)` | | Last name |
| `identification_number` | `varchar(255)` | | National ID / identification number |

**`banking_core_account`**

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `bigint(20)` | PK, AUTO_INCREMENT | Account ID |
| `number` | `varchar(255)` | | Account number (e.g., `100015003000`) |
| `type` | `varchar(255)` | | Account type (e.g., `SAVINGS_ACCOUNT`) |
| `status` | `varchar(255)` | | Account status (e.g., `ACTIVE`) |
| `actual_balance` | `decimal(19,2)` | | Actual balance |
| `available_balance` | `decimal(19,2)` | | Available balance |
| `user_id` | `bigint(20)` | FK → `banking_core_user.id` | Owner user |

**`banking_core_utility_account`**

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `bigint(20)` | PK, AUTO_INCREMENT | Utility account ID |
| `number` | `varchar(255)` | | Utility account number |
| `provider_name` | `varchar(255)` | | Provider name (e.g., `VODAFONE`, `AIRTEL`) |

**`banking_core_transaction`**

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `bigint(20)` | PK, AUTO_INCREMENT | Transaction ID |
| `amount` | `decimal(19,2)` | | Transaction amount (negative for debits) |
| `transaction_type` | `varchar(30)` | NOT NULL | `FUND_TRANSFER` or `UTILITY_PAYMENT` |
| `reference_number` | `varchar(50)` | NOT NULL | Reference (target account number or utility ref) |
| `transaction_id` | `varchar(50)` | NOT NULL | UUID-based transaction identifier |
| `account_id` | `bigint(20)` | FK → `banking_core_account.id` | Associated bank account |

#### Enums (Core Banking)

- **`AccountType`**: `SAVINGS_ACCOUNT`, `CURRENT_ACCOUNT`, `FIXED_DEPOSIT`
- **`AccountStatus`**: `ACTIVE`, `INACTIVE`
- **`TransactionType`**: `FUND_TRANSFER`, `UTILITY_PAYMENT`

#### Relationships

```
banking_core_user (1) ──── (N) banking_core_account
banking_core_account (1) ──── (N) banking_core_transaction
banking_core_utility_account (standalone, no FK)
```

### 2.2 Internet Banking User Service (MySQL schema: `banking_core_user_service`)

#### Entity

**`user`** (JPA entity: `UserEntity`)

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `bigint` | PK, AUTO_INCREMENT | Internal user ID |
| `auth_id` | `varchar` | | Keycloak user ID |
| `identification` | `varchar` | | National ID matching core banking user |
| `status` | `varchar` (ENUM) | | `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | `timestamp` | Audit | Creation timestamp |
| `created_by` | `varchar` | Audit | Creator |
| `modified_date` | `timestamp` | Audit | Last modification timestamp |
| `modified_by` | `varchar` | Audit | Last modifier |
| `version` | `bigint` | Optimistic locking | JPA version field |

#### Enums (User Service)

- **`Status`**: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST`

### 2.3 Internet Banking Fund Transfer Service (MySQL schema: `banking_core_fund_transfer_service`)

#### Entity

**`fund_transfer`** (JPA entity: `FundTransferEntity`)

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `bigint` | PK, AUTO_INCREMENT | Transfer record ID |
| `from_account` | `varchar` | | Source account number |
| `to_account` | `varchar` | | Destination account number |
| `amount` | `decimal` | | Transfer amount |
| `transaction_reference` | `varchar` | | UUID from core banking |
| `status` | `varchar` (ENUM) | | `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `modified_date` | `timestamp` | Audit | Timestamps |
| `created_by` / `modified_by` | `varchar` | Audit | Audit fields |
| `version` | `bigint` | Optimistic locking | JPA version field |

#### Enums (Fund Transfer)

- **`TransactionStatus`**: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED`

### 2.4 Internet Banking Utility Payment Service (MySQL schema: `banking_core_utility_payment_service`)

#### Entity

**`utility_payment`** (JPA entity: `UtilityPaymentEntity`)

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `bigint` | PK, AUTO_INCREMENT | Payment record ID |
| `provider_id` | `bigint` | | Utility provider ID |
| `amount` | `decimal` | | Payment amount |
| `reference_number` | `varchar` | | Customer reference number |
| `account` | `varchar` | | Source bank account number |
| `transaction_id` | `varchar` | | UUID from core banking |
| `status` | `varchar` (ENUM) | | `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `modified_date` | `timestamp` | Audit | Timestamps |
| `created_by` / `modified_by` | `varchar` | Audit | Audit fields |
| `version` | `bigint` | Optimistic locking | JPA version field |

---

## 3. API Surface Map

### 3.1 API Gateway Routes (Port 8082)

All external traffic enters through the API Gateway. Routes are configured via Spring Cloud Config:

| Path Prefix | Target Service | Auth Required |
|-------------|---------------|---------------|
| `/user/**` | internet-banking-user-service | Yes (except `/user/api/v1/bank-users/register`) |
| `/fund-transfer/**` | internet-banking-fund-transfer-service | Yes |
| `/banking-core/**` | core-banking-service | Yes |
| `/utility-payment/**` | internet-banking-utility-payment-service | Yes |
| `/actuator/**` | (all services) | No |

### 3.2 Core Banking Service (Port 8092)

**Base path**: `/api/v1`

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `GET` | `/api/v1/user/{identification}` | Get user by national ID | — | `User { id, firstName, lastName, email, identificationNumber, bankAccounts[] }` |
| `GET` | `/api/v1/user` | List users (paginated) | — (query: `page`, `size`, `sort`) | `List<User>` |
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | — | `BankAccount { id, number, type, status, actualBalance, availableBalance }` |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | — | `UtilityAccount { id, number, providerName }` |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `{ account, providerId, amount, referenceNumber }` | `{ message, transactionId }` |

### 3.3 Internet Banking User Service (Port 8083)

**Base path**: `/api/v1/bank-users`

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/bank-users/register` | Register new internet banking user | `{ email, identification, password }` | `User { id, email, identification, authId, status }` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user status | `{ status }` (APPROVED/DISABLED/BLACKLIST) | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | — (query: `page`, `size`, `sort`) | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by internal ID | — | `User` |

### 3.4 Internet Banking Fund Transfer Service (Port 8084)

**Base path**: `/api/v1/transfer`

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `{ fromAccount, toAccount, amount, authID }` | `{ message, transactionId }` |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | — (query: `page`, `size`, `sort`) | `List<FundTransfer>` |

### 3.5 Internet Banking Utility Payment Service (Port 8085)

**Base path**: `/api/v1/utility-payment`

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | — (query: `page`, `size`, `sort`) | `List<UtilityPayment>` |

### 3.6 Infrastructure Endpoints

| Service | Endpoint | Purpose |
|---------|----------|---------|
| Service Registry | `GET /eureka/apps` | Service registration listing |
| Config Server | `GET /{application}/{profile}` | Configuration retrieval |
| All services | `GET /actuator/health` | Health check |
| All services | `GET /actuator/info` | Application info (git properties) |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

**Location**: `internet-banking-user-service` → `UserService.createUser()`

1. Check if email is already registered in Keycloak (via `KeycloakUserService.readUserByEmail()`)
2. If already exists → throw `UserAlreadyRegisteredException`
3. Fetch user from core banking by identification number (via Feign call to `core-banking-service`)
4. Validate email matches the core banking record; if mismatch → throw `InvalidEmailException`
5. Create Keycloak user representation (email, name, credentials, enabled=false, emailVerified=false)
6. Call Keycloak Admin API to create user
7. If Keycloak returns 201, save user entity locally with status `PENDING`
8. If core banking user not found → throw `InvalidBankingUserException`

### 4.2 User Approval Flow

**Location**: `internet-banking-user-service` → `UserService.updateUser()`

1. Fetch user entity by ID
2. If new status is `APPROVED`:
   - Fetch Keycloak user representation
   - Set `enabled=true` and `emailVerified=true`
   - Update Keycloak user
3. Update local user entity status

### 4.3 Fund Transfer Flow

**Location**: `internet-banking-fund-transfer-service` → `FundTransferService.fundTransfer()`

1. Save initial fund transfer record with status `PENDING`
2. Call core banking service via Feign (`POST /api/v1/transaction/fund-transfer`)
3. Core banking (`TransactionService.fundTransfer()`):
   a. Read source and destination bank accounts
   b. **Validate balance**: source account `actualBalance >= 0` AND `actualBalance >= amount`
   c. If insufficient → throw `InsufficientFundsException`
   d. Debit source account (subtract from `actualBalance` and `availableBalance`)
   e. Credit destination account (add to `actualBalance` and `availableBalance`)
   f. Create two `TransactionEntity` records (debit + credit) with `FUND_TRANSFER` type
   g. Return transaction ID
4. Update fund transfer record: set `transactionReference` and status to `SUCCESS`

### 4.4 Utility Payment Flow

**Location**: `internet-banking-utility-payment-service` → `UtilityPaymentService.utilPayment()`

1. Save initial utility payment record with status `PROCESSING`
2. Call core banking service via Feign (`POST /api/v1/transaction/util-payment`)
3. Core banking (`TransactionService.utilPayment()`):
   a. Read source bank account
   b. **Validate balance**: same as fund transfer
   c. Read utility account by provider ID
   d. Debit source account
   e. Create `TransactionEntity` record with `UTILITY_PAYMENT` type
   f. Return transaction ID
4. Update utility payment record: set `transactionId` and status to `SUCCESS`

### 4.5 Authentication and Authorization

**Location**: `internet-banking-api-gateway` → `SecurityConfiguration`

- All requests routed through API Gateway require valid JWT token (Keycloak-issued)
- Exception: `/user/api/v1/bank-users/register` is publicly accessible
- Exception: All `/actuator/**` endpoints are publicly accessible
- Gateway injects `X-Auth-Id` header (from JWT principal) into proxied requests
- Downstream services read `X-Auth-Id` via `AppAuthUserFilter` (present in user, fund-transfer, and utility-payment services)

### 4.6 Balance Validation Rules

**Location**: `core-banking-service` → `TransactionService.validateBalance()`

```
IF actualBalance < 0 OR actualBalance < requestedAmount
  THEN throw InsufficientFundsException
```

---

## 5. Integration Points

### 5.1 Keycloak

| Aspect | Detail |
|--------|--------|
| **Version** | 23.0.7 |
| **Backend DB** | PostgreSQL 15 |
| **Realm** | `javatodev-internet-banking` (imported from `realm-export.json`) |
| **Client** | `internet-banking-api-client` (confidential, client_credentials grant) |
| **Usage by User Service** | User creation, update, search via Keycloak Admin Client SDK (v24.0.4) |
| **Usage by API Gateway** | JWT validation via `jwk-set-uri` endpoint |
| **Configuration** | `app.config.keycloak.*` properties (server-url, realm, clientId, client-secret) |

### 5.2 RabbitMQ

| Aspect | Detail |
|--------|--------|
| **Status** | Referenced in README/architecture as intended for notification service |
| **Current Implementation** | **Not implemented** - no RabbitMQ dependency in any `build.gradle`, no message producer/consumer code |
| **Intended Use** | Fund transfer and utility payment services were planned to publish notification messages |

### 5.3 Zipkin (Distributed Tracing)

| Aspect | Detail |
|--------|--------|
| **Version** | Zipkin 3 (Docker image: `openzipkin/zipkin:3`) |
| **Port** | 9411 |
| **Client Libraries** | `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` in all business services and gateway |
| **Coverage** | All 5 non-infrastructure services (gateway, user, fund-transfer, utility-payment, core-banking) |
| **Feign Tracing** | `feign-micrometer` dependency enables trace propagation across Feign calls |

### 5.4 Database Connections

| Service | Database Schema | Managed By |
|---------|----------------|------------|
| core-banking-service | `banking_core_service` | Flyway migrations (3 migration scripts) |
| internet-banking-user-service | `banking_core_user_service` | JPA `hibernate.ddl-auto` (via config server) |
| internet-banking-fund-transfer-service | `banking_core_fund_transfer_service` | JPA `hibernate.ddl-auto` (via config server) |
| internet-banking-utility-payment-service | `banking_core_utility_payment_service` | JPA `hibernate.ddl-auto` (via config server) |

**MySQL**: Version 8.4.0, single instance, credentials: `javatodev_development` / `oPItyPticIAt` (created via `privileges.sql` init script).

### 5.5 Spring Cloud Config Server

| Aspect | Detail |
|--------|--------|
| **Git Repository** | `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` |
| **Branch** | `main` |
| **Search Path** | `configuration` |
| **Consumers** | All services except Service Registry fetch config on startup via `bootstrap.yml` |
| **Profiles** | `default` (localhost), `dev` (IP-based), `docker` (container DNS) |

### 5.6 OpenFeign Inter-Service Calls

| Feign Client | Source Service | Target Service | Endpoints Called |
|--------------|---------------|----------------|-----------------|
| `BankingCoreRestClient` (user-service) | User Service | Core Banking | `GET /api/v1/user/{identification}` |
| `BankingCoreFeignClient` (fund-transfer) | Fund Transfer Service | Core Banking | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/fund-transfer` |
| `BankingCoreRestClient` (utility-payment) | Utility Payment Service | Core Banking | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/util-payment` |

---

## 6. Build and Deployment Pipeline Summary

### Build System

- **Build Tool**: Gradle (per-service `build.gradle`, no multi-project build)
- **Java Version**: 21 (Eclipse Temurin 21.0.2_13-jre-alpine in Docker)
- **Spring Boot**: 3.2.4
- **Spring Cloud**: 2023.0.0
- **Plugins**: `spring-boot`, `io.spring.dependency-management`, `com.gorylenko.gradle-git-properties`

### Docker

Each service has its own `Dockerfile` following the same pattern:

```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
EXPOSE <port>
COPY wait-for-it.sh wait-for-it.sh
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

- All services include `wait-for-it.sh` for startup ordering
- Docker profile activates `bootstrap-docker.yml` (config server URI: `http://internet-banking-config-server:8090`)

### Docker Compose

**File**: `docker-compose/docker-compose.yml`

Orchestrates all components on a custom bridge network (`172.25.0.0/16`):

| Container | Image | IP | Port |
|-----------|-------|----|------|
| openzipkin_server | openzipkin/zipkin:3 | 172.25.0.12 | 9411 |
| keycloak_web | quay.io/keycloak/keycloak:23.0.7 | 172.25.0.11 | 8080 |
| keycloak_postgre_db | postgres:15 | 172.25.0.10 | 5432 (internal) |
| mysql_javatodev_app | Custom (mysql:8.4.0 + init) | 172.25.0.9 | 3306 |
| internet-banking-config-server | javatodev/internet-banking-config-server | 172.25.0.8 | 8090 |
| internet-banking-service-registry | javatodev/internet-banking-service-registry | 172.25.0.7 | 8081 |
| internet-banking-api-gateway | javatodev/internet-banking-api-gateway | 172.25.0.6 | 8082 |
| internet-banking-user-service | javatodev/internet-banking-user-service | 172.25.0.5 | 8083 |
| internet-banking-fund-transfer-service | javatodev/internet-banking-fund-transfer-service | 172.25.0.4 | 8084 |
| internet-banking-utility-payment-service | javatodev/internet-banking-utility-payment-service | 172.25.0.3 | 8085 |
| core-banking-service | javatodev/core-banking-service | 172.25.0.2 | 8092 |

**Startup order** (enforced via `wait-for-it.sh`):
1. MySQL, PostgreSQL, Zipkin, Keycloak
2. Config Server
3. Service Registry
4. All business services (wait for both Config Server and Service Registry)

### Database Initialization

- **MySQL**: Custom Dockerfile runs `privileges.sql` via `/docker-entrypoint-initdb.d/` to create the app user and 4 database schemas
- **Core Banking**: Flyway migrations create tables and seed test data
- **Other Services**: Schema created by JPA/Hibernate (`ddl-auto` setting from config server)
- **Keycloak**: Realm imported from `realm-export.json` on first start

### Test Data

Pre-seeded via Flyway migration `V1.0.20210427174721__temp_data.sql`:
- 4 banking users (Sam, Guru, Ragu, Randor)
- 14 savings accounts with balances ranging from 12,000 to 889,000.33
- 6 utility providers (Vodafone, Verizon, Singtel, Hutch, Airtel, GIO)

**Test credentials**: `ib_admin@javatodev.com` / `5V7huE3G86uB`

### Postman Collection

A Postman collection and environment (`LOCAL_DOCKER_SETUP`) are provided in the `postman_collection/` directory for API testing.
