# Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Summary

This is a **Java 21 / Spring Boot 3.2.4 / Spring Cloud 2023.0.0** internet banking application composed of **6 microservices** that model a simplified banking core with user registration, fund transfers, and utility payments.

### 1.2 Service Inventory

| Service | Port | Role | Database |
|---|---|---|---|
| **internet-banking-config-server** | 8090 | Centralized configuration via Spring Cloud Config (Git-backed) | None |
| **internet-banking-service-registry** | 8081 | Netflix Eureka service discovery server | None |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway — routing, OAuth2/JWT security | None |
| **internet-banking-user-service** | 8083 | User registration, profile management, Keycloak integration | MySQL (`banking_core_user_service`) |
| **internet-banking-fund-transfer-service** | 8084 | Inter-account fund transfer orchestration | MySQL (`banking_core_fund_transfer_service`) |
| **internet-banking-utility-payment-service** | 8085 | Utility bill payment orchestration | MySQL (`banking_core_utility_payment_service`) |
| **core-banking-service** | 8092 | Core banking engine — accounts, users, transactions | MySQL (`banking_core_service`) |

> **Note:** A Notification Service is mentioned in the README as "PENDING Development" and does not exist in the codebase.

### 1.3 Communication Patterns

```
                          ┌──────────────────┐
                          │   API Gateway    │
                          │  (OAuth2 / JWT)  │
                          └────────┬─────────┘
                  ┌────────────────┼────────────────┐
                  ▼                ▼                 ▼
         ┌───────────────┐ ┌──────────────┐ ┌──────────────────┐
         │ User Service  │ │ Fund Transfer│ │ Utility Payment  │
         │               │ │   Service    │ │    Service       │
         └───────┬───────┘ └──────┬───────┘ └────────┬─────────┘
                 │                │                   │
                 │     OpenFeign (sync HTTP)          │
                 ▼                ▼                   ▼
              ┌──────────────────────────────────────────┐
              │          Core Banking Service             │
              └──────────────────────────────────────────┘
```

- **Synchronous (HTTP/REST via OpenFeign):** User, Fund Transfer, and Utility Payment services call Core Banking Service using Spring Cloud OpenFeign clients. Service discovery is via Eureka (service names, not hardcoded URLs).
- **API Gateway routing:** All external traffic enters through the API Gateway, which routes to downstream services by Eureka service name. The gateway injects an `X-Auth-Id` header (extracted from the JWT principal) into every proxied request.
- **Asynchronous (RabbitMQ):** Mentioned in the README for notification events but **not implemented** in the current codebase. No RabbitMQ dependencies exist.

### 1.4 Infrastructure Components

| Component | Purpose | Version / Image |
|---|---|---|
| **MySQL 8.4.0** | Primary data store for all business services | `mysql:8.4.0` (custom Dockerfile) |
| **Keycloak 23.0.7** | Identity & access management (OAuth2 / OIDC) | `quay.io/keycloak/keycloak:23.0.7` |
| **PostgreSQL 15** | Keycloak's backing database | `postgres:15` |
| **Zipkin 3** | Distributed tracing collector | `openzipkin/zipkin:3` |
| **Spring Cloud Config** | Externalized config from Git repo | Built-in service |
| **Netflix Eureka** | Service registry / discovery | Built-in service |

### 1.5 Configuration Management

- **Spring Cloud Config Server** pulls configuration from the Git repository: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` (branch: `main`, path: `configuration`).
- Each service has `bootstrap.yml` (localhost), `bootstrap-dev.yml` (LAN IP), and `bootstrap-docker.yml` (Docker hostname) variants pointing to the config server URI.
- Docker Compose uses `-Dspring.profiles.active=docker` to select the Docker-specific bootstrap.

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL: `banking_core_service`)

Managed by **Flyway** migrations.

#### `banking_core_user`
| Column | Type | Constraints |
|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `email` | VARCHAR(255) | |
| `first_name` | VARCHAR(255) | |
| `last_name` | VARCHAR(255) | |
| `identification_number` | VARCHAR(255) | Unique national ID |

#### `banking_core_account`
| Column | Type | Constraints |
|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `number` | VARCHAR(255) | Account number |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `ACTIVE` |
| `actual_balance` | DECIMAL(19,2) | |
| `available_balance` | DECIMAL(19,2) | |
| `user_id` | BIGINT | FK -> `banking_core_user.id` |

#### `banking_core_utility_account`
| Column | Type | Constraints |
|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `number` | VARCHAR(255) | |
| `provider_name` | VARCHAR(255) | e.g. VODAFONE, VERIZON |

#### `banking_core_transaction`
| Column | Type | Constraints |
|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `amount` | DECIMAL(19,2) | |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | |
| `transaction_id` | VARCHAR(50) | UUID |
| `account_id` | BIGINT | FK -> `banking_core_account.id` |

**Relationships:**
- `User 1:N Account` (a user owns multiple bank accounts)
- `Account 1:1 Transaction` (mapped via `@OneToOne` in JPA, though logically 1:N)

### 2.2 Internet Banking User Service (MySQL: `banking_core_user_service`)

Schema managed by **JPA auto-DDL** (no Flyway).

#### `user`
| Column | Type | Constraints |
|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `auth_id` | VARCHAR | Keycloak user UUID |
| `identification` | VARCHAR | National ID (links to core banking user) |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED` |
| `created_date` | TIMESTAMP | Audit field |
| `created_by` | VARCHAR | Audit field |
| `modified_date` | TIMESTAMP | Audit field |
| `modified_by` | VARCHAR | Audit field |
| `version` | BIGINT | Optimistic locking |

### 2.3 Fund Transfer Service (MySQL: `banking_core_fund_transfer_service`)

Schema managed by **JPA auto-DDL** (no Flyway).

#### `fund_transfer`
| Column | Type | Constraints |
|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `transaction_reference` | VARCHAR | UUID from core banking |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `status` | VARCHAR | Enum: `PENDING`, `SUCCESS`, `FAILED` |
| `created_date` / `modified_date` | TIMESTAMP | Audit fields |
| `version` | BIGINT | Optimistic locking |

### 2.4 Utility Payment Service (MySQL: `banking_core_utility_payment_service`)

Schema managed by **JPA auto-DDL** (no Flyway).

#### `utility_payment`
| Column | Type | Constraints |
|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `provider_id` | BIGINT | Utility provider reference |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Customer reference |
| `account` | VARCHAR | Payer's bank account number |
| `transaction_id` | VARCHAR | UUID from core banking |
| `status` | VARCHAR | Enum: `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `modified_date` | TIMESTAMP | Audit fields |
| `version` | BIGINT | Optimistic locking |

---

## 3. API Surface Map

### 3.1 API Gateway Routes (Port 8082)

All external requests are routed through the gateway with path prefix stripping:

| Path Prefix | Target Service |
|---|---|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |
| `/banking-core/**` | core-banking-service |

**Security:** All endpoints require a valid JWT token except:
- `POST /user/api/v1/bank-users/register` (public)
- `/actuator/**` on all services (public)

### 3.2 Core Banking Service (Port 8092)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | — | `BankAccount` (id, number, type, status, availableBalance, actualBalance) |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | — | `UtilityAccount` (id, number, providerName) |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer | `{fromAccount, toAccount, amount}` | `{message, transactionId}` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` |
| `GET` | `/api/v1/user/{identification}` | Get user by national ID | — | `User` (id, firstName, lastName, email, identificationNumber) |
| `GET` | `/api/v1/user` | List users (paginated) | — | `List<User>` |

### 3.3 Internet Banking User Service (Port 8083)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Register new user | `{email, identification, password}` | `User` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user (approve/reject) | `{status}` | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | — | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | — | `User` |

### 3.4 Fund Transfer Service (Port 8084)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `{fromAccount, toAccount, amount}` | `{message, transactionId}` |
| `GET` | `/api/v1/transfer` | List transfers (paginated) | — | `List<FundTransfer>` |

### 3.5 Utility Payment Service (Port 8085)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` |
| `GET` | `/api/v1/utility-payment` | List payments (paginated) | — | `List<UtilityPayment>` |

### 3.6 Infrastructure Endpoints

| Service | Endpoint | Description |
|---|---|---|
| Config Server | `GET /{application}/{profile}` | Retrieve config for a service/profile |
| Service Registry | `GET /eureka/apps` | List registered services |
| All services | `GET /actuator/health` | Health check (Spring Boot Actuator) |
| All services | `GET /actuator/info` | Application info (git properties) |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

1. Client calls `POST /user/api/v1/bank-users/register` with `{email, identification, password}`.
2. **User Service** checks if email already exists in Keycloak (`readUserByEmail`). If yes, throws `UserAlreadyRegisteredException`.
3. Calls **Core Banking Service** via Feign to verify the identification number exists in the banking core user table.
4. Validates that the email in the request matches the email on the core banking user record. If not, throws `InvalidEmailException`.
5. Creates a Keycloak user (disabled, email unverified) with the provided password.
6. Stores a local user record with `status=PENDING` and the Keycloak `authId`.
7. An admin later calls `PATCH /update/{id}` with `status=APPROVED` to enable the Keycloak user and mark email as verified.

### 4.2 Fund Transfer Flow

1. Client calls `POST /fund-transfer/api/v1/transfer` with `{fromAccount, toAccount, amount}`.
2. **Fund Transfer Service** saves a `PENDING` record in its local database.
3. Delegates to **Core Banking Service** via Feign (`POST /api/v1/transaction/fund-transfer`).
4. **Core Banking Service**:
   - Reads both source and destination bank accounts.
   - Validates that the source account has sufficient funds (`actualBalance >= amount`).
   - Deducts from source account (`actualBalance -= amount`, `availableBalance = actualBalance - amount`).
   - Credits destination account (`actualBalance += amount`, `availableBalance = actualBalance + amount`).
   - Records two transaction entries (debit and credit) with the same `transactionId`.
   - The entire operation is wrapped in `@Transactional`.
5. Fund Transfer Service updates its record to `SUCCESS` with the core banking `transactionId`.

### 4.3 Utility Payment Flow

1. Client calls `POST /utility-payment/api/v1/utility-payment` with `{providerId, amount, referenceNumber, account}`.
2. **Utility Payment Service** saves a `PROCESSING` record in its local database.
3. Delegates to **Core Banking Service** via Feign (`POST /api/v1/transaction/util-payment`).
4. **Core Banking Service**:
   - Reads the payer's bank account and validates sufficient funds.
   - Looks up the utility provider account by ID.
   - Deducts funds from the payer's account.
   - Records a transaction entry of type `UTILITY_PAYMENT`.
5. Utility Payment Service updates its record to `SUCCESS` with the `transactionId`.

### 4.4 Balance Validation Rules

- `actualBalance` must be >= 0.
- `actualBalance` must be >= requested transfer/payment `amount`.
- Violation throws `InsufficientFundsException` with code `BANKING-CORE-SERVICE-1001`.

---

## 5. Integration Points

### 5.1 Keycloak Integration (User Service only)

- **Library:** `keycloak-admin-client:24.0.4`
- **Configuration:** `app.config.keycloak.server-url`, `realm`, `clientId`, `client-secret` (from Spring Cloud Config)
- **Auth:** Client credentials grant (`client_credentials`)
- **Operations:** Create user, update user (enable/verify), search by email, read by ID
- **Singleton pattern:** `KeycloakProperties` maintains a static Keycloak instance (not thread-safe)

### 5.2 RabbitMQ (Not Implemented)

- Referenced in the README for notification events from Fund Transfer and Utility Payment services.
- No RabbitMQ dependencies or configuration exist in the codebase.

### 5.3 Zipkin Distributed Tracing

- All services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave`.
- Zipkin server runs at port 9411 in Docker Compose.
- Trace propagation is automatic via Spring Boot Actuator + Micrometer.

### 5.4 Database Connections

- **Driver:** `com.mysql:mysql-connector-j:8.4.0`
- **Connection details:** Configured via Spring Cloud Config (externalized to Git repo).
- **Core Banking Service** uses Flyway 10.12.0 for schema migrations.
- Other services rely on JPA `ddl-auto` (likely `update` mode, configured externally).
- **Test profile:** All services use H2 in-memory database (`com.h2database:h2:2.2.224`).

### 5.5 Inter-Service Communication (OpenFeign)

| Calling Service | Target Service | Feign Client | Endpoints Called |
|---|---|---|---|
| User Service | Core Banking | `BankingCoreRestClient` | `GET /api/v1/user/{identification}` |
| Fund Transfer Service | Core Banking | `BankingCoreFeignClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/fund-transfer` |
| Utility Payment Service | Core Banking | `BankingCoreRestClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/util-payment` |

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build tool:** Gradle (wrapper included per service, Gradle 8.6)
- **No multi-module build:** Each service is an independent Gradle project with its own `build.gradle`, `settings.gradle`, and Gradle wrapper. There is no root-level `build.gradle` or `settings.gradle`.
- **Plugins:** `spring-boot`, `spring-dependency-management`, `gradle-git-properties`
- **Test framework:** JUnit 5 (`useJUnitPlatform()`)

### 6.2 Docker Deployment

- **Two Compose files:**
  - `docker-compose-support-apps.yml`: Infrastructure only (Zipkin, Keycloak, PostgreSQL, MySQL, Config Server, Service Registry)
  - `docker-compose.yml`: Full stack (infrastructure + all application services)
- **Custom network:** `javatodev_ib_network` (172.25.0.0/16) with static IPs per container.
- **Startup ordering:** `wait-for-it.sh` scripts ensure services wait for dependencies (registry, config server, MySQL) before starting.
- **Docker images:** Pre-built images from `javatodev/*` registry (no Dockerfiles in repo for application services).
- **Profile activation:** `-Dspring.profiles.active=docker` selects Docker-specific bootstrap config.

### 6.3 MySQL Initialization

- Custom MySQL Docker image (`docker-compose/mysql/Dockerfile`) based on `mysql:8.4.0`.
- `privileges.sql` runs on first start: creates user `javatodev_development`, creates 4 databases.
- Root password: set via environment variable in Compose.

### 6.4 Keycloak Initialization

- Keycloak starts in dev mode with `--import-realm`.
- Realm configuration is mounted from `./keycloak` directory.
- Pre-configured with realm, client, and test user data.
- Test credentials: `ib_admin@javatodev.com / 5V7huE3G86uB`

### 6.5 CI/CD

- No CI/CD pipeline exists in the repository (`.github/` contains only `FUNDING.yml`).
- No GitHub Actions workflows.
- No Kubernetes manifests despite Kubernetes being listed in the technology stack.
