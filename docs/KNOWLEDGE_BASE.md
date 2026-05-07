# Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Summary

This is a Java 21 / Spring Boot 3.2.4 internet banking application composed of **6 microservices** communicating via synchronous REST (OpenFeign) with centralized configuration, service discovery, and an API gateway.

### 1.2 Microservices Inventory

| Service | Port | Description |
|---|---|---|
| **core-banking-service** | 8092 | Core banking engine: manages users, bank accounts, utility accounts, and processes fund transfers & utility payments at the ledger level |
| **internet-banking-user-service** | 8083 | User registration & management; integrates with Keycloak for identity and with core-banking-service for user validation |
| **internet-banking-fund-transfer-service** | 8084 | Orchestrates fund transfers; persists transfer records locally and delegates to core-banking-service |
| **internet-banking-utility-payment-service** | 8085 | Orchestrates utility payments; persists payment records locally and delegates to core-banking-service |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway; routes all external traffic, enforces OAuth2/JWT authentication via Keycloak |
| **internet-banking-service-registry** | 8081 | Netflix Eureka server for service discovery |
| **internet-banking-config-server** | 8090 | Spring Cloud Config Server; fetches configuration from a remote Git repository |

### 1.3 Communication Patterns

```
                        ┌──────────────────┐
           External     │   API Gateway    │   OAuth2/JWT enforcement
           Clients ────>│   (port 8082)    │
                        └────────┬─────────┘
                                 │ Routes via Eureka service names
                 ┌───────────────┼───────────────────────┐
                 │               │                       │
        ┌────────▼──────┐ ┌─────▼────────────┐ ┌────────▼──────────┐
        │  User Service │ │ Fund Transfer    │ │ Utility Payment  │
        │  (port 8083)  │ │ Service (8084)   │ │ Service (8085)   │
        └───────┬───────┘ └────────┬─────────┘ └────────┬─────────┘
                │ Feign            │ Feign               │ Feign
                └──────────────────┼─────────────────────┘
                                   │
                          ┌────────▼────────┐
                          │  Core Banking   │
                          │  Service (8092) │
                          └────────┬────────┘
                                   │ JPA/Hibernate
                          ┌────────▼────────┐
                          │    MySQL 8.4    │
                          └─────────────────┘
```

- **Synchronous REST via OpenFeign**: All inter-service calls use Spring Cloud OpenFeign clients resolved through Eureka.
- **API Gateway routing**: The gateway forwards the authenticated user's Keycloak subject ID as an `X-Auth-Id` HTTP header to downstream services.
- **Centralized Configuration**: All services (except the registry and config server) bootstrap from the config server, which reads from a remote Git repo (`internet-banking-microservices-configurations`).
- **Service Discovery**: All services register with Eureka; Feign clients use logical service names for resolution.

### 1.4 Infrastructure Components

| Component | Image / Version | Purpose |
|---|---|---|
| MySQL | `mysql:8.4.0` | Primary data store for all business services (4 schemas) |
| Keycloak | `quay.io/keycloak/keycloak:23.0.7` | Identity & access management (OAuth2 / OIDC) |
| PostgreSQL 15 | `postgres:15` | Keycloak's backing database |
| Zipkin | `openzipkin/zipkin:3` | Distributed tracing collection & UI |
| RabbitMQ | _(referenced in README, not yet in docker-compose)_ | Planned for notification service (pending development) |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (`banking_core_service` database)

#### `banking_core_user`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK AUTO_INCREMENT | |
| `email` | VARCHAR(255) | |
| `first_name` | VARCHAR(255) | |
| `last_name` | VARCHAR(255) | |
| `identification_number` | VARCHAR(255) | NIC / national ID |

#### `banking_core_account`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK AUTO_INCREMENT | |
| `number` | VARCHAR(255) | 12-digit account number (e.g. `100015003000`) |
| `actual_balance` | DECIMAL(19,2) | Ledger balance |
| `available_balance` | DECIMAL(19,2) | Available for transactions |
| `status` | VARCHAR(255) | `ACTIVE` |
| `type` | VARCHAR(255) | `SAVINGS_ACCOUNT` |
| `user_id` | BIGINT FK -> `banking_core_user.id` | |

#### `banking_core_utility_account`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK AUTO_INCREMENT | |
| `number` | VARCHAR(255) | Provider's account number |
| `provider_name` | VARCHAR(255) | e.g. `VODAFONE`, `VERIZON`, `HUTCH` |

#### `banking_core_transaction`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK AUTO_INCREMENT | |
| `amount` | DECIMAL(19,2) | Positive = credit, negative = debit |
| `transaction_type` | VARCHAR(30) | `FUND_TRANSFER` or `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Target account number or utility ref |
| `transaction_id` | VARCHAR(50) | UUID correlation ID |
| `account_id` | BIGINT FK -> `banking_core_account.id` | |

### 2.2 Fund Transfer Service (`banking_core_fund_transfer_service` database)

#### `fund_transfer` (JPA auto-created)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK AUTO_INCREMENT | |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `status` | VARCHAR (enum) | `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `transaction_reference` | VARCHAR | UUID from core-banking-service |
| `created_date` | TIMESTAMP | Audit field |
| `created_by` | VARCHAR | Audit field |
| `modified_date` | TIMESTAMP | Audit field |
| `modified_by` | VARCHAR | Audit field |
| `version` | BIGINT | Optimistic locking |

### 2.3 User Service (`banking_core_user_service` database)

#### `user` (JPA auto-created)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK AUTO_INCREMENT | |
| `auth_id` | VARCHAR | Keycloak user ID (UUID) |
| `identification` | VARCHAR | NIC / national ID |
| `status` | VARCHAR (enum) | `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | TIMESTAMP | Audit field |
| `version` | BIGINT | Optimistic locking |

### 2.4 Utility Payment Service (`banking_core_utility_payment_service` database)

#### `utility_payment` (JPA auto-created)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK AUTO_INCREMENT | |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Customer's reference (e.g. phone number) |
| `account` | VARCHAR | Payer's bank account number |
| `transaction_id` | VARCHAR | UUID from core-banking-service |
| `status` | VARCHAR (enum) | `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` | TIMESTAMP | Audit field |
| `version` | BIGINT | Optimistic locking |

### 2.5 Entity Relationships

```
banking_core_user 1──* banking_core_account 1──* banking_core_transaction
                                                          │
                       banking_core_utility_account ───────┘ (referenced by provider_id)

fund_transfer (standalone, references accounts by number string)
user (standalone, references Keycloak by auth_id)
utility_payment (standalone, references accounts by number string)
```

---

## 3. API Surface Map

All endpoints are exposed through the API Gateway at `http://localhost:8082`.

### 3.1 Core Banking Service (gateway prefix: `/core`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | - | `BankAccount` (id, number, actualBalance, availableBalance, status, type) |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | - | `UtilityAccount` (id, number, providerName) |
| `GET` | `/api/v1/user/{identification}` | Get user by NIC | - | `User` (id, firstName, lastName, email, identificationNumber, bankAccounts[]) |
| `GET` | `/api/v1/user` | List users (paginated) | - | `List<User>` |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |

### 3.2 User Service (gateway prefix: `/user`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/bank-user` | List registered banking users (paginated) | - | `List<User>` |
| `POST` | `/api/v1/bank-user/register` | Register new banking user | `{ email, password, identification }` | `User` |
| `GET` | `/api/v1/bank-user/{id}` | Get user by ID | - | `User` |
| `PATCH` | `/api/v1/bank-user/update/{id}` | Update user status | `{ status }` | `User` |

### 3.3 Fund Transfer Service (gateway prefix: `/fund-transfer`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | - | `List<FundTransfer>` |

### 3.4 Utility Payment Service (gateway prefix: `/payment`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | - | `List<UtilityPayment>` |

### 3.5 Infrastructure Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/actuator/health` | Health check (all services) |
| `GET` | `/{service-prefix}/actuator/health` | Per-service health via gateway |
| `POST` | Keycloak token endpoint | OAuth2 token acquisition |

### 3.6 Authentication Flow

1. Client requests an OAuth2 token from Keycloak: `POST http://localhost:8080/realms/javatodev-internet-banking/protocol/openid-connect/token`
2. Client includes the JWT Bearer token in the `Authorization` header for all API calls through the gateway.
3. The gateway validates the JWT against Keycloak's JWK endpoint.
4. The gateway extracts the authenticated principal and forwards it as `X-Auth-Id` header to downstream services.
5. Downstream services read `X-Auth-Id` via `AppAuthUserFilter` and store it in `ApiRequestContextHolder` (thread-local).

---

## 4. Key Business Logic Inventory

### 4.1 Fund Transfer Flow

1. **Fund Transfer Service** receives `POST /api/v1/transfer` with `{ fromAccount, toAccount, amount }`.
2. Creates a `FundTransferEntity` with status `PENDING` in the local database.
3. Calls **Core Banking Service** `POST /api/v1/transaction/fund-transfer` via Feign.
4. Core Banking Service:
   - Reads both bank accounts from the database.
   - Validates the sender has sufficient balance (`actualBalance >= amount`).
   - Debits the sender: `actualBalance -= amount`, `availableBalance = actualBalance - amount`.
   - Credits the receiver: `actualBalance += amount`, `availableBalance = actualBalance + amount`.
   - Records two `TransactionEntity` rows (debit + credit) with a shared `transactionId`.
   - Returns `{ message, transactionId }`.
5. Fund Transfer Service updates the local entity to `SUCCESS` with the transaction reference.

### 4.2 Utility Payment Flow

1. **Utility Payment Service** receives `POST /api/v1/utility-payment` with `{ providerId, amount, referenceNumber, account }`.
2. Creates a `UtilityPaymentEntity` with status `PROCESSING` in the local database.
3. Calls **Core Banking Service** `POST /api/v1/transaction/util-payment` via Feign.
4. Core Banking Service:
   - Reads the payer's bank account.
   - Validates sufficient balance.
   - Reads the utility provider account by ID.
   - Debits the payer: `actualBalance -= amount`, `availableBalance = actualBalance - amount`.
   - Records a `TransactionEntity` row (debit only; no credit to utility provider's bank account).
   - Returns `{ message, transactionId }`.
5. Utility Payment Service updates the local entity to `SUCCESS` with the transaction ID.

### 4.3 User Registration Flow

1. **User Service** receives `POST /api/v1/bank-user/register` with `{ email, password, identification }`.
2. Checks Keycloak for existing user with same email (rejects if found).
3. Calls **Core Banking Service** `GET /api/v1/user/{identification}` to verify the user exists in the core banking system.
4. Validates the email matches the core banking record.
5. Creates a Keycloak user with the email, name, and provided password (disabled, email unverified).
6. Persists a local `UserEntity` with status `PENDING` and the Keycloak `authId`.
7. Admin must later call `PATCH /api/v1/bank-user/update/{id}` with `{ status: "APPROVED" }` to:
   - Enable the Keycloak user.
   - Mark email as verified.
   - Update local status to `APPROVED`.

### 4.4 Balance Validation Rules

- **Insufficient funds**: Throws `InsufficientFundsException` if `actualBalance < 0` OR `actualBalance < requestedAmount`.
- No minimum balance enforcement beyond zero.
- No daily/per-transaction transfer limits.
- No same-account transfer prevention.

### 4.5 Audit Trail

- The `AuditAware` base class (present in user, fund-transfer, and utility-payment services) captures `createdDate`, `createdBy`, `modifiedDate`, `modifiedBy`, and `version` (optimistic locking) for all entities.
- `AuditorAwareConfig` extracts the current user from `ApiRequestContextHolder` (populated from the `X-Auth-Id` header).

---

## 5. Integration Points

### 5.1 Keycloak (Identity & Access Management)

- **Version**: 23.0.7
- **Realm**: `javatodev-internet-banking`
- **Client**: `internet-banking-core-client`
- **Integration mode**:
  - API Gateway: OAuth2 Resource Server (JWT validation via JWK endpoint)
  - User Service: Keycloak Admin Client (`keycloak-admin-client:24.0.4`) for user CRUD
- **Configuration**: Server URL, realm, clientId, and clientSecret sourced from Spring Cloud Config (`app.config.keycloak.*`)
- **Realm import**: Pre-configured realm JSON loaded at Keycloak startup via volume mount

### 5.2 Zipkin (Distributed Tracing)

- **Version**: 3.x
- **Port**: 9411
- **Integration**: `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` in all business services
- **Trace propagation**: Automatic via Spring Boot Actuator + Brave

### 5.3 MySQL (Data Persistence)

- **Version**: 8.4.0
- **Databases**: `banking_core_service`, `banking_core_fund_transfer_service`, `banking_core_user_service`, `banking_core_utility_payment_service`
- **User**: `javatodev_development` with broad privileges
- **Schema management**: Flyway migrations in core-banking-service; JPA auto-DDL (`hibernate.ddl-auto`) in other services (controlled via config server)
- **Driver**: `com.mysql:mysql-connector-j:8.4.0`

### 5.4 Spring Cloud Config Server

- **Git repo**: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch**: `main`
- **Path**: `/configuration`
- All services bootstrap from the config server; profile-specific configs via `bootstrap-{profile}.yml`

### 5.5 Netflix Eureka (Service Discovery)

- Single-node Eureka server (self-registration disabled).
- All services register and resolve each other via Eureka service names.

### 5.6 RabbitMQ (Planned)

- Referenced in README for a future Notification Service.
- **Not yet implemented** in docker-compose or any service code.

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build tool**: Gradle 8.6 (wrapper included per service)
- **Java**: 21 (Eclipse Temurin)
- **Spring Boot**: 3.2.4
- **Spring Cloud**: 2023.0.0
- Each service is an independent Gradle project (no multi-project build).
- Git properties plugin (`com.gorylenko.gradle-git-properties:2.4.2`) generates build metadata.

### 6.2 Docker

- **Base image**: `eclipse-temurin:21.0.2_13-jre-alpine`
- Each service has a `Dockerfile` that:
  1. Copies the fat JAR from `build/libs/`.
  2. Copies `wait-for-it.sh` for startup dependency management.
  3. Installs `bash` (Alpine) for wait-for-it compatibility.
  4. Runs with `-Dspring.profiles.active=docker`.

### 6.3 Docker Compose

- `docker-compose.yml`: Full stack (all 6 services + MySQL + Keycloak + PostgreSQL + Zipkin).
- `docker-compose-support-apps.yml`: Support infrastructure only (MySQL, Keycloak, PostgreSQL, Zipkin, config server, registry).
- Custom bridge network `javatodev_ib_network` with static IPs (`172.25.0.0/16`).
- Named volumes for MySQL and PostgreSQL data persistence.
- Startup ordering via `wait-for-it.sh` scripts (waits for registry, config server, and MySQL).

### 6.4 CI/CD

- **GitHub Actions workflows have been removed** (per commit `fa1c445`).
- No active CI/CD pipeline in the repository.

### 6.5 Build Commands

```bash
# Build a single service
cd core-banking-service && ./gradlew build

# Build Docker image (after Gradle build)
docker build -t javatodev/core-banking-service .

# Start full stack
cd docker-compose && docker-compose up -d

# Start support infrastructure only (for local dev)
cd docker-compose && docker-compose -f docker-compose-support-apps.yml up -d
```

### 6.6 Test Data

Pre-seeded via Flyway migration `V1.0.20210427174721__temp_data.sql`:
- 4 users (Sam, Guru, Ragu, Randor)
- 14 savings accounts with balances ranging from 12,000 to 889,000.33
- 6 utility provider accounts (VODAFONE, VERIZON, SINGTEL, HUTCH, AIRTEL, GIO)

Test credentials: `ib_admin@javatodev.com / 5V7huE3G86uB`
