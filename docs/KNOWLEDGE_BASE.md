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

### 1.1 System Summary

The Internet Banking system is a Java 21 / Spring Boot 3.2.4 microservices application consisting of **6 independently deployable services** communicating via synchronous REST (OpenFeign) and backed by a MySQL database. The system implements retail internet banking features: user registration, fund transfers between accounts, and utility bill payments.

### 1.2 Services

| Service | Port | Description |
|---------|------|-------------|
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway acting as the single entry point. Enforces OAuth2/JWT authentication via Keycloak and forwards the authenticated principal as an `X-Auth-Id` header to downstream services. |
| **internet-banking-service-registry** | 8081 | Netflix Eureka Server providing service discovery. All business services register here and resolve other services by name. |
| **internet-banking-config-server** | 8090 | Spring Cloud Config Server backed by a remote Git repository (`internet-banking-microservices-configurations`). Serves externalized configuration to all services at startup via the bootstrap phase. |
| **core-banking-service** | 8092 | The authoritative banking ledger. Manages users, bank accounts, utility accounts, and processes transactions (fund transfers and utility payments) at the database level. Owns the Flyway-managed schema. |
| **internet-banking-user-service** | 8083 | Manages internet banking user lifecycle (registration, approval, update). Integrates with Keycloak for identity management and calls core-banking-service to validate user identity. |
| **internet-banking-fund-transfer-service** | 8084 | Orchestrates fund transfers. Persists a local transfer record, delegates the actual ledger operation to core-banking-service via Feign. |
| **internet-banking-utility-payment-service** | 8085 | Orchestrates utility bill payments. Persists a local payment record, delegates the actual ledger operation to core-banking-service via Feign. |

### 1.3 Communication Patterns

```
                                 +---------------------+
                                 |  Keycloak (IAM)     |
                                 |  :8080              |
                                 +----------+----------+
                                            |
                                            | JWT validation / user mgmt
                                            |
+----------+      +---------------------+  |
|  Client  | ---> | API Gateway (:8082) |--+
+----------+      +----+----+----+------+
                       |    |    |
          +------------+    |    +------------+
          |                 |                 |
          v                 v                 v
  +-------+------+  +------+-------+  +------+--------+
  | User Service |  | Fund Transfer|  | Utility       |
  | :8083        |  | Service:8084 |  | Payment :8085 |
  +------+-------+  +------+-------+  +------+--------+
         |                 |                  |
         |   OpenFeign     |   OpenFeign      |   OpenFeign
         +--------+--------+--------+--------+
                  |
                  v
         +--------+---------+
         | Core Banking     |
         | Service :8092    |
         +--------+---------+
                  |
                  v
         +--------+---------+
         | MySQL Database   |
         | :3306            |
         +------------------+
```

**Synchronous REST via OpenFeign:**
- `internet-banking-user-service` -> `core-banking-service`: Validates user identification during registration (`GET /api/v1/user/{identification}`)
- `internet-banking-fund-transfer-service` -> `core-banking-service`: Reads accounts (`GET /api/v1/account/bank-account/{account_number}`) and executes fund transfers (`POST /api/v1/transaction/fund-transfer`)
- `internet-banking-utility-payment-service` -> `core-banking-service`: Reads accounts (`GET /api/v1/account/bank-account/{account_number}`) and executes utility payments (`POST /api/v1/transaction/util-payment`)

**Service Discovery:**
- All business services register with Eureka and resolve other services by their `spring.application.name` (e.g., `core-banking-service`).

**Authentication Flow:**
- API Gateway validates JWT tokens issued by Keycloak and extracts the principal name into the `X-Auth-Id` header.
- Downstream services read this header via `AppAuthUserFilter` and store it in a thread-local `ApiRequestContextHolder`.

### 1.4 Infrastructure Components

| Component | Image/Technology | Static IP | Purpose |
|-----------|-----------------|-----------|---------|
| **Keycloak** | `quay.io/keycloak/keycloak:23.0.7` | 172.25.0.11 | OAuth2 / OpenID Connect identity provider. Realm imported on startup. |
| **PostgreSQL** | `postgres:15` | 172.25.0.10 | Keycloak's backing database. Port closed externally. |
| **MySQL** | Custom image (with init SQL) | 172.25.0.9 | Application database for all 4 business services (separate schemas). |
| **Zipkin** | `openzipkin/zipkin:3` | 172.25.0.12 | Distributed tracing collector and UI. |
| **RabbitMQ** | Referenced in README | N/A | Mentioned for notification service (not yet implemented). |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL: `banking_core_service`)

The core banking service owns the authoritative ledger data. Schema is managed by **Flyway** migrations.

#### `banking_core_user`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto) | Surrogate key |
| `first_name` | VARCHAR(255) | User's first name |
| `last_name` | VARCHAR(255) | User's last name |
| `email` | VARCHAR(255) | User's email |
| `identification_number` | VARCHAR(255) | National ID (NIC) - the cross-service linking identifier |

#### `banking_core_account`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto) | Surrogate key |
| `number` | VARCHAR(255) | Account number (12-digit string) |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `ACTIVE` |
| `actual_balance` | DECIMAL(19,2) | Actual ledger balance |
| `available_balance` | DECIMAL(19,2) | Available balance for transactions |
| `user_id` | BIGINT (FK) | References `banking_core_user.id` |

**Relationships:** Many accounts per user (`@ManyToOne` from account to user, `@OneToMany` from user to accounts).

#### `banking_core_utility_account`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto) | Surrogate key |
| `number` | VARCHAR(255) | Utility account number |
| `provider_name` | VARCHAR(255) | Provider name (e.g., VODAFONE, VERIZON, SINGTEL) |

#### `banking_core_transaction`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto) | Surrogate key |
| `amount` | DECIMAL(19,2) | Signed transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Reference (destination account number or utility ref) |
| `transaction_id` | VARCHAR(50) | UUID grouping related debit/credit entries |
| `account_id` | BIGINT (FK) | References `banking_core_account.id` |

**Relationships:** `@OneToOne(cascade=ALL)` from transaction to account (note: this mapping is semantically incorrect - should be `@ManyToOne`).

#### Entity-Relationship Diagram

```
banking_core_user  1---*  banking_core_account  1---1  banking_core_transaction
                                                       (should be 1---*)
banking_core_utility_account (standalone)
```

### 2.2 Internet Banking User Service (MySQL: `banking_core_user_service`)

#### `user`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto) | Surrogate key |
| `auth_id` | VARCHAR | Keycloak user ID |
| `identification` | VARCHAR | NIC linking to core banking |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED` |
| `created_date` | TIMESTAMP | Audit field (from AuditAware) |
| `created_by` | VARCHAR | Audit field |
| `modified_date` | TIMESTAMP | Audit field |
| `modified_by` | VARCHAR | Audit field |
| `version` | BIGINT | Optimistic locking version |

### 2.3 Internet Banking Fund Transfer Service (MySQL: `banking_core_fund_transfer_service`)

#### `fund_transfer`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto) | Surrogate key |
| `transaction_reference` | VARCHAR | UUID from core banking |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `status` | VARCHAR | Enum: `PENDING`, `SUCCESS` |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | Various | Audit fields from AuditAware |

### 2.4 Internet Banking Utility Payment Service (MySQL: `banking_core_utility_payment_service`)

#### `utility_payment`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto) | Surrogate key |
| `provider_id` | BIGINT | References utility account provider |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Customer reference number |
| `account` | VARCHAR | Source bank account number |
| `transaction_id` | VARCHAR | UUID from core banking |
| `status` | VARCHAR | Enum: `PROCESSING`, `SUCCESS` |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | Various | Audit fields from AuditAware |

---

## 3. API Surface Map

### 3.1 API Gateway Routes

All requests enter through the API Gateway at `:8082`. The gateway strips the service prefix and routes to the appropriate downstream service via Eureka.

| Gateway Path Prefix | Target Service |
|---------------------|---------------|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/banking-core/**` | core-banking-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |

**Public (unauthenticated) endpoints:**
- `POST /user/api/v1/bank-users/register`
- `GET /actuator/**` (all services)

All other endpoints require a valid JWT Bearer token.

### 3.2 Core Banking Service Endpoints

Base path: `/api/v1`

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|-------------|----------|
| `GET` | `/account/bank-account/{account_number}` | Get bank account by number | - | `BankAccount { id, number, type, status, availableBalance, actualBalance, user }` |
| `GET` | `/account/util-account/{account_name}` | Get utility account by provider name | - | `UtilityAccount { id, number, providerName }` |
| `GET` | `/user/{identification}` | Get user by NIC | - | `User { id, firstName, lastName, email, identificationNumber, accounts[] }` |
| `GET` | `/user` | List users (paginated) | Pageable params | `List<User>` |
| `POST` | `/transaction/fund-transfer` | Execute internal fund transfer | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `POST` | `/transaction/util-payment` | Execute utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |

### 3.3 Internet Banking User Service Endpoints

Base path: `/api/v1/bank-users`

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|-------------|----------|
| `POST` | `/register` | Register new internet banking user | `{ email, identification, password }` | `User { id, email, identification, authId, status }` |
| `PATCH` | `/update/{id}` | Update user (e.g., approve) | `{ status }` | `User` |
| `GET` | `/` | List users (paginated) | Pageable params | `List<User>` |
| `GET` | `/{id}` | Get user by ID | - | `User` |

### 3.4 Internet Banking Fund Transfer Service Endpoints

Base path: `/api/v1/transfer`

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|-------------|----------|
| `POST` | `/` | Initiate fund transfer | `{ fromAccount, toAccount, amount, authID }` | `{ message, transactionId }` |
| `GET` | `/` | List fund transfers (paginated) | Pageable params | `List<FundTransfer>` |

### 3.5 Internet Banking Utility Payment Service Endpoints

Base path: `/api/v1/utility-payment`

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|-------------|----------|
| `POST` | `/` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/` | List utility payments (paginated) | Pageable params | `List<UtilityPayment>` |

### 3.6 Infrastructure Endpoints

| Service | Endpoint | Description |
|---------|----------|-------------|
| All services | `GET /actuator/health` | Spring Boot health check |
| All services | `GET /actuator/info` | Application info (git properties) |
| Service Registry | `GET /` (web UI on :8081) | Eureka dashboard |
| Config Server | `GET /{application}/{profile}` | Configuration retrieval |
| Core Banking | `GET /swagger-ui.html` | OpenAPI documentation (springdoc) |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

**Location:** `internet-banking-user-service` > `UserService.createUser()`

1. Check if email is already registered in Keycloak. If yes, throw `UserAlreadyRegisteredException`.
2. Validate user exists in core banking by NIC (`BankingCoreRestClient.readUser(identification)`).
3. Verify the email matches between the registration request and the core banking record. If mismatch, throw `InvalidEmailException`.
4. Create user in Keycloak with `emailVerified=false`, `enabled=false`, and the provided password.
5. If Keycloak returns HTTP 201, persist the user locally with `status=PENDING`.
6. If the user is not found in core banking, throw `InvalidBankingUserException`.

**Key rule:** A user must exist in core banking (by NIC) before they can register for internet banking.

### 4.2 User Approval Flow

**Location:** `internet-banking-user-service` > `UserService.updateUser()`

1. When status is changed to `APPROVED`, the corresponding Keycloak user is enabled (`enabled=true`, `emailVerified=true`).
2. The local user entity's status is updated to `APPROVED`.

### 4.3 Fund Transfer Rules

**Location:** `core-banking-service` > `TransactionService.fundTransfer()` and `internalFundTransfer()`

1. Look up both source and destination accounts by account number.
2. **Balance validation:** Source account's `actualBalance` must be >= 0 AND >= transfer amount. If not, throw `InsufficientFundsException`.
3. **Debit source:** Subtract amount from `actualBalance`; set `availableBalance = actualBalance - amount` (note: this double-subtracts, which is a bug).
4. Record a debit transaction entry (negative amount) linked to the source account.
5. **Credit destination:** Add amount to `actualBalance`; set `availableBalance = actualBalance + amount` (note: this double-adds, which is a bug).
6. Record a credit transaction entry (positive amount) linked to the destination account.
7. Both entries share the same UUID `transactionId`.
8. The entire operation runs within a single `@Transactional` boundary.

**Orchestration layer** (`internet-banking-fund-transfer-service` > `FundTransferService`):
1. Save a local `FundTransferEntity` with status `PENDING`.
2. Call core banking's fund transfer endpoint via Feign.
3. Update local entity with the transaction reference and status `SUCCESS`.

### 4.4 Utility Payment Processing

**Location:** `core-banking-service` > `TransactionService.utilPayment()`

1. Look up the source bank account.
2. **Balance validation:** Same as fund transfer.
3. Look up the utility account by provider ID.
4. **Debit source:** Subtract amount from `actualBalance`; set `availableBalance = actualBalance - amount` (same double-subtract bug).
5. Record a transaction entry with type `UTILITY_PAYMENT`.
6. Comment indicates "we can call third party API to process UTIL payment" - this integration is not implemented.

**Orchestration layer** (`internet-banking-utility-payment-service` > `UtilityPaymentService`):
1. Save a local `UtilityPaymentEntity` with status `PROCESSING`.
2. Call core banking's utility payment endpoint via Feign.
3. Update local entity with transaction ID and status `SUCCESS`.

### 4.5 Authentication & Authorization Context

**Location:** API Gateway > `GatewayConfiguration` and downstream `AppAuthUserFilter`

1. The API Gateway extracts the JWT principal name and adds it as `X-Auth-Id` header.
2. If no principal is found, it defaults to `"SYSTEM USER"`.
3. Downstream services extract this header via `AppAuthUserFilter` into a thread-local `ApiRequestContext`.
4. The auth context is used by `AuditorAwareConfig` to populate `createdBy`/`modifiedBy` audit fields.

---

## 5. Integration Points

### 5.1 Keycloak

| Aspect | Details |
|--------|---------|
| **Version** | 23.0.7 |
| **Admin credentials** | `admin` / `password` (docker-compose) |
| **Realm** | Imported from `docker-compose/keycloak/` on startup |
| **Integration** | `keycloak-admin-client:24.0.4` in user-service |
| **Auth type** | `client_credentials` grant (service account) |
| **Configuration** | `app.config.keycloak.server-url`, `realm`, `clientId`, `client-secret` (from Config Server) |
| **Usage** | User creation, user search by email, user enable/disable, email verification |

### 5.2 RabbitMQ

| Aspect | Details |
|--------|---------|
| **Status** | Referenced in README but **not implemented** in code |
| **Planned use** | Push notification messages from fund transfer and utility payment services to a notification service |
| **Dependencies** | No RabbitMQ dependencies in any `build.gradle` |

### 5.3 Zipkin (Distributed Tracing)

| Aspect | Details |
|--------|---------|
| **Version** | Zipkin 3 (via Docker) |
| **Port** | 9411 |
| **Integration** | Micrometer Tracing Bridge Brave + Zipkin Reporter in all services |
| **Dependencies** | `micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`, `feign-micrometer` |
| **Coverage** | All 4 business services + API Gateway include tracing dependencies |

### 5.4 Database Connections

| Service | Database | Schema |
|---------|----------|--------|
| core-banking-service | MySQL (172.25.0.9:3306) | `banking_core_service` |
| internet-banking-user-service | MySQL (172.25.0.9:3306) | `banking_core_user_service` |
| internet-banking-fund-transfer-service | MySQL (172.25.0.9:3306) | `banking_core_fund_transfer_service` |
| internet-banking-utility-payment-service | MySQL (172.25.0.9:3306) | `banking_core_utility_payment_service` |
| Keycloak | PostgreSQL (172.25.0.10:5432) | `keycloak` |

**MySQL credentials:** User `javatodev_development` with password `oPItyPticIAt` (created via `privileges.sql`).

### 5.5 Spring Cloud Config Server

| Aspect | Details |
|--------|---------|
| **Git repository** | `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` |
| **Branch** | `main` |
| **Search path** | `configuration/` |
| **Bootstrap** | All services use `bootstrap.yml` to point to Config Server at `http://localhost:8090` (dev) or `http://internet-banking-config-server:8090` (Docker) |

### 5.6 Service Discovery (Eureka)

| Aspect | Details |
|--------|---------|
| **Server** | internet-banking-service-registry on port 8081 |
| **Clients** | All 4 business services + API Gateway register as Eureka clients |
| **Usage** | Feign clients resolve service names (e.g., `core-banking-service`) via Eureka |

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build tool:** Gradle (each service has its own `build.gradle` and Gradle wrapper)
- **Java version:** 21 (sourceCompatibility)
- **Spring Boot:** 3.2.4 via `org.springframework.boot` plugin
- **Spring Cloud:** 2023.0.0
- **Git properties:** `com.gorylenko.gradle-git-properties:2.4.2` plugin generates `git.properties` for `/actuator/info`

### 6.2 Docker Deployment

Each service has a `Dockerfile` and a `wait-for-it.sh` script for startup ordering.

**Startup order (enforced by `wait-for-it.sh` in docker-compose):**

1. Infrastructure: MySQL, PostgreSQL, Keycloak, Zipkin (no dependencies)
2. Config Server (no wait dependencies)
3. Service Registry (no wait dependencies)
4. API Gateway: waits for Service Registry + Config Server
5. Business services: wait for Service Registry + Config Server + MySQL

**Docker Compose files:**
- `docker-compose.yml`: Full stack (all services + infrastructure)
- `docker-compose-support-apps.yml`: Infrastructure only (for local development of individual services)

**Network:** Custom bridge network `javatodev_ib_network` with subnet `172.25.0.0/16`. Each container has a static IP.

### 6.3 Profile Management

| Profile | Config Server URI | Usage |
|---------|------------------|-------|
| `default` | `http://localhost:8090` | Local development |
| `docker` | `http://internet-banking-config-server:8090` | Docker Compose deployment |
| `dev` | `http://localhost:8090` | Development environment |

### 6.4 Testing Infrastructure

- **Test framework:** JUnit 5 (via `spring-boot-starter-test`)
- **Test database:** H2 in-memory (for core-banking-service tests)
- **Flyway:** Disabled in test profile
- **Existing tests:** Only core-banking-service has unit tests (`AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`). Other services have only empty application context tests.

### 6.5 CI/CD

- No CI/CD pipeline configuration files exist in the repository (GitHub Actions workflows were removed).
- No Kubernetes manifests are present despite Kubernetes being listed in the technology stack.
