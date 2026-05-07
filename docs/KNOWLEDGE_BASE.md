# Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Summary

This is a **Java 21 / Spring Boot 3.2.4 / Spring Cloud 2023.0.0** internet banking application composed of **6 microservices** that together model account management, fund transfers, utility payments, and user registration with Keycloak-based authentication.

### 1.2 Service Inventory

| # | Service | Port | Role |
|---|---------|------|------|
| 1 | **core-banking-service** | 8092 | Core banking engine: accounts, users, transactions, balance management |
| 2 | **internet-banking-user-service** | 8083 | User registration/management, Keycloak integration |
| 3 | **internet-banking-fund-transfer-service** | 8084 | Orchestrates fund transfers between accounts via core-banking-service |
| 4 | **internet-banking-utility-payment-service** | 8085 | Orchestrates utility bill payments via core-banking-service |
| 5 | **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway, OAuth2/JWT security, request routing |
| 6 | **internet-banking-config-server** | 8090 | Spring Cloud Config Server (Git-backed centralized configuration) |

**Infrastructure services** (not counted as microservices above but deployed alongside):

| Component | Port | Purpose |
|-----------|------|---------|
| **internet-banking-service-registry** | 8081 | Netflix Eureka service discovery server |
| **MySQL 8.4** | 3306 | Shared relational database (4 schemas) |
| **Keycloak 23.0.7** | 8080 | Identity & access management (OAuth2 / OIDC) |
| **PostgreSQL 15** | 5432 (internal) | Keycloak's backing database |
| **Zipkin 3** | 9411 | Distributed tracing collector & UI |

### 1.3 Communication Patterns

```
                           +-----------+
                           |  Keycloak |
                           | (AuthN)   |
                           +-----+-----+
                                 |
                                 | JWT validation
                                 v
  Client ---> [API Gateway :8082] ---> Routes via Eureka
                 |        |        |        |
                 v        v        v        v
           user-svc  fund-xfer  util-pay  core-banking
           :8083     :8084      :8085     :8092
                 \        |        /
                  +-------+-------+
                  | OpenFeign calls|
                  +-------+-------+
                          |
                          v
                   [core-banking-service]
                          |
                          v
                     [MySQL 8.4]
```

- **Synchronous (HTTP/REST via OpenFeign):** All inter-service calls use Spring Cloud OpenFeign with Eureka service discovery. The fund-transfer and utility-payment services call core-banking-service to execute transactions. The user-service calls core-banking-service to validate user identification.
- **Service Discovery:** Netflix Eureka. All business services register as Eureka clients; the API Gateway resolves service names at routing time.
- **Configuration:** Spring Cloud Config Server fetches configuration from [a Git repository](https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git). Services bootstrap by contacting the config server on startup.
- **Authentication Flow:** The API Gateway acts as an OAuth2 Resource Server validating JWTs issued by Keycloak. Downstream services receive a custom `X-Auth-Id` header injected by a `GlobalFilter` in the gateway.
- **Distributed Tracing:** Micrometer Tracing with Brave bridge exports spans to Zipkin.
- **Asynchronous (RabbitMQ):** Documented in architecture diagrams but **not yet implemented** in the codebase. A Notification Service is listed as "PENDING Development."

### 1.4 Request Flow Example (Fund Transfer)

1. Client sends `POST /fund-transfer/api/v1/transfer` with JWT Bearer token.
2. API Gateway validates JWT via Keycloak JWK endpoint, injects `X-Auth-Id` header.
3. Gateway routes to `internet-banking-fund-transfer-service` via Eureka.
4. Fund-transfer service saves a `PENDING` record, then calls `core-banking-service` via Feign (`POST /api/v1/transaction/fund-transfer`).
5. Core-banking validates balances, debits source, credits destination, records transaction entities, returns `transactionId`.
6. Fund-transfer service updates local record to `SUCCESS` and returns response.

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (`banking_core_service` schema)

#### Entities

**`banking_core_user`**

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `first_name` | VARCHAR(255) | User's first name |
| `last_name` | VARCHAR(255) | User's last name |
| `email` | VARCHAR(255) | Email address |
| `identification_number` | VARCHAR(255) | National ID / NIC |

**`banking_core_account`**

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `number` | VARCHAR(255) | Account number |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `available_balance` | DECIMAL(19,2) | Balance available for transactions |
| `actual_balance` | DECIMAL(19,2) | Ledger balance |
| `user_id` | BIGINT (FK -> `banking_core_user.id`) | Account owner |

**`banking_core_utility_account`**

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `number` | VARCHAR(255) | Utility provider account number |
| `provider_name` | VARCHAR(255) | Provider name (e.g., VODAFONE, AIRTEL) |

**`banking_core_transaction`**

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `amount` | DECIMAL(19,2) | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Destination account number or reference |
| `transaction_id` | VARCHAR(50) | UUID grouping debit/credit legs |
| `account_id` | BIGINT (FK -> `banking_core_account.id`) | Associated account |

#### Relationships

```
banking_core_user (1) ---< (N) banking_core_account
banking_core_account (1) ---< (N) banking_core_transaction
banking_core_utility_account (standalone, no FK relationships)
```

### 2.2 User Service (`banking_core_user_service` schema)

**`user`** (entity: `UserEntity extends AuditAware`)

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `auth_id` | VARCHAR | Keycloak user ID |
| `identification` | VARCHAR | NIC / identification number |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | INSTANT | Audit: creation timestamp |
| `created_by` | VARCHAR | Audit: creator |
| `modified_date` | INSTANT | Audit: last modification timestamp |
| `modified_by` | VARCHAR | Audit: last modifier |
| `version` | BIGINT | Optimistic locking version |

### 2.3 Fund Transfer Service (`banking_core_fund_transfer_service` schema)

**`fund_transfer`** (entity: `FundTransferEntity extends AuditAware`)

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `transaction_reference` | VARCHAR | Transaction ID from core-banking |
| `status` | VARCHAR | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| Audit fields | (inherited) | `created_date`, `created_by`, `modified_date`, `modified_by`, `version` |

### 2.4 Utility Payment Service (`banking_core_utility_payment_service` schema)

**`utility_payment`** (entity: `UtilityPaymentEntity extends AuditAware`)

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Bill/reference number |
| `account` | VARCHAR | Payer's bank account number |
| `transaction_id` | VARCHAR | Transaction ID from core-banking |
| `status` | VARCHAR | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| Audit fields | (inherited) | `created_date`, `created_by`, `modified_date`, `modified_by`, `version` |

---

## 3. API Surface Map

All endpoints are routed through the API Gateway (`:8082`) with path prefixes configured in Spring Cloud Gateway. Direct service ports are shown for reference.

### 3.1 Core Banking Service (`:8092`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | - | `BankAccount` (id, number, type, status, availableBalance, actualBalance, user) |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | - | `UtilityAccount` (id, number, providerName) |
| `GET` | `/api/v1/user/{identification}` | Get user by identification number | - | `User` (id, firstName, lastName, email, identificationNumber, bankAccounts[]) |
| `GET` | `/api/v1/user` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |
| `POST` | `/api/v1/transaction/fund-transfer` | Execute fund transfer | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `POST` | `/api/v1/transaction/util-payment` | Execute utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |

### 3.2 User Service (`:8083`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| `POST` | `/api/v1/bank-users/register` | Register new user (public, no auth) | `{ email, identification, password }` | `User` (id, email, identification, authId, status) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user (e.g., approve) | `{ status }` | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | - | `User` |

### 3.3 Fund Transfer Service (`:8084`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `{ fromAccount, toAccount, amount, authID }` | `{ message, transactionId }` |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | Query: `page`, `size`, `sort` | `List<FundTransfer>` |

### 3.4 Utility Payment Service (`:8085`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | Query: `page`, `size`, `sort` | `List<UtilityPayment>` |

### 3.5 API Gateway Routes

| Path Prefix | Target Service | Auth Required |
|-------------|---------------|---------------|
| `/user/**` | internet-banking-user-service | Yes (except `/user/api/v1/bank-users/register`) |
| `/fund-transfer/**` | internet-banking-fund-transfer-service | Yes |
| `/utility-payment/**` | internet-banking-utility-payment-service | Yes |
| `/banking-core/**` | core-banking-service | Yes |
| `/actuator/**` | (all services) | No |

### 3.6 OpenAPI / Swagger

All four business services include `springdoc-openapi-starter-webflux-ui:2.1.0` in their dependencies.

---

## 4. Key Business Logic Inventory

### 4.1 Fund Transfer Rules

**Location:** `core-banking-service` > `TransactionService.fundTransfer()`

1. **Account validation:** Both source (`fromAccount`) and destination (`toAccount`) must exist in the banking core.
2. **Balance check:** Source account's `actualBalance` must be >= 0 AND >= transfer `amount`. Violation throws `InsufficientFundsException`.
3. **Debit/Credit execution** (`internalFundTransfer`):
   - Debit source: `actualBalance -= amount`, `availableBalance = actualBalance - amount` (note: this double-subtracts, see Gap Analysis).
   - Credit destination: `actualBalance += amount`, `availableBalance = actualBalance + amount` (note: this double-adds).
   - Two `TransactionEntity` records created (one debit, one credit) sharing the same `transactionId` (UUID).
4. **Orchestration layer** (`fund-transfer-service`): Saves a `PENDING` record locally, calls core-banking, updates to `SUCCESS` on return. No failure/rollback handling.

### 4.2 Utility Payment Processing

**Location:** `core-banking-service` > `TransactionService.utilPayment()`

1. Source account must exist and have sufficient balance.
2. Utility provider is looked up by `providerId`.
3. Source account balance is debited (same double-subtraction issue as fund transfers).
4. A single `TransactionEntity` is recorded.
5. No actual third-party payment provider integration (placeholder comment in code).

**Orchestration layer** (`utility-payment-service`): Similar to fund-transfer; saves `PROCESSING` record, calls core-banking, updates to `SUCCESS`.

### 4.3 User Registration & Management

**Location:** `internet-banking-user-service` > `UserService.createUser()`

1. Check Keycloak for existing user by email - reject if already registered (`UserAlreadyRegisteredException`).
2. Validate user exists in core-banking by identification number via Feign call.
3. Validate email matches the core-banking record (`InvalidEmailException`).
4. Create user in Keycloak (disabled, email unverified) with provided password.
5. On Keycloak success (HTTP 201), retrieve Keycloak user ID, save local `UserEntity` with `PENDING` status.
6. If identification not found in core-banking, throw `InvalidBankingUserException`.

**User Approval** (`UserService.updateUser()`): When status changes to `APPROVED`, enables the Keycloak user and marks email as verified.

### 4.4 Authentication & Authorization

1. **Keycloak** issues JWT tokens using `client_credentials` grant type for service-to-Keycloak communication.
2. **API Gateway** validates JWTs via `spring-boot-starter-oauth2-resource-server` using the Keycloak JWK endpoint.
3. **User identity propagation:** Gateway `GlobalFilter` extracts `Principal.getName()` and forwards it as `X-Auth-Id` header.
4. **Downstream filters:** Each business service has an `AppAuthUserFilter` that reads `X-Auth-Id` and stores it in `ApiRequestContextHolder` (thread-local). This is used by `AuditorAwareConfig` for audit fields.

---

## 5. Integration Points

### 5.1 Keycloak

- **Version:** 23.0.7
- **Purpose:** Identity provider, user management, JWT issuance
- **Integration:** User-service uses `keycloak-admin-client:24.0.4` to create/update/search users. Gateway validates JWTs.
- **Configuration:** Realm is imported on startup from `docker-compose/keycloak/realm-export.json`. Keycloak uses PostgreSQL 15 as its backing store.
- **Properties:** `app.config.keycloak.server-url`, `app.config.keycloak.realm`, `app.config.keycloak.clientId`, `app.config.keycloak.client-secret` (externalized via Config Server).

### 5.2 RabbitMQ

- **Status:** Referenced in architecture diagrams but **not implemented** in the codebase.
- **Planned use:** Notification service (pending development) would consume messages from a RabbitMQ queue after fund transfers and utility payments.

### 5.3 Zipkin

- **Version:** 3
- **Purpose:** Distributed tracing
- **Integration:** All services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave`. Feign calls include `feign-micrometer` for trace propagation.
- **Dashboard:** Port 9411

### 5.4 Database Connections

- **MySQL 8.4:** Single instance, 4 schemas:
  - `banking_core_service` (core-banking)
  - `banking_core_user_service` (user-service)
  - `banking_core_fund_transfer_service` (fund-transfer)
  - `banking_core_utility_payment_service` (utility-payment)
- **Credentials:** User `javatodev_development` with password `oPItyPticIAt`, root password `woVERANKliGharym` (hardcoded in docker-compose and MySQL Dockerfile).
- **Connection:** Via Spring Data JPA, externalized via Config Server.
- **Migrations:** Flyway (core-banking-service only, 3 migration files).

### 5.5 Eureka Service Registry

- All business services register as Eureka clients.
- API Gateway uses Eureka for dynamic routing.
- Config: each service has `spring-cloud-starter-netflix-eureka-client` dependency.

### 5.6 Spring Cloud Config Server

- **Config source:** Git repository at `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` (branch: `main`, path: `configuration/`).
- **Bootstrap:** Each service has `bootstrap.yml` (local) and `bootstrap-docker.yml` (Docker) pointing to the config server.

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Gradle 8.6** with `spring-boot` and `spring-dependency-management` plugins.
- Each service is an independent Gradle project (no multi-project build; each has its own `build.gradle`, `settings.gradle`, `gradlew`).
- **Git Properties plugin** (`com.gorylenko.gradle-git-properties:2.4.2`) generates `git.properties` at build time.
- Tests use JUnit 5 Platform (`useJUnitPlatform()`).

### 6.2 Docker

Each service has a `Dockerfile`:
- **Base image:** `eclipse-temurin:21.0.2_13-jre-alpine`
- **Pattern:** Copy fat JAR from `build/libs/`, expose port, set entrypoint with `-Dspring.profiles.active=docker`.
- **Startup ordering:** `wait-for-it.sh` ensures services wait for the config server, service registry, and MySQL before starting.

### 6.3 Docker Compose

Two compose files in `docker-compose/`:

1. **`docker-compose.yml`:** Full stack (all 6 services + MySQL + Keycloak + PostgreSQL + Zipkin) on a custom bridge network (`172.25.0.0/16`) with static IPs.
2. **`docker-compose-support-apps.yml`:** Infrastructure only (Zipkin, Keycloak, PostgreSQL, MySQL, Config Server, Service Registry).

### 6.4 CI/CD

- **GitHub Actions:** No workflow files present (`.github/` only contains `FUNDING.yml`).
- **No CI pipeline** is configured in the repository.

### 6.5 Test Data

Flyway migrations in core-banking-service seed:
- 4 users (Sam, Guru, Ragu, Randor)
- 14 savings accounts with various balances
- 6 utility providers (VODAFONE, VERIZON, SINGTEL, HUTCH, AIRTEL, GIO)

Keycloak realm is imported with pre-configured realm, client, and user data matching the test credentials: `ib_admin@javatodev.com / 5V7huE3G86uB`.
