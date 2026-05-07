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

### System Summary

This is a **Java 21 / Spring Boot 3.2.4** internet banking application built on a microservices architecture using **Spring Cloud 2023.0.0**. It implements a banking domain with fund transfers, utility payments, and user management.

### Services

| Service | Port | Purpose | Key Dependencies |
|---|---|---|---|
| **core-banking-service** | 8092 | Core banking engine: accounts, users, transactions, balance management | MySQL, Eureka Client, Spring Data JPA, Flyway |
| **internet-banking-user-service** | 8083 | Internet banking user registration, approval workflows, Keycloak integration | MySQL, Eureka Client, OpenFeign, Keycloak Admin Client |
| **internet-banking-fund-transfer-service** | 8084 | Fund transfer orchestration between accounts | MySQL, Eureka Client, OpenFeign |
| **internet-banking-utility-payment-service** | 8085 | Utility bill payment orchestration | MySQL, Eureka Client, OpenFeign |
| **internet-banking-api-gateway** | 8082 | Single entry point: routing, OAuth2/JWT security, principal propagation | Spring Cloud Gateway, OAuth2 Resource Server, Eureka Client |
| **internet-banking-service-registry** | 8081 | Eureka Server for service discovery | Spring Cloud Netflix Eureka Server |
| **internet-banking-config-server** | 8090 | Centralized configuration backed by Git repository | Spring Cloud Config Server |

### Communication Patterns

```
                         +-----------------+
                         |   API Gateway   |
                         |   (8082)        |
                         +--------+--------+
                                  |
                    +-------------+-------------+
                    |             |              |
              +-----v----+ +-----v------+ +-----v--------+
              |  User Svc | | Fund Xfer  | | Util Payment |
              |  (8083)   | | Svc (8084) | | Svc (8085)   |
              +-----+-----+ +-----+------+ +------+-------+
                    |              |                |
                    +--------------+----------------+
                                   |
                           +-------v--------+
                           | Core Banking   |
                           | Service (8092) |
                           +----------------+
```

- **Synchronous (HTTP/REST via OpenFeign):** All inter-service calls use Spring Cloud OpenFeign clients with Eureka service discovery (no hardcoded URLs).
  - `user-service` --> `core-banking-service` (user lookup by identification)
  - `fund-transfer-service` --> `core-banking-service` (account lookup, fund transfer execution)
  - `utility-payment-service` --> `core-banking-service` (account lookup, utility payment execution)
- **Asynchronous (RabbitMQ):** Mentioned in README for notification service but **not implemented** in the current codebase.
- **Service Discovery:** Netflix Eureka (service registry at port 8081). All services register as Eureka clients.
- **Centralized Configuration:** Spring Cloud Config Server fetches configuration from a remote Git repository (`https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`).

### Infrastructure Components

| Component | Technology | Purpose |
|---|---|---|
| Service Discovery | Netflix Eureka | Service registration and lookup |
| API Gateway | Spring Cloud Gateway (WebFlux) | Request routing, security enforcement, principal propagation |
| Config Server | Spring Cloud Config | Externalized configuration from Git |
| Identity Provider | Keycloak 23.0.7 | OAuth2/OIDC authentication, user management |
| Database | MySQL 8.4.0 | Persistent storage for all business services |
| Distributed Tracing | Zipkin 3 + Micrometer Brave | Request tracing across services |
| Database (Keycloak) | PostgreSQL 15 | Keycloak internal storage |

### Security Architecture

- **API Gateway** enforces OAuth2/JWT authentication via Keycloak's JWK Set URI.
- The gateway extracts the authenticated principal name and propagates it downstream via the `X-Auth-Id` HTTP header using a `GlobalFilter`.
- Each downstream service has an `AppAuthUserFilter` (servlet filter) that reads `X-Auth-Id` and stores it in a `ThreadLocal` (`ApiRequestContextHolder`) for audit purposes.
- The `/user/api/v1/bank-users/register` endpoint is explicitly permitted without authentication.
- All `/actuator/**` endpoints are permitted without authentication.
- CSRF is disabled on the gateway.

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL: `banking_core_service`)

#### `banking_core_user`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto) | Unique user ID |
| `first_name` | VARCHAR(255) | First name |
| `last_name` | VARCHAR(255) | Last name |
| `email` | VARCHAR(255) | Email address |
| `identification_number` | VARCHAR(255) | National ID / identification number |

**Relationships:** One user has many bank accounts (`@OneToMany` with `BankAccountEntity`).

#### `banking_core_account`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto) | Unique account ID |
| `number` | VARCHAR(255) | Account number (e.g., `100015003000`) |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `available_balance` | DECIMAL(19,2) | Available balance |
| `actual_balance` | DECIMAL(19,2) | Actual/ledger balance |
| `user_id` | BIGINT (FK) | References `banking_core_user.id` |

**Relationships:** Many accounts belong to one user (`@ManyToOne`). Referenced by transactions (`@OneToOne` in `TransactionEntity`).

#### `banking_core_utility_account`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto) | Unique utility account ID |
| `number` | VARCHAR(255) | Utility account number |
| `provider_name` | VARCHAR(255) | Provider name (e.g., `VODAFONE`, `VERIZON`) |

#### `banking_core_transaction`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto) | Unique transaction ID |
| `amount` | DECIMAL(19,2) | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Reference (target account number or utility reference) |
| `transaction_id` | VARCHAR(50) | UUID transaction identifier |
| `account_id` | BIGINT (FK) | References `banking_core_account.id` |

**Note:** Schema managed by Flyway migrations in `core-banking-service/src/main/resources/db/migration/`.

### 2.2 User Service (MySQL: `banking_core_user_service`)

#### `user` (JPA-managed, no Flyway)

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto) | Unique user ID |
| `auth_id` | VARCHAR | Keycloak user UUID |
| `identification` | VARCHAR | National ID / identification number |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | TIMESTAMP | Audit: creation timestamp |
| `created_by` | VARCHAR | Audit: creator ID |
| `modified_date` | TIMESTAMP | Audit: last modification timestamp |
| `modified_by` | VARCHAR | Audit: modifier ID |
| `version` | BIGINT | Optimistic locking version |

**Extends:** `AuditAware` (JPA auditing with `@CreatedDate`, `@CreatedBy`, `@LastModifiedDate`, `@LastModifiedBy`, `@Version`).

### 2.3 Fund Transfer Service (MySQL: `banking_core_fund_transfer_service`)

#### `fund_transfer` (JPA-managed, no Flyway)

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto) | Unique transfer ID |
| `transaction_reference` | VARCHAR | UUID transaction reference from core banking |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `status` | VARCHAR | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` | TIMESTAMP | Audit field |
| `created_by` | VARCHAR | Audit field |
| `modified_date` | TIMESTAMP | Audit field |
| `modified_by` | VARCHAR | Audit field |
| `version` | BIGINT | Optimistic locking |

### 2.4 Utility Payment Service (MySQL: `banking_core_utility_payment_service`)

#### `utility_payment` (JPA-managed, no Flyway)

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto) | Unique payment ID |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Customer reference |
| `account` | VARCHAR | Source bank account number |
| `transaction_id` | VARCHAR | UUID transaction reference from core banking |
| `status` | VARCHAR | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` | TIMESTAMP | Audit field |
| `created_by` | VARCHAR | Audit field |
| `modified_date` | TIMESTAMP | Audit field |
| `modified_by` | VARCHAR | Audit field |
| `version` | BIGINT | Optimistic locking |

---

## 3. API Surface Map

### 3.1 API Gateway Routes

All requests flow through the gateway at port **8082**. The gateway routes are configured via Spring Cloud Config (external Git repo). Based on the security configuration path matchers:

| Gateway Path Prefix | Target Service |
|---|---|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/banking-core/**` | core-banking-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |

### 3.2 Core Banking Service (port 8092)

#### Account Controller (`/api/v1/account`)

| Method | Path | Description | Request | Response |
|---|---|---|---|---|
| `GET` | `/bank-account/{account_number}` | Get bank account by account number | Path: `account_number` (String) | `BankAccount` (id, number, type, status, availableBalance, actualBalance, user) |
| `GET` | `/util-account/{account_name}` | Get utility account by provider name | Path: `account_name` (String) | `UtilityAccount` (id, number, providerName) |

#### User Controller (`/api/v1/user`)

| Method | Path | Description | Request | Response |
|---|---|---|---|---|
| `GET` | `/{identification}` | Get user by identification number | Path: `identification` (String) | `User` (id, firstName, lastName, email, identificationNumber, bankAccounts[]) |
| `GET` | `/` | List all users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |

#### Transaction Controller (`/api/v1/transaction`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/fund-transfer` | Process fund transfer | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `POST` | `/util-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |

### 3.3 Internet Banking User Service (port 8083)

#### User Controller (`/api/v1/bank-users`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/register` | Register a new internet banking user | `{ email, identification, password }` | `User` (id, email, identification, authId, status) |
| `PATCH` | `/update/{id}` | Update user status (approve/disable) | `{ status }` (PENDING/APPROVED/DISABLED/BLACKLIST) | `User` |
| `GET` | `/` | List all users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |
| `GET` | `/{id}` | Get user by ID | Path: `id` (Long) | `User` |

### 3.4 Internet Banking Fund Transfer Service (port 8084)

#### Fund Transfer Controller (`/api/v1/transfer`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/` | Initiate a fund transfer | `{ fromAccount, toAccount, amount, authID }` | `{ message, transactionId }` |
| `GET` | `/` | List all fund transfers (paginated) | Query: `page`, `size`, `sort` | `List<FundTransfer>` |

### 3.5 Internet Banking Utility Payment Service (port 8085)

#### Utility Payment Controller (`/api/v1/utility-payment`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/` | Process a utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/` | List all utility payments (paginated) | Query: `page`, `size`, `sort` | `List<UtilityPayment>` |

### 3.6 Infrastructure Endpoints

| Service | Endpoint | Description |
|---|---|---|
| All services | `/actuator/health` | Health check (Spring Boot Actuator) |
| All services | `/actuator/info` | Application info (git properties) |
| Service Registry | `http://localhost:8081/` | Eureka dashboard |
| Config Server | `http://localhost:8090/{app}/{profile}` | Configuration retrieval |
| Zipkin | `http://localhost:9411/` | Distributed tracing UI |
| Keycloak | `http://localhost:8080/` | Identity management console |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

**Location:** `internet-banking-user-service` > `UserService.createUser()`

1. Check if email is already registered in Keycloak (via `KeycloakUserService.readUserByEmail()`).
2. If email exists, throw `UserAlreadyRegisteredException`.
3. Look up the user in core banking service by identification number (via OpenFeign to `core-banking-service`).
4. If found, validate that the email matches the core banking record. If mismatch, throw `InvalidEmailException`.
5. Create a Keycloak user representation with the email, name, and provided password. Set `emailVerified=false` and `enabled=false`.
6. Call Keycloak Admin API to create the user.
7. If creation succeeds (HTTP 201), retrieve the Keycloak user ID, save the user entity locally with `status=PENDING`.
8. If user not found in core banking, throw `InvalidBankingUserException`.

### 4.2 User Approval Flow

**Location:** `internet-banking-user-service` > `UserService.updateUser()`

1. Look up user entity by ID.
2. If the new status is `APPROVED`, read the Keycloak user representation.
3. Set `enabled=true` and `emailVerified=true` in Keycloak.
4. Update the local user entity status.

### 4.3 Fund Transfer Flow

**Location:** `internet-banking-fund-transfer-service` > `FundTransferService.fundTransfer()`

1. Create a local `FundTransferEntity` with status `PENDING` and persist it.
2. Call `core-banking-service` via OpenFeign to execute the actual fund transfer.
3. On success, update the local entity with the transaction reference and set status to `SUCCESS`.
4. Return success response.

**Core Banking Logic:** `core-banking-service` > `TransactionService.fundTransfer()`

1. Read both source and destination bank accounts.
2. Validate the source account has sufficient funds (`actualBalance >= amount`).
3. Debit the source account (`actualBalance -= amount`, `availableBalance = actualBalance - amount`).
4. Create a debit transaction record.
5. Credit the destination account (`actualBalance += amount`, `availableBalance = actualBalance + amount`).
6. Create a credit transaction record.
7. Return the transaction ID (UUID).

**Note:** The entire operation is `@Transactional` to ensure atomicity.

### 4.4 Utility Payment Flow

**Location:** `internet-banking-utility-payment-service` > `UtilityPaymentService.utilPayment()`

1. Create a local `UtilityPaymentEntity` with status `PROCESSING` and persist it.
2. Call `core-banking-service` via OpenFeign to process the payment.
3. On success, update the local entity with the transaction ID and set status to `SUCCESS`.
4. Return success response.

**Core Banking Logic:** `core-banking-service` > `TransactionService.utilPayment()`

1. Read the source bank account.
2. Validate sufficient funds.
3. Read the utility account by provider ID.
4. Debit the source account.
5. Create a utility payment transaction record.
6. Return the transaction ID (UUID).

### 4.5 Balance Validation Rules

**Location:** `core-banking-service` > `TransactionService.validateBalance()`

- Throws `InsufficientFundsException` if `actualBalance < 0` OR `actualBalance < requestedAmount`.

### 4.6 Audit Trail

- All entities in user-service, fund-transfer-service, and utility-payment-service extend `AuditAware`.
- Tracks: `createdDate`, `createdBy`, `modifiedDate`, `modifiedBy`, and optimistic locking `version`.
- The `createdBy`/`modifiedBy` fields are populated from the `X-Auth-Id` header via the `AuditorAwareConfig`.

---

## 5. Integration Points

### 5.1 Keycloak (Identity Provider)

- **Version:** 23.0.7
- **Protocol:** HTTP REST (Keycloak Admin Client SDK, version 24.0.4)
- **Used by:** `internet-banking-user-service` exclusively
- **Operations:** Create user, read user, update user (enable/verify), search by email
- **Configuration:** `app.config.keycloak.server-url`, `app.config.keycloak.realm`, `app.config.keycloak.clientId`, `app.config.keycloak.client-secret` (from Spring Cloud Config)
- **Authentication:** Client credentials grant (`grant_type=client_credentials`)
- **Gateway Integration:** JWT validation via `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`

### 5.2 RabbitMQ (Message Broker)

- **Status:** Referenced in README but **not implemented** in the codebase.
- **Intended use:** Push notification messages from fund-transfer-service and utility-payment-service to a notification service.

### 5.3 Zipkin (Distributed Tracing)

- **Version:** 3
- **Protocol:** HTTP (Brave/Micrometer tracing bridge)
- **Used by:** All business services (core-banking, user, fund-transfer, utility-payment) and the API gateway.
- **Dependencies:** `micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`, `feign-micrometer`
- **Dashboard:** `http://localhost:9411`

### 5.4 Database Connections

| Service | Database | Schema |
|---|---|---|
| core-banking-service | MySQL 8.4.0 | `banking_core_service` |
| internet-banking-user-service | MySQL 8.4.0 | `banking_core_user_service` |
| internet-banking-fund-transfer-service | MySQL 8.4.0 | `banking_core_fund_transfer_service` |
| internet-banking-utility-payment-service | MySQL 8.4.0 | `banking_core_utility_payment_service` |
| Keycloak | PostgreSQL 15 | `keycloak` |

- **MySQL credentials:** Root password `woVERANKliGharym`, application user `javatodev_development` / `oPItyPticIAt` (created via `privileges.sql`).
- **Connection details:** Configured via Spring Cloud Config Server (external Git repository).

### 5.5 Spring Cloud Config (External Configuration)

- **Config Server Port:** 8090
- **Git Repository:** `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch:** `main`
- **Search Path:** `configuration`
- **Bootstrap profiles:** `default` (localhost), `dev` (192.168.1.5), `docker` (container DNS)

### 5.6 Eureka Service Discovery

- **Registry Port:** 8081
- All business services and the gateway register as Eureka clients.
- OpenFeign clients resolve service names (e.g., `core-banking-service`) through Eureka.

---

## 6. Build and Deployment Pipeline Summary

### Build System

- **Build Tool:** Gradle (per-service, no multi-module parent project)
- **Gradle Version:** 8.6 (via Gradle Wrapper)
- **Java Version:** 21 (Eclipse Temurin)
- **Spring Boot:** 3.2.4
- **Spring Cloud:** 2023.0.0
- **Plugin:** `com.gorylenko.gradle-git-properties` (2.4.2) generates `git.properties` for Actuator `/info` endpoint.

### Docker

Each service has its own `Dockerfile`:

```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh && apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

- Uses `wait-for-it.sh` for startup ordering (waits for config-server, service-registry, and MySQL).
- Docker images are tagged under `javatodev/` namespace.

### Docker Compose

Two compose files in `docker-compose/`:

1. **`docker-compose.yml`** - Full stack (all services + infrastructure)
2. **`docker-compose-support-apps.yml`** - Infrastructure only (Zipkin, Keycloak, MySQL, Config Server, Service Registry)

**Network:** Custom bridge network `javatodev_ib_network` with subnet `172.25.0.0/16` and static IP assignments.

**Startup Order:** Managed via `wait-for-it.sh` entrypoints:
1. Service Registry (8081) and Config Server (8090) start first
2. Business services wait for both + MySQL before starting

### Database Migration

- **Core Banking Service:** Uses Flyway with 3 versioned migrations:
  - `V1.0.20210427174638` - Creates base tables (user, account, utility_account)
  - `V1.0.20210427174721` - Seeds test data (4 users, 14 accounts, 6 utility providers)
  - `V1.0.20210429210839` - Creates transaction table
- **Other services:** Rely on JPA/Hibernate auto-DDL (no Flyway migrations).

### Test Data

Pre-seeded credentials for testing:
- **Keycloak:** `ib_admin@javatodev.com` / `5V7huE3G86uB`
- **Core Banking Users:** Sam Silva (808829932V), Guru Darmaraj (901830556V), Ragu Sivaraj (348829932V), Randor Manoon (842829932V)
- **Utility Providers:** VODAFONE, VERIZON, SINGTEL, HUTCH, AIRTEL, GIO
