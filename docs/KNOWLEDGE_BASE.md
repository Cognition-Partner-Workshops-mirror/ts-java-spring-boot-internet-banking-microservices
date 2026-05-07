# Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Summary

This is a **Java 21 / Spring Boot 3.2.4 / Spring Cloud 2023.0.0** internet banking application composed of **6 microservices** that together implement core banking operations: user management, fund transfers, and utility payments.

### 1.2 Microservices Inventory

| Service | Port | Purpose | Has Own DB? |
|---|---|---|---|
| **internet-banking-service-registry** | 8081 | Netflix Eureka server for service discovery | No |
| **internet-banking-config-server** | 8090 | Spring Cloud Config server (Git-backed) | No |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway with OAuth2/Keycloak security | No |
| **internet-banking-user-service** | 8083 | User registration, approval, Keycloak integration | Yes (MySQL `banking_core_user_service`) |
| **internet-banking-fund-transfer-service** | 8084 | Fund transfer orchestration | Yes (MySQL `banking_core_fund_transfer_service`) |
| **internet-banking-utility-payment-service** | 8085 | Utility bill payment orchestration | Yes (MySQL `banking_core_utility_payment_service`) |
| **core-banking-service** | 8092 | Core banking ledger: accounts, users, transactions | Yes (MySQL `banking_core_service`) |

### 1.3 Communication Patterns

```
                                 ┌─────────────────────┐
                                 │   Keycloak (8080)    │
                                 │   OAuth2 / OIDC      │
                                 └──────────┬───────────┘
                                            │ JWT validation
┌──────────┐    ┌──────────────────┐    ┌───┴───────────────┐
│  Client   │───▶│  API Gateway     │───▶│  Downstream       │
│           │    │  (8082)          │    │  Services          │
└──────────┘    └──────────────────┘    └───────────────────┘
```

- **Synchronous (REST/OpenFeign):** All inter-service communication uses Spring Cloud OpenFeign over HTTP, with service discovery via Eureka.
  - `internet-banking-user-service` → `core-banking-service` (user lookup by identification)
  - `internet-banking-fund-transfer-service` → `core-banking-service` (account lookup, fund transfer execution)
  - `internet-banking-utility-payment-service` → `core-banking-service` (account lookup, utility payment execution)
- **Asynchronous (RabbitMQ):** Listed in the technology stack and README for notification service, but **not yet implemented** in the current codebase. No RabbitMQ dependencies exist in any `build.gradle`.
- **Service Discovery:** All business services register with Eureka and use logical service names in Feign clients (e.g., `@FeignClient(name = "core-banking-service")`).
- **Centralized Configuration:** All services (except config-server and service-registry) pull configuration from the config server, which reads from a [Git repository](https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git).
- **API Gateway Routing:** The gateway routes requests with path prefixes (`/user/**`, `/fund-transfer/**`, `/banking-core/**`, `/utility-payment/**`) to the corresponding downstream services.

### 1.4 Infrastructure Components

| Component | Image / Technology | Purpose |
|---|---|---|
| **MySQL 8.4.0** | `mysql:8.4.0` (custom Dockerfile) | Primary database for all services |
| **Keycloak 23.0.7** | `quay.io/keycloak/keycloak:23.0.7` | Identity & access management (OAuth2/OIDC) |
| **PostgreSQL 15** | `postgres:15` | Keycloak's backing database |
| **Zipkin 3** | `openzipkin/zipkin:3` | Distributed tracing |
| **Eureka** | Embedded in service-registry | Service discovery |
| **Spring Cloud Config** | Embedded in config-server | Centralized configuration |

---

## 2. Data Model Documentation

### 2.1 core-banking-service

Managed via **Flyway** migrations. Database: `banking_core_service`.

#### Entities

**`banking_core_user`**
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `first_name` | VARCHAR(255) | |
| `last_name` | VARCHAR(255) | |
| `email` | VARCHAR(255) | |
| `identification_number` | VARCHAR(255) | National ID |

**`banking_core_account`**
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `number` | VARCHAR(255) | Account number |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `actual_balance` | DECIMAL(19,2) | Ledger balance |
| `available_balance` | DECIMAL(19,2) | Available balance |
| `user_id` | BIGINT (FK → `banking_core_user.id`) | Account owner |

**`banking_core_utility_account`**
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `number` | VARCHAR(255) | Provider account number |
| `provider_name` | VARCHAR(255) | e.g., VODAFONE, VERIZON |

**`banking_core_transaction`**
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `amount` | DECIMAL(19,2) | Signed amount |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Target account or reference |
| `transaction_id` | VARCHAR(50) | UUID-based transaction identifier |
| `account_id` | BIGINT (FK → `banking_core_account.id`) | |

#### Relationships
```
banking_core_user  1───*  banking_core_account
banking_core_account  1───1  banking_core_transaction
```

### 2.2 internet-banking-user-service

Database: `banking_core_user_service`. Schema managed by **JPA/Hibernate** (`ddl-auto` via config server).

**`user`** (entity: `UserEntity` extends `AuditAware`)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `auth_id` | VARCHAR | Keycloak user ID |
| `identification` | VARCHAR | National ID (links to core-banking user) |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | TIMESTAMP | Audit field |
| `created_by` | VARCHAR | Audit field |
| `modified_date` | TIMESTAMP | Audit field |
| `modified_by` | VARCHAR | Audit field |
| `version` | BIGINT | Optimistic locking |

### 2.3 internet-banking-fund-transfer-service

Database: `banking_core_fund_transfer_service`. Schema managed by **JPA/Hibernate**.

**`fund_transfer`** (entity: `FundTransferEntity` extends `AuditAware`)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `transaction_reference` | VARCHAR | UUID from core-banking |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `status` | VARCHAR | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | | Audit fields |

### 2.4 internet-banking-utility-payment-service

Database: `banking_core_utility_payment_service`. Schema managed by **JPA/Hibernate**.

**`utility_payment`** (entity: `UtilityPaymentEntity` extends `AuditAware`)
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Customer reference |
| `account` | VARCHAR | Payer's bank account number |
| `transaction_id` | VARCHAR | UUID from core-banking |
| `status` | VARCHAR | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | | Audit fields |

---

## 3. API Surface Map

### 3.1 API Gateway Routes (port 8082)

All requests pass through the gateway with JWT authentication (Keycloak). The gateway strips the prefix and forwards to the downstream service.

| Gateway Path | Downstream Service | Auth Required |
|---|---|---|
| `/user/**` | internet-banking-user-service | Yes (except `/user/api/v1/bank-users/register`) |
| `/fund-transfer/**` | internet-banking-fund-transfer-service | Yes |
| `/banking-core/**` | core-banking-service | Yes |
| `/utility-payment/**` | internet-banking-utility-payment-service | Yes |
| `/actuator/**` | Various | No |

### 3.2 core-banking-service (port 8092)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | - | `BankAccount` |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | - | `UtilityAccount` |
| `GET` | `/api/v1/user/{identification}` | Get user by identification number | - | `User` |
| `GET` | `/api/v1/user` | List users (paginated) | - | `List<User>` |
| `POST` | `/api/v1/transaction/fund-transfer` | Execute fund transfer | `FundTransferRequest` | `FundTransferResponse` |
| `POST` | `/api/v1/transaction/util-payment` | Execute utility payment | `UtilityPaymentRequest` | `UtilityPaymentResponse` |

**Request/Response Shapes:**

```json
// FundTransferRequest
{ "fromAccount": "string", "toAccount": "string", "amount": 0.00 }

// FundTransferResponse
{ "message": "string", "transactionId": "string" }

// UtilityPaymentRequest
{ "providerId": 0, "amount": 0.00, "referenceNumber": "string", "account": "string" }

// UtilityPaymentResponse
{ "message": "string", "transactionId": "string" }

// BankAccount
{ "id": 0, "number": "string", "type": "SAVINGS_ACCOUNT|FIXED_DEPOSIT|LOAN_ACCOUNT",
  "status": "PENDING|ACTIVE|DORMANT|BLOCKED", "availableBalance": 0.00,
  "actualBalance": 0.00, "user": { ... } }

// User (core-banking)
{ "id": 0, "firstName": "string", "lastName": "string", "email": "string",
  "identificationNumber": "string", "bankAccounts": [ ... ] }
```

### 3.3 internet-banking-user-service (port 8083)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Register new user (public) | `User` | `User` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user status | `UserUpdateRequest` | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | - | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | - | `User` |

**Request/Response Shapes:**

```json
// User (request for registration)
{ "email": "string", "identification": "string", "password": "string" }

// UserUpdateRequest
{ "status": "PENDING|APPROVED|DISABLED|BLACKLIST" }

// User (response)
{ "id": 0, "email": "string", "identification": "string", "authId": "string",
  "status": "PENDING|APPROVED|DISABLED|BLACKLIST", "version": 0 }
```

### 3.4 internet-banking-fund-transfer-service (port 8084)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `FundTransferRequest` | `FundTransferResponse` |
| `GET` | `/api/v1/transfer` | List transfers (paginated) | - | `List<FundTransfer>` |

**Request/Response Shapes:**

```json
// FundTransferRequest
{ "fromAccount": "string", "toAccount": "string", "amount": 0.00, "authID": "string" }

// FundTransferResponse
{ "message": "string", "transactionId": "string" }

// FundTransfer (list item)
{ "id": 0, "transactionReference": "string", "status": "string",
  "fromAccount": "string", "toAccount": "string", "amount": 0.00, "version": 0 }
```

### 3.5 internet-banking-utility-payment-service (port 8085)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `UtilityPaymentRequest` | `UtilityPaymentResponse` |
| `GET` | `/api/v1/utility-payment` | List payments (paginated) | - | `List<UtilityPayment>` |

**Request/Response Shapes:**

```json
// UtilityPaymentRequest
{ "providerId": 0, "amount": 0.00, "referenceNumber": "string", "account": "string" }

// UtilityPaymentResponse
{ "message": "string", "transactionId": "string" }

// UtilityPayment (list item)
{ "providerId": 0, "amount": 0.00, "referenceNumber": "string", "account": "string",
  "status": "PENDING|PROCESSING|SUCCESS|FAILED", "version": 0 }
```

### 3.6 Actuator Endpoints (all services)

All services expose Spring Boot Actuator at `/actuator/**` (unauthenticated at the gateway level).

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow (`internet-banking-user-service`)

1. Check if email already exists in Keycloak; reject if duplicate (`UserAlreadyRegisteredException`).
2. Call `core-banking-service` via Feign to verify user exists by identification number.
3. Validate that the email in the request matches the email in core-banking (`InvalidEmailException`).
4. Create user in Keycloak (disabled, email unverified) with provided password.
5. On successful Keycloak creation (HTTP 201), persist user locally with `PENDING` status and Keycloak auth ID.
6. If core-banking user not found, throw `InvalidBankingUserException`.

### 4.2 User Approval Flow (`internet-banking-user-service`)

1. Admin calls `PATCH /api/v1/bank-users/update/{id}` with `{ "status": "APPROVED" }`.
2. If status is `APPROVED`, the service enables the user in Keycloak and marks email as verified.
3. User entity status is updated in the local database.

### 4.3 Fund Transfer Flow

**Orchestration layer** (`internet-banking-fund-transfer-service`):
1. Receive transfer request with `fromAccount`, `toAccount`, `amount`.
2. Persist a local `FundTransferEntity` with status `PENDING`.
3. Call `core-banking-service` POST `/api/v1/transaction/fund-transfer` via Feign.
4. On success, update local entity to `SUCCESS` with transaction reference.

**Execution layer** (`core-banking-service` → `TransactionService`):
1. Look up both accounts by account number (throw `EntityNotFoundException` if missing).
2. **Balance validation:** Check `fromAccount.actualBalance >= amount` and `actualBalance > 0`; throw `InsufficientFundsException` otherwise.
3. Debit source account: subtract amount from `actualBalance` and `availableBalance`.
4. Record debit transaction entry (negative amount).
5. Credit destination account: add amount to `actualBalance` and `availableBalance`.
6. Record credit transaction entry (positive amount).
7. Return generated UUID transaction ID.
8. Entire operation is `@Transactional`.

### 4.4 Utility Payment Flow

**Orchestration layer** (`internet-banking-utility-payment-service`):
1. Receive payment request with `providerId`, `amount`, `referenceNumber`, `account`.
2. Persist a local `UtilityPaymentEntity` with status `PROCESSING`.
3. Call `core-banking-service` POST `/api/v1/transaction/util-payment` via Feign.
4. On success, update local entity to `SUCCESS` with transaction ID.

**Execution layer** (`core-banking-service` → `TransactionService`):
1. Look up payer's bank account by number.
2. **Balance validation:** Same as fund transfer.
3. Look up utility provider account by ID.
4. Debit payer's account.
5. Record transaction entry with type `UTILITY_PAYMENT`.
6. Return generated UUID transaction ID.

### 4.5 Authentication & Authorization

- **Keycloak** manages the user identity store (realm: `javatodev-internet-banking`, client: `internet-banking-api-client`).
- **API Gateway** enforces OAuth2 JWT validation for all routes except user registration and actuator endpoints.
- **X-Auth-Id header:** Gateway extracts the authenticated principal name from the JWT and forwards it as `X-Auth-Id` header to downstream services.
- **AppAuthUserFilter:** Fund-transfer and utility-payment services read `X-Auth-Id` from the request header and store it in a thread-local `ApiRequestContextHolder` for audit purposes.

---

## 5. Integration Points

### 5.1 Keycloak

- **Version:** 23.0.7
- **Connection:** User service connects via `keycloak-admin-client` SDK (v24.0.4).
- **Configuration:** `app.config.keycloak.server-url`, `realm`, `clientId`, `client-secret` (from Spring Cloud Config).
- **Operations:** Create user, update user (enable/disable), search users by email, read user by auth ID.
- **Realm Data:** Pre-loaded via `realm-export.json` mounted into the Keycloak container.

### 5.2 Zipkin (Distributed Tracing)

- **Version:** Zipkin 3
- **Libraries:** `micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`, `feign-micrometer`.
- **Enabled in:** All 7 services (including gateway, config server, service registry).
- **Port:** 9411

### 5.3 MySQL

- **Version:** 8.4.0
- **Connection:** MySQL Connector/J 8.4.0 via Spring Data JPA.
- **Databases:** 4 separate databases created by `privileges.sql`:
  - `banking_core_service` (core-banking-service, Flyway-managed)
  - `banking_core_user_service` (user-service, JPA ddl-auto)
  - `banking_core_fund_transfer_service` (fund-transfer-service, JPA ddl-auto)
  - `banking_core_utility_payment_service` (utility-payment-service, JPA ddl-auto)
- **User:** `javatodev_development` / `oPItyPticIAt`

### 5.4 RabbitMQ

- **Status:** Referenced in README and architecture diagram but **NOT implemented** in the current codebase.
- **Intended use:** Notification service (marked as "PENDING Development" in README).

### 5.5 Spring Cloud Config (Git-backed)

- **Config repo:** `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch:** `main`
- **Search path:** `configuration/`
- **Consumers:** All services except config-server and service-registry use `spring-cloud-starter-config` + `spring-cloud-starter-bootstrap`.

### 5.6 Netflix Eureka (Service Discovery)

- **Server:** `internet-banking-service-registry` (port 8081)
- **Clients:** All other services register and discover via Eureka.
- **Feign integration:** Service names in `@FeignClient` annotations resolve via Eureka.

### 5.7 OpenFeign (Inter-Service REST Calls)

| Caller | Feign Client Interface | Target Service | Endpoints Called |
|---|---|---|---|
| user-service | `BankingCoreRestClient` | core-banking-service | `GET /api/v1/user/{identification}` |
| fund-transfer-service | `BankingCoreFeignClient` | core-banking-service | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/fund-transfer` |
| utility-payment-service | `BankingCoreRestClient` | core-banking-service | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/util-payment` |

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build tool:** Gradle (per-service, no multi-project build)
- **Each service** has its own `build.gradle`, `settings.gradle`, and standalone Gradle wrapper.
- **Plugins:** `spring-boot`, `spring-dependency-management`, `gradle-git-properties`
- **Java version:** 21 (source compatibility)
- **Test framework:** JUnit 5 (`useJUnitPlatform()`)

### 6.2 Docker

- **Base image:** `eclipse-temurin:21.0.2_13-jre-alpine`
- **Pattern:** Each service has a `Dockerfile` that copies the built JAR and a `wait-for-it.sh` script.
- **Profile:** Docker containers run with `-Dspring.profiles.active=docker`.

### 6.3 Docker Compose

Two compose files:

1. **`docker-compose.yml`** (full stack): All 7 services + MySQL + Keycloak + PostgreSQL + Zipkin on a custom bridge network (`172.25.0.0/16`).
2. **`docker-compose-support-apps.yml`** (infrastructure only): Zipkin + Keycloak + PostgreSQL + MySQL + Config Server + Service Registry.

**Startup ordering:** Services use `wait-for-it.sh` entrypoints to wait for service-registry (8081), config-server (8090), and MySQL (3306) before starting.

### 6.4 CI/CD

- **GitHub Actions:** A `.github/FUNDING.yml` exists but no workflow files are present. No CI/CD pipeline is defined.

### 6.5 Database Migrations

- **core-banking-service:** Uses Flyway with 3 versioned migrations:
  - `V1.0.20210427174638` - Create base tables (user, account, utility_account)
  - `V1.0.20210427174721` - Seed test data (4 users, 14 accounts, 6 utility providers)
  - `V1.0.20210429210839` - Create transaction table
- **Other services:** Rely on JPA `hibernate.ddl-auto` (configured via external config server) - no migration scripts.

### 6.6 OpenAPI / Swagger

- **Library:** `springdoc-openapi-starter-webflux-ui:2.1.0`
- **Included in:** core-banking-service, user-service, fund-transfer-service, utility-payment-service
- **Annotations:** `@Tag` and `@Operation` annotations present on all controllers.
