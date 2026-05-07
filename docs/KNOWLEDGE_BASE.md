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

### 1.1 Technology Stack

| Component | Version |
|-----------|---------|
| Java | 21 |
| Spring Boot | 3.2.4 |
| Spring Cloud | 2023.0.0 |
| Keycloak | 23.0.7 |
| MySQL | 8.4.0 |
| PostgreSQL | 15 (Keycloak backing store) |
| Zipkin | 3 |
| Docker Compose | 3.6 |

### 1.2 Microservices

The application comprises **6 microservices**, each with an independent Gradle build and Dockerfile:

| Service | Port | Purpose |
|---------|------|---------|
| `internet-banking-api-gateway` | 8082 | Spring Cloud Gateway; OAuth2/JWT security via Keycloak; routes requests to downstream services |
| `internet-banking-service-registry` | 8081 | Netflix Eureka server for service discovery |
| `internet-banking-config-server` | 8090 | Spring Cloud Config server; reads configuration from a remote GitHub repository |
| `core-banking-service` | 8092 | Core banking engine: manages users, accounts, transactions, and utility accounts |
| `internet-banking-fund-transfer-service` | 8084 | Orchestrates fund transfers between bank accounts via Feign calls to core-banking |
| `internet-banking-utility-payment-service` | 8085 | Orchestrates utility payments via Feign calls to core-banking |

### 1.3 Communication Patterns

```
                          +-----------------------+
                          |     Keycloak (IAM)    |
                          |     Port 8080         |
                          +-----------+-----------+
                                      |
                                      | JWT validation
                                      v
+----------+       +-----------------+-------------------+
|  Client  | ----> |  API Gateway (8082)                 |
+----------+       |  OAuth2 Resource Server              |
                   +-+-------+-------+-------+-----------+
                     |       |       |       |
                     v       v       v       v
              /user/** /fund-transfer/** /payment/** /core/**
                     |       |       |       |
                     v       v       v       v
              +---------+ +--------+ +--------+ +---------+
              |  User   | | Fund   | | Util   | |  Core   |
              | Service | |Transfer| |Payment | | Banking |
              | (8083)  | | (8084) | | (8085) | | (8092)  |
              +----+----+ +---+----+ +---+----+ +---------+
                   |          |          |           ^
                   |          +----------+-----------+
                   |               Feign (sync REST)
                   +------------------------------------->
```

- **Synchronous REST via OpenFeign**: Fund-transfer and utility-payment services call core-banking-service through Feign clients. User-service also calls core-banking for user lookup.
- **Service Discovery**: All services register with Eureka and discover each other via service names.
- **No asynchronous messaging**: RabbitMQ is mentioned in documentation but is **not implemented** in the codebase. No message queues are used.
- **Auth propagation**: The API Gateway extracts the JWT principal name and forwards it as an `X-Auth-Id` HTTP header to downstream services. Downstream services read this header via `AppAuthUserFilter`.

### 1.4 Infrastructure Components

| Component | Container Name | IP (Docker) | Port |
|-----------|----------------|-------------|------|
| Zipkin (Tracing) | `openzipkin_server` | 172.25.0.12 | 9411 |
| Keycloak (IAM) | `keycloak_web` | 172.25.0.11 | 8080 |
| PostgreSQL (Keycloak DB) | `keycloak_postgre_db` | 172.25.0.10 | 5432 (internal) |
| MySQL (App DB) | `mysql_javatodev_app` | 172.25.0.9 | 3306 |
| Config Server | `internet-banking-config-server` | 172.25.0.8 | 8090 |
| Service Registry | `internet-banking-service-registry` | 172.25.0.7 | 8081 |
| API Gateway | `internet-banking-api-gateway` | 172.25.0.6 | 8082 |
| User Service | `internet-banking-user-service` | 172.25.0.5 | 8083 |
| Fund Transfer Service | `internet-banking-fund-transfer-service` | 172.25.0.4 | 8084 |
| Utility Payment Service | `internet-banking-utility-payment-service` | 172.25.0.3 | 8085 |
| Core Banking Service | `core-banking-service` | 172.25.0.2 | 8092 |

All containers share a Docker bridge network `javatodev_ib_network` (subnet `172.25.0.0/16`).

---

## 2. Data Model Documentation

### 2.1 Database Schemas

The application uses a single MySQL 8.4 instance with **4 schemas** (all accessed by user `javatodev_development`):

| Schema | Used By |
|--------|---------|
| `banking_core_service` | core-banking-service |
| `banking_core_fund_transfer_service` | internet-banking-fund-transfer-service |
| `banking_core_user_service` | internet-banking-user-service |
| `banking_core_utility_payment_service` | internet-banking-utility-payment-service |

### 2.2 Core Banking Service Schema (`banking_core_service`)

#### `banking_core_user`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | User identifier |
| `first_name` | VARCHAR(255) | | User's first name |
| `last_name` | VARCHAR(255) | | User's last name |
| `email` | VARCHAR(255) | | User's email address |
| `identification_number` | VARCHAR(255) | | National ID (NIC) |

#### `banking_core_account`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Account identifier |
| `number` | VARCHAR(255) | | Account number |
| `type` | VARCHAR(255) | | Enum: SAVINGS_ACCOUNT, FIXED_DEPOSIT, LOAN_ACCOUNT |
| `status` | VARCHAR(255) | | Enum: PENDING, ACTIVE, DORMANT, BLOCKED |
| `actual_balance` | DECIMAL(19,2) | | Actual account balance |
| `available_balance` | DECIMAL(19,2) | | Available balance |
| `user_id` | BIGINT | FK -> `banking_core_user.id` | Account owner |

#### `banking_core_transaction`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Transaction identifier |
| `amount` | DECIMAL(19,2) | | Transaction amount |
| `transaction_type` | VARCHAR(30) | NOT NULL | Enum: FUND_TRANSFER, UTILITY_PAYMENT |
| `reference_number` | VARCHAR(50) | NOT NULL | Reference (target account or utility ref) |
| `transaction_id` | VARCHAR(50) | NOT NULL | UUID transaction identifier |
| `account_id` | BIGINT | FK -> `banking_core_account.id` | Associated account |

#### `banking_core_utility_account`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Utility account identifier |
| `number` | VARCHAR(255) | | Utility account number |
| `provider_name` | VARCHAR(255) | | Utility provider name (e.g., VODAFONE, VERIZON) |

**Relationships:**
- `banking_core_user` 1:N `banking_core_account` (via `user_id` FK)
- `banking_core_account` 1:N `banking_core_transaction` (via `account_id` FK)
- `banking_core_utility_account` is standalone (no FK relationships)

### 2.3 Fund Transfer Service Schema (`banking_core_fund_transfer_service`)

#### `fund_transfer`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Transfer identifier |
| `transaction_reference` | VARCHAR(255) | | UUID from core-banking |
| `from_account` | VARCHAR(255) | | Source account number |
| `to_account` | VARCHAR(255) | | Destination account number |
| `amount` | DECIMAL(19,2) | | Transfer amount |
| `status` | VARCHAR(255) | | Enum: PENDING, PROCESSING, SUCCESS, FAILED |
| `created_date` | TIMESTAMP | Audit | Record creation time |
| `created_by` | VARCHAR(255) | Audit | Creator |
| `modified_date` | TIMESTAMP | Audit | Last modification time |
| `modified_by` | VARCHAR(255) | Audit | Last modifier |
| `version` | BIGINT | | Optimistic locking version |

### 2.4 User Service Schema (`banking_core_user_service`)

#### `user`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | User identifier |
| `auth_id` | VARCHAR(255) | | Keycloak user UUID |
| `identification` | VARCHAR(255) | | National ID (NIC) |
| `status` | VARCHAR(255) | | Enum: PENDING, APPROVED, DISABLED, BLACKLIST |
| `created_date` | TIMESTAMP | Audit | Record creation time |
| `created_by` | VARCHAR(255) | Audit | Creator |
| `modified_date` | TIMESTAMP | Audit | Last modification time |
| `modified_by` | VARCHAR(255) | Audit | Last modifier |
| `version` | BIGINT | | Optimistic locking version |

### 2.5 Utility Payment Service Schema (`banking_core_utility_payment_service`)

#### `utility_payment`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Payment identifier |
| `provider_id` | BIGINT | | Utility provider ID |
| `amount` | DECIMAL(19,2) | | Payment amount |
| `reference_number` | VARCHAR(255) | | Payment reference |
| `account` | VARCHAR(255) | | Source bank account number |
| `transaction_id` | VARCHAR(255) | | UUID from core-banking |
| `status` | VARCHAR(255) | | Enum: PENDING, PROCESSING, SUCCESS, FAILED |
| `created_date` | TIMESTAMP | Audit | Record creation time |
| `created_by` | VARCHAR(255) | Audit | Creator |
| `modified_date` | TIMESTAMP | Audit | Last modification time |
| `modified_by` | VARCHAR(255) | Audit | Last modifier |
| `version` | BIGINT | | Optimistic locking version |

---

## 3. API Surface Map

### 3.1 API Gateway Routes

| Gateway Path Prefix | Target Service |
|---------------------|----------------|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/payment/**` | internet-banking-utility-payment-service |
| `/core/**` | core-banking-service |

### 3.2 Core Banking Service Endpoints

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | - | `BankAccount` (id, number, type, status, availableBalance, actualBalance) |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | - | `UtilityAccount` (id, number, providerName) |
| `POST` | `/api/v1/transaction/fund-transfer` | Process a fund transfer | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `POST` | `/api/v1/transaction/util-payment` | Process a utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/user/{identification}` | Get user by NIC identification | - | `User` (id, firstName, lastName, email, identificationNumber, bankAccounts[]) |
| `GET` | `/api/v1/user` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |

### 3.3 Fund Transfer Service Endpoints

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| `POST` | `/api/v1/transfer` | Initiate a fund transfer | `{ fromAccount, toAccount, amount, authID }` | `{ message, transactionId }` |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | Query: `page`, `size`, `sort` | `List<FundTransfer>` |

### 3.4 User Service Endpoints

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| `POST` | `/api/v1/bank-users/register` | Register a new user (PUBLIC) | `{ email, identification, password }` | `User` (id, authId, identification, status) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user status | `{ status }` | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | - | `User` |

### 3.5 Utility Payment Service Endpoints

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| `POST` | `/api/v1/utility-payment` | Process a utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | Query: `page`, `size`, `sort` | `List<UtilityPayment>` |

### 3.6 Infrastructure Endpoints

| Service | Endpoint | Description |
|---------|----------|-------------|
| Service Registry | `GET /` | Eureka Dashboard |
| All services | `GET /actuator/**` | Spring Boot Actuator health/info endpoints |

---

## 4. Key Business Logic Inventory

### 4.1 Fund Transfer Flow

**Location:** `FundTransferService.fundTransfer()` (fund-transfer-service) -> `TransactionService.fundTransfer()` / `internalFundTransfer()` (core-banking-service)

1. Fund-transfer-service saves a `FundTransferEntity` with status `PENDING`
2. Makes a synchronous Feign call to core-banking `/api/v1/transaction/fund-transfer`
3. Core-banking validates both accounts exist (`AccountService.readBankAccount()`)
4. Core-banking validates the source account has sufficient balance (`validateBalance()`)
5. Core-banking debits the source account and credits the destination account atomically (`internalFundTransfer()`)
6. Core-banking creates two `TransactionEntity` records (one debit, one credit) with a shared `transactionId`
7. Fund-transfer-service updates the entity to `SUCCESS` with the transaction reference
8. Returns success response with transaction ID

**Business Rules:**
- Both source and destination accounts must exist
- Source account `actualBalance` must be >= transfer `amount`
- Source account `actualBalance` must be >= 0
- Transfer is atomic within core-banking (single `@Transactional` method)

**Known Bug:** `internalFundTransfer()` has a double-deduction defect. After setting `actualBalance = actualBalance - amount`, it then sets `availableBalance = actualBalance - amount` (using the already-debited value), causing `availableBalance` to be debited twice. The same bug exists in `utilPayment()`.

### 4.2 Utility Payment Flow

**Location:** `UtilityPaymentService.utilPayment()` (utility-payment-service) -> `TransactionService.utilPayment()` (core-banking-service)

1. Utility-payment-service saves a `UtilityPaymentEntity` with status `PROCESSING`
2. Makes a synchronous Feign call to core-banking `/api/v1/transaction/util-payment`
3. Core-banking validates the source account and utility provider exist
4. Core-banking validates sufficient balance
5. Core-banking debits the source account
6. Core-banking creates a `TransactionEntity` record (type: `UTILITY_PAYMENT`)
7. Utility-payment-service updates the entity to `SUCCESS` with the transaction ID

**Business Rules:**
- Source account must exist
- Utility provider must exist (looked up by `providerId`)
- Source account must have sufficient balance

### 4.3 User Registration Flow

**Location:** `UserService.createUser()` (user-service)

1. Check if email already exists in Keycloak (prevents duplicate registration)
2. Look up user by NIC identification in core-banking via Feign call
3. Validate that the provided email matches the core-banking user's email
4. Create user in Keycloak with:
   - Email verification set to `false`
   - Account `enabled` set to `false`
   - Temporary password from request
5. Look up the newly created Keycloak user to get the `authId` (Keycloak UUID)
6. Save `UserEntity` in local database with status `PENDING`
7. Admin later approves via `PATCH /api/v1/bank-users/update/{id}` with `status: APPROVED`

**Admin Approval Flow (`UserService.updateUser()`):**
1. Find user entity by ID
2. If new status is `APPROVED`, update Keycloak user: set `enabled=true` and `emailVerified=true`
3. Update local entity status

### 4.4 Balance Validation Rules

**Location:** `TransactionService.validateBalance()` (core-banking-service)

```
if (actualBalance < 0 OR actualBalance < requestedAmount) -> throw InsufficientFundsException
```

- No minimum balance requirement beyond zero
- No maximum transfer limit
- No daily/monthly transaction limits
- No account status validation (e.g., DORMANT accounts can still transact)

---

## 5. Integration Points

### 5.1 Keycloak (Identity & Access Management)

| Aspect | Details |
|--------|---------|
| **Version** | 23.0.7 |
| **Realm** | `javatodev-internet-banking` (imported from `docker-compose/keycloak/` directory) |
| **Client** | `internet-banking-api-client` |
| **Integration Library** | `keycloak-admin-client:24.0.4` (user-service) |
| **Auth Flow** | OAuth2 Resource Server at Gateway; JWT validation via `jwk-set-uri` |
| **Service Account** | User-service uses Keycloak Admin Client to create/update users programmatically |
| **Test Credentials** | `ib_admin@javatodev.com` / `5V7huE3G86uB` |

### 5.2 Zipkin (Distributed Tracing)

| Aspect | Details |
|--------|---------|
| **Version** | 3 |
| **Port** | 9411 |
| **Integration** | Micrometer Tracing Bridge Brave (`micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`) |
| **Coverage** | All 4 business services include tracing dependencies |
| **Feign Integration** | `feign-micrometer` included for trace propagation across Feign calls |

### 5.3 RabbitMQ

| Aspect | Details |
|--------|---------|
| **Status** | **NOT IMPLEMENTED** |
| **Documentation** | README mentions RabbitMQ for notification service |
| **Reality** | No RabbitMQ dependency in any `build.gradle`; no message producers/consumers in code |
| **Notification Service** | Marked as "PENDING Development" in README |

### 5.4 Database Connections

| Service | Database | Schema | Migration Tool |
|---------|----------|--------|----------------|
| core-banking-service | MySQL 8.4 | `banking_core_service` | Flyway (3 migration scripts) |
| fund-transfer-service | MySQL 8.4 | `banking_core_fund_transfer_service` | JPA/Hibernate auto-DDL |
| user-service | MySQL 8.4 | `banking_core_user_service` | JPA/Hibernate auto-DDL |
| utility-payment-service | MySQL 8.4 | `banking_core_utility_payment_service` | JPA/Hibernate auto-DDL |

All services connect to the same MySQL instance (user: `javatodev_development`, password in config server).

### 5.5 Spring Cloud Config Server

| Aspect | Details |
|--------|---------|
| **Config Repo** | `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` |
| **Search Path** | `configuration/` |
| **Branch** | `main` |
| **Bootstrap** | Services use `bootstrap.yml` with profile-specific variants (`dev`, `docker`) |
| **Profiles** | `default` (localhost), `dev` (192.168.1.5), `docker` (container name resolution) |

### 5.6 Spring Cloud Eureka (Service Registry)

| Aspect | Details |
|--------|---------|
| **Port** | 8081 |
| **Self-Registration** | Disabled (`register-with-eureka: false`) |
| **Fetch Registry** | Disabled (`fetch-registry: false`) |
| **All Services** | Register as Eureka clients for discovery |

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build Tool:** Gradle (each service has its own independent `build.gradle`)
- **No multi-module root project:** Each service is built independently
- **Plugins:** `spring-boot`, `spring-dependency-management`, `gradle-git-properties`
- **Java Source Compatibility:** 21
- **Test Framework:** JUnit 5 (via `spring-boot-starter-test`)
- **Test Database:** H2 in-memory (configured in `src/test/resources/application.yml`)

**Build Command (per service):**
```bash
./gradlew clean build
```

### 6.2 Docker

Each service has a `Dockerfile` using the same pattern:
```dockerfile
FROM eclipse-temurin:21-jre-alpine
# Copies built JAR and wait-for-it.sh script
```

### 6.3 Docker Compose

Two compose files in `docker-compose/`:

| File | Contents |
|------|----------|
| `docker-compose-support-apps.yml` | Infrastructure only: MySQL, Keycloak, PostgreSQL, Zipkin, Config Server, Service Registry |
| `docker-compose.yml` | Full stack: all infrastructure + all 6 application services |

**Startup Ordering:**
- `wait-for-it.sh` script with 50-second timeout ensures services wait for:
  1. Service Registry (8081)
  2. Config Server (8090)
  3. MySQL (3306) (for services with database)

**Network:** Static IP assignment on `javatodev_ib_network` (172.25.0.0/16).

### 6.4 CI/CD Pipeline

**No CI/CD pipeline** is configured in the repository. There are no GitHub Actions workflows, Jenkinsfiles, or equivalent pipeline definitions.

### 6.5 Test Data

Flyway migrations in core-banking-service seed:
- 4 users (Sam, Guru, Ragu, Randor)
- 14 bank accounts across the 4 users
- 6 utility providers (VODAFONE, VERIZON, SINGTEL, HUTCH, AIRTEL, GIO)

### 6.6 Postman Collection

API testing suites are provided in `postman_collection/`:
- `JAVA_TO_DEV_MICROSERVICES.postman_collection.json`
- `BANKING_CORE_MICROSERVICES_PROJECT.postman_environment.json`
- Environment: `LOCAL_DOCKER_SETUP`
