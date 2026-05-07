# Application Knowledge Base

## 1. Architecture Overview

### 1.1 Service Inventory

| # | Service | Port | Type | Description |
|---|---------|------|------|-------------|
| 1 | **core-banking-service** | 8092 | Business | Simulated banking core: accounts, users, transaction processing (fund transfers & utility payments). Owns the MySQL schema with Flyway migrations. |
| 2 | **internet-banking-user-service** | 8083 | Business | User registration, approval, and profile management. Integrates with Keycloak for identity and with core-banking-service via Feign for user verification. |
| 3 | **internet-banking-fund-transfer-service** | 8084 | Business | Orchestrates fund transfers. Persists a local transfer record, then delegates the actual ledger movement to core-banking-service via Feign. |
| 4 | **internet-banking-utility-payment-service** | 8085 | Business | Orchestrates utility bill payments. Persists a local payment record, then delegates to core-banking-service via Feign. |
| 5 | **internet-banking-api-gateway** | 8082 | Infrastructure | Spring Cloud Gateway. Routes external traffic to downstream services. Enforces OAuth 2.0 (Keycloak JWT) authentication. Injects `X-Auth-Id` header. |
| 6 | **internet-banking-service-registry** | 8081 | Infrastructure | Netflix Eureka server for service discovery. |
| 7 | **internet-banking-config-server** | 8090 | Infrastructure | Spring Cloud Config Server backed by a remote Git repository (`internet-banking-microservices-configurations`). |

### 1.2 Communication Patterns

```
                          +-----------+
       Internet --------> | API       |
       (JWT Bearer)       | Gateway   |
                          | :8082     |
                          +-----+-----+
                                |  routes via Eureka
               +----------------+----------------+
               |                |                |
        +------v------+  +-----v------+  +------v-------+
        | User        |  | Fund       |  | Utility      |
        | Service     |  | Transfer   |  | Payment      |
        | :8083       |  | Service    |  | Service      |
        +------+------+  | :8084      |  | :8085        |
               |          +-----+------+  +------+-------+
               |                |                |
               |   Feign (sync) |   Feign (sync) |
               +-------+--------+-------+--------+
                        |
                  +-----v------+
                  | Core       |
                  | Banking    |
                  | Service    |
                  | :8092      |
                  +-----+------+
                        |
                  +-----v------+
                  | MySQL      |
                  | :3306      |
                  +------------+
```

- **Synchronous / REST**: All inter-service communication uses **Spring Cloud OpenFeign** over HTTP, resolved via **Eureka** service names.
- **Asynchronous / Messaging**: **RabbitMQ** is listed in the tech stack and intended for notification messages, but the notification service is **not yet implemented**. No queue producers or consumers exist in the current codebase.
- **Service Discovery**: Eureka client/server model. All business services register with the Eureka server.
- **Centralized Configuration**: Spring Cloud Config Server fetches configuration from a remote Git repo. Services bootstrap from the config server before starting.
- **API Gateway**: Spring Cloud Gateway acts as the single entry point, performing JWT validation and routing.

### 1.3 Infrastructure Components

| Component | Technology | Purpose | Docker IP |
|-----------|-----------|---------|-----------|
| Database | MySQL 8 | Shared database for all business services (core-banking schema + per-service tables) | 172.25.0.9 |
| Identity Provider | Keycloak 23.0.7 | OAuth 2.0 / OpenID Connect provider | 172.25.0.11 |
| Keycloak DB | PostgreSQL 15 | Keycloak's backing store | 172.25.0.10 |
| Distributed Tracing | Zipkin 3 | Trace collection and visualization | 172.25.0.12 |
| Service Registry | Netflix Eureka | Service discovery | 172.25.0.7 |
| Config Server | Spring Cloud Config | Externalized configuration from Git | 172.25.0.8 |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL `banking_core_service` schema)

#### `banking_core_user`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT PK AUTO_INCREMENT | Surrogate key |
| `first_name` | VARCHAR(255) | First name |
| `last_name` | VARCHAR(255) | Last name |
| `email` | VARCHAR(255) | Email address |
| `identification_number` | VARCHAR(255) | National ID / identification |

#### `banking_core_account`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT PK AUTO_INCREMENT | Surrogate key |
| `number` | VARCHAR(255) | Account number (e.g. `100015003000`) |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `available_balance` | DECIMAL(19,2) | Available funds |
| `actual_balance` | DECIMAL(19,2) | Ledger balance |
| `user_id` | BIGINT FK -> `banking_core_user.id` | Account owner |

#### `banking_core_utility_account`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT PK AUTO_INCREMENT | Surrogate key |
| `number` | VARCHAR(255) | Provider account number |
| `provider_name` | VARCHAR(255) | Utility provider name (e.g. `VODAFONE`) |

#### `banking_core_transaction`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT PK AUTO_INCREMENT | Surrogate key |
| `amount` | DECIMAL(19,2) | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Destination account number or reference |
| `transaction_id` | VARCHAR(50) | UUID correlating both sides of a transfer |
| `account_id` | BIGINT FK -> `banking_core_account.id` | Source/destination account |

#### Relationships
```
banking_core_user 1---* banking_core_account
banking_core_account 1---* banking_core_transaction
banking_core_utility_account (standalone, no FK relationships)
```

### 2.2 Internet Banking User Service

#### `user` (JPA-managed, auto-created via Hibernate DDL)
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT PK AUTO_INCREMENT | Surrogate key |
| `auth_id` | VARCHAR(255) | Keycloak user UUID |
| `identification` | VARCHAR(255) | NIC / identification number |
| `status` | VARCHAR(255) | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | TIMESTAMP | Audit: creation time |
| `created_by` | VARCHAR(255) | Audit: creator |
| `modified_date` | TIMESTAMP | Audit: last modification time |
| `modified_by` | VARCHAR(255) | Audit: modifier |
| `version` | BIGINT | Optimistic locking |

### 2.3 Internet Banking Fund Transfer Service

#### `fund_transfer` (JPA-managed)
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT PK AUTO_INCREMENT | Surrogate key |
| `transaction_reference` | VARCHAR(255) | UUID from core-banking |
| `from_account` | VARCHAR(255) | Source account number |
| `to_account` | VARCHAR(255) | Destination account number |
| `amount` | DECIMAL(19,2) | Transfer amount |
| `status` | VARCHAR(255) | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| Audit columns | (inherited from `AuditAware`) | created_date, created_by, modified_date, modified_by, version |

### 2.4 Internet Banking Utility Payment Service

#### `utility_payment` (JPA-managed)
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT PK AUTO_INCREMENT | Surrogate key |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL(19,2) | Payment amount |
| `reference_number` | VARCHAR(255) | Customer reference |
| `account` | VARCHAR(255) | Source account number |
| `transaction_id` | VARCHAR(255) | UUID from core-banking |
| `status` | VARCHAR(255) | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| Audit columns | (inherited from `AuditAware`) | created_date, created_by, modified_date, modified_by, version |

---

## 3. API Surface Map

### 3.1 API Gateway Routes (external-facing, port 8082)

All requests pass through the gateway with JWT authentication. The gateway strips path prefixes and routes to downstream services via Eureka:

| Gateway Path Prefix | Downstream Service |
|---------------------|--------------------|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |
| `/banking-core/**` | core-banking-service |

**Public endpoints (no JWT required):**
- `POST /user/api/v1/bank-users/register`
- `/actuator/**` (all services)

### 3.2 Core Banking Service (port 8092)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | - | `BankAccount` | Lookup bank account by number |
| `GET` | `/api/v1/account/util-account/{account_name}` | - | `UtilityAccount` | Lookup utility account by provider name |
| `POST` | `/api/v1/transaction/fund-transfer` | `FundTransferRequest {fromAccount, toAccount, amount}` | `FundTransferResponse {message, transactionId}` | Execute a fund transfer between two accounts |
| `POST` | `/api/v1/transaction/util-payment` | `UtilityPaymentRequest {providerId, amount, referenceNumber, account}` | `UtilityPaymentResponse {message, transactionId}` | Execute a utility payment |
| `GET` | `/api/v1/user/{identification}` | - | `User` | Lookup user by identification number |
| `GET` | `/api/v1/user` | `?page=&size=&sort=` | `List<User>` | Paginated user listing |

### 3.3 Internet Banking User Service (port 8083)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `POST` | `/api/v1/bank-users/register` | `User {email, identification, password}` | `User` | Register a new internet banking user (creates Keycloak user + local record) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `UserUpdateRequest {status}` | `User` | Update user status (e.g., approve registration) |
| `GET` | `/api/v1/bank-users` | `?page=&size=&sort=` | `List<User>` | Paginated user listing (enriched with Keycloak data) |
| `GET` | `/api/v1/bank-users/{id}` | - | `User` | Retrieve user by database ID |

### 3.4 Internet Banking Fund Transfer Service (port 8084)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `POST` | `/api/v1/transfer` | `FundTransferRequest {fromAccount, toAccount, amount, authID}` | `FundTransferResponse {message, transactionId}` | Initiate a fund transfer |
| `GET` | `/api/v1/transfer` | `?page=&size=&sort=` | `List<FundTransfer>` | Paginated transfer history |

### 3.5 Internet Banking Utility Payment Service (port 8085)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `POST` | `/api/v1/utility-payment` | `UtilityPaymentRequest {providerId, amount, referenceNumber, account}` | `UtilityPaymentResponse {message, transactionId}` | Process a utility payment |
| `GET` | `/api/v1/utility-payment` | `?page=&size=&sort=` | `List<UtilityPayment>` | Paginated payment history |

### 3.6 Infrastructure Endpoints

| Service | Endpoint | Description |
|---------|----------|-------------|
| Service Registry | `http://localhost:8081/` | Eureka dashboard |
| Config Server | `http://localhost:8090/{app}/{profile}` | Retrieve config for a given app/profile |
| Zipkin | `http://localhost:9411/` | Trace visualization UI |
| Keycloak | `http://localhost:8080/` | Identity management console |
| All services | `/actuator/**` | Spring Boot Actuator endpoints |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow (`internet-banking-user-service`)

1. Check if email is already registered in **Keycloak** -> throw `UserAlreadyRegisteredException` if exists.
2. Verify the identification number exists in **core-banking-service** via Feign -> throw `InvalidBankingUserException` if not found.
3. Validate that the email matches the core banking record -> throw `InvalidEmailException` if mismatch.
4. Create the user in **Keycloak** (disabled, email unverified, with provided password).
5. Persist a local `UserEntity` with status `PENDING` and the Keycloak `authId`.

### 4.2 User Approval Flow (`internet-banking-user-service`)

1. Admin sends `PATCH /api/v1/bank-users/update/{id}` with `{status: "APPROVED"}`.
2. Service reads the Keycloak user and sets `enabled=true`, `emailVerified=true`.
3. Local user entity status is updated to `APPROVED`.

### 4.3 Fund Transfer Flow

1. **Fund Transfer Service** receives the request, saves a `FundTransferEntity` with status `PENDING`.
2. Delegates to **Core Banking Service** via Feign `POST /api/v1/transaction/fund-transfer`.
3. **Core Banking Service**:
   - Reads both source and destination `BankAccount` records.
   - Validates source account has sufficient balance (`actualBalance >= amount`).
   - Debits source: `actualBalance -= amount`, `availableBalance = actualBalance - amount`.
   - Credits destination: `actualBalance += amount`, `availableBalance = actualBalance + amount`.
   - Creates two `TransactionEntity` records (debit + credit) with the same `transactionId`.
4. **Fund Transfer Service** updates local entity to status `SUCCESS` with the `transactionReference`.

### 4.4 Utility Payment Flow

1. **Utility Payment Service** receives the request, saves a `UtilityPaymentEntity` with status `PROCESSING`.
2. Delegates to **Core Banking Service** via Feign `POST /api/v1/transaction/util-payment`.
3. **Core Banking Service**:
   - Reads the source `BankAccount` and validates sufficient balance.
   - Reads the `UtilityAccount` by provider ID.
   - Debits the source account.
   - Creates a `TransactionEntity` with type `UTILITY_PAYMENT`.
4. **Utility Payment Service** updates local entity to status `SUCCESS` with the `transactionId`.

### 4.5 Balance Validation Rules

- Both `actualBalance > 0` AND `actualBalance >= transferAmount` must be true.
- Throws `InsufficientFundsException` with code `BANKING-CORE-SERVICE-1001` on failure.
- No account status check (e.g., DORMANT or BLOCKED accounts are not rejected).

### 4.6 Known Business Logic Issues

- **Double-deduction bug**: In `TransactionService.utilPayment()` and `internalFundTransfer()`, `availableBalance` is set to `actualBalance - amount` AFTER `actualBalance` has already been reduced, resulting in a double deduction of `availableBalance`.
- **No account status validation**: Transfers/payments from DORMANT or BLOCKED accounts are permitted.
- **No idempotency**: No mechanism to prevent duplicate fund transfers or payments.
- **No transaction rollback on Feign failure**: If the core-banking call succeeds but the orchestrating service fails afterward, the local record remains in PENDING/PROCESSING status with no retry or compensation logic.

---

## 5. Integration Points

### 5.1 Keycloak Integration

- **Service**: internet-banking-user-service
- **Library**: `keycloak-admin-client:24.0.4`
- **Connection**: Configured via `app.config.keycloak.*` properties (server-url, realm, clientId, client-secret)
- **Grant type**: `client_credentials`
- **Operations**: Create user, update user (enable/disable), search by email, read by ID
- **Realm**: Imported from `docker-compose/keycloak/realm-export.json` at startup

### 5.2 RabbitMQ (Planned)

- **Status**: Referenced in README/tech stack but **not implemented**.
- **Intended use**: Fund transfer and utility payment services would publish notification messages; a notification service would consume them.
- **No RabbitMQ dependency** exists in any `build.gradle`.

### 5.3 Zipkin (Distributed Tracing)

- **Integration**: Via Micrometer Tracing Bridge (`micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`)
- **All business services** include the tracing dependencies.
- **Zipkin server** runs on port 9411 in Docker Compose.

### 5.4 Database Connections

- **Engine**: MySQL 8 (custom Docker image from `docker-compose/mysql/`)
- **Schema management**: Flyway migrations (core-banking-service only)
- **Other services**: Use Hibernate auto-DDL (spring.jpa.hibernate.ddl-auto via config server)
- **Connection details**: Configured via Spring Cloud Config Server (externalized)
- **Test databases**: H2 in-memory (dependency present in all business services)

### 5.5 Spring Cloud Config Server

- **Git repository**: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch**: `main`
- **Search path**: `configuration/`
- **Bootstrap**: All services connect to config server at startup via `bootstrap.yml`
- **Profiles**: `default` (localhost), `dev` (custom IP), `docker` (Docker networking)

### 5.6 Eureka Service Discovery

- **Server**: internet-banking-service-registry (port 8081)
- **Clients**: All other services register and discover via Eureka
- **Feign clients** use Eureka service names (e.g., `core-banking-service`) for resolution

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build tool**: Gradle (each service is an independent Gradle project, NOT a multi-project build)
- **Java version**: 21
- **Spring Boot**: 3.2.4
- **Spring Cloud**: 2023.0.0
- **Key plugins**: `spring-boot`, `spring-dependency-management`, `gradle-git-properties`

### 6.2 CI/CD

- **No GitHub Actions workflows** (`.github/workflows/` is empty; only `FUNDING.yml` exists)
- **No automated CI/CD pipeline** is configured in the repository.

### 6.3 Docker Compose Deployment

Two compose files in `docker-compose/`:

1. **`docker-compose.yml`** - Full stack (all services + infrastructure)
2. **`docker-compose-support-apps.yml`** - Infrastructure only (Zipkin, Keycloak, MySQL, Config Server, Service Registry)

**Startup orchestration**: Services use `wait-for-it.sh` scripts to wait for dependencies:
- Config Server & Service Registry must be ready before business services start.
- MySQL must be ready before JPA-based services start.
- Profile: `docker` is activated via `-Dspring.profiles.active=docker`.

**Docker images**: Pre-built images from `javatodev/*` Docker Hub registry.

### 6.4 Custom Docker Images

- **MySQL**: Custom `Dockerfile` in `docker-compose/mysql/` with `privileges.sql` for database initialization.
- **Keycloak**: Custom `Dockerfile` in `docker-compose/keycloak/` with `realm-export.json` for realm import.

### 6.5 Local Development

- Run infrastructure via `docker-compose-support-apps.yml`.
- Run business services individually via Gradle: `./gradlew bootRun`.
- Default profile connects to `localhost` for config server and MySQL.
- Test credentials: `ib_admin@javatodev.com / 5V7huE3G86uB`.
