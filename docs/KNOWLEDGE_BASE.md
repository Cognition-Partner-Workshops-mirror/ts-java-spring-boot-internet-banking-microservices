# Application Knowledge Base

## Internet Banking Concept — Java Spring Boot Microservices

**Tech Stack:** Java 21 · Spring Boot 3.2.4 · Spring Cloud 2023.0.0 · Gradle · Docker Compose

---

## 1. Architecture Overview

### 1.1 Services

| # | Service | Port | Role |
|---|---------|------|------|
| 1 | **internet-banking-service-registry** | 8081 | Netflix Eureka Server — service discovery |
| 2 | **internet-banking-config-server** | 8090 | Spring Cloud Config Server — centralized configuration backed by a Git repository |
| 3 | **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway — single entry point, OAuth2/JWT security, request routing |
| 4 | **internet-banking-user-service** | 8083 | User registration, approval, profile management; Keycloak integration |
| 5 | **internet-banking-fund-transfer-service** | 8084 | Fund transfers between bank accounts |
| 6 | **internet-banking-utility-payment-service** | 8085 | Utility bill payments (telecom, electric, etc.) |
| 7 | **core-banking-service** | 8092 | Core banking engine — accounts, users, transactions, balance management |

> **Note:** A Notification service is described in the README but is **not yet implemented** (marked "PENDING Development").

### 1.2 Communication Patterns

| Pattern | Implementation | Usage |
|---------|---------------|-------|
| **Synchronous REST** | Spring Cloud OpenFeign | Fund-transfer-service → core-banking-service; User-service → core-banking-service; Utility-payment-service → core-banking-service |
| **Service Discovery** | Netflix Eureka | All business services register with the Eureka server and resolve each other by service name |
| **API Gateway** | Spring Cloud Gateway | Routes external traffic to downstream services via Eureka-resolved service names |
| **Centralized Config** | Spring Cloud Config (Git-backed) | All services fetch configuration from the Config Server on startup via `bootstrap.yml` |
| **Async Messaging** | RabbitMQ (planned) | Listed in tech stack for notification events; not yet wired in code |

### 1.3 Infrastructure Components

| Component | Image / Tech | Purpose |
|-----------|-------------|---------|
| **MySQL 8.4** | `mysql:8.4.0` (custom Dockerfile) | Primary RDBMS for all business services. Four databases: `banking_core_service`, `banking_core_fund_transfer_service`, `banking_core_user_service`, `banking_core_utility_payment_service` |
| **Keycloak 23.0.7** | `quay.io/keycloak/keycloak:23.0.7` | Identity & Access Management — OAuth2/OIDC provider |
| **PostgreSQL 15** | `postgres:15` | Keycloak's backing store |
| **Zipkin 3** | `openzipkin/zipkin:3` | Distributed tracing collector & UI |
| **RabbitMQ** | Referenced in tech stack | Message broker for async notifications (not yet deployed in docker-compose) |

### 1.4 Network Topology (Docker Compose)

All containers run on a custom bridge network `javatodev_ib_network` (`172.25.0.0/16`) with static IP assignments:

```
172.25.0.2   core-banking-service
172.25.0.3   internet-banking-utility-payment-service
172.25.0.4   internet-banking-fund-transfer-service
172.25.0.5   internet-banking-user-service
172.25.0.6   internet-banking-api-gateway
172.25.0.7   internet-banking-service-registry
172.25.0.8   internet-banking-config-server
172.25.0.9   mysql_javatodev_app
172.25.0.10  keycloak_postgre_db
172.25.0.11  keycloak_web
172.25.0.12  openzipkin_server
```

Service startup order is enforced via `wait-for-it.sh` entrypoints that block until the service registry, config server, and MySQL are accepting connections.

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (`banking_core_service` database)

#### `banking_core_user`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `first_name` | VARCHAR(255) | |
| `last_name` | VARCHAR(255) | |
| `email` | VARCHAR(255) | |
| `identification_number` | VARCHAR(255) | National ID / passport |

#### `banking_core_account`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `number` | VARCHAR(255) | Account number |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `actual_balance` | DECIMAL(19,2) | |
| `available_balance` | DECIMAL(19,2) | |
| `user_id` | BIGINT (FK → `banking_core_user.id`) | |

#### `banking_core_utility_account`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `number` | VARCHAR(255) | Provider account number |
| `provider_name` | VARCHAR(255) | e.g., VODAFONE, VERIZON |

#### `banking_core_transaction`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `amount` | DECIMAL(19,2) | Negative for debits |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Target account or reference |
| `transaction_id` | VARCHAR(50) | UUID correlation ID |
| `account_id` | BIGINT (FK → `banking_core_account.id`) | |

**Relationships:**
- `User 1:N BankAccount` (via `user_id` FK)
- `BankAccount 1:1 Transaction` (via `account_id` FK — each transaction row is one side of the ledger)

### 2.2 User Service (`banking_core_user_service` database)

#### `user`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `auth_id` | VARCHAR(255) | Keycloak user ID |
| `identification` | VARCHAR(255) | Maps to core banking `identification_number` |
| `status` | VARCHAR(255) | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | TIMESTAMP | JPA Auditing |
| `created_by` | VARCHAR(255) | JPA Auditing |
| `modified_date` | TIMESTAMP | JPA Auditing |
| `modified_by` | VARCHAR(255) | JPA Auditing |
| `version` | BIGINT | Optimistic locking |

### 2.3 Fund Transfer Service (`banking_core_fund_transfer_service` database)

#### `fund_transfer`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `transaction_reference` | VARCHAR(255) | Core banking transaction ID |
| `from_account` | VARCHAR(255) | Source account number |
| `to_account` | VARCHAR(255) | Destination account number |
| `amount` | DECIMAL(19,2) | |
| `status` | VARCHAR(255) | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | — | JPA Auditing + optimistic lock |

### 2.4 Utility Payment Service (`banking_core_utility_payment_service` database)

#### `utility_payment`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL(19,2) | |
| `reference_number` | VARCHAR(255) | |
| `account` | VARCHAR(255) | Payer's bank account number |
| `transaction_id` | VARCHAR(255) | Core banking transaction ID |
| `status` | VARCHAR(255) | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | — | JPA Auditing + optimistic lock |

### 2.5 Schema Management

- **Core Banking Service:** Flyway migrations in `src/main/resources/db/migration/`
  - `V1.0.20210427174638__create_base_table_structure.sql` — creates `banking_core_user`, `banking_core_account`, `banking_core_utility_account`
  - `V1.0.20210427174721__temp_data.sql` — seeds test users, accounts, and utility providers
  - `V1.0.20210429210839__create_transaction_table.sql` — creates `banking_core_transaction`
- **Other services:** Rely on JPA `ddl-auto` (configured via external config server); no Flyway migrations.

---

## 3. API Surface Map

### 3.1 Core Banking Service (port 8092)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | — | `BankAccount` (id, number, type, status, availableBalance, actualBalance) |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | — | `UtilityAccount` (id, number, providerName) |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/user/{identification}` | Get user by identification number | — | `User` (id, firstName, lastName, email, identificationNumber, bankAccounts[]) |
| `GET` | `/api/v1/user` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |

### 3.2 Internet Banking User Service (port 8083)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/bank-users/register` | Register new internet banking user | `{ email, identification, password }` | `User` (id, email, identification, authId, status) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user (e.g., approve) | `{ status }` | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | — | `User` |

### 3.3 Internet Banking Fund Transfer Service (port 8084)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `{ fromAccount, toAccount, amount, authID }` | `{ message, transactionId }` |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | Query: `page`, `size`, `sort` | `List<FundTransfer>` |

### 3.4 Internet Banking Utility Payment Service (port 8085)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | Query: `page`, `size`, `sort` | `List<UtilityPayment>` |

### 3.5 API Gateway Routes (port 8082)

The gateway routes are configured externally via the Config Server Git repository. Based on the security configuration, the following path prefixes are exposed:

| Path Prefix | Target Service |
|-------------|---------------|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/banking-core/**` | core-banking-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |

**Public endpoints** (no authentication required):
- `POST /user/api/v1/bank-users/register`
- `/actuator/**` (all services)

All other endpoints require a valid JWT (OAuth2 Resource Server with Keycloak JWK URI).

### 3.6 Infrastructure Endpoints

| Service | Endpoint | Description |
|---------|----------|-------------|
| Eureka Dashboard | `http://localhost:8081` | Service registry UI |
| Zipkin UI | `http://localhost:9411` | Distributed tracing |
| Keycloak Admin | `http://localhost:8080` | IAM admin console (admin/password) |
| Spring Actuator | `/actuator/**` on each service | Health, info, metrics |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

1. Client calls `POST /user/api/v1/bank-users/register` with `{ email, identification, password }`
2. **User Service** checks if email already exists in Keycloak (via admin API)
3. Validates user against core banking via Feign: `GET /api/v1/user/{identification}`
4. Verifies email matches the core banking record
5. Creates user in Keycloak (disabled, email unverified) with provided password
6. Retrieves the Keycloak-assigned user ID
7. Persists a local `UserEntity` with status `PENDING`
8. Admin later calls `PATCH /api/v1/bank-users/update/{id}` with `{ status: "APPROVED" }` to enable the Keycloak user

### 4.2 Fund Transfer Rules

1. Client calls `POST /api/v1/transfer` on the Fund Transfer Service
2. Service persists a `FundTransferEntity` with status `PENDING`
3. Delegates to core banking via Feign: `POST /api/v1/transaction/fund-transfer`
4. **Core banking logic:**
   - Reads both source and destination `BankAccount` entities
   - **Balance validation:** `actualBalance >= 0 AND actualBalance >= transferAmount`
   - Debits source account: `actualBalance -= amount`, `availableBalance = actualBalance - amount`
   - Credits destination account: `actualBalance += amount`, `availableBalance = actualBalance + amount`
   - Creates two `TransactionEntity` records (debit with negative amount, credit with positive amount)
   - Generates a UUID `transactionId` for correlation
   - Entire operation runs in a `@Transactional` scope
5. Fund Transfer Service updates local entity to `SUCCESS` with the transaction reference

### 4.3 Utility Payment Processing

1. Client calls `POST /api/v1/utility-payment` on the Utility Payment Service
2. Service persists a `UtilityPaymentEntity` with status `PROCESSING`
3. Delegates to core banking via Feign: `POST /api/v1/transaction/util-payment`
4. **Core banking logic:**
   - Reads the payer's `BankAccount`
   - **Balance validation** (same as fund transfer)
   - Reads the `UtilityAccount` by provider ID
   - Debits the payer's account
   - Creates a `TransactionEntity` with type `UTILITY_PAYMENT`
   - Returns a UUID `transactionId`
5. Utility Payment Service updates local entity to `SUCCESS`

### 4.4 User Management (Core Banking)

- Users are read-only in the core banking service (no create/update endpoints)
- Lookup by identification number (`findByIdentificationNumber`)
- Paginated list retrieval
- Each user has associated bank accounts (lazy-loaded `@OneToMany`)

### 4.5 Authentication & Authorization

1. Users authenticate via Keycloak OAuth2 (OIDC) to obtain a JWT
2. The API Gateway validates JWTs using Keycloak's JWK Set URI
3. Gateway injects the authenticated principal's name into an `X-Auth-Id` header
4. Downstream services extract `X-Auth-Id` via `AppAuthUserFilter` for audit trails
5. The user registration endpoint (`/register`) is the only public endpoint

---

## 5. Integration Points

### 5.1 Keycloak (Identity Provider)

- **Version:** 23.0.7
- **Connection:** User Service → Keycloak Admin REST API via `keycloak-admin-client:24.0.4`
- **Configuration:** `app.config.keycloak.*` properties (server-url, realm, clientId, client-secret)
- **Realm:** `javatodev-internet-banking` (imported from `realm-export.json`)
- **Functions:** User creation, user update (enable/disable), user lookup by email, user read by ID
- **Gateway Integration:** OAuth2 Resource Server validates JWTs against Keycloak's JWK endpoint

### 5.2 RabbitMQ (Message Broker) — PLANNED

- Listed in the technology stack
- Intended for notification events from Fund Transfer and Utility Payment services
- **Not yet implemented** in code or Docker Compose

### 5.3 Zipkin (Distributed Tracing)

- **Version:** 3
- **Dependencies:** `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` in all business services
- **Port:** 9411
- **Feign tracing:** `feign-micrometer` dependency included for propagating trace context across Feign calls

### 5.4 Database Connections

| Service | Database | Driver | Schema Management |
|---------|----------|--------|-------------------|
| core-banking-service | `banking_core_service` (MySQL) | `mysql-connector-j:8.4.0` | Flyway (`flyway-core:10.12.0`, `flyway-mysql:10.12.0`) |
| internet-banking-user-service | `banking_core_user_service` (MySQL) | `mysql-connector-j:8.4.0` | JPA ddl-auto |
| internet-banking-fund-transfer-service | `banking_core_fund_transfer_service` (MySQL) | `mysql-connector-j:8.4.0` | JPA ddl-auto |
| internet-banking-utility-payment-service | `banking_core_utility_payment_service` (MySQL) | `mysql-connector-j:8.4.0` | JPA ddl-auto |

All database connection details (URL, username, password) are externalized to the Spring Cloud Config Server Git repository.

### 5.5 Inter-Service Communication (Feign Clients)

| Caller | Target | Feign Client Class | Endpoints Called |
|--------|--------|--------------------|-----------------|
| User Service | core-banking-service | `BankingCoreRestClient` | `GET /api/v1/user/{identification}` |
| Fund Transfer Service | core-banking-service | `BankingCoreFeignClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/fund-transfer` |
| Utility Payment Service | core-banking-service | `BankingCoreRestClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/util-payment` |

### 5.6 Spring Cloud Config Server

- **Git Repository:** `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch:** `main`
- **Search Path:** `configuration/`
- All services connect via `bootstrap.yml` → `spring.cloud.config.uri`
- Profile-specific bootstrap files: `bootstrap-dev.yml` (local IP), `bootstrap-docker.yml` (container hostname)

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Gradle** (per-service `build.gradle`, no root-level multi-project build)
- Each service is an independent Gradle project with its own `build.gradle`
- Common plugins: `java`, `org.springframework.boot:3.2.4`, `io.spring.dependency-management:1.1.4`, `com.gorylenko.gradle-git-properties:2.4.2`
- Test framework: JUnit 5 (`useJUnitPlatform()`)

### 6.2 Docker

Each service has its own `Dockerfile`:
- **Base image:** `eclipse-temurin:21.0.2_13-jre-alpine`
- Copies the built JAR (`build/libs/*.jar`) as `app.jar`
- Includes `wait-for-it.sh` for startup ordering
- Installs `bash` (required by `wait-for-it.sh` on Alpine)
- Default entrypoint: `java -jar -Dspring.profiles.active=docker /app.jar`

### 6.3 Docker Compose

Two compose files in `docker-compose/`:

1. **`docker-compose.yml`** — Full stack (all services + infrastructure)
2. **`docker-compose-support-apps.yml`** — Infrastructure only (Zipkin, Keycloak, PostgreSQL, MySQL, Config Server, Service Registry)

Custom Docker builds:
- `docker-compose/mysql/` — MySQL with init script (`privileges.sql`) that creates the four databases and a development user
- `docker-compose/keycloak/` — Keycloak with realm import (`realm-export.json`)

### 6.4 Build & Deploy Steps

```bash
# 1. Build all services
cd core-banking-service && ./gradlew build
cd internet-banking-user-service && ./gradlew build
cd internet-banking-fund-transfer-service && ./gradlew build
cd internet-banking-utility-payment-service && ./gradlew build
cd internet-banking-api-gateway && ./gradlew build
cd internet-banking-config-server && ./gradlew build
cd internet-banking-service-registry && ./gradlew build

# 2. Build Docker images
docker build -t javatodev/core-banking-service ./core-banking-service
docker build -t javatodev/internet-banking-user-service ./internet-banking-user-service
# ... etc.

# 3. Start everything
cd docker-compose && docker-compose up -d
```

### 6.5 CI/CD

- No GitHub Actions workflows present (removed for PAT scope compatibility)
- No Jenkinsfile, GitLab CI, or other CI/CD configuration files in the repository
- Deployment is manual via Docker Compose

### 6.6 Test Data

Pre-seeded via Flyway migration `V1.0.20210427174721__temp_data.sql`:
- 4 test users (Sam Silva, Guru Darmaraj, Ragu Sivaraj, Randor Manoon)
- 14 savings accounts with various balances
- 6 utility providers (VODAFONE, VERIZON, SINGTEL, HUTCH, AIRTEL, GIO)

Keycloak test credentials: `ib_admin@javatodev.com / 5V7huE3G86uB`
