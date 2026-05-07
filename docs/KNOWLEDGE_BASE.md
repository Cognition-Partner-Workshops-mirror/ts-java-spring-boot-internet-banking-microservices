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

This is an **Internet Banking** application built with **Java 21** and **Spring Boot 3.2.4** using a microservices architecture. It implements core banking operations including user management, fund transfers, and utility payments across **6 microservices**, orchestrated via Docker Compose.

### Services

| Service | Port | Purpose |
|---|---|---|
| **core-banking-service** | 8092 | Core banking engine: manages accounts, users, and processes financial transactions (fund transfers, utility payments) |
| **internet-banking-user-service** | 8083 | Internet banking user registration and management; integrates with Keycloak for identity |
| **internet-banking-fund-transfer-service** | 8084 | Orchestrates fund transfer requests; delegates to core-banking-service via Feign |
| **internet-banking-utility-payment-service** | 8085 | Orchestrates utility payment requests; delegates to core-banking-service via Feign |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway; routes all external traffic, enforces OAuth2/JWT authentication |
| **internet-banking-service-registry** | 8081 | Netflix Eureka server for service discovery |
| **internet-banking-config-server** | 8090 | Spring Cloud Config Server; serves externalized configuration from a Git repository |

### Communication Patterns

```
                          ┌──────────────────┐
                          │   API Gateway    │ :8082
                          │  (Spring Cloud   │
                          │   Gateway + JWT) │
                          └────────┬─────────┘
                                   │
               ┌───────────────────┼───────────────────┐
               │                   │                   │
    ┌──────────▼──────┐ ┌─────────▼────────┐ ┌────────▼──────────┐
    │  User Service   │ │ Fund Transfer    │ │ Utility Payment   │
    │     :8083       │ │ Service :8084    │ │ Service :8085     │
    └──────┬──────────┘ └──────┬───────────┘ └──────┬────────────┘
           │                   │                    │
           │  Feign            │  Feign             │  Feign
           │                   │                    │
    ┌──────▼───────────────────▼────────────────────▼────────────┐
    │                   Core Banking Service                     │
    │                        :8092                               │
    └────────────────────────────────────────────────────────────┘
```

- **Client → API Gateway**: All external requests enter through the API Gateway (port 8082). The gateway enforces JWT authentication via Keycloak's OAuth2 resource server.
- **API Gateway → Downstream Services**: The gateway routes requests to downstream services using Eureka service discovery. It injects an `X-Auth-Id` header with the authenticated principal's name.
- **Downstream → Core Banking**: The user, fund-transfer, and utility-payment services call the core-banking-service via **OpenFeign** clients (service-to-service, using Eureka service names for resolution).
- **User Service → Keycloak**: The user service calls Keycloak Admin REST API directly (via `keycloak-admin-client` library) to create, read, and update users in the identity provider.
- **Service Discovery**: All services register with the **Eureka Service Registry** and discover each other by service name.
- **Centralized Configuration**: All services pull configuration from the **Config Server**, which reads from a remote Git repository (`internet-banking-microservices-configurations`).
- **RabbitMQ**: Referenced in documentation as a notification transport, but **not currently implemented** in the codebase (notification service is listed as "PENDING Development").

### Infrastructure Components

| Component | Technology | Purpose |
|---|---|---|
| Service Registry | Netflix Eureka | Service discovery and registration |
| Config Server | Spring Cloud Config | Centralized externalized configuration from Git |
| API Gateway | Spring Cloud Gateway | Request routing, security enforcement |
| Identity Provider | Keycloak 23.0.7 | OAuth2/OIDC authentication and user management |
| Database | MySQL 8.4.0 | Persistent storage for all services |
| Distributed Tracing | Zipkin 3 + Micrometer Brave | Trace propagation across services |
| Database Migration | Flyway 10.12.0 | Schema versioning (core-banking-service only) |

---

## 2. Data Model Documentation

### core-banking-service

This service owns the core banking domain. It uses **Flyway** for schema management and a **MySQL** database (`banking_core_service`).

#### Entities

**`banking_core_user`** — Bank customers

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `first_name` | VARCHAR(255) | Customer first name |
| `last_name` | VARCHAR(255) | Customer last name |
| `email` | VARCHAR(255) | Customer email address |
| `identification_number` | VARCHAR(255) | National identification (e.g., NIC number) |

**`banking_core_account`** — Bank accounts

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `number` | VARCHAR(255) | Account number |
| `type` | VARCHAR(255) | Account type enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | Account status enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `available_balance` | DECIMAL(19,2) | Available balance |
| `actual_balance` | DECIMAL(19,2) | Actual/ledger balance |
| `user_id` | BIGINT (FK → `banking_core_user.id`) | Account owner |

**`banking_core_transaction`** — Transaction records

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `amount` | DECIMAL(19,2) | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Reference (to-account number or utility ref) |
| `transaction_id` | VARCHAR(50) | UUID grouping both sides of a transfer |
| `account_id` | BIGINT (FK → `banking_core_account.id`) | Associated account |

**`banking_core_utility_account`** — Utility provider accounts

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `number` | VARCHAR(255) | Provider account number |
| `provider_name` | VARCHAR(255) | Provider name (e.g., VODAFONE, AIRTEL) |

#### Relationships

```
banking_core_user (1) ──── (*) banking_core_account
banking_core_account (1) ──── (*) banking_core_transaction
banking_core_utility_account (standalone, no FK relationships)
```

### internet-banking-user-service

Database: `banking_core_user_service`

**`user`** — Internet banking user registrations

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `auth_id` | VARCHAR | Keycloak user ID |
| `identification` | VARCHAR | Links to core banking user's identification number |
| `status` | VARCHAR (enum) | Registration status: `PENDING`, `APPROVED` |
| `created_date` | TIMESTAMP | Audit: creation timestamp |
| `created_by` | VARCHAR | Audit: creator |
| `modified_date` | TIMESTAMP | Audit: last modification timestamp |
| `modified_by` | VARCHAR | Audit: last modifier |
| `version` | BIGINT | Optimistic locking version |

### internet-banking-fund-transfer-service

Database: `banking_core_fund_transfer_service`

**`fund_transfer`** — Fund transfer request records

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `transaction_reference` | VARCHAR | Transaction ID returned from core banking |
| `status` | VARCHAR (enum) | Status: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` | TIMESTAMP | Audit field |
| `created_by` | VARCHAR | Audit field |
| `modified_date` | TIMESTAMP | Audit field |
| `modified_by` | VARCHAR | Audit field |
| `version` | BIGINT | Optimistic locking version |

### internet-banking-utility-payment-service

Database: `banking_core_utility_payment_service`

**`utility_payment`** — Utility payment request records

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Utility reference number |
| `account` | VARCHAR | Paying account number |
| `transaction_id` | VARCHAR | Transaction ID from core banking |
| `status` | VARCHAR (enum) | Status: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` | TIMESTAMP | Audit field |
| `created_by` | VARCHAR | Audit field |
| `modified_date` | TIMESTAMP | Audit field |
| `modified_by` | VARCHAR | Audit field |
| `version` | BIGINT | Optimistic locking version |

---

## 3. API Surface Map

### API Gateway Routes

All external traffic enters through the API Gateway (`:8082`). Routes are configured via Spring Cloud Config and use Eureka service discovery with path-based routing:

| Path Prefix | Target Service | Strip Prefix |
|---|---|---|
| `/user/**` | internet-banking-user-service | Yes |
| `/fund-transfer/**` | internet-banking-fund-transfer-service | Yes |
| `/utility-payment/**` | internet-banking-utility-payment-service | Yes |
| `/banking-core/**` | core-banking-service | Yes |

**Authentication**: All endpoints require a valid JWT token from Keycloak, **except**:
- `POST /user/api/v1/bank-users/register` — public (user registration)
- `/actuator/**` on all service prefixes — public (health/metrics)

### core-banking-service (`:8092`)

Base path: `/api/v1`

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | — | `BankAccount` |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | — | `UtilityAccount` |
| `GET` | `/api/v1/user/{identification}` | Get user by identification number | — | `User` |
| `GET` | `/api/v1/user` | List users (paginated) | — | `List<User>` |
| `POST` | `/api/v1/transaction/fund-transfer` | Process a fund transfer | `FundTransferRequest { fromAccount, toAccount, amount }` | `FundTransferResponse { message, transactionId }` |
| `POST` | `/api/v1/transaction/util-payment` | Process a utility payment | `UtilityPaymentRequest { providerId, amount, referenceNumber, account }` | `UtilityPaymentResponse { message, transactionId }` |

### internet-banking-user-service (`:8083`)

Base path: `/api/v1/bank-users`

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Register a new internet banking user | `User { email, identification, password }` | `User` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user (e.g., approve registration) | `UserUpdateRequest { status }` | `User` |
| `GET` | `/api/v1/bank-users` | List all users (paginated) | — | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | — | `User` |

### internet-banking-fund-transfer-service (`:8084`)

Base path: `/api/v1/transfer`

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate a fund transfer | `FundTransferRequest { fromAccount, toAccount, amount }` | `FundTransferResponse { message, transactionId }` |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | — | `List<FundTransfer>` |

### internet-banking-utility-payment-service (`:8085`)

Base path: `/api/v1/utility-payment`

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process a utility payment | `UtilityPaymentRequest { providerId, amount, referenceNumber, account }` | `UtilityPaymentResponse { message, transactionId }` |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | — | `List<UtilityPayment>` |

### Common Response Shapes

**Error Response** (all services):
```json
{
  "code": "BANKING-CORE-SERVICE-1000",
  "message": "Requested entity not present in the DB."
}
```

**Fallback Error** (unhandled exceptions):
```
"Exception occur inside API <exception details>"
```

---

## 4. Key Business Logic Inventory

### User Registration Flow

1. Client calls `POST /user/api/v1/bank-users/register` with `{ email, identification, password }`.
2. **User Service** checks if email already exists in Keycloak — rejects with `UserAlreadyRegisteredException` if found.
3. **User Service** calls **Core Banking** (`GET /api/v1/user/{identification}`) via Feign to validate the identification exists in the bank's user base.
4. Validates that the email in the request matches the email in core banking — rejects with `InvalidEmailException` if mismatch.
5. Creates a **Keycloak user** (disabled, email unverified) with the provided password.
6. Saves a local `UserEntity` record with status `PENDING` and the Keycloak `authId`.
7. An admin later calls `PATCH /update/{id}` with `{ status: "APPROVED" }` to enable the Keycloak user and verify their email.

### Fund Transfer Flow

1. Client calls `POST /fund-transfer/api/v1/transfer` with `{ fromAccount, toAccount, amount }`.
2. **Fund Transfer Service** saves a local `FundTransferEntity` with status `PENDING`.
3. Calls **Core Banking** (`POST /api/v1/transaction/fund-transfer`) via Feign.
4. **Core Banking TransactionService**:
   - Reads both `fromAccount` and `toAccount` via `AccountService`.
   - Validates the source account has sufficient funds (`actualBalance >= amount`).
   - Debits the source account: `actualBalance -= amount`, `availableBalance = actualBalance - amount`.
   - Credits the destination account: `actualBalance += amount`, `availableBalance = actualBalance + amount`.
   - Creates two `TransactionEntity` records (one debit, one credit) linked by the same `transactionId` (UUID).
5. **Fund Transfer Service** updates local record to `SUCCESS` with the returned `transactionReference`.

### Utility Payment Flow

1. Client calls `POST /utility-payment/api/v1/utility-payment` with `{ providerId, amount, referenceNumber, account }`.
2. **Utility Payment Service** saves a local `UtilityPaymentEntity` with status `PROCESSING`.
3. Calls **Core Banking** (`POST /api/v1/transaction/util-payment`) via Feign.
4. **Core Banking TransactionService**:
   - Reads the paying `account` via `AccountService`.
   - Validates sufficient funds.
   - Reads the utility provider by `providerId`.
   - Debits the paying account.
   - Creates a `TransactionEntity` with type `UTILITY_PAYMENT`.
5. **Utility Payment Service** updates local record to `SUCCESS` with the returned `transactionId`.

### Validation Rules

| Rule | Location | Behavior |
|---|---|---|
| Insufficient funds | `TransactionService.validateBalance()` | Throws `InsufficientFundsException` if `actualBalance < 0` or `actualBalance < amount` |
| Entity not found | Multiple services | Throws `EntityNotFoundException` (account, user, utility provider) |
| Duplicate email registration | `UserService.createUser()` | Throws `UserAlreadyRegisteredException` |
| Email mismatch | `UserService.createUser()` | Throws `InvalidEmailException` if request email ≠ core banking email |
| Unknown identification | `UserService.createUser()` | Throws `InvalidBankingUserException` if core banking user not found |

### Audit Trail

The **user**, **fund-transfer**, and **utility-payment** services use a `@MappedSuperclass` `AuditAware` with Spring Data JPA auditing:
- `createdDate`, `createdBy`, `modifiedDate`, `modifiedBy` — auto-populated via `AuditingEntityListener`
- `version` — optimistic locking field (`@Version`)

---

## 5. Integration Points

### Keycloak (Identity Provider)

- **Version**: 23.0.7
- **Connection**: User Service → Keycloak Admin REST API via `keycloak-admin-client:24.0.4`
- **Auth Mode**: Client credentials grant (`client_credentials`)
- **Configuration** (via Spring Cloud Config):
  - `app.config.keycloak.server-url` — Keycloak base URL
  - `app.config.keycloak.realm` — Target realm
  - `app.config.keycloak.clientId` — Client ID
  - `app.config.keycloak.client-secret` — Client secret
- **Operations**: Create user, read user by email, read user by ID, update user (enable/verify)
- **API Gateway**: Validates JWT tokens using Keycloak's JWK Set URI (`spring.security.oauth2.resourceserver.jwt.jwk-set-uri`)
- **Realm Import**: Pre-configured realm exported to `docker-compose/keycloak/realm-export.json`

### RabbitMQ (Message Broker)

- **Status**: Referenced in README but **NOT implemented** in the codebase
- **Intended Use**: Notification service would consume messages from fund transfer and utility payment services
- **Current State**: No RabbitMQ dependency in any `build.gradle`, no producer/consumer code exists

### Zipkin (Distributed Tracing)

- **Version**: Zipkin 3 (Docker image: `openzipkin/zipkin:3`)
- **Port**: 9411
- **Integration**: All services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` dependencies
- **Feign Tracing**: `feign-micrometer` dependency for propagating trace context across Feign calls
- **Configuration**: Trace export endpoint configured via Spring Cloud Config

### Database Connections

| Service | Database Name | Driver | Migration |
|---|---|---|---|
| core-banking-service | `banking_core_service` | MySQL 8.4.0 | Flyway (3 migration scripts) |
| internet-banking-user-service | `banking_core_user_service` | MySQL 8.4.0 | JPA auto (no Flyway) |
| internet-banking-fund-transfer-service | `banking_core_fund_transfer_service` | MySQL 8.4.0 | JPA auto (no Flyway) |
| internet-banking-utility-payment-service | `banking_core_utility_payment_service` | MySQL 8.4.0 | JPA auto (no Flyway) |

- **MySQL Initialization**: Custom Docker image (`docker-compose/mysql/Dockerfile`) runs `privileges.sql` to create a shared DB user (`javatodev_development`) and all four databases.
- **Test Databases**: All services use H2 in-memory database for testing.

### Service Discovery (Eureka)

- **Server**: `internet-banking-service-registry` on port 8081
- **Clients**: All business services register with Eureka using their `spring.application.name`
- **Feign Resolution**: Feign clients use Eureka service names (e.g., `@FeignClient(name = "core-banking-service")`)

### Spring Cloud Config

- **Server**: `internet-banking-config-server` on port 8090
- **Source**: Git repository at `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Search Path**: `/configuration`
- **Label**: `main`
- **Client Bootstrap**: Each service has `bootstrap.yml` pointing to `http://localhost:8090` (local) or `http://internet-banking-config-server:8090` (Docker)

---

## 6. Build and Deployment Pipeline Summary

### Build System

- **Build Tool**: Gradle (each service is an independent Gradle project; no multi-module root build)
- **Java Version**: 21 (source compatibility)
- **Spring Boot**: 3.2.4 via `org.springframework.boot` Gradle plugin
- **Spring Cloud**: 2023.0.0 BOM
- **Git Properties Plugin**: `com.gorylenko.gradle-git-properties:2.4.2` generates `git.properties` at build time

### Docker

Each service has its own `Dockerfile`:
- **Base Image**: `eclipse-temurin:21.0.2_13-jre-alpine`
- **Pattern**: `ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar`
- **Startup Script**: `wait-for-it.sh` ensures dependencies (Eureka, Config Server, MySQL) are available before starting
- **Profile**: `-Dspring.profiles.active=docker`

### Docker Compose

Two compose files in `docker-compose/`:

1. **`docker-compose.yml`** — Full stack (all infrastructure + all microservices)
2. **`docker-compose-support-apps.yml`** — Infrastructure only (Zipkin, Keycloak, MySQL, Config Server, Service Registry) for local development

**Network**: Custom bridge network `javatodev_ib_network` (subnet `172.25.0.0/16`) with static IP assignments.

### CI/CD

- **GitHub Actions**: No workflow files present (removed for PAT scope compatibility per commit history)
- **No CI pipeline** currently configured in the repository

### Test Framework

- **Framework**: JUnit 5 (JUnit Platform) + Mockito
- **Test Database**: H2 in-memory
- **Coverage**: Unit tests exist only in `core-banking-service` (3 test files: `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`). Other services have only empty Spring Boot application context tests.
- **No integration tests, contract tests, or end-to-end tests** are present.

### Configuration Profiles

| Profile | Purpose | Config Source |
|---|---|---|
| (default) | Local development | `bootstrap.yml` → `localhost:8090` |
| `dev` | Development with specific IP | `bootstrap-dev.yml` → `192.168.1.5:8090` |
| `docker` | Docker Compose deployment | `bootstrap-docker.yml` → `internet-banking-config-server:8090` |
