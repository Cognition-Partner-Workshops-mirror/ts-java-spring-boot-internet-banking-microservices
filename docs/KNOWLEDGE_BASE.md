# Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Summary

This is a Java 21 / Spring Boot 3.2.4 / Spring Cloud 2023.0.0 internet banking application composed of **6 microservices** that together model a simplified banking system with user registration, fund transfers, and utility payments.

### 1.2 Services

| Service | Port | Description | Database |
|---|---|---|---|
| **core-banking-service** | 8092 | Core banking engine — manages users, accounts, transactions. Acts as the system of record for balances and account data. | `banking_core_service` (MySQL) |
| **internet-banking-user-service** | 8083 | Internet banking user registration and management. Integrates with Keycloak for identity. | `banking_core_user_service` (MySQL) |
| **internet-banking-fund-transfer-service** | 8084 | Orchestrates fund transfers between accounts via the core banking service. | `banking_core_fund_transfer_service` (MySQL) |
| **internet-banking-utility-payment-service** | 8085 | Orchestrates utility bill payments via the core banking service. | `banking_core_utility_payment_service` (MySQL) |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway — single entry point for all client requests. Handles OAuth2/JWT authentication and route proxying. | None |
| **internet-banking-service-registry** | 8081 | Netflix Eureka server — service discovery for all microservices. | None |
| **internet-banking-config-server** | 8090 | Spring Cloud Config Server — centralised configuration from a Git repository. | None |

### 1.3 Communication Patterns

```
                         ┌─────────────────────┐
                         │   API Gateway (:8082)│
          Client ──JWT──►│  OAuth2 Resource     │
                         │  Server + Routes     │
                         └────┬────┬────┬───────┘
                              │    │    │
               ┌──────────────┘    │    └──────────────┐
               ▼                   ▼                   ▼
     ┌─────────────────┐ ┌────────────────┐ ┌──────────────────┐
     │ User Service     │ │ Fund Transfer  │ │ Utility Payment  │
     │ (:8083)          │ │ Service (:8084)│ │ Service (:8085)  │
     └───────┬──────────┘ └───────┬────────┘ └────────┬─────────┘
             │ Feign               │ Feign              │ Feign
             ▼                     ▼                    ▼
          ┌──────────────────────────────────────────────┐
          │         Core Banking Service (:8092)         │
          └──────────────────────────────────────────────┘
```

- **Client → Gateway**: HTTP/REST with JWT Bearer token (Keycloak-issued).
- **Gateway → Downstream Services**: Route proxying with `X-Auth-Id` header injection (authenticated principal name).
- **Downstream Services → Core Banking**: Synchronous REST calls via **Spring Cloud OpenFeign** with Eureka-based service discovery.
- **Service Discovery**: All services register with **Netflix Eureka** (`internet-banking-service-registry`).
- **Configuration**: All services (except registry) fetch configuration from **Spring Cloud Config Server**, which reads from a remote Git repository.
- **Distributed Tracing**: Micrometer Tracing with Brave bridge, reporting spans to **Zipkin**.

### 1.4 Infrastructure Components

| Component | Technology | Purpose |
|---|---|---|
| Identity Provider | Keycloak 23.0.7 (PostgreSQL 15 backend) | OAuth2/OIDC authentication, user identity storage, realm/client management |
| Message Broker | RabbitMQ (referenced in README, not yet implemented) | Planned: notification messages from fund-transfer and payment services |
| Distributed Tracing | Zipkin 3 | Trace aggregation and visualization |
| Database | MySQL 8.4.0 | Persistent storage for all 4 data-bearing services |
| Service Discovery | Netflix Eureka | Runtime service registration and lookup |
| Config Management | Spring Cloud Config Server (Git-backed) | Externalized configuration per service/profile |
| Containerization | Docker / Docker Compose | Local development and deployment orchestration |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service

#### `banking_core_user`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto) | |
| `first_name` | VARCHAR(255) | |
| `last_name` | VARCHAR(255) | |
| `email` | VARCHAR(255) | |
| `identification_number` | VARCHAR(255) | National ID / passport number |

#### `banking_core_account`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto) | |
| `number` | VARCHAR(255) | Account number (e.g., `100015003000`) |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `ACTIVE`, `INACTIVE` |
| `available_balance` | DECIMAL(19,2) | |
| `actual_balance` | DECIMAL(19,2) | |
| `user_id` | BIGINT (FK → `banking_core_user.id`) | |

#### `banking_core_transaction`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto) | |
| `amount` | DECIMAL(19,2) | Negative for debits, positive for credits |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Target account number or reference |
| `transaction_id` | VARCHAR(50) | UUID grouping related debit/credit entries |
| `account_id` | BIGINT (FK → `banking_core_account.id`) | |

#### `banking_core_utility_account`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto) | |
| `number` | VARCHAR(255) | Provider account number |
| `provider_name` | VARCHAR(255) | e.g., `VODAFONE`, `AIRTEL` |

#### Relationships
```
banking_core_user 1───* banking_core_account
banking_core_account 1───* banking_core_transaction
banking_core_utility_account (standalone — referenced by provider ID)
```

### 2.2 Internet Banking User Service

#### `user` (JPA-managed, auto DDL)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto) | |
| `auth_id` | VARCHAR | Keycloak user ID |
| `identification` | VARCHAR | Linked to core banking `identification_number` |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED`, `REJECTED` |
| `created_at` / `updated_at` | TIMESTAMP | Audit fields (via `AuditAware` base class) |
| `created_by` / `updated_by` | VARCHAR | Audit fields |

### 2.3 Fund Transfer Service

#### `fund_transfer` (JPA-managed, auto DDL)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto) | |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `status` | VARCHAR | Enum: `PENDING`, `SUCCESS`, `FAILED`, `PROCESSING` |
| `transaction_reference` | VARCHAR | Core banking transaction ID |
| `created_at` / `updated_at` | TIMESTAMP | Audit fields |

### 2.4 Utility Payment Service

#### `utility_payment` (JPA-managed, auto DDL)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto) | |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Bill reference |
| `account` | VARCHAR | Payer's account number |
| `transaction_id` | VARCHAR | Core banking transaction ID |
| `status` | VARCHAR | Enum: `PROCESSING`, `SUCCESS`, `FAILED`, `PENDING` |
| `created_at` / `updated_at` | TIMESTAMP | Audit fields |

---

## 3. API Surface Map

### 3.1 API Gateway Routes

The gateway proxies requests with path prefixes to downstream services:

| Path Prefix | Target Service |
|---|---|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |
| `/banking-core/**` | core-banking-service |

Authentication: All routes require a valid JWT except `/user/api/v1/bank-users/register` and `/actuator/**` paths.

### 3.2 Core Banking Service (`:8092`)

| Method | Endpoint | Request Body | Response Body | Description |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | — | `BankAccount` | Look up bank account by number |
| `GET` | `/api/v1/account/util-account/{account_name}` | — | `UtilityAccount` | Look up utility account by provider name |
| `GET` | `/api/v1/user/{identification}` | — | `User` | Look up user by identification number |
| `GET` | `/api/v1/user` | `?page=&size=&sort=` | `List<User>` | Paginated user listing |
| `POST` | `/api/v1/transaction/fund-transfer` | `FundTransferRequest { fromAccount, toAccount, amount }` | `FundTransferResponse { message, transactionId }` | Execute a fund transfer |
| `POST` | `/api/v1/transaction/util-payment` | `UtilityPaymentRequest { providerId, amount, referenceNumber, account }` | `UtilityPaymentResponse { message, transactionId }` | Execute a utility payment |

### 3.3 Internet Banking User Service (`:8083`)

| Method | Endpoint | Request Body | Response Body | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | `User { email, identification, password }` | `User` | Register new internet banking user (creates Keycloak account) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `UserUpdateRequest { status }` | `User` | Update user status (e.g., approve registration) |
| `GET` | `/api/v1/bank-users` | `?page=&size=&sort=` | `List<User>` | Paginated user listing |
| `GET` | `/api/v1/bank-users/{id}` | — | `User` | Get user by ID |

### 3.4 Fund Transfer Service (`:8084`)

| Method | Endpoint | Request Body | Response Body | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | `FundTransferRequest { fromAccount, toAccount, amount }` | `FundTransferResponse { message, transactionId }` | Initiate a fund transfer |
| `GET` | `/api/v1/transfer` | `?page=&size=&sort=` | `List<FundTransfer>` | List all fund transfers (paginated) |

### 3.5 Utility Payment Service (`:8085`)

| Method | Endpoint | Request Body | Response Body | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | `UtilityPaymentRequest { providerId, amount, referenceNumber, account }` | `UtilityPaymentResponse { message, transactionId }` | Process a utility payment |
| `GET` | `/api/v1/utility-payment` | `?page=&size=&sort=` | `List<UtilityPayment>` | List all utility payments (paginated) |

### 3.6 Service Registry (`:8081`)

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/` | Eureka dashboard |
| `GET` | `/eureka/apps` | Registered applications |

### 3.7 Config Server (`:8090`)

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/{application}/{profile}` | Fetch configuration for a service and profile |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow
1. Client calls `POST /user/api/v1/bank-users/register` (unauthenticated).
2. User service checks if email already exists in Keycloak — throws `UserAlreadyRegisteredException` if so.
3. Calls core banking service to verify the identification number exists and email matches.
4. Creates a Keycloak user (disabled, email unverified) with client-provided password.
5. Saves local user record with `status = PENDING`.
6. An admin later calls `PATCH /update/{id}` with `status = APPROVED`, which enables the Keycloak account and marks email as verified.

### 4.2 Fund Transfer Flow
1. Client calls `POST /fund-transfer/api/v1/transfer`.
2. Fund transfer service saves a `PENDING` record locally.
3. Delegates to core banking service via Feign: `POST /api/v1/transaction/fund-transfer`.
4. Core banking validates both accounts exist and source has sufficient funds.
5. Debits source account (both `actualBalance` and `availableBalance`).
6. Credits destination account.
7. Creates two `TransactionEntity` records (debit + credit) with matching `transactionId`.
8. Returns `transactionId` to fund transfer service, which updates local record to `SUCCESS`.

### 4.3 Utility Payment Flow
1. Client calls `POST /utility-payment/api/v1/utility-payment`.
2. Utility payment service saves a `PROCESSING` record locally.
3. Delegates to core banking service via Feign: `POST /api/v1/transaction/util-payment`.
4. Core banking validates payer account exists, has sufficient funds, and utility provider exists.
5. Debits payer account and creates a `TransactionEntity` record.
6. Returns `transactionId` to utility payment service, which updates local record to `SUCCESS`.

### 4.4 Balance Validation Rules
- The source account's `actualBalance` must be >= 0 **and** >= the transfer/payment amount.
- If validation fails, `InsufficientFundsException` is thrown (HTTP 400).

### 4.5 Authentication & Authorization
- The API gateway acts as an OAuth2 resource server validating JWTs issued by Keycloak.
- A `GlobalFilter` extracts the authenticated principal name and injects it as the `X-Auth-Id` HTTP header to downstream services.
- Downstream services read `X-Auth-Id` via `AppAuthUserFilter` and store it in a thread-local `ApiRequestContextHolder` for audit purposes.
- User registration is the only publicly accessible endpoint.

---

## 5. Integration Points

### 5.1 Keycloak (Identity Provider)
- **Version**: 23.0.7
- **Connection**: Admin client SDK (`keycloak-admin-client:24.0.4`) using `client_credentials` grant.
- **Configuration**: `app.config.keycloak.server-url`, `realm`, `clientId`, `client-secret` (via Config Server).
- **Operations**: Create user, read user by email/ID, update user (enable/disable, email verification).
- **Realm data**: Pre-imported from `docker-compose/keycloak/` volume mount.

### 5.2 RabbitMQ (Message Broker)
- **Status**: Referenced in README as planned for notification service; **not yet implemented** in code.
- **Intended use**: Fund transfer and utility payment services push notification events.

### 5.3 Zipkin (Distributed Tracing)
- **Version**: Zipkin 3
- **Integration**: All services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave`.
- **Endpoint**: `http://172.25.0.12:9411` (Docker network).

### 5.4 MySQL Database
- **Version**: 8.4.0
- **Connection**: Single MySQL instance with 4 separate databases.
- **Schema management**: Core banking service uses **Flyway** migrations; other services use JPA auto-DDL (`hibernate.ddl-auto`).
- **Credentials**: Root password `woVERANKliGharym`, app user `javatodev_development` / `oPItyPticIAt`.

### 5.5 Spring Cloud Config Server
- **Git repository**: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch**: `main`
- **Path**: `configuration/`
- Each service bootstraps by fetching `{application-name}-{profile}.yml` from the config server.

### 5.6 Netflix Eureka (Service Discovery)
- All services register with Eureka at `http://localhost:8081/eureka` (or Docker equivalent).
- Feign clients use logical service names (e.g., `core-banking-service`) resolved via Eureka.

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System
- **Build tool**: Gradle (per-service `build.gradle`, no multi-project root build file).
- **Java version**: 21 (`sourceCompatibility = '21'`).
- **Spring Boot**: 3.2.4 with Spring Cloud 2023.0.0.
- Each service is built independently: `./gradlew build` within each service directory.
- Git properties plugin (`com.gorylenko.gradle-git-properties`) generates `git.properties` for actuator `/info` endpoint.

### 6.2 Docker
- Each service has a `Dockerfile` based on `eclipse-temurin:21.0.2_13-jre-alpine`.
- JARs are copied into the image (`ADD build/libs/*.jar app.jar`).
- `wait-for-it.sh` is used to block startup until dependencies (Eureka, Config Server, MySQL) are available.
- Active Spring profile set to `docker` via `-Dspring.profiles.active=docker`.

### 6.3 Docker Compose
Two compose files in `docker-compose/`:

| File | Contents |
|---|---|
| `docker-compose.yml` | Full stack: all 6 services + MySQL + Keycloak + PostgreSQL + Zipkin |
| `docker-compose-support-apps.yml` | Infrastructure only: MySQL + Keycloak + PostgreSQL + Zipkin + Config Server + Service Registry |

- Custom bridge network `javatodev_ib_network` with subnet `172.25.0.0/16`.
- Static IP assignments for all containers.
- Named volumes for MySQL and PostgreSQL data persistence.

### 6.4 Database Initialization
- MySQL container built from custom `Dockerfile` that runs `privileges.sql` on first boot.
- `privileges.sql` creates app user, grants permissions, and creates all 4 databases.
- Core banking service Flyway migrations create tables and seed test data.
- Other services rely on JPA `hibernate.ddl-auto` for schema management.

### 6.5 Test Infrastructure
- **Test framework**: JUnit 5 (`useJUnitPlatform()`).
- **Test database**: H2 in-memory for core-banking-service tests (Flyway disabled).
- **Existing tests**: Core banking service has unit tests for `AccountService`, `UserService`, and `TransactionService`. Other services have only empty application context tests.
- **Mocking**: Mockito for service-layer unit tests.
