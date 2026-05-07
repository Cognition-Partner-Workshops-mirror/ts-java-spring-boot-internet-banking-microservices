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

### High-Level Design

The system follows a **microservices architecture** built on Spring Boot 3.2.4 and Spring Cloud 2023.0.0. It models an internet banking platform with six independently deployable services, centralized configuration, service discovery, an API gateway, and distributed tracing.

### Services

| Service | Port | Description |
|---|---|---|
| **core-banking-service** | 8092 | System of record for bank accounts, users, utility accounts, and ledger transactions. Acts as the "core bank" that other services delegate to. |
| **internet-banking-user-service** | 8083 | Manages internet banking user registration, approval workflows, and profile retrieval. Integrates with Keycloak for identity and with core-banking-service for user verification. |
| **internet-banking-fund-transfer-service** | 8084 | Orchestrates account-to-account fund transfers. Persists transfer records locally and delegates the actual balance movement to core-banking-service. |
| **internet-banking-utility-payment-service** | 8085 | Processes utility bill payments (e.g., telecom providers). Persists payment records locally and delegates balance deduction to core-banking-service. |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway. Single entry point for all client traffic. Enforces OAuth2/JWT authentication via Keycloak and routes requests to downstream services through Eureka. |
| **internet-banking-service-registry** | 8081 | Netflix Eureka Server. Provides dynamic service discovery so services locate each other by name rather than hard-coded URLs. |
| **internet-banking-config-server** | 8090 | Spring Cloud Config Server. Centralizes externalized configuration for all services; each service fetches its properties at bootstrap time. |

### Communication Patterns

| Pattern | Technology | Usage |
|---|---|---|
| **Synchronous REST** | Spring Cloud OpenFeign | Fund-transfer-service and utility-payment-service call core-banking-service via Feign clients. User-service calls core-banking-service to verify users by identification. |
| **Service Discovery** | Netflix Eureka | All business services register with Eureka. Feign clients resolve service names (e.g., `core-banking-service`) through Eureka rather than hard-coded hosts. |
| **API Gateway Routing** | Spring Cloud Gateway | The gateway routes incoming requests to downstream services using path-based prefixes (e.g., `/user/**`, `/fund-transfer/**`, `/banking-core/**`, `/utility-payment/**`). |
| **Auth Token Propagation** | Custom GlobalFilter + `X-Auth-Id` header | The gateway extracts the authenticated principal name from the JWT and forwards it downstream as an `X-Auth-Id` HTTP header. Downstream services read this header via `AppAuthUserFilter`. |
| **Async Messaging (planned)** | RabbitMQ | Referenced in the README for a future Notification Service but not yet implemented in code. |

### Infrastructure Components

| Component | Image/Technology | Purpose |
|---|---|---|
| **MySQL 8** | Custom image (`docker-compose/mysql/`) | Persistent storage for all four business services (separate databases per service). |
| **PostgreSQL 15** | `postgres:15` | Backing store for the Keycloak identity provider. |
| **Keycloak 23.0.7** | `quay.io/keycloak/keycloak:23.0.7` | OAuth2/OpenID Connect identity provider. Manages realms, clients, users, and roles for the banking platform. Realm data is imported from `docker-compose/keycloak/`. |
| **Zipkin 3** | `openzipkin/zipkin:3` | Distributed tracing collector. All services send traces via Micrometer Tracing + Brave. |

### Docker Network

All containers run on a custom bridge network (`javatodev_ib_network`, subnet `172.25.0.0/16`) with statically assigned IP addresses for predictable inter-service communication during local development.

---

## 2. Data Model Documentation

### 2.1 core-banking-service (MySQL: `banking_core_service`)

#### `banking_core_user`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto) | Surrogate primary key |
| `first_name` | VARCHAR(255) | User's first name |
| `last_name` | VARCHAR(255) | User's last name |
| `email` | VARCHAR(255) | User's email address |
| `identification_number` | VARCHAR(255) | National ID / NIC number (unique business identifier) |

**Relationships:** One-to-many with `banking_core_account`.

#### `banking_core_account`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto) | Surrogate primary key |
| `number` | VARCHAR(255) | Account number (business key) |
| `type` | ENUM: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` | Account type |
| `status` | ENUM: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` | Account status |
| `available_balance` | DECIMAL(19,2) | Balance available for transactions |
| `actual_balance` | DECIMAL(19,2) | Ledger balance |
| `user_id` | BIGINT (FK) | References `banking_core_user.id` |

#### `banking_core_utility_account`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto) | Surrogate primary key |
| `number` | VARCHAR(255) | Utility provider account number |
| `provider_name` | VARCHAR(255) | Utility provider name (e.g., VODAFONE, AIRTEL) |

#### `banking_core_transaction`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto) | Surrogate primary key |
| `amount` | DECIMAL(19,2) | Transaction amount (negative for debits) |
| `transaction_type` | ENUM: `FUND_TRANSFER`, `UTILITY_PAYMENT` | Type of transaction |
| `reference_number` | VARCHAR(50) | Reference (destination account number or utility ref) |
| `transaction_id` | VARCHAR(50) | UUID grouping related debit/credit entries |
| `account_id` | BIGINT (FK) | References `banking_core_account.id` |

**Schema management:** Flyway migrations in `core-banking-service/src/main/resources/db/migration/`.

### 2.2 internet-banking-user-service (MySQL: `banking_core_user_service`)

#### `user`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto) | Surrogate primary key |
| `auth_id` | VARCHAR(255) | Keycloak user UUID (links to Keycloak identity) |
| `identification` | VARCHAR(255) | National ID / NIC (links to core-banking-service user) |
| `status` | ENUM: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` | Registration approval status |
| `created_at` | TIMESTAMP | Audit: creation timestamp |
| `updated_at` | TIMESTAMP | Audit: last update timestamp |
| `created_by` | VARCHAR(255) | Audit: creator |
| `updated_by` | VARCHAR(255) | Audit: last updater |

Entity extends `AuditAware` for automatic audit field population.

### 2.3 internet-banking-fund-transfer-service (MySQL: `banking_core_fund_transfer_service`)

#### `fund_transfer`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto) | Surrogate primary key |
| `from_account` | VARCHAR(255) | Source account number |
| `to_account` | VARCHAR(255) | Destination account number |
| `amount` | DECIMAL(19,2) | Transfer amount |
| `transaction_reference` | VARCHAR(255) | UUID returned by core-banking-service |
| `status` | ENUM: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` | Transfer status |
| `created_at` / `updated_at` / `created_by` / `updated_by` | Audit fields | Via `AuditAware` |

### 2.4 internet-banking-utility-payment-service (MySQL: `banking_core_utility_payment_service`)

#### `utility_payment`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto) | Surrogate primary key |
| `provider_id` | BIGINT | ID of the utility provider (references core banking utility account) |
| `amount` | DECIMAL(19,2) | Payment amount |
| `reference_number` | VARCHAR(255) | Customer reference / bill number |
| `account` | VARCHAR(255) | Source bank account number |
| `transaction_id` | VARCHAR(255) | UUID returned by core-banking-service |
| `status` | ENUM: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` | Payment status |
| `created_at` / `updated_at` / `created_by` / `updated_by` | Audit fields | Via `AuditAware` |

### Entity-Relationship Summary

```
banking_core_user  1──N  banking_core_account  1──1  banking_core_transaction
                                                      (per entry; two entries per fund transfer)

banking_core_utility_account  (standalone, referenced by providerId from utility-payment-service)

user (user-service)  ──keycloak──  Keycloak UserRepresentation
                     ──identification──  banking_core_user (core-banking-service)

fund_transfer (fund-transfer-service)  ──REST──  banking_core_transaction (core-banking-service)

utility_payment (utility-payment-service)  ──REST──  banking_core_transaction (core-banking-service)
```

---

## 3. API Surface Map

### 3.1 API Gateway Routes (Port 8082)

All external traffic enters through the gateway. Routes are prefixed per service:

| Gateway Path Prefix | Target Service | Auth Required |
|---|---|---|
| `/user/**` | internet-banking-user-service | Yes (except `/user/api/v1/bank-users/register`) |
| `/fund-transfer/**` | internet-banking-fund-transfer-service | Yes |
| `/banking-core/**` | core-banking-service | Yes |
| `/utility-payment/**` | internet-banking-utility-payment-service | Yes |
| `/actuator/**` (all prefixes) | Respective service actuator | No |

### 3.2 core-banking-service (Port 8092)

#### Account Controller (`/api/v1/account`)

| Method | Endpoint | Description | Request | Response |
|---|---|---|---|---|
| `GET` | `/bank-account/{account_number}` | Get bank account by number | Path: `account_number` (String) | `BankAccount { id, number, type, status, availableBalance, actualBalance, user }` |
| `GET` | `/util-account/{account_name}` | Get utility account by provider name | Path: `account_name` (String) | `UtilityAccount { id, number, providerName }` |

#### User Controller (`/api/v1/user`)

| Method | Endpoint | Description | Request | Response |
|---|---|---|---|---|
| `GET` | `/{identification}` | Read user by NIC/identification | Path: `identification` (String) | `User { id, firstName, lastName, email, identificationNumber, bankAccounts[] }` |
| `GET` | `/` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |

#### Transaction Controller (`/api/v1/transaction`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/fund-transfer` | Process fund transfer | `{ fromAccount, toAccount, amount }` | `FundTransferResponse { message, transactionId }` |
| `POST` | `/util-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `UtilityPaymentResponse { message, transactionId }` |

### 3.3 internet-banking-user-service (Port 8083)

#### User Controller (`/api/v1/bank-users`)

| Method | Endpoint | Description | Request | Response |
|---|---|---|---|---|
| `POST` | `/register` | Register new internet banking user | Body: `{ email, identification, password }` | `User { id, email, identification, authId, status }` |
| `PATCH` | `/update/{id}` | Update user (e.g., approve registration) | Path: `id`; Body: `{ status }` | `User` |
| `GET` | `/` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |
| `GET` | `/{id}` | Read user by ID | Path: `id` (Long) | `User` |

### 3.4 internet-banking-fund-transfer-service (Port 8084)

#### Fund Transfer Controller (`/api/v1/transfer`)

| Method | Endpoint | Description | Request | Response |
|---|---|---|---|---|
| `POST` | `/` | Initiate fund transfer | Body: `{ fromAccount, toAccount, amount, authID }` | `FundTransferResponse { message, transactionId }` |
| `GET` | `/` | List fund transfers (paginated) | Query: `page`, `size`, `sort` | `List<FundTransfer>` |

### 3.5 internet-banking-utility-payment-service (Port 8085)

#### Utility Payment Controller (`/api/v1/utility-payment`)

| Method | Endpoint | Description | Request | Response |
|---|---|---|---|---|
| `POST` | `/` | Process utility payment | Body: `{ providerId, amount, referenceNumber, account }` | `UtilityPaymentResponse { message, transactionId }` |
| `GET` | `/` | List utility payments (paginated) | Query: `page`, `size`, `sort` | `List<UtilityPayment>` |

### 3.6 Infrastructure Endpoints

| Service | Endpoint | Description |
|---|---|---|
| All services | `/actuator/**` | Spring Boot Actuator (health, info, metrics, etc.) |
| Service Registry | `http://localhost:8081` | Eureka dashboard |
| Config Server | `http://localhost:8090/{app}/{profile}` | Configuration retrieval |
| Zipkin | `http://localhost:9411` | Distributed tracing UI |
| Keycloak | `http://localhost:8080` | Identity provider admin console |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

1. Client calls `POST /user/api/v1/bank-users/register` (unauthenticated, allowed through gateway).
2. `UserService.createUser()`:
   - Checks Keycloak for existing user with the same email; throws `UserAlreadyRegisteredException` if found.
   - Calls core-banking-service via Feign (`GET /api/v1/user/{identification}`) to verify the user exists in the core bank.
   - Validates that the provided email matches the core bank record; throws `InvalidEmailException` if mismatched.
   - Creates a Keycloak user (disabled, email unverified) with the provided password.
   - Retrieves the Keycloak-assigned `authId` and persists a local user record with status `PENDING`.
3. Admin approves user via `PATCH /update/{id}` with `{ status: "APPROVED" }`:
   - Enables the Keycloak user and marks email as verified.
   - Updates local status to `APPROVED`.

### 4.2 Fund Transfer Rules

1. Client calls `POST /fund-transfer/api/v1/transfer`.
2. `FundTransferService.fundTransfer()`:
   - Creates a local `FundTransferEntity` with status `PENDING`.
   - Delegates to core-banking-service via Feign (`POST /api/v1/transaction/fund-transfer`).
3. `TransactionService.fundTransfer()` (core-banking-service):
   - Reads both source and destination accounts.
   - **Balance validation:** `actualBalance >= 0` AND `actualBalance >= transferAmount`; throws `InsufficientFundsException` otherwise.
   - Performs the transfer in a `@Transactional` block:
     - Debits source: subtracts amount from `actualBalance` and `availableBalance`.
     - Credits destination: adds amount to `actualBalance` and `availableBalance`.
     - Creates two `TransactionEntity` records (debit and credit) linked by the same `transactionId`.
4. Fund-transfer-service updates local record to `SUCCESS` with the transaction reference.

### 4.3 Utility Payment Processing

1. Client calls `POST /utility-payment/api/v1/utility-payment`.
2. `UtilityPaymentService.utilPayment()`:
   - Creates a local `UtilityPaymentEntity` with status `PROCESSING`.
   - Delegates to core-banking-service via Feign (`POST /api/v1/transaction/util-payment`).
3. `TransactionService.utilPayment()` (core-banking-service):
   - Reads the source bank account and validates the balance (same rules as fund transfer).
   - Reads the utility provider account by `providerId`.
   - Debits the source account.
   - Creates a `TransactionEntity` record of type `UTILITY_PAYMENT`.
4. Utility-payment-service updates local record to `SUCCESS`.

### 4.4 Authentication & Authorization

- The API gateway enforces OAuth2 JWT validation using Keycloak's JWK Set URI.
- The gateway's `GatewayConfiguration` extracts the authenticated principal and passes it downstream as `X-Auth-Id` header.
- Downstream services read `X-Auth-Id` via `AppAuthUserFilter` and store it in a thread-local `ApiRequestContext` for audit purposes.
- User registration (`/user/api/v1/bank-users/register`) and all actuator endpoints are permitted without authentication.

---

## 5. Integration Points

### 5.1 Keycloak (Identity Provider)

| Aspect | Detail |
|---|---|
| **Version** | 23.0.7 |
| **Connection** | `keycloak-admin-client:24.0.4` (Java SDK) |
| **Realm** | Configured via `app.config.keycloak.realm` property |
| **Auth method** | Client credentials grant (`client_credentials`) |
| **Operations** | Create user, update user (enable/disable), search by email, read by auth ID |
| **Realm import** | Pre-configured realm JSON imported at Keycloak startup from `docker-compose/keycloak/` volume mount |

### 5.2 RabbitMQ (Message Broker)

| Aspect | Detail |
|---|---|
| **Status** | Referenced in README but **not yet implemented** in code |
| **Planned use** | Notification service will consume messages from fund transfer and utility payment services |

### 5.3 Zipkin (Distributed Tracing)

| Aspect | Detail |
|---|---|
| **Version** | Zipkin 3 |
| **Integration** | `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` in all business services |
| **Feign tracing** | `feign-micrometer` dependency included for propagating trace context through Feign calls |
| **Dashboard** | `http://localhost:9411` |

### 5.4 Database Connections

| Service | Database | Schema/DB Name | Driver |
|---|---|---|---|
| core-banking-service | MySQL 8 | `banking_core_service` | `mysql-connector-j:8.4.0` |
| internet-banking-user-service | MySQL 8 | `banking_core_user_service` | `mysql-connector-j:8.4.0` |
| internet-banking-fund-transfer-service | MySQL 8 | `banking_core_fund_transfer_service` | `mysql-connector-j:8.4.0` |
| internet-banking-utility-payment-service | MySQL 8 | `banking_core_utility_payment_service` | `mysql-connector-j:8.4.0` |
| Keycloak | PostgreSQL 15 | `keycloak` | Internal JDBC driver |

All MySQL databases are created via `docker-compose/mysql/privileges.sql` on first container startup.

### 5.5 Inter-Service REST Calls (Feign Clients)

| Caller | Target | Feign Client | Endpoints Called |
|---|---|---|---|
| internet-banking-user-service | core-banking-service | `BankingCoreRestClient` | `GET /api/v1/user/{identification}` |
| internet-banking-fund-transfer-service | core-banking-service | `BankingCoreFeignClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/fund-transfer` |
| internet-banking-utility-payment-service | core-banking-service | `BankingCoreRestClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/util-payment` |

All Feign clients resolve `core-banking-service` by name via Eureka.

---

## 6. Build and Deployment Pipeline Summary

### Build System

- **Build tool:** Gradle (per-service `build.gradle`, no root-level multi-project build)
- **Java version:** 21 (Eclipse Temurin JDK)
- **Spring Boot:** 3.2.4
- **Spring Cloud:** 2023.0.0
- **Git properties plugin:** `com.gorylenko.gradle-git-properties:2.4.2` (generates `git.properties` for actuator `/info`)

### Docker

Each service has its own `Dockerfile`:
- **Base image:** `eclipse-temurin:21.0.2_13-jre-alpine`
- **Entry point:** `java -jar -Dspring.profiles.active=docker /app.jar`
- **wait-for-it.sh:** Each service includes a `wait-for-it.sh` script to ensure dependencies (Eureka, Config Server, MySQL) are healthy before the application starts.

### Docker Compose

Two compose files in `docker-compose/`:

| File | Purpose |
|---|---|
| `docker-compose.yml` | Full stack: infrastructure + all application services |
| `docker-compose-support-apps.yml` | Infrastructure only: Zipkin, Keycloak, PostgreSQL, MySQL, Config Server, Service Registry |

### Startup Order

1. **Infrastructure:** MySQL, PostgreSQL, Keycloak, Zipkin
2. **Platform services:** Config Server, Service Registry
3. **Application services** (each waits for Service Registry + Config Server + MySQL via `wait-for-it.sh`):
   - API Gateway
   - User Service
   - Fund Transfer Service
   - Utility Payment Service
   - Core Banking Service

### Configuration Profiles

| Profile | Usage |
|---|---|
| `default` | Local development (Config Server at `localhost:8090`) |
| `dev` | Development with dev-specific bootstrap config |
| `docker` | Docker Compose deployment (Config Server at `internet-banking-config-server:8090`) |

### Test Infrastructure

- **Test framework:** JUnit 5 (JUnit Platform)
- **Mocking:** Mockito
- **Test database:** H2 in-memory (Flyway disabled in test profile)
- **Test location:** `src/test/` in each service; only `core-banking-service` has meaningful unit tests currently

### Seed Data

Pre-loaded via Flyway migration (`V1.0.20210427174721__temp_data.sql`):
- 4 test users with NIC identifications
- 14 savings accounts across users with varying balances
- 6 utility provider accounts (VODAFONE, VERIZON, SINGTEL, HUTCH, AIRTEL, GIO)

**Test credentials:** `ib_admin@javatodev.com` / `5V7huE3G86uB` (Keycloak)
