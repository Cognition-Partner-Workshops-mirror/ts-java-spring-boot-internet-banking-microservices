# Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Summary

This is a **Java 21 / Spring Boot 3.2.4 / Spring Cloud 2023.0.0** internet banking platform composed of **6 microservices** that together provide user management, fund transfers, utility payments, and core banking operations.

### 1.2 Service Inventory

| Service | Port | Role | Database |
|---|---|---|---|
| **core-banking-service** | 8092 | Core banking ledger — accounts, users, transactions | MySQL (`banking_core_service`) |
| **internet-banking-user-service** | 8083 | User registration & management, Keycloak integration | MySQL (`banking_core_user_service`) |
| **internet-banking-fund-transfer-service** | 8084 | Fund transfer orchestration | MySQL (`banking_core_fund_transfer_service`) |
| **internet-banking-utility-payment-service** | 8085 | Utility bill payment orchestration | MySQL (`banking_core_utility_payment_service`) |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway — routing, OAuth2 enforcement | None |
| **internet-banking-service-registry** | 8081 | Netflix Eureka server — service discovery | None |
| **internet-banking-config-server** | 8090 | Spring Cloud Config — centralized configuration from Git | None |

> **Note:** A **Notification Service** is referenced in the README as "PENDING Development" and is not implemented.

### 1.3 Communication Patterns

```
                        ┌─────────────────────────┐
                        │   Keycloak (OAuth2/OIDC) │
                        │   Port 8080              │
                        └────────┬────────────────-┘
                                 │ JWT validation
                                 ▼
┌──────────┐  HTTP   ┌──────────────────────┐
│  Client   │ ──────>│  API Gateway (8082)   │
└──────────┘         │  Spring Cloud Gateway │
                     └──┬────┬────┬─────────┘
                        │    │    │  Route by path prefix
           ┌────────────┘    │    └────────────────┐
           ▼                 ▼                     ▼
  ┌────────────────┐ ┌────────────────┐  ┌──────────────────┐
  │ User Service   │ │ Fund Transfer  │  │ Utility Payment  │
  │ (8083)         │ │ Service (8084) │  │ Service (8085)   │
  └───────┬────────┘ └───────┬────────┘  └────────┬─────────┘
          │                  │                     │
          │  OpenFeign       │  OpenFeign          │  OpenFeign
          ▼                  ▼                     ▼
     ┌─────────────────────────────────────────────────┐
     │            Core Banking Service (8092)           │
     │   Accounts · Users · Transactions · Ledger      │
     └─────────────────────┬───────────────────────────┘
                           │
                           ▼
                    ┌─────────────┐
                    │  MySQL 8.4  │
                    │  Port 3306  │
                    └─────────────┘
```

**Synchronous:** All inter-service communication uses **OpenFeign** (HTTP/REST) via Eureka service discovery. Services resolve each other by Eureka application name (`core-banking-service`).

**Asynchronous:** RabbitMQ is listed in the technology stack but is **not implemented** in the current codebase (intended for the pending Notification Service).

### 1.4 Infrastructure Components

| Component | Image/Version | Purpose |
|---|---|---|
| **MySQL** | `mysql:8.4.0` | Primary data store for all business services |
| **Keycloak** | `quay.io/keycloak/keycloak:23.0.7` | Identity provider (OAuth2/OIDC), backed by PostgreSQL |
| **PostgreSQL** | `postgres:15` | Keycloak metadata store |
| **Zipkin** | `openzipkin/zipkin:3` | Distributed tracing collector |
| **Eureka** | Embedded (Spring Cloud) | Service discovery registry |
| **Spring Cloud Config** | Embedded | Centralized configuration from Git repo |

### 1.5 Gateway Routing

The API Gateway routes requests by path prefix to downstream services (resolved via Eureka):

| Path Prefix | Target Service |
|---|---|
| `/user/**` | `internet-banking-user-service` |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` |
| `/utility-payment/**` | `internet-banking-utility-payment-service` |
| `/core/**` | `core-banking-service` |

The Gateway also injects an `X-Auth-Id` header (from the JWT principal) into proxied requests for downstream audit tracking.

### 1.6 Security Model

- The API Gateway enforces **OAuth2 Resource Server** (JWT) authentication via Keycloak.
- `/user/api/v1/bank-users/register` and all `/actuator/**` paths are **publicly accessible**.
- All other endpoints require a valid JWT bearer token.
- Downstream services extract the `X-Auth-Id` header for audit logging (created-by / modified-by) via a custom servlet filter (`AppAuthUserFilter`).
- Keycloak realm: `javatodev-internet-banking`, client: `internet-banking-api-client`.

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL: `banking_core_service`)

#### `banking_core_user`
| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` PK AUTO_INCREMENT | |
| `first_name` | `VARCHAR(255)` | |
| `last_name` | `VARCHAR(255)` | |
| `email` | `VARCHAR(255)` | |
| `identification_number` | `VARCHAR(255)` | National ID / passport |

#### `banking_core_account`
| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` PK AUTO_INCREMENT | |
| `number` | `VARCHAR(255)` | Account number (e.g. `100015003000`) |
| `type` | `VARCHAR(255)` | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | `VARCHAR(255)` | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `actual_balance` | `DECIMAL(19,2)` | |
| `available_balance` | `DECIMAL(19,2)` | |
| `user_id` | `BIGINT` FK → `banking_core_user.id` | |

#### `banking_core_utility_account`
| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` PK AUTO_INCREMENT | |
| `number` | `VARCHAR(255)` | Provider account number |
| `provider_name` | `VARCHAR(255)` | e.g. `VODAFONE`, `VERIZON`, `HUTCH` |

#### `banking_core_transaction`
| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` PK AUTO_INCREMENT | |
| `amount` | `DECIMAL(19,2)` | Signed (negative for debits) |
| `transaction_type` | `VARCHAR(30)` | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | `VARCHAR(50)` | Destination account or reference |
| `transaction_id` | `VARCHAR(50)` | UUID correlation ID |
| `account_id` | `BIGINT` FK → `banking_core_account.id` | |

**Relationships:**
- `banking_core_user` 1 ↔ N `banking_core_account`
- `banking_core_account` 1 ↔ N `banking_core_transaction`

### 2.2 User Service (MySQL: `banking_core_user_service`)

#### `user`
| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` PK AUTO_INCREMENT | |
| `auth_id` | `VARCHAR(255)` | Keycloak user UUID |
| `identification` | `VARCHAR(255)` | National ID (links to core banking user) |
| `status` | `VARCHAR(255)` | Enum: `PENDING`, `APPROVED`, `REJECTED` |
| `created_date` | `INSTANT` | JPA Auditing |
| `created_by` | `VARCHAR(255)` | JPA Auditing |
| `modified_date` | `INSTANT` | JPA Auditing |
| `modified_by` | `VARCHAR(255)` | JPA Auditing |
| `version` | `BIGINT` | Optimistic locking |

### 2.3 Fund Transfer Service (MySQL: `banking_core_fund_transfer_service`)

#### `fund_transfer` (derived from entity, table name via JPA default)
| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` PK AUTO_INCREMENT | |
| `from_account` | `VARCHAR(255)` | Source account number |
| `to_account` | `VARCHAR(255)` | Destination account number |
| `amount` | `DECIMAL` | Transfer amount |
| `status` | `VARCHAR(255)` | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `transaction_reference` | `VARCHAR(255)` | UUID from core banking |
| Audit columns | | `created_date`, `created_by`, `modified_date`, `modified_by`, `version` |

### 2.4 Utility Payment Service (MySQL: `banking_core_utility_payment_service`)

#### `utility_payment`
| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` PK AUTO_INCREMENT | |
| `provider_id` | `BIGINT` | Utility provider reference |
| `amount` | `DECIMAL` | Payment amount |
| `reference_number` | `VARCHAR(255)` | Customer reference (e.g. phone number) |
| `account` | `VARCHAR(255)` | Source bank account number |
| `transaction_id` | `VARCHAR(255)` | UUID from core banking |
| `status` | `VARCHAR(255)` | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| Audit columns | | `created_date`, `created_by`, `modified_date`, `modified_by`, `version` |

---

## 3. API Surface Map

### 3.1 Core Banking Service (`/api/v1/...` — Port 8092, Gateway prefix `/core`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | — | `BankAccount` (id, number, type, status, availableBalance, actualBalance, user) |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider | — | `UtilityAccount` (id, number, providerName) |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer | `{fromAccount, toAccount, amount}` | `{message, transactionId}` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` |
| `GET` | `/api/v1/user/{identification}` | Get user by ID number | — | `User` (id, firstName, lastName, email, identificationNumber, bankAccounts[]) |
| `GET` | `/api/v1/user` | List users (paginated) | Pageable params | `List<User>` |

### 3.2 User Service (`/api/v1/bank-users/...` — Port 8083, Gateway prefix `/user`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Register new user | `{email, password, identification}` | `User` (id, email, identification, authId, status) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user (e.g. approve) | `{status}` | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | Pageable params | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | — | `User` |

### 3.3 Fund Transfer Service (`/api/v1/transfer/...` — Port 8084, Gateway prefix `/fund-transfer`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `{fromAccount, toAccount, amount, authID}` | `{message, transactionId}` |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | Pageable params | `List<FundTransfer>` |

### 3.4 Utility Payment Service (`/api/v1/utility-payment/...` — Port 8085, Gateway prefix `/utility-payment`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` |
| `GET` | `/api/v1/utility-payment` | List payments (paginated) | Pageable params | `List<UtilityPayment>` |

### 3.5 Infrastructure Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/{service}/actuator/health` | Health check (via Gateway) |
| `GET` | `/{service}/actuator/info` | Build/git info |
| `GET` | `:8081/` | Eureka dashboard |
| `GET` | `:9411/` | Zipkin tracing UI |
| `GET` | `:8080/` | Keycloak admin console |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

1. Client `POST /user/api/v1/bank-users/register` with `{email, password, identification}`.
2. **User Service** checks Keycloak — if email already exists, throws `UserAlreadyRegisteredException`.
3. Calls **Core Banking Service** (`GET /api/v1/user/{identification}`) via Feign to verify the user exists in the banking core.
4. Validates that the email matches the core banking record; throws `InvalidEmailException` if mismatch.
5. Creates a Keycloak user (disabled, email unverified) with the provided password.
6. Persists a local `UserEntity` with `status=PENDING` and the Keycloak `authId`.
7. An admin later `PATCH /update/{id}` with `{status: "APPROVED"}`, which enables the Keycloak user and sets `emailVerified=true`.

### 4.2 Fund Transfer Flow

1. Client `POST /fund-transfer/api/v1/transfer` with `{fromAccount, toAccount, amount}`.
2. **Fund Transfer Service** persists a `FundTransferEntity` with `status=PENDING`.
3. Calls **Core Banking Service** (`POST /api/v1/transaction/fund-transfer`) via Feign.
4. Core Banking validates the sender account balance (`actualBalance >= amount`); throws `InsufficientFundsException` if insufficient.
5. Core Banking debits source account, credits destination account, creates two `TransactionEntity` records (debit + credit), returns a `transactionId`.
6. Fund Transfer Service updates its record to `status=SUCCESS` with the `transactionReference`.

**Business Rules:**
- Balance validation: `actualBalance >= 0 AND actualBalance >= transferAmount`.
- Both `actualBalance` and `availableBalance` are updated (note: potential double-deduction bug — see Gap Analysis).
- A UUID-based `transactionId` correlates the transfer across services.

### 4.3 Utility Payment Flow

1. Client `POST /utility-payment/api/v1/utility-payment` with `{providerId, amount, referenceNumber, account}`.
2. **Utility Payment Service** persists a `UtilityPaymentEntity` with `status=PROCESSING`.
3. Calls **Core Banking Service** (`POST /api/v1/transaction/util-payment`) via Feign.
4. Core Banking validates balance, looks up the utility provider, debits the source account, saves a transaction record.
5. Utility Payment Service updates its record to `status=SUCCESS`.

### 4.4 Audit Trail

- The API Gateway extracts the JWT principal name and forwards it as `X-Auth-Id` header.
- Downstream services use `AppAuthUserFilter` → `ApiRequestContextHolder` (ThreadLocal) → `AuditorAwareConfig` to populate JPA `@CreatedBy` / `@LastModifiedBy` fields.
- Optimistic locking via `@Version` on audited entities.

---

## 5. Integration Points

### 5.1 Keycloak (Identity Provider)

- **Realm:** `javatodev-internet-banking`
- **Client:** `internet-banking-api-client` (confidential, `client_credentials` grant for admin API)
- **User Service** uses the **Keycloak Admin Client** (`keycloak-admin-client:24.0.4`) for:
  - Creating users (`users().create()`)
  - Reading users by email or auth ID
  - Enabling users and marking emails as verified
- **API Gateway** validates JWTs via `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` pointing to Keycloak's JWKS endpoint.

### 5.2 MySQL

- All four business services share a single MySQL 8.4 instance but use **separate databases**:
  - `banking_core_service` (with Flyway migrations)
  - `banking_core_user_service` (JPA auto-DDL)
  - `banking_core_fund_transfer_service` (JPA auto-DDL)
  - `banking_core_utility_payment_service` (JPA auto-DDL)
- Connection user: `javatodev_development` with broad privileges.

### 5.3 Zipkin (Distributed Tracing)

- All services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave`.
- Trace data is exported to Zipkin at `http://zipkin:9411` (Docker) or `http://localhost:9411` (local).
- Feign calls propagate trace context via `feign-micrometer`.

### 5.4 Spring Cloud Config Server

- Serves configuration from: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` (branch `main`, path `configuration/`).
- All services bootstrap by connecting to the config server at `http://localhost:8090` (or Docker hostname).
- Profile-based config: `default`, `dev`, `docker`.

### 5.5 Eureka Service Registry

- All business services register as Eureka clients.
- Feign clients resolve services by Eureka application name (e.g., `@FeignClient(value = "core-banking-service")`).
- Eureka dashboard available at port `8081`.

### 5.6 RabbitMQ (Not Implemented)

- Listed in the technology stack and architecture diagram for notification messaging.
- No RabbitMQ dependency, configuration, or producer/consumer code exists in the current codebase.

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Gradle 8.6** with the Gradle Wrapper (`gradlew`).
- Each service is an independent Gradle project (no multi-project build).
- Common plugins: `java`, `org.springframework.boot:3.2.4`, `io.spring.dependency-management:1.1.4`, `com.gorylenko.gradle-git-properties:2.4.2`.
- Java source compatibility: **21**.
- Tests run with JUnit 5 (`useJUnitPlatform()`).

### 6.2 Docker

- Each service has a `Dockerfile` based on `eclipse-temurin:21.0.2_13-jre-alpine`.
- Build pattern: copy pre-built JAR → `ENTRYPOINT java -jar -Dspring.profiles.active=docker /app.jar`.
- `wait-for-it.sh` is included for startup ordering (waits on config server, registry, and MySQL).

### 6.3 Docker Compose

- `docker-compose.yml` — Full stack (all 6 services + MySQL + Keycloak + PostgreSQL + Zipkin).
- `docker-compose-support-apps.yml` — Infrastructure only (MySQL, Keycloak, PostgreSQL, Zipkin, Config Server, Service Registry).
- Custom bridge network `javatodev_ib_network` (subnet `172.25.0.0/16`) with static IPs.
- MySQL initialized with `privileges.sql` (creates databases and application user).
- Keycloak auto-imports realm from `keycloak/realm-export.json`.

### 6.4 CI/CD

- No CI/CD pipeline is configured (`.github/` contains only `FUNDING.yml`).
- No GitHub Actions workflows, Jenkinsfiles, or other pipeline definitions.

### 6.5 Database Migrations

- **Core Banking Service** uses **Flyway** with 3 versioned migrations:
  - `V1.0.20210427174638` — Create base tables (user, account, utility_account)
  - `V1.0.20210427174721` — Seed test data (4 users, 14 accounts, 6 utility providers)
  - `V1.0.20210429210839` — Create transaction table
- Other services rely on JPA `hibernate.ddl-auto` (not explicitly set in main configs, controlled via config server).

### 6.6 Test Data

Pre-seeded test accounts:
- **Users:** Sam Silva (808829932V), Guru Darmaraj (901830556V), Ragu Sivaraj (348829932V), Randor Manoon (842829932V)
- **Utility Providers:** VODAFONE, VERIZON, SINGTEL, HUTCH, AIRTEL, GIO
- **Test Credentials:** `ib_admin@javatodev.com` / `5V7huE3G86uB`
