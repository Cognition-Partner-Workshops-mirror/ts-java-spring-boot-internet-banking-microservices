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

### 1.1 High-Level Architecture

The application follows a **Spring Cloud microservices architecture** with 6 independently deployable services communicating via synchronous REST (OpenFeign) and routing through a centralized API Gateway. Service discovery is handled by Netflix Eureka, and centralized configuration is served by Spring Cloud Config Server backed by a Git repository.

```
                    +-----------------------+
                    |   External Clients    |
                    +-----------+-----------+
                                |
                    +-----------v-----------+
                    |   API Gateway (:8082) |  <-- OAuth2/JWT via Keycloak
                    +-----------+-----------+
                                |
         +----------------------+----------------------+
         |                      |                      |
+--------v--------+  +---------v---------+  +---------v---------+
| User Service    |  | Fund Transfer     |  | Utility Payment   |
| (:8083)         |  | Service (:8084)   |  | Service (:8085)   |
+--------+--------+  +---------+---------+  +---------+---------+
         |                      |                      |
         |           +----------v----------+           |
         +---------->| Core Banking Service|<----------+
                     | (:8092)             |
                     +----------+----------+
                                |
                     +----------v----------+
                     |   MySQL Database    |
                     |   (:3306)           |
                     +---------------------+

Supporting Infrastructure:
  - Service Registry (Eureka) (:8081)
  - Config Server (:8090) -> Git repo for config
  - Keycloak (:8080) + PostgreSQL
  - Zipkin (:9411) for distributed tracing
```

### 1.2 Services Inventory

| # | Service | Port | Purpose | Database |
|---|---------|------|---------|----------|
| 1 | `internet-banking-config-server` | 8090 | Centralized configuration management via Spring Cloud Config, backed by a Git repository | None |
| 2 | `internet-banking-service-registry` | 8081 | Netflix Eureka service discovery server | None |
| 3 | `internet-banking-api-gateway` | 8082 | Spring Cloud Gateway - routing, security (OAuth2/JWT), header propagation | None |
| 4 | `internet-banking-user-service` | 8083 | User registration, management, Keycloak integration | MySQL (`banking_core_user_service`) |
| 5 | `internet-banking-fund-transfer-service` | 8084 | Fund transfer orchestration between bank accounts | MySQL (`banking_core_fund_transfer_service`) |
| 6 | `internet-banking-utility-payment-service` | 8085 | Utility bill payment processing | MySQL (`banking_core_utility_payment_service`) |
| 7 | `core-banking-service` | 8092 | Core banking engine - accounts, users, transactions | MySQL (`banking_core_service`) |

### 1.3 Communication Patterns

| Pattern | Technology | Usage |
|---------|-----------|-------|
| **Synchronous REST** | Spring Cloud OpenFeign | Service-to-service calls (User/Fund Transfer/Utility Payment -> Core Banking) |
| **Service Discovery** | Netflix Eureka | All services register with Eureka; Feign clients resolve service names |
| **API Gateway** | Spring Cloud Gateway | Single entry point; routes requests to downstream services by path prefix |
| **Auth Header Propagation** | Custom `GlobalFilter` | Gateway extracts JWT principal name and forwards it as `X-Auth-Id` header |
| **Configuration** | Spring Cloud Config | All services fetch configuration from a centralized Git-backed config server |
| **Distributed Tracing** | Micrometer Tracing + Zipkin | Trace propagation via Brave across all services |

### 1.4 Infrastructure Components

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Database (app) | MySQL 8 | Persistent storage for all business services |
| Database (Keycloak) | PostgreSQL 15 | Keycloak identity store |
| Identity Provider | Keycloak 23.0.7 | OAuth2/OIDC authentication and authorization |
| Distributed Tracing | Zipkin 3 | Trace collection and visualization |
| Service Registry | Netflix Eureka | Service discovery |
| Config Server | Spring Cloud Config | Externalized configuration via Git |
| Container Runtime | Docker / Docker Compose | Containerized deployment |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service Database (`banking_core_service`)

#### `banking_core_user`

| Column | Type | Constraints | Description |
|--------|------|------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Unique user identifier |
| `first_name` | VARCHAR(255) | | User's first name |
| `last_name` | VARCHAR(255) | | User's last name |
| `email` | VARCHAR(255) | | User's email address |
| `identification_number` | VARCHAR(255) | | National ID / identification number |

#### `banking_core_account`

| Column | Type | Constraints | Description |
|--------|------|------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Unique account identifier |
| `number` | VARCHAR(255) | | Account number (e.g., `100015003000`) |
| `type` | VARCHAR(255) | ENUM(`SAVINGS_ACCOUNT`, ...) | Account type |
| `status` | VARCHAR(255) | ENUM(`ACTIVE`, ...) | Account status |
| `available_balance` | DECIMAL(19,2) | | Available balance |
| `actual_balance` | DECIMAL(19,2) | | Actual/ledger balance |
| `user_id` | BIGINT | FK -> `banking_core_user.id` | Owning user |

#### `banking_core_transaction`

| Column | Type | Constraints | Description |
|--------|------|------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Unique transaction identifier |
| `amount` | DECIMAL(19,2) | | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | NOT NULL, ENUM(`FUND_TRANSFER`, `UTILITY_PAYMENT`) | Type of transaction |
| `reference_number` | VARCHAR(50) | NOT NULL | Reference (destination account or utility ref) |
| `transaction_id` | VARCHAR(50) | NOT NULL | UUID transaction identifier |
| `account_id` | BIGINT | FK -> `banking_core_account.id` | Associated account |

#### `banking_core_utility_account`

| Column | Type | Constraints | Description |
|--------|------|------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Unique utility account identifier |
| `number` | VARCHAR(255) | | Utility account number |
| `provider_name` | VARCHAR(255) | | Utility provider name (e.g., VODAFONE, VERIZON) |

### 2.2 User Service Database (`banking_core_user_service`)

#### `user`

| Column | Type | Constraints | Description |
|--------|------|------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Unique user identifier |
| `auth_id` | VARCHAR | | Keycloak user ID (UUID) |
| `identification` | VARCHAR | | National identification number |
| `status` | VARCHAR | ENUM(`PENDING`, `APPROVED`) | Registration status |
| `created_date` | TIMESTAMP | Auto (audit) | Record creation timestamp |
| `created_by` | VARCHAR | Auto (audit) | Creator identifier |
| `modified_date` | TIMESTAMP | Auto (audit) | Last modification timestamp |
| `modified_by` | VARCHAR | Auto (audit) | Modifier identifier |
| `version` | BIGINT | Optimistic lock | Version for optimistic locking |

### 2.3 Fund Transfer Service Database (`banking_core_fund_transfer_service`)

#### `fund_transfer`

| Column | Type | Constraints | Description |
|--------|------|------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Unique transfer identifier |
| `from_account` | VARCHAR | | Source account number |
| `to_account` | VARCHAR | | Destination account number |
| `amount` | DECIMAL | | Transfer amount |
| `transaction_reference` | VARCHAR | | Core banking transaction UUID |
| `status` | VARCHAR | ENUM(`PENDING`, `SUCCESS`, `PROCESSING`, `FAILED`) | Transfer status |
| `created_date` | TIMESTAMP | Auto (audit) | Record creation timestamp |
| `created_by` | VARCHAR | Auto (audit) | Creator identifier |
| `modified_date` | TIMESTAMP | Auto (audit) | Last modification timestamp |
| `modified_by` | VARCHAR | Auto (audit) | Modifier identifier |
| `version` | BIGINT | Optimistic lock | Version for optimistic locking |

### 2.4 Utility Payment Service Database (`banking_core_utility_payment_service`)

#### `utility_payment`

| Column | Type | Constraints | Description |
|--------|------|------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Unique payment identifier |
| `provider_id` | BIGINT | | Utility provider identifier |
| `amount` | DECIMAL | | Payment amount |
| `reference_number` | VARCHAR | | Utility reference number |
| `account` | VARCHAR | | Source bank account number |
| `transaction_id` | VARCHAR | | Core banking transaction UUID |
| `status` | VARCHAR | ENUM(`PROCESSING`, `SUCCESS`, `PENDING`, `FAILED`) | Payment status |
| `created_date` | TIMESTAMP | Auto (audit) | Record creation timestamp |
| `created_by` | VARCHAR | Auto (audit) | Creator identifier |
| `modified_date` | TIMESTAMP | Auto (audit) | Last modification timestamp |
| `modified_by` | VARCHAR | Auto (audit) | Modifier identifier |
| `version` | BIGINT | Optimistic lock | Version for optimistic locking |

### 2.5 Entity Relationships

```
Core Banking Service:
  banking_core_user 1──* banking_core_account (user_id FK)
  banking_core_account 1──1 banking_core_transaction (account_id FK)
  banking_core_utility_account (standalone)

User Service:
  user (standalone, links to Keycloak via auth_id and Core Banking via identification)

Fund Transfer Service:
  fund_transfer (standalone, references Core Banking accounts by account number strings)

Utility Payment Service:
  utility_payment (standalone, references Core Banking accounts by account number strings)
```

---

## 3. API Surface Map

### 3.1 API Gateway Routes

All external requests enter through the API Gateway (`localhost:8082`). The gateway routes by path prefix:

| Path Prefix | Target Service | Auth Required |
|------------|----------------|---------------|
| `/user/**` | `internet-banking-user-service` | Yes (except `/user/api/v1/bank-users/register`) |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` | Yes |
| `/banking-core/**` | `core-banking-service` | Yes |
| `/utility-payment/**` | `internet-banking-utility-payment-service` | Yes |
| `/actuator/**` | Various | No |

### 3.2 Core Banking Service (`/api/v1/...` on port 8092)

#### Account Controller (`/api/v1/account`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | - | `BankAccount` (id, number, type, status, availableBalance, actualBalance) |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | - | `UtilityAccount` (id, number, providerName) |

#### User Controller (`/api/v1/user`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `GET` | `/api/v1/user/{identification}` | Get user by identification number | - | `User` (id, firstName, lastName, email, identificationNumber, accounts[]) |
| `GET` | `/api/v1/user` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |

#### Transaction Controller (`/api/v1/transaction`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer | `FundTransferRequest` (fromAccount, toAccount, amount) | `FundTransferResponse` (message, transactionId) |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `UtilityPaymentRequest` (account, providerId, amount, referenceNumber) | `UtilityPaymentResponse` (message, transactionId) |

### 3.3 Internet Banking User Service (`/api/v1/bank-users` on port 8083)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/bank-users/register` | Register new user | `User` (identification, email, password) | `User` (id, authId, identification, status) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user (e.g., approve) | `UserUpdateRequest` (status) | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | - | `User` |

### 3.4 Internet Banking Fund Transfer Service (`/api/v1/transfer` on port 8084)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `FundTransferRequest` (fromAccount, toAccount, amount) | `FundTransferResponse` (message, transactionId) |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | Query: `page`, `size`, `sort` | `List<FundTransfer>` |

### 3.5 Internet Banking Utility Payment Service (`/api/v1/utility-payment` on port 8085)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `UtilityPaymentRequest` (providerId, amount, referenceNumber, account) | `UtilityPaymentResponse` (message, transactionId) |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | Query: `page`, `size`, `sort` | `List<UtilityPayment>` |

### 3.6 Infrastructure Endpoints

| Service | Endpoint | Description |
|---------|----------|-------------|
| Service Registry | `GET /` (port 8081) | Eureka dashboard |
| Actuator (all services) | `GET /actuator/health` | Health check |
| Actuator (all services) | `GET /actuator/info` | Application info |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

**Location:** `internet-banking-user-service` -> `UserService.createUser()`

1. **Duplicate check:** Query Keycloak by email; reject if user already exists (`UserAlreadyRegisteredException`)
2. **Core Banking validation:** Call Core Banking Service via Feign to verify the identification number exists
3. **Email validation:** Compare email from Core Banking with the provided email (`InvalidEmailException` on mismatch)
4. **Keycloak user creation:** Create a Keycloak `UserRepresentation` with email, name, credentials; user is created as disabled and email-unverified
5. **Local persistence:** Retrieve Keycloak auth ID, save user entity with `PENDING` status

### 4.2 User Approval Flow

**Location:** `internet-banking-user-service` -> `UserService.updateUser()`

1. If status update is `APPROVED`, fetch Keycloak user by `authId`
2. Enable user and mark email as verified in Keycloak
3. Update local user entity status to `APPROVED`

### 4.3 Fund Transfer Rules

**Location:** `internet-banking-fund-transfer-service` -> `FundTransferService.fundTransfer()` and `core-banking-service` -> `TransactionService.fundTransfer()`

**Orchestration (Fund Transfer Service):**
1. Save a local `FundTransferEntity` with `PENDING` status
2. Call Core Banking Service via Feign to execute the transfer
3. Update local entity with transaction reference and `SUCCESS` status

**Execution (Core Banking Service):**
1. Read source and destination `BankAccount` from the database
2. **Balance validation:** Source account `actualBalance` must be >= 0 AND >= transfer amount; otherwise throw `InsufficientFundsException`
3. **Debit source:** Subtract amount from `actualBalance`; recalculate `availableBalance` (Note: there is a bug - `availableBalance` is set to `actualBalance - amount` again, effectively double-subtracting)
4. **Record debit transaction:** Save `TransactionEntity` with negative amount, type `FUND_TRANSFER`
5. **Credit destination:** Add amount to `actualBalance`; recalculate `availableBalance` (same double-add bug)
6. **Record credit transaction:** Save `TransactionEntity` with positive amount
7. Return UUID transaction ID

### 4.4 Utility Payment Processing

**Location:** `internet-banking-utility-payment-service` -> `UtilityPaymentService.utilPayment()` and `core-banking-service` -> `TransactionService.utilPayment()`

**Orchestration (Utility Payment Service):**
1. Save a local `UtilityPaymentEntity` with `PROCESSING` status
2. Call Core Banking Service via Feign to process payment
3. Update local entity with transaction ID and `SUCCESS` status

**Execution (Core Banking Service):**
1. Read source `BankAccount`
2. **Balance validation:** Same as fund transfer
3. Read `UtilityAccount` by provider ID
4. **Debit source account:** Subtract amount (same double-subtract bug as fund transfer)
5. **Record transaction:** Save `TransactionEntity` with negative amount, type `UTILITY_PAYMENT`
6. Return UUID transaction ID

### 4.5 Identified Balance Calculation Bug

In `TransactionService.internalFundTransfer()` and `TransactionService.utilPayment()`, the available balance is incorrectly calculated:

```java
// Bug: actualBalance is already updated, so this subtracts the amount twice
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
```

The second line should be:
```java
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance());
```

### 4.6 Auth Header Propagation

**Location:** `internet-banking-api-gateway` -> `GatewayConfiguration`

The API Gateway extracts the JWT principal name and propagates it as `X-Auth-Id` header to downstream services. Each downstream service has an `AppAuthUserFilter` that reads this header and stores it in a `ThreadLocal`-based `ApiRequestContextHolder` for use in audit fields.

---

## 5. Integration Points

### 5.1 Keycloak (Identity Provider)

| Aspect | Detail |
|--------|--------|
| **Version** | 23.0.7 |
| **Integration point** | `internet-banking-user-service` via `keycloak-admin-client:24.0.4` |
| **Auth flow** | `client_credentials` grant type |
| **Configuration** | `app.config.keycloak.*` properties (server-url, realm, clientId, client-secret) |
| **Realm** | `javatodev-internet-banking` |
| **Client** | `internet-banking-api-client` |
| **API Gateway** | Validates JWT tokens using `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` |
| **Operations** | Create user, update user, search user by email, read user by ID |

### 5.2 RabbitMQ (Message Broker)

| Aspect | Detail |
|--------|--------|
| **Status** | Referenced in architecture diagrams but **NOT implemented** in current code |
| **Intended use** | Fund transfer and utility payment services were to push notification messages to RabbitMQ for a Notification Service |
| **Notification Service** | Listed as "PENDING Development" in README |

### 5.3 Zipkin (Distributed Tracing)

| Aspect | Detail |
|--------|--------|
| **Version** | Zipkin 3 (Docker image) |
| **Port** | 9411 |
| **Integration** | `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` in all services |
| **Feign tracing** | `feign-micrometer` dependency included for trace propagation across Feign calls |

### 5.4 Database Connections

| Service | Database | Schema | Driver | Migration |
|---------|----------|--------|--------|-----------|
| Core Banking | MySQL 8 | `banking_core_service` | `mysql-connector-j:8.4.0` | Flyway (`flyway-core:10.12.0`, `flyway-mysql:10.12.0`) |
| User Service | MySQL 8 | `banking_core_user_service` | `mysql-connector-j:8.4.0` | JPA auto (no Flyway) |
| Fund Transfer | MySQL 8 | `banking_core_fund_transfer_service` | `mysql-connector-j:8.4.0` | JPA auto (no Flyway) |
| Utility Payment | MySQL 8 | `banking_core_utility_payment_service` | `mysql-connector-j:8.4.0` | JPA auto (no Flyway) |
| Keycloak | PostgreSQL 15 | `keycloak` | Internal | Keycloak-managed |

### 5.5 Inter-Service Communication (Feign Clients)

| Source Service | Target Service | Feign Client | Endpoints Called |
|---------------|---------------|--------------|-----------------|
| User Service | Core Banking | `BankingCoreRestClient` | `GET /api/v1/user/{identification}` |
| Fund Transfer | Core Banking | `BankingCoreFeignClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/fund-transfer` |
| Utility Payment | Core Banking | `BankingCoreRestClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/util-payment` |

### 5.6 Spring Cloud Config Server

| Aspect | Detail |
|--------|--------|
| **Port** | 8090 |
| **Backend** | Git repository: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` |
| **Branch** | `main` |
| **Search path** | `configuration` |
| **Consumers** | All application services bootstrap from this config server |

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

| Aspect | Detail |
|--------|--------|
| **Build tool** | Gradle (per-service `build.gradle`, no multi-project build) |
| **Java version** | 21 (Eclipse Temurin) |
| **Spring Boot** | 3.2.4 |
| **Spring Cloud** | 2023.0.0 |
| **Dependency management** | Spring Cloud BOM |
| **Plugins** | `spring-boot`, `io.spring.dependency-management`, `com.gorylenko.gradle-git-properties` |
| **Test framework** | JUnit 5 (`useJUnitPlatform()`) |

### 6.2 Docker Configuration

Each service has its own `Dockerfile`:

```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh
RUN apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

**Key characteristics:**
- JRE-only Alpine base image for minimal footprint
- `wait-for-it.sh` script for startup ordering (waits for Service Registry, Config Server, MySQL)
- Docker profile activation via `-Dspring.profiles.active=docker`

### 6.3 Docker Compose

Two compose files exist:

1. **`docker-compose.yml`** - Full stack deployment (all services + infrastructure)
2. **`docker-compose-support-apps.yml`** - Infrastructure only (Zipkin, Keycloak, MySQL, Config Server, Service Registry)

**Startup ordering** is managed via `wait-for-it.sh` in container entrypoints:
1. Service Registry + Config Server + MySQL start first
2. Application services wait for all three before starting

**Network:** Custom bridge network `javatodev_ib_network` with subnet `172.25.0.0/16` and static IP assignments.

### 6.4 Database Initialization

- **MySQL:** Custom Dockerfile in `docker-compose/mysql/` runs `privileges.sql` to create the development user and all four databases
- **Core Banking migrations:** Flyway manages schema and seed data with versioned SQL scripts
- **Keycloak:** Realm configuration imported from `docker-compose/keycloak/realm-export.json`

### 6.5 Test Configuration

- **Core Banking Service:** H2 in-memory database for tests, Flyway disabled, Eureka pointing to localhost
- **User/Fund Transfer/Utility Payment Services:** H2 in-memory database, Flyway disabled, Eureka disabled
- **Test scope dependencies:** `spring-boot-starter-test`, `h2:2.2.224`

### 6.6 OpenAPI Documentation

- **Library:** `springdoc-openapi-starter-webflux-ui:2.1.0`
- **Annotations:** `@Tag` and `@Operation` annotations present on controllers in core-banking-service, user-service, fund-transfer-service, and utility-payment-service
- **Swagger UI:** Available at `/swagger-ui.html` on each service (when running)
