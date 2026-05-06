# Application Knowledge Base

## 1. Architecture Overview

### 1.1 Services

The application comprises **6 microservices**, each built with Java 21 and Spring Boot 3.2.4:

| Service | Directory | Port | Role |
|---|---|---|---|
| **Core Banking Service** | `core-banking-service/` | 8092 | Central banking ledger — accounts, users, transactions |
| **Fund Transfer Service** | `internet-banking-fund-transfer-service/` | 8084 | Orchestrates account-to-account fund transfers |
| **Utility Payment Service** | `internet-banking-utility-payment-service/` | 8085 | Processes utility bill payments |
| **User Service** | `internet-banking-user-service/` | 8083 | Internet-banking user registration & management (Keycloak integration) |
| **API Gateway** | `internet-banking-api-gateway/` | 8082 | Spring Cloud Gateway — single entry-point, JWT validation, request routing |
| **Service Registry** | `internet-banking-service-registry/` | 8081 | Netflix Eureka server for service discovery |
| **Config Server** | `internet-banking-config-server/` | 8090 | Spring Cloud Config server (Git-backed) |

### 1.2 Communication Patterns

| Pattern | Technology | Usage |
|---|---|---|
| **Synchronous REST** | OpenFeign | Fund Transfer → Core Banking, Utility Payment → Core Banking, User → Core Banking |
| **Service Discovery** | Netflix Eureka | All services register with and discover each other through Eureka |
| **API Gateway** | Spring Cloud Gateway | Routes external requests to internal services; injects `X-Auth-Id` header |
| **Centralized Config** | Spring Cloud Config (Git-backed) | All services pull configuration from the Config Server at startup |
| **Distributed Tracing** | Micrometer Tracing + Brave + Zipkin | Trace propagation across service calls |

### 1.3 Infrastructure Components

| Component | Technology | Purpose |
|---|---|---|
| **Database** | MySQL 8.4 | Single shared MySQL instance with 4 databases (one per business service) |
| **Identity Provider** | Keycloak 23.0.7 (backed by PostgreSQL 15) | OAuth2/OpenID Connect — user realm, client credentials, JWT issuing |
| **Distributed Tracing** | Zipkin 3 | Collects and visualizes distributed traces |
| **Message Broker** | RabbitMQ (referenced in README, not yet implemented) | Planned for async notifications |
| **Containerization** | Docker / Docker Compose | Full-stack local deployment |
| **Build Tool** | Gradle 8.6 + Spring Boot Gradle Plugin | Per-service builds, no multi-project build |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (`banking_core_service` database)

#### `banking_core_user`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK AUTO_INCREMENT | |
| `first_name` | VARCHAR(255) | |
| `last_name` | VARCHAR(255) | |
| `email` | VARCHAR(255) | |
| `identification_number` | VARCHAR(255) | National ID / NIC |

#### `banking_core_account`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK AUTO_INCREMENT | |
| `number` | VARCHAR(255) | Account number (e.g., `100015003000`) |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `actual_balance` | DECIMAL(19,2) | |
| `available_balance` | DECIMAL(19,2) | |
| `user_id` | BIGINT FK → `banking_core_user.id` | |

#### `banking_core_transaction`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK AUTO_INCREMENT | |
| `amount` | DECIMAL(19,2) | Negative for debits, positive for credits |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Target account number or util reference |
| `transaction_id` | VARCHAR(50) | UUID — links debit/credit leg pair |
| `account_id` | BIGINT FK → `banking_core_account.id` | |

#### `banking_core_utility_account`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK AUTO_INCREMENT | |
| `number` | VARCHAR(255) | Utility account number |
| `provider_name` | VARCHAR(255) | e.g., `VODAFONE`, `AIRTEL` |

**Seed data:** 4 users, 14 bank accounts, 6 utility providers.

### 2.2 Fund Transfer Service (`banking_core_fund_transfer_service` database)

#### `fund_transfer`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK AUTO_INCREMENT | |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `status` | VARCHAR | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `transaction_reference` | VARCHAR | UUID from Core Banking response |
| `created_date` | TIMESTAMP | JPA Auditing |
| `created_by` | VARCHAR | JPA Auditing (from `X-Auth-Id`) |
| `modified_date` | TIMESTAMP | JPA Auditing |
| `modified_by` | VARCHAR | JPA Auditing |
| `version` | BIGINT | Optimistic locking |

### 2.3 User Service (`banking_core_user_service` database)

#### `user`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK AUTO_INCREMENT | |
| `auth_id` | VARCHAR | Keycloak user ID |
| `identification` | VARCHAR | NIC / national ID |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | — | JPA Auditing fields |

### 2.4 Utility Payment Service (`banking_core_utility_payment_service` database)

#### `utility_payment`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK AUTO_INCREMENT | |
| `provider_id` | BIGINT | Reference to utility provider |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Customer reference |
| `account` | VARCHAR | Source bank account number |
| `transaction_id` | VARCHAR | UUID from Core Banking response |
| `status` | VARCHAR | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | — | JPA Auditing fields |

---

## 3. API Surface Map

### 3.1 API Gateway Routes (port 8082)

All external requests enter through the API Gateway. The gateway validates JWT tokens via Keycloak's JWK endpoint and injects `X-Auth-Id` header. The user registration endpoint (`/user/api/v1/bank-users/register`) is publicly accessible.

### 3.2 Core Banking Service (port 8092)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | — | `BankAccount` (id, number, type, status, availableBalance, actualBalance, user) |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | — | `UtilityAccount` (id, number, providerName) |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer at core level | `{fromAccount, toAccount, amount}` | `{message, transactionId}` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment at core level | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` |
| `GET` | `/api/v1/user/{identification}` | Get user by identification number | — | `User` (id, firstName, lastName, email, identificationNumber, bankAccounts[]) |
| `GET` | `/api/v1/user` | List users (paginated) | Pageable params | `List<User>` |

### 3.3 Fund Transfer Service (port 8084)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `{fromAccount, toAccount, amount, authID}` | `{message, transactionId}` |
| `GET` | `/api/v1/transfer` | List all fund transfers (paginated) | Pageable params | `List<FundTransfer>` |

### 3.4 User Service (port 8083)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Register new internet banking user | `{email, identification, password}` | `User` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user status | `{status}` | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | Pageable params | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | — | `User` |

### 3.5 Utility Payment Service (port 8085)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | Pageable params | `List<UtilityPayment>` |

### 3.6 Infrastructure Endpoints

| Service | Endpoint | Purpose |
|---|---|---|
| Service Registry | `GET /` (port 8081) | Eureka dashboard |
| Config Server | `GET /{application}/{profile}` (port 8090) | Configuration retrieval |
| All services | `GET /actuator/**` | Health, info, metrics (publicly accessible via gateway) |

---

## 4. Key Business Logic Inventory

### 4.1 Fund Transfer Flow

1. **Fund Transfer Service** receives POST `/api/v1/transfer`
2. Persists a `FundTransferEntity` with status `PENDING`
3. Calls **Core Banking** via Feign: `POST /api/v1/transaction/fund-transfer`
4. **Core Banking** `TransactionService.fundTransfer()`:
   - Reads source and destination `BankAccount` DTOs
   - Validates source account has sufficient funds (`actualBalance >= amount`)
   - Calls `internalFundTransfer()`:
     - Debits source: `actualBalance -= amount`, `availableBalance = actualBalance - amount`
     - Creates debit `TransactionEntity` (negative amount)
     - Credits destination: `actualBalance += amount`, `availableBalance = actualBalance + amount`
     - Creates credit `TransactionEntity` (positive amount)
     - Returns UUID `transactionId`
5. **Fund Transfer Service** updates entity status to `SUCCESS`, stores `transactionReference`

### 4.2 Utility Payment Flow

1. **Utility Payment Service** receives POST `/api/v1/utility-payment`
2. Persists a `UtilityPaymentEntity` with status `PROCESSING`
3. Calls **Core Banking** via Feign: `POST /api/v1/transaction/util-payment`
4. **Core Banking** `TransactionService.utilPayment()`:
   - Reads source `BankAccount`, validates balance
   - Reads `UtilityAccount` by provider ID
   - Debits source account
   - Creates `TransactionEntity` with type `UTILITY_PAYMENT`
   - Returns UUID `transactionId`
5. **Utility Payment Service** updates entity status to `SUCCESS`, stores `transactionId`

### 4.3 User Registration Flow

1. **User Service** receives POST `/api/v1/bank-users/register`
2. Checks Keycloak: if email already registered → throws `UserAlreadyRegisteredException`
3. Calls **Core Banking** via Feign: `GET /api/v1/user/{identification}` to verify bank customer
4. Validates email matches core banking record
5. Creates Keycloak user (disabled, email unverified, with provided password)
6. Persists local `UserEntity` with status `PENDING` and Keycloak `authId`

### 4.4 User Approval Flow

1. Admin calls PATCH `/api/v1/bank-users/update/{id}` with `{status: "APPROVED"}`
2. **User Service** enables the Keycloak account and sets `emailVerified = true`
3. Updates local entity status to `APPROVED`

### 4.5 Balance Validation Rules

- `TransactionService.validateBalance()`: throws `InsufficientFundsException` if `actualBalance < 0` or `actualBalance < requestedAmount`
- Error code: `BANKING-CORE-SERVICE-1001`

---

## 5. Integration Points

### 5.1 Keycloak

- **Used by:** User Service, API Gateway
- **Version:** 23.0.7
- **Realm:** `javatodev-internet-banking` (auto-imported via `realm-export.json`)
- **Client:** `internet-banking-api-client` (client credentials grant)
- **User Service** uses `keycloak-admin-client:24.0.4` to:
  - Create users, update users, search by email, read user by auth ID
- **API Gateway** validates JWTs via `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`
- **Configuration:** `app.config.keycloak.*` properties (server-url, realm, clientId, client-secret)

### 5.2 RabbitMQ

- **Status:** Referenced in README but **not implemented** in codebase
- **Planned use:** Notification service (push messages for fund transfers and utility payments)

### 5.3 Zipkin

- **Used by:** All services (via Micrometer Tracing Bridge Brave)
- **Port:** 9411
- **Purpose:** Distributed tracing across service-to-service calls

### 5.4 Database Connections

| Service | Database | Technology |
|---|---|---|
| Core Banking | `banking_core_service` | MySQL 8.4, Flyway migrations |
| Fund Transfer | `banking_core_fund_transfer_service` | MySQL 8.4, JPA auto-DDL |
| User Service | `banking_core_user_service` | MySQL 8.4, JPA auto-DDL |
| Utility Payment | `banking_core_utility_payment_service` | MySQL 8.4, JPA auto-DDL |

All services share a single MySQL instance with separate databases. Credentials: `javatodev_development / oPItyPticIAt`.

### 5.5 Spring Cloud Config

- **Config Server** reads from Git: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch:** `main`, **Path:** `configuration/`
- All services use `bootstrap.yml` to connect to config server at startup
- Profile-specific bootstrap files: `bootstrap-dev.yml` (local IP), `bootstrap-docker.yml` (Docker hostname)

### 5.6 Service Discovery (Eureka)

- **Server:** `internet-banking-service-registry` on port 8081
- All other services register as Eureka clients
- Feign clients use service names (e.g., `core-banking-service`) resolved via Eureka

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Gradle 8.6** — each service has its own `build.gradle` (no multi-project root build)
- **Spring Boot Gradle Plugin 3.2.4** — produces fat JARs
- **Git Properties Plugin** — embeds git commit info into `/actuator/info`
- **Java 21** — `sourceCompatibility = '21'`

### 6.2 Docker

- Each service has a `Dockerfile` based on `eclipse-temurin:21.0.2_13-jre-alpine`
- Images are tagged as `javatodev/<service-name>`
- Services include `wait-for-it.sh` for startup ordering
- Docker profile activated via `-Dspring.profiles.active=docker`

### 6.3 Docker Compose

Two compose files in `docker-compose/`:

1. **`docker-compose-support-apps.yml`** — infrastructure only (Zipkin, Keycloak, PostgreSQL, MySQL, Config Server, Service Registry)
2. **`docker-compose.yml`** — full stack (all infrastructure + all application services)

**Network:** Custom bridge `javatodev_ib_network` (`172.25.0.0/16`) with static IPs per container.

**Startup orchestration:** `wait-for-it.sh` ensures services wait for:
- Service Registry (8081)
- Config Server (8090)
- MySQL (3306)

### 6.4 CI/CD

- No CI/CD pipeline configuration found in the repository (no GitHub Actions, Jenkinsfile, etc.)
- Deployment is manual via `docker-compose up -d`

### 6.5 Testing

- **Core Banking Service** has unit tests:
  - `AccountServiceTest` — 6 tests (CRUD for bank/utility accounts)
  - `TransactionServiceTest` — 6 tests (fund transfer, utility payment, insufficient funds, not found)
  - `UserServiceTest` — tests for user read operations
  - Uses H2 in-memory database for test profile
- **Other services:** Only boilerplate `contextLoads()` test stubs
- **No integration tests, contract tests, or E2E tests**
