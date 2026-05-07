# Internet Banking Microservices — Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Summary

The application is a **Java 21 / Spring Boot 3.2.4 / Spring Cloud 2023.0.0** internet banking platform composed of **6 microservices** communicating via synchronous REST (OpenFeign) and coordinated through Netflix Eureka service discovery. An API Gateway (Spring Cloud Gateway) provides a single entry point with OAuth 2.0 / Keycloak-based security.

### 1.2 Service Inventory

| Service | Port | Purpose | Database |
|---|---|---|---|
| **core-banking-service** | 8092 | Core banking engine — accounts, users, transactions | MySQL (`banking_core_service`) |
| **internet-banking-user-service** | 8083 | User registration/management, Keycloak integration | MySQL (shared instance) |
| **internet-banking-fund-transfer-service** | 8084 | Fund transfer orchestration | MySQL (shared instance) |
| **internet-banking-utility-payment-service** | 8085 | Utility payment orchestration | MySQL (shared instance) |
| **internet-banking-api-gateway** | 8082 | API Gateway — routing, security, header propagation | None |
| **internet-banking-service-registry** | 8081 | Eureka Service Registry | None |
| **internet-banking-config-server** | 8090 | Centralized configuration (Spring Cloud Config) | None |

### 1.3 Communication Patterns

```
                          ┌──────────────────┐
                          │   Keycloak (IdP)  │
                          │     :8080         │
                          └────────┬─────────┘
                                   │ JWT validation
┌───────────┐   HTTP    ┌─────────▼──────────┐
│  Client    │──────────►│   API Gateway      │
│            │           │   :8082            │
└───────────┘           └──┬──┬──┬──┬────────┘
                           │  │  │  │  route by path prefix
           ┌───────────────┘  │  │  └────────────────┐
           ▼                  ▼  ▼                    ▼
  ┌────────────────┐ ┌───────────────┐  ┌─────────────────────┐
  │ user-service   │ │ fund-transfer │  │ utility-payment     │
  │ :8083          │ │ :8084         │  │ :8085               │
  └──────┬─────────┘ └──────┬────────┘  └──────┬──────────────┘
         │ Feign             │ Feign             │ Feign
         ▼                   ▼                   ▼
  ┌──────────────────────────────────────────────────────────┐
  │              core-banking-service :8092                   │
  │  (accounts, users, transactions, balance management)     │
  └──────────────────────────────────────────────────────────┘
                          │
                    ┌─────▼─────┐
                    │   MySQL   │
                    │   :3306   │
                    └───────────┘
```

- **Synchronous REST via OpenFeign**: All inter-service calls use Feign clients registered against Eureka service names.
- **Service Discovery**: Netflix Eureka (`internet-banking-service-registry`).
- **Centralized Config**: Spring Cloud Config Server serves configuration to all services via bootstrap profiles (`bootstrap.yml`, `bootstrap-docker.yml`).
- **API Gateway Routing**: Spring Cloud Gateway routes requests by path prefix (`/user/**`, `/fund-transfer/**`, `/banking-core/**`, `/utility-payment/**`).
- **Authentication**: OAuth 2.0 Resource Server (JWT) at the gateway level. The gateway extracts the authenticated principal and propagates it as an `X-Auth-Id` header to downstream services.
- **Distributed Tracing**: Micrometer Tracing + Brave + Zipkin reporter across all business services.

### 1.4 Infrastructure Components

| Component | Technology | Container Name | IP (Docker) |
|---|---|---|---|
| Identity Provider | Keycloak 23.0.7 (backed by PostgreSQL 15) | `keycloak_web` | 172.25.0.11 |
| Database | MySQL (custom image with seed data) | `mysql_javatodev_app` | 172.25.0.9 |
| Keycloak DB | PostgreSQL 15 | `keycloak_postgre_db` | 172.25.0.10 |
| Distributed Tracing | Zipkin 3 | `openzipkin_server` | 172.25.0.12 |
| Message Broker | RabbitMQ (referenced in README, not yet integrated) | — | — |

---

## 2. Data Model Documentation

### 2.1 core-banking-service

Managed via **Flyway** migrations (MySQL).

#### `banking_core_user`

| Column | Type | Constraints |
|---|---|---|
| `id` | `BIGINT` (auto-increment) | PK |
| `first_name` | `VARCHAR(255)` | |
| `last_name` | `VARCHAR(255)` | |
| `email` | `VARCHAR(255)` | |
| `identification_number` | `VARCHAR(255)` | Unique business key |

#### `banking_core_account`

| Column | Type | Constraints |
|---|---|---|
| `id` | `BIGINT` (auto-increment) | PK |
| `number` | `VARCHAR(255)` | Account number (business key) |
| `type` | `VARCHAR(255)` | Enum: `SAVINGS_ACCOUNT` |
| `status` | `VARCHAR(255)` | Enum: `ACTIVE` |
| `actual_balance` | `DECIMAL(19,2)` | |
| `available_balance` | `DECIMAL(19,2)` | |
| `user_id` | `BIGINT` | FK → `banking_core_user.id` |

#### `banking_core_utility_account`

| Column | Type | Constraints |
|---|---|---|
| `id` | `BIGINT` (auto-increment) | PK |
| `number` | `VARCHAR(255)` | |
| `provider_name` | `VARCHAR(255)` | Business key for lookup |

#### `banking_core_transaction`

| Column | Type | Constraints |
|---|---|---|
| `id` | `BIGINT` (auto-increment) | PK |
| `amount` | `DECIMAL(19,2)` | Positive for credit, negative for debit |
| `transaction_type` | `VARCHAR(30)` | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | `VARCHAR(50)` | Target account or reference |
| `transaction_id` | `VARCHAR(50)` | UUID grouping identifier |
| `account_id` | `BIGINT` | FK → `banking_core_account.id` |

#### Relationships

```
banking_core_user  1 ──── * banking_core_account
banking_core_account  1 ──── * banking_core_transaction
banking_core_utility_account (standalone)
```

### 2.2 internet-banking-fund-transfer-service

#### `fund_transfer` (JPA auto-created)

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` (auto-increment) | PK |
| `from_account` | `VARCHAR(255)` | Source account number |
| `to_account` | `VARCHAR(255)` | Destination account number |
| `amount` | `DECIMAL(19,2)` | |
| `transaction_reference` | `VARCHAR(255)` | UUID from core-banking-service |
| `status` | `VARCHAR(255)` | Enum: `PENDING`, `SUCCESS`, `FAILED` |
| `created_by` | `VARCHAR(255)` | Audit field (inherited from `AuditAware`) |
| `created_at` | `DATETIME` | Audit field |
| `updated_by` | `VARCHAR(255)` | Audit field |
| `updated_at` | `DATETIME` | Audit field |

### 2.3 internet-banking-utility-payment-service

#### `utility_payment` (JPA auto-created)

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` (auto-increment) | PK |
| `provider_id` | `BIGINT` | Utility provider ID |
| `amount` | `DECIMAL(19,2)` | |
| `reference_number` | `VARCHAR(255)` | |
| `account` | `VARCHAR(255)` | Source account number |
| `transaction_id` | `VARCHAR(255)` | UUID from core-banking-service |
| `status` | `VARCHAR(255)` | Enum: `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_by` / `created_at` / `updated_by` / `updated_at` | | Audit fields |

### 2.4 internet-banking-user-service

#### `user` (JPA auto-created)

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` (auto-increment) | PK |
| `auth_id` | `VARCHAR(255)` | Keycloak user ID |
| `identification` | `VARCHAR(255)` | NIC / identification number |
| `status` | `VARCHAR(255)` | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_by` / `created_at` / `updated_by` / `updated_at` | | Audit fields |

---

## 3. API Surface Map

### 3.1 API Gateway Routes

All endpoints below are accessed through the gateway at `:8082` with the following path prefixes:

| Prefix | Target Service |
|---|---|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/banking-core/**` | core-banking-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |

### 3.2 core-banking-service (`:8092`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by account number | — | `BankAccount { number, type, status, availableBalance, actualBalance }` |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | — | `UtilityAccount { id, number, providerName }` |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer | `FundTransferRequest { fromAccount, toAccount, amount }` | `FundTransferResponse { message, transactionId }` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `UtilityPaymentRequest { providerId, amount, referenceNumber, account }` | `UtilityPaymentResponse { message, transactionId }` |
| `GET` | `/api/v1/user/{identification}` | Get user by identification number | — | `User { firstName, lastName, email, identificationNumber, accounts[] }` |
| `GET` | `/api/v1/user` | Get paginated users | Query: `page`, `size`, `sort` | `List<User>` |

### 3.3 internet-banking-user-service (`:8083`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Register a new user (public) | `User { email, identification, password }` | `User { id, email, identification, authId, status }` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user status | `UserUpdateRequest { status }` | `User` |
| `GET` | `/api/v1/bank-users` | List paginated users | Query: `page`, `size` | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | — | `User` |

### 3.4 internet-banking-fund-transfer-service (`:8084`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `FundTransferRequest { fromAccount, toAccount, amount }` | `FundTransferResponse { message, transactionId }` |
| `GET` | `/api/v1/transfer` | List paginated fund transfers | Query: `page`, `size` | `List<FundTransfer>` |

### 3.5 internet-banking-utility-payment-service (`:8085`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `UtilityPaymentRequest { providerId, amount, referenceNumber, account }` | `UtilityPaymentResponse { message, transactionId }` |
| `GET` | `/api/v1/utility-payment` | List paginated utility payments | Query: `page`, `size` | `List<UtilityPayment>` |

### 3.6 Infrastructure Endpoints

| Service | Endpoint | Notes |
|---|---|---|
| All services | `/actuator/**` | Spring Boot Actuator (health, info, etc.) — permitted without auth at the gateway |
| Service Registry | `:8081` | Eureka dashboard |
| Config Server | `:8090` | Spring Cloud Config REST endpoints |
| Zipkin | `:9411` | Distributed tracing UI |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

1. Client sends `POST /user/api/v1/bank-users/register` (public endpoint, no auth required).
2. **user-service** checks if email exists in Keycloak. If yes → `UserAlreadyRegisteredException`.
3. Calls **core-banking-service** `GET /api/v1/user/{identification}` via Feign to verify the user exists in the banking core.
4. Validates email matches the core banking record. If not → `InvalidEmailException`.
5. Creates user in Keycloak (disabled, email unverified) with provided password.
6. Saves local user record with `PENDING` status and Keycloak `authId`.
7. Admin later approves via `PATCH /api/v1/bank-users/update/{id}` → enables Keycloak user and sets `APPROVED` status.

### 4.2 Fund Transfer Flow

1. Client sends `POST /fund-transfer/api/v1/transfer` (authenticated).
2. **fund-transfer-service** persists a `PENDING` record to its local `fund_transfer` table.
3. Calls **core-banking-service** `POST /api/v1/transaction/fund-transfer` via Feign.
4. Core service:
   - Validates both accounts exist.
   - Validates `fromAccount` has sufficient balance (`actualBalance >= amount`).
   - Debits `fromAccount` (subtracts from `actualBalance` and `availableBalance`).
   - Credits `toAccount` (adds to `actualBalance` and `availableBalance`).
   - Creates two `TransactionEntity` records (debit + credit) with a shared `transactionId`.
5. **fund-transfer-service** updates its record to `SUCCESS` with the `transactionReference`.

### 4.3 Utility Payment Flow

1. Client sends `POST /utility-payment/api/v1/utility-payment` (authenticated).
2. **utility-payment-service** persists a `PROCESSING` record.
3. Calls **core-banking-service** `POST /api/v1/transaction/util-payment` via Feign.
4. Core service:
   - Validates account exists and has sufficient balance.
   - Looks up utility provider by ID.
   - Debits the source account.
   - Creates a `TransactionEntity` of type `UTILITY_PAYMENT`.
5. **utility-payment-service** updates its record to `SUCCESS` with the `transactionId`.

### 4.4 Balance Validation Rules

- Applies to both fund transfers and utility payments.
- Rejects if `actualBalance < 0` OR `actualBalance < requestedAmount`.
- Throws `InsufficientFundsException` with error code `BANKING-CORE-SERVICE-1001`.

### 4.5 Authentication & Authorization

- **Gateway-level only**: JWT validation via Keycloak JWKS endpoint.
- No role-based access control (RBAC) is implemented on individual endpoints.
- The `/user/api/v1/bank-users/register` endpoint is explicitly permitted without authentication.
- All `/actuator/**` endpoints are permitted without authentication across all services.
- The `X-Auth-Id` header is propagated from the gateway to downstream services for audit context.

---

## 5. Integration Points

### 5.1 Keycloak (Identity Provider)

- **Version**: 23.0.7
- **Connection**: Admin REST API via `keycloak-admin-client:24.0.4`
- **Used by**: `internet-banking-user-service` (KeycloakUserService)
- **Operations**: Create user, read user, search by email, update user (enable/verify)
- **Configuration**: `app.config.keycloak.*` properties (server-url, realm, clientId, client-secret)
- **Grant Type**: `client_credentials`
- **Realm data**: Pre-imported from `docker-compose/keycloak/` volume mount

### 5.2 MySQL Database

- **Version**: Custom Docker image (based on `docker-compose/mysql/Dockerfile`)
- **Connection**: Shared MySQL instance at `:3306`
- **Databases**: `banking_core_service` (Flyway-managed), plus JPA auto-created schemas for other services
- **Root password**: Configured in `docker-compose.yml`

### 5.3 Zipkin (Distributed Tracing)

- **Version**: 3.x
- **Connection**: HTTP reporter at `:9411`
- **Libraries**: `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave`
- **Coverage**: All 4 business services + API gateway

### 5.4 RabbitMQ (Message Broker)

- **Status**: Referenced in README architecture but **not yet integrated** in the codebase.
- **Planned use**: Notification service (PENDING development).

### 5.5 Service-to-Service Communication (Feign Clients)

| Client Service | Target Service | Feign Interface | Endpoints Called |
|---|---|---|---|
| fund-transfer-service | core-banking-service | `BankingCoreFeignClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/fund-transfer` |
| utility-payment-service | core-banking-service | `BankingCoreRestClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/util-payment` |
| user-service | core-banking-service | `BankingCoreRestClient` | `GET /api/v1/user/{identification}` |

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build Tool**: Gradle (per-service, no multi-project root build)
- **Java**: 21 (source compatibility)
- **Spring Boot**: 3.2.4 (Spring Boot Gradle plugin)
- **Spring Cloud**: 2023.0.0 BOM
- **Plugins**: `com.gorylenko.gradle-git-properties:2.4.2` (on 5 of 6 services)

### 6.2 Docker

- **Base Image**: `eclipse-temurin:21.0.2_13-jre-alpine`
- **Pattern**: Each service has its own `Dockerfile` that copies the fat JAR and a `wait-for-it.sh` script
- **Startup**: Uses `wait-for-it.sh` to wait for service-registry + config-server + MySQL before starting
- **Profiles**: `-Dspring.profiles.active=docker` activates `bootstrap-docker.yml` pointing to Docker container hostnames

### 6.3 Docker Compose

- **Primary file**: `docker-compose/docker-compose.yml` — full stack (infra + all 6 services)
- **Support file**: `docker-compose/docker-compose-support-apps.yml` — infra only (Zipkin, Keycloak, MySQL, config-server, service-registry)
- **Network**: Custom bridge network `javatodev_ib_network` (`172.25.0.0/16`) with static IPs
- **Volumes**: Persistent volumes for MySQL and PostgreSQL data

### 6.4 Database Migrations

- **Tool**: Flyway (`flyway-core:10.12.0`, `flyway-mysql:10.12.0`)
- **Location**: `core-banking-service/src/main/resources/db/migration/`
- **Migrations**:
  - `V1.0.20210427174638`: Create base tables (`banking_core_user`, `banking_core_account`, `banking_core_utility_account`)
  - `V1.0.20210427174721`: Seed test data (4 users, 14 accounts, 6 utility providers)
  - `V1.0.20210429210839`: Create `banking_core_transaction` table

### 6.5 Test Infrastructure

- **Framework**: JUnit 5 (via `spring-boot-starter-test`)
- **Test DB**: H2 in-memory for `core-banking-service` tests
- **Unit tests exist for**: `core-banking-service` only (AccountServiceTest, TransactionServiceTest, UserServiceTest)
- **No tests for**: user-service, fund-transfer-service, utility-payment-service, api-gateway, service-registry, config-server

### 6.6 CI/CD

- **GitHub Actions**: Not present (workflows were removed)
- **No automated CI/CD pipeline** is currently configured in the repository
