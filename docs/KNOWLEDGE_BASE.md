# Application Knowledge Base

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Data Model Documentation](#2-data-model-documentation)
3. [API Surface Map](#3-api-surface-map)
4. [Key Business Logic Inventory](#4-key-business-logic-inventory)
5. [Integration Points](#5-integration-points)
6. [Build and Deployment Pipeline Summary](#6-build-and-deployment-pipeline-summary)

---

## 1. Architecture Overview

### System Description

This is a **Java 21 / Spring Boot 3.2.4** internet banking application built using a microservices architecture with **Spring Cloud 2023.0.0**. It implements core banking operations including user management, fund transfers, and utility payments.

### Microservices Inventory

| # | Service | Port | Purpose | Database |
|---|---------|------|---------|----------|
| 1 | **internet-banking-config-server** | 8090 | Centralized configuration management via Spring Cloud Config | None |
| 2 | **internet-banking-service-registry** | 8081 | Service discovery via Netflix Eureka Server | None |
| 3 | **internet-banking-api-gateway** | 8082 | API routing, authentication (OAuth2/Keycloak), request enrichment | None |
| 4 | **internet-banking-user-service** | 8083 | User registration, approval, profile management | MySQL (`banking_core_user_service`) |
| 5 | **internet-banking-fund-transfer-service** | 8084 | Fund transfer orchestration between accounts | MySQL (`banking_core_fund_transfer_service`) |
| 6 | **internet-banking-utility-payment-service** | 8085 | Utility bill payment processing | MySQL (`banking_core_utility_payment_service`) |
| 7 | **core-banking-service** | 8092 | Core banking engine: accounts, users, transactions | MySQL (`banking_core_service`) |

> **Note:** Although the README references a "Notification service" that consumes from RabbitMQ, it is listed as **PENDING development** and does not exist in the codebase.

### Communication Patterns

```
                         +-----------------------+
                         |   Keycloak (8080)     |
                         |   (AuthN / AuthZ)     |
                         +-----------+-----------+
                                     |
                                     | JWT validation
                                     v
+----------+    +--------------------+--------------------+    +------------------+
|  Client  |--->|  API Gateway (8082)                     |--->| Service Registry |
+----------+    |  - OAuth2 Resource Server                |    | Eureka (8081)    |
                |  - Injects X-Auth-Id header              |    +------------------+
                +----+----------+-----------+--------------+
                     |          |           |
              /user/*    /fund-transfer/*  /utility-payment/*  /banking-core/*
                     |          |           |               |
                     v          v           v               v
              +-----------+ +----------+ +-----------+ +-----------+
              |  User Svc | | Fund Xfr | | Util Pay  | | Core Bank |
              |  (8083)   | | (8084)   | | (8085)    | | (8092)    |
              +-----+-----+ +----+-----+ +-----+-----+ +-----------+
                    |             |              |              ^
                    |  OpenFeign  |   OpenFeign  |              |
                    +-------------+--------------+--------------+
                                  |
                         +--------v--------+
                         |   MySQL (3306)  |
                         |  4 databases    |
                         +-----------------+
```

**Service-to-Service Communication:**
- **Synchronous (HTTP/REST via OpenFeign):**
  - `internet-banking-user-service` --> `core-banking-service` (user lookup by identification)
  - `internet-banking-fund-transfer-service` --> `core-banking-service` (account lookup, fund transfer execution)
  - `internet-banking-utility-payment-service` --> `core-banking-service` (account lookup, utility payment execution)
- **Service Discovery:** All services register with Eureka; Feign clients resolve service names via Eureka
- **Configuration:** All services pull configuration from Config Server at startup (bootstrap.yml)

### Infrastructure Components

| Component | Image/Version | Purpose |
|-----------|--------------|---------|
| **Keycloak** | `quay.io/keycloak/keycloak:23.0.7` | Identity and Access Management (OAuth2/OIDC) |
| **PostgreSQL** | `postgres:15` | Keycloak's backing store |
| **MySQL** | Custom (with seed data) | Application database (4 schemas) |
| **Zipkin** | `openzipkin/zipkin:3` | Distributed tracing |
| **Spring Cloud Config Server** | Custom | Centralized configuration (Git-backed) |
| **Netflix Eureka** | Custom | Service registry and discovery |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (`banking_core_service`)

#### `banking_core_user`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT(20) | PK, AUTO_INCREMENT | Surrogate key |
| `email` | VARCHAR(255) | | User email address |
| `first_name` | VARCHAR(255) | | First name |
| `last_name` | VARCHAR(255) | | Last name |
| `identification_number` | VARCHAR(255) | | National ID / NIC number |

#### `banking_core_account`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT(20) | PK, AUTO_INCREMENT | Surrogate key |
| `number` | VARCHAR(255) | | Account number (12-digit string) |
| `type` | VARCHAR(255) | | Enum: `SAVINGS_ACCOUNT` |
| `status` | VARCHAR(255) | | Enum: `ACTIVE` |
| `actual_balance` | DECIMAL(19,2) | | Actual account balance |
| `available_balance` | DECIMAL(19,2) | | Available balance |
| `user_id` | BIGINT(20) | FK -> `banking_core_user.id` | Account owner |

#### `banking_core_utility_account`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT(20) | PK, AUTO_INCREMENT | Surrogate key |
| `number` | VARCHAR(255) | | Utility provider account number |
| `provider_name` | VARCHAR(255) | | Provider name (e.g., VODAFONE, VERIZON) |

#### `banking_core_transaction`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT(20) | PK, AUTO_INCREMENT | Surrogate key |
| `amount` | DECIMAL(19,2) | | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | NOT NULL | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | NOT NULL | Reference (target account or ref #) |
| `transaction_id` | VARCHAR(50) | NOT NULL | UUID transaction identifier |
| `account_id` | BIGINT(20) | FK -> `banking_core_account.id` | Associated account |

**Entity Relationships (Core Banking):**
```
banking_core_user  1 ---< N  banking_core_account
banking_core_account  1 ---< N  banking_core_transaction
banking_core_utility_account  (standalone - no FK relationships)
```

### 2.2 Internet Banking User Service (`banking_core_user_service`)

#### `user`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `auth_id` | VARCHAR | Keycloak user ID |
| `identification` | VARCHAR | National ID (links to core banking) |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED` |
| `created_by` | VARCHAR | Audit field (from `AuditAware`) |
| `created_date` | DATETIME | Audit field |
| `last_modified_by` | VARCHAR | Audit field |
| `last_modified_date` | DATETIME | Audit field |

> Schema managed by JPA auto-DDL (no Flyway migrations for this service).

### 2.3 Internet Banking Fund Transfer Service (`banking_core_fund_transfer_service`)

#### `fund_transfer`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `transaction_reference` | VARCHAR | Core banking transaction ID |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `status` | VARCHAR | Enum: `PENDING`, `SUCCESS` |
| `created_by` | VARCHAR | Audit field |
| `created_date` | DATETIME | Audit field |
| `last_modified_by` | VARCHAR | Audit field |
| `last_modified_date` | DATETIME | Audit field |

### 2.4 Internet Banking Utility Payment Service (`banking_core_utility_payment_service`)

#### `utility_payment`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Customer reference number |
| `account` | VARCHAR | Source bank account number |
| `transaction_id` | VARCHAR | Core banking transaction ID |
| `status` | VARCHAR | Enum: `PROCESSING`, `SUCCESS` |
| `created_by` | VARCHAR | Audit field |
| `created_date` | DATETIME | Audit field |
| `last_modified_by` | VARCHAR | Audit field |
| `last_modified_date` | DATETIME | Audit field |

---

## 3. API Surface Map

### 3.1 API Gateway Routes (Port 8082)

All downstream services are accessed through the API Gateway with the following path prefixes:

| Prefix | Target Service |
|--------|---------------|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |
| `/banking-core/**` | core-banking-service |

**Authentication:** All endpoints require a valid JWT (Keycloak) except:
- `POST /user/api/v1/bank-users/register` (public)
- `/actuator/**` endpoints (public)

### 3.2 Core Banking Service (Port 8092)

#### Account Controller (`/api/v1/account`)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| `GET` | `/bank-account/{account_number}` | Get bank account by number | Path: `account_number` (String) | `BankAccount { id, number, type, status, availableBalance, actualBalance, user }` |
| `GET` | `/util-account/{account_name}` | Get utility account by provider | Path: `account_name` (String) | `UtilityAccount { id, number, providerName }` |

#### User Controller (`/api/v1/user`)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| `GET` | `/{identification}` | Get user by ID number | Path: `identification` (String) | `User { id, firstName, lastName, email, identificationNumber, accounts[] }` |
| `GET` | `/` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |

#### Transaction Controller (`/api/v1/transaction`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/fund-transfer` | Execute fund transfer | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `POST` | `/util-payment` | Execute utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |

### 3.3 Internet Banking User Service (Port 8083)

#### User Controller (`/api/v1/bank-users`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/register` | Register new user | `{ firstName, lastName, email, password, identification }` | `User { id, authId, identification, status }` |
| `PATCH` | `/update/{id}` | Update user (approve/reject) | `{ status }` | `User` |
| `GET` | `/` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |
| `GET` | `/{id}` | Get user by ID | Path: `id` (Long) | `User` |

### 3.4 Internet Banking Fund Transfer Service (Port 8084)

#### Fund Transfer Controller (`/api/v1/transfer`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/` | Initiate fund transfer | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `GET` | `/` | List transfers (paginated) | Query: `page`, `size`, `sort` | `List<FundTransfer>` |

### 3.5 Internet Banking Utility Payment Service (Port 8085)

#### Utility Payment Controller (`/api/v1/utility-payment`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/` | List payments (paginated) | Query: `page`, `size`, `sort` | `List<UtilityPayment>` |

### 3.6 Infrastructure Endpoints

| Service | Endpoint | Purpose |
|---------|----------|---------|
| All services | `/actuator/health` | Health check |
| All services | `/actuator/info` | Build/git info |
| Config Server | `/actuator/**` | Configuration management |
| Service Registry | `http://localhost:8081` | Eureka dashboard |
| Zipkin | `http://localhost:9411` | Distributed tracing UI |
| Keycloak | `http://localhost:8080` | Identity management UI |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

**Location:** `internet-banking-user-service` -> `UserService.createUser()`

1. Check if email is already registered in Keycloak (via Keycloak Admin API)
2. If registered, throw `UserAlreadyRegisteredException`
3. Fetch user from core banking service by identification number (OpenFeign call)
4. Validate that the provided email matches the core banking record
5. Create user in Keycloak with:
   - Email verified: `false`
   - Account enabled: `false`
   - Credentials set from request
6. On successful Keycloak creation (HTTP 201):
   - Re-read user from Keycloak to get `authId`
   - Save user locally with status `PENDING`
7. If core banking user not found, throw `InvalidBankingUserException`

### 4.2 User Approval Flow

**Location:** `internet-banking-user-service` -> `UserService.updateUser()`

1. Find user entity by ID
2. If status is being set to `APPROVED`:
   - Read user from Keycloak by `authId`
   - Set `enabled = true` and `emailVerified = true` in Keycloak
   - Update Keycloak user record
3. Save updated status locally

### 4.3 Fund Transfer Rules

**Location:** `core-banking-service` -> `TransactionService.fundTransfer()` and `internalFundTransfer()`

1. Resolve both source and destination accounts by account number
2. **Balance validation:** Source account's `actualBalance` must be >= 0 AND >= transfer amount
3. If validation fails, throw `InsufficientFundsException`
4. Generate UUID transaction ID
5. **Debit source account:**
   - `actualBalance -= amount`
   - `availableBalance = actualBalance - amount` (note: potential double-subtraction bug)
   - Save account entity
   - Record debit transaction (negative amount)
6. **Credit destination account:**
   - `actualBalance += amount`
   - `availableBalance = actualBalance + amount` (note: potential double-addition bug)
   - Save account entity
   - Record credit transaction (positive amount)
7. Return transaction ID

**Orchestration Layer** (`internet-banking-fund-transfer-service` -> `FundTransferService`):
1. Save initial fund transfer record with status `PENDING`
2. Call core banking service via Feign client
3. Update record with transaction reference and status `SUCCESS`
4. No error handling for failed core banking calls (will propagate exception)

### 4.4 Utility Payment Processing

**Location:** `core-banking-service` -> `TransactionService.utilPayment()`

1. Generate UUID transaction ID
2. Resolve source bank account
3. **Balance validation:** Same rules as fund transfer
4. Resolve utility provider account by provider ID
5. **Debit source account:**
   - `actualBalance -= amount`
   - `availableBalance = actualBalance - amount` (same double-subtraction pattern)
6. Record transaction with type `UTILITY_PAYMENT`
7. Return response with transaction ID

**Orchestration Layer** (`internet-banking-utility-payment-service` -> `UtilityPaymentService`):
1. Save initial payment record with status `PROCESSING`
2. Call core banking service via Feign client
3. Update record with transaction ID and status `SUCCESS`

### 4.5 Authentication & Authorization

**Location:** `internet-banking-api-gateway` -> `SecurityConfiguration` and `GatewayConfiguration`

1. API Gateway acts as OAuth2 Resource Server validating JWTs against Keycloak's JWK endpoint
2. All requests (except registration and actuator) require valid JWT
3. Gateway extracts authenticated principal name and injects it as `X-Auth-Id` header
4. Downstream services extract `X-Auth-Id` via `AppAuthUserFilter` for audit purposes

---

## 5. Integration Points

### 5.1 Keycloak Integration

| Aspect | Details |
|--------|---------|
| **Version** | 23.0.7 |
| **Protocol** | OAuth2 / OIDC |
| **Admin Client** | `org.keycloak:keycloak-admin-client:24.0.4` |
| **Consuming Service** | `internet-banking-user-service` |
| **Operations** | Create user, update user, search by email, read user by ID |
| **Configuration** | `app.config.keycloak.*` properties (server-url, realm, clientId, client-secret) |
| **Auth Flow** | Client credentials grant for admin operations |
| **API Gateway** | JWT validation via `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` |
| **Realm Data** | Pre-imported via Docker volume mount (`./keycloak:/opt/keycloak/data/import`) |

### 5.2 RabbitMQ Integration

| Aspect | Details |
|--------|---------|
| **Status** | Referenced in architecture but **NOT implemented** in code |
| **Intended Use** | Push notification messages from Fund Transfer and Utility Payment services |
| **Consumer** | Notification Service (PENDING development) |

> RabbitMQ is mentioned in the README and architecture diagram but there are no RabbitMQ dependencies in any `build.gradle` file and no message producer/consumer code exists.

### 5.3 Zipkin / Distributed Tracing

| Aspect | Details |
|--------|---------|
| **Version** | Zipkin 3 (Docker image) |
| **Client Library** | `io.micrometer:micrometer-tracing-bridge-brave` + `io.zipkin.reporter2:zipkin-reporter-brave` |
| **Configured Services** | All 7 services include tracing dependencies |
| **Feign Integration** | `io.github.openfeign:feign-micrometer` for trace propagation across Feign calls |
| **Configuration** | Via Spring Cloud Config (external Git repo) |
| **UI** | `http://localhost:9411` |

### 5.4 Database Connections

| Service | Database | Schema | Migration Strategy |
|---------|----------|--------|-------------------|
| core-banking-service | MySQL | `banking_core_service` | **Flyway** (3 migration scripts) |
| internet-banking-user-service | MySQL | `banking_core_user_service` | JPA auto-DDL |
| internet-banking-fund-transfer-service | MySQL | `banking_core_fund_transfer_service` | JPA auto-DDL |
| internet-banking-utility-payment-service | MySQL | `banking_core_utility_payment_service` | JPA auto-DDL |
| Keycloak | PostgreSQL | `keycloak` | Keycloak-managed |

**Connection Details (Docker):**
- MySQL Host: `mysql_javatodev_app:3306`
- MySQL User: `javatodev_development` / Password: `oPItyPticIAt`
- MySQL Root Password: `woVERANKliGharym`
- PostgreSQL (Keycloak): `keycloakdb:5432`, user: `keycloak`, password: `password`

### 5.5 Spring Cloud Config Server

| Aspect | Details |
|--------|---------|
| **Port** | 8090 |
| **Backend** | Git repository: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` |
| **Branch** | `main` |
| **Search Path** | `configuration` |
| **Consuming Services** | All services except service-registry use bootstrap.yml to connect |
| **Profiles** | `dev` (localhost), `docker` (container networking) |

### 5.6 Netflix Eureka Service Registry

| Aspect | Details |
|--------|---------|
| **Port** | 8081 |
| **Registered Services** | All application services register as Eureka clients |
| **Usage** | Feign clients resolve service names to instances via Eureka |
| **Configuration** | `register-with-eureka: false`, `fetch-registry: false` (server mode) |

---

## 6. Build and Deployment Pipeline Summary

### Build System

- **Build Tool:** Gradle (per-service `build.gradle`, no multi-project root build)
- **Java Version:** 21 (Eclipse Temurin `21.0.2_13-jre-alpine` for Docker)
- **Spring Boot:** 3.2.4 via Spring Boot Gradle Plugin
- **Spring Cloud:** 2023.0.0 BOM
- **Additional Plugins:**
  - `com.gorylenko.gradle-git-properties:2.4.2` (all services except service-registry) - embeds Git info into build artifacts

### Docker Build

Each service has its own `Dockerfile`:

```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh
RUN apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

**Build Steps (per service):**
1. `./gradlew build` (compiles, tests, produces JAR)
2. `docker build -t javatodev/<service-name> .`

### Docker Compose Deployment

**Two compose files:**
1. `docker-compose.yml` - Full stack (all services + infrastructure)
2. `docker-compose-support-apps.yml` - Infrastructure only (Zipkin, Keycloak, MySQL, Config Server, Service Registry)

**Startup Order (enforced via `wait-for-it.sh`):**
1. Infrastructure: MySQL, PostgreSQL, Keycloak, Zipkin
2. Config Server (port 8090)
3. Service Registry (port 8081)
4. Application services wait for both Config Server and Service Registry before starting
5. Database-dependent services additionally wait for MySQL

**Network:** Custom bridge network `javatodev_ib_network` (subnet `172.25.0.0/16`) with static IP assignments.

### CI/CD

- **GitHub Actions:** Previously configured but workflows have been removed (commit: "Remove GH Actions workflows")
- **No active CI/CD pipeline** exists in the repository
- **No Kubernetes manifests** are present despite Kubernetes being listed in the tech stack

### Test Data

Flyway migrations seed the core banking database with:
- 4 users with identification numbers
- 14 savings accounts with various balances
- 6 utility provider accounts (VODAFONE, VERIZON, SINGTEL, HUTCH, AIRTEL, GIO)
- Keycloak realm data pre-imported via Docker volume

**Test Credentials:** `ib_admin@javatodev.com` / `5V7huE3G86uB`
