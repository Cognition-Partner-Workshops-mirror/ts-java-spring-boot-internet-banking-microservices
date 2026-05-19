# Internet Banking Microservices — Application Knowledge Base

## 1. Architecture Overview

### 1.1 Services

The application comprises **7 microservices** (6 in the original repo + the API gateway acting as an edge service):

| Service | Port | Role |
|---|---|---|
| **internet-banking-service-registry** | 8081 | Netflix Eureka service-discovery server |
| **internet-banking-config-server** | 8090 | Spring Cloud Config Server backed by a Git repository |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway — single entry-point, OAuth2 resource server |
| **internet-banking-user-service** | 8083 | User registration, approval, Keycloak integration |
| **internet-banking-fund-transfer-service** | 8084 | Fund transfer orchestration between accounts |
| **internet-banking-utility-payment-service** | 8085 | Utility bill payment orchestration |
| **core-banking-service** | 8092 | Core banking engine — accounts, users, transactions |

### 1.2 Communication Patterns

| Pattern | Implementation | Details |
|---|---|---|
| Synchronous REST | OpenFeign clients | `fund-transfer-service` → `core-banking-service`; `utility-payment-service` → `core-banking-service`; `user-service` → `core-banking-service` |
| Service Discovery | Netflix Eureka | All services register with the Eureka server; Feign clients resolve service names through Eureka |
| Centralized Configuration | Spring Cloud Config Server | Configs fetched from a remote Git repo (`internet-banking-microservices-configurations`) via `bootstrap.yml` |
| API Gateway Routing | Spring Cloud Gateway | Routes traffic to downstream services; injects `X-Auth-Id` header from OAuth2 principal |
| Authentication | OAuth2 / Keycloak | API Gateway acts as OAuth2 resource-server; JWT validation via JWK Set URI |
| Distributed Tracing | Micrometer + Zipkin | `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` across all services |

### 1.3 Infrastructure Components

| Component | Technology | Purpose |
|---|---|---|
| Database | MySQL 8 | Shared instance with 4 schemas: `banking_core_service`, `banking_core_fund_transfer_service`, `banking_core_user_service`, `banking_core_utility_payment_service` |
| Identity Provider | Keycloak 23.0.7 (PostgreSQL 15 backend) | User authentication, realm/client management, OAuth2 token issuance |
| Distributed Tracing | Zipkin 3 | Collects and visualizes distributed traces |
| Message Broker | RabbitMQ (mentioned in README) | Planned for notification service — **not yet implemented** |

---

## 2. Data Model Documentation

### 2.1 core-banking-service

#### `banking_core_user`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `first_name` | VARCHAR(255) | |
| `last_name` | VARCHAR(255) | |
| `email` | VARCHAR(255) | |
| `identification_number` | VARCHAR(255) | National ID / passport number |

#### `banking_core_account`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `number` | VARCHAR(255) | Account number |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `actual_balance` | DECIMAL(19,2) | |
| `available_balance` | DECIMAL(19,2) | |
| `user_id` | BIGINT (FK → `banking_core_user.id`) | Many-to-one |

#### `banking_core_transaction`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `amount` | DECIMAL(19,2) | Negative for debits |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Target account number or utility reference |
| `transaction_id` | VARCHAR(50) | UUID generated per transfer |
| `account_id` | BIGINT (FK → `banking_core_account.id`) | One-to-one (cascade ALL) |

#### `banking_core_utility_account`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `number` | VARCHAR(255) | Provider account number |
| `provider_name` | VARCHAR(255) | e.g., VODAFONE, VERIZON |

#### Relationships

```
banking_core_user  1──*  banking_core_account  1──1  banking_core_transaction
```

### 2.2 internet-banking-user-service

#### `user` table (managed via JPA, schema: `banking_core_user_service`)

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `auth_id` | VARCHAR | Keycloak user ID |
| `identification` | VARCHAR | Links to core-banking user |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | TIMESTAMP | Audit field |
| `created_by` | VARCHAR | Audit field |
| `modified_date` | TIMESTAMP | Audit field |
| `modified_by` | VARCHAR | Audit field |
| `version` | BIGINT | Optimistic locking |

### 2.3 internet-banking-fund-transfer-service

#### `fund_transfer` table (schema: `banking_core_fund_transfer_service`)

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `transaction_reference` | VARCHAR | UUID from core-banking |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `status` | VARCHAR | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | — | Audit fields (inherited from `AuditAware`) |

### 2.4 internet-banking-utility-payment-service

#### `utility_payment` table (schema: `banking_core_utility_payment_service`)

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Bill reference |
| `account` | VARCHAR | Payer's bank account number |
| `transaction_id` | VARCHAR | UUID from core-banking |
| `status` | VARCHAR | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | — | Audit fields (inherited from `AuditAware`) |

---

## 3. API Surface Map

### 3.1 core-banking-service (port 8092)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Look up bank account by number | — | `BankAccount` (id, number, type, status, balances, user) |
| `GET` | `/api/v1/account/util-account/{account_name}` | Look up utility account by provider name | — | `UtilityAccount` (id, number, providerName) |
| `POST` | `/api/v1/transaction/fund-transfer` | Execute fund transfer in core bank | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `POST` | `/api/v1/transaction/util-payment` | Execute utility payment in core bank | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/user/{identification}` | Get user by identification number | — | `User` (id, firstName, lastName, email, identificationNumber, bankAccounts) |
| `GET` | `/api/v1/user` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |

### 3.2 internet-banking-user-service (port 8083)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Register new internet banking user | `{ email, identification, password }` | `User` (id, email, identification, authId, status) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user status (approve/disable) | `{ status }` | `User` |
| `GET` | `/api/v1/bank-users` | List registered users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | — | `User` |

### 3.3 internet-banking-fund-transfer-service (port 8084)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `{ fromAccount, toAccount, amount, authID }` | `{ message, transactionId }` |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | Query: `page`, `size`, `sort` | `List<FundTransfer>` |

### 3.4 internet-banking-utility-payment-service (port 8085)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | Query: `page`, `size`, `sort` | `List<UtilityPayment>` |

### 3.5 API Gateway Routing (port 8082)

The API Gateway routes requests to downstream services. Based on the Spring Cloud Gateway configuration (loaded from Config Server), expected route prefixes are:

| Gateway Path Prefix | Target Service |
|---|---|
| `/user/**` | `internet-banking-user-service` |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` |
| `/utility-payment/**` | `internet-banking-utility-payment-service` |
| `/banking-core/**` | `core-banking-service` |

### 3.6 Infrastructure Endpoints

| Service | Endpoint | Description |
|---|---|---|
| Service Registry | `GET /` (8081) | Eureka dashboard |
| Config Server | `GET /{app}/{profile}` (8090) | Configuration retrieval |
| All services | `GET /actuator/**` | Spring Boot Actuator (health, info, etc.) |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow (`internet-banking-user-service`)

1. Check if email already exists in Keycloak → reject with `UserAlreadyRegisteredException` if duplicate
2. Look up user by identification number via Feign call to `core-banking-service`
3. Validate email matches core-banking record → reject with `InvalidEmailException` if mismatch
4. Create user in Keycloak (disabled, email unverified) with provided password
5. Persist local `UserEntity` with status `PENDING` and Keycloak auth ID
6. Admin later approves user via `PATCH /update/{id}` → enables Keycloak account and sets `emailVerified=true`

### 4.2 Fund Transfer Flow (`internet-banking-fund-transfer-service` → `core-banking-service`)

1. **Fund transfer service** receives request, persists `FundTransferEntity` with status `PENDING`
2. Delegates to `core-banking-service` via Feign (`POST /api/v1/transaction/fund-transfer`)
3. **Core banking** validates source account balance (`actualBalance >= amount`)
4. Debits source account (`actualBalance -= amount`, `availableBalance = actualBalance - amount`)
5. Credits destination account (`actualBalance += amount`, `availableBalance = actualBalance + amount`)
6. Creates `TransactionEntity` records for both accounts with UUID `transactionId`
7. On success, fund transfer service updates local entity status to `SUCCESS` with transaction reference

**Business Rules:**
- Transfer rejected if `actualBalance < 0` or `actualBalance < amount` → `InsufficientFundsException`
- Account not found → `EntityNotFoundException`
- No transfer limits or daily caps enforced
- No idempotency checks — duplicate submissions will process twice

### 4.3 Utility Payment Flow (`internet-banking-utility-payment-service` → `core-banking-service`)

1. **Utility payment service** receives request, persists `UtilityPaymentEntity` with status `PROCESSING`
2. Delegates to `core-banking-service` via Feign (`POST /api/v1/transaction/util-payment`)
3. **Core banking** validates source account balance
4. Looks up utility provider by ID
5. Debits source account (both `actualBalance` and `availableBalance` reduced)
6. Creates `TransactionEntity` with negative amount and `UTILITY_PAYMENT` type
7. On success, utility payment service updates local entity to `SUCCESS` with transaction ID

### 4.4 Authentication & Authorization

- API Gateway enforces OAuth2 JWT validation via Keycloak JWK Set
- `/user/api/v1/bank-users/register` is publicly accessible (no auth required)
- All `/actuator/**` endpoints are publicly accessible
- All other routes require a valid JWT token
- Gateway injects `X-Auth-Id` header (Keycloak principal name) into downstream requests
- Downstream services extract `X-Auth-Id` via `AppAuthUserFilter` for audit trails

---

## 5. Integration Points

### 5.1 Keycloak (Identity Provider)

- **Service:** `internet-banking-user-service`
- **Client:** `keycloak-admin-client` (v24.0.4)
- **Operations:** Create user, update user (enable/verify email), search by email, read by auth ID
- **Configuration:** Server URL, realm, client ID, client secret injected via Spring Cloud Config properties (`app.config.keycloak.*`)
- **Grant type:** `client_credentials`

### 5.2 RabbitMQ (Message Broker)

- **Status:** Referenced in README but **not implemented** in current codebase
- **Planned use:** Notification service (not yet developed) would consume messages from fund-transfer and utility-payment services

### 5.3 Zipkin (Distributed Tracing)

- **Port:** 9411
- **Integration:** `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` in all services except config-server and service-registry
- **Trace propagation:** Via Brave tracer through Feign interceptors (`feign-micrometer`)

### 5.4 Database Connections

| Service | Database Schema | Technology | Migration |
|---|---|---|---|
| `core-banking-service` | `banking_core_service` | MySQL + JPA + Flyway | 3 migration scripts |
| `internet-banking-user-service` | `banking_core_user_service` | MySQL + JPA | Hibernate auto-DDL (no Flyway) |
| `internet-banking-fund-transfer-service` | `banking_core_fund_transfer_service` | MySQL + JPA | Hibernate auto-DDL (no Flyway) |
| `internet-banking-utility-payment-service` | `banking_core_utility_payment_service` | MySQL + JPA | Hibernate auto-DDL (no Flyway) |

All services connect to the **same MySQL instance** (container `mysql_javatodev_app`) with dedicated schemas.

### 5.5 Spring Cloud Config Server

- **Git backend:** `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch:** `main`
- **Search path:** `configuration/`
- **Consumers:** All services except service-registry use `bootstrap.yml` to fetch configs at startup
- **Profiles:** `default`, `dev`, `docker`

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build tool:** Gradle 8.6 (per-service wrapper — `gradlew`)
- **No root-level build file** — each service is an independent Gradle project
- **Shared library:** `banking-common` (available in the blueprint but not in the original 7-service repo)
- **Java:** OpenJDK 21 (source compatibility 21)
- **Spring Boot:** 3.2.4, Spring Cloud 2023.0.0

### 6.2 Docker

Each service has its own `Dockerfile`:
- Base image: `eclipse-temurin:21.0.2_13-jre-alpine`
- Builds a fat JAR via `./gradlew build`
- Uses `wait-for-it.sh` to wait for dependencies (Eureka, Config Server, MySQL) before starting
- Docker profile activated via `-Dspring.profiles.active=docker`

### 6.3 Docker Compose

- `docker-compose.yml` — full stack (all 7 services + MySQL + Keycloak + Zipkin)
- `docker-compose-support-apps.yml` — infrastructure only (MySQL, Keycloak, Zipkin, Config Server, Service Registry)
- Custom Docker network `javatodev_ib_network` with static IP assignments (172.25.0.x)
- MySQL uses a custom Dockerfile that creates 4 database schemas and a dedicated DB user

### 6.4 CI/CD

- **No CI/CD pipeline** configured — `.github/` only contains `FUNDING.yml`
- No GitHub Actions, Jenkins, or other CI configuration present
- No automated build, test, or deployment workflows

### 6.5 Database Migrations

- `core-banking-service` uses **Flyway** with 3 versioned migrations in `src/main/resources/db/migration/`
- Other services rely on **Hibernate auto-DDL** (no managed migrations)

### 6.6 API Documentation

- `core-banking-service`, `fund-transfer-service`, `user-service`, and `utility-payment-service` include `springdoc-openapi-starter-webflux-ui` (v2.1.0)
- OpenAPI annotations (`@Tag`, `@Operation`) present on controllers
- Swagger UI accessible at `/swagger-ui.html` for each service (when running)
