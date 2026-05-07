# Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Summary

This is a **Java 21 / Spring Boot 3.2.4 / Spring Cloud 2023.0.0** internet banking application composed of **6 microservices** that together implement account management, fund transfers, utility payments, and user registration with Keycloak-based authentication.

### 1.2 Service Inventory

| Service | Port | Role | Database |
|---|---|---|---|
| **core-banking-service** | 8092 | Central banking ledger: accounts, users, transactions | MySQL (`banking_core_service`) |
| **internet-banking-user-service** | 8083 | User registration & management via Keycloak | MySQL (`banking_core_user_service`) |
| **internet-banking-fund-transfer-service** | 8084 | Fund transfer orchestration | MySQL (`banking_core_fund_transfer_service`) |
| **internet-banking-utility-payment-service** | 8085 | Utility payment orchestration | MySQL (`banking_core_utility_payment_service`) |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway, OAuth2 resource server | None |
| **internet-banking-service-registry** | 8081 | Netflix Eureka service registry | None |
| **internet-banking-config-server** | 8090 | Spring Cloud Config Server (Git-backed) | None |

### 1.3 Communication Patterns

```
                         ┌──────────────────────┐
                         │   Keycloak (8080)     │
                         │   OAuth2 / OIDC       │
                         └──────────┬───────────┘
                                    │ JWT validation
                                    ▼
┌─────────┐    HTTP    ┌─────────────────────────┐
│  Client  │──────────▶│  API Gateway (8082)     │
└─────────┘            │  Spring Cloud Gateway   │
                       │  + OAuth2 Resource Svr  │
                       └──────┬──────┬──────┬────┘
                              │      │      │
              ┌───────────────┘      │      └───────────────┐
              ▼                      ▼                      ▼
   ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────┐
   │ User Service     │  │ Fund Transfer    │  │ Utility Payment      │
   │ (8083)           │  │ Service (8084)   │  │ Service (8085)       │
   └────────┬─────────┘  └────────┬─────────┘  └────────┬─────────────┘
            │ Feign                │ Feign                │ Feign
            ▼                      ▼                      ▼
   ┌─────────────────────────────────────────────────────────┐
   │              Core Banking Service (8092)                │
   │   Accounts  │  Users  │  Transactions  │  Utility Accts │
   └─────────────────────────────────────────────────────────┘
                              │
                              ▼
                       ┌──────────────┐
                       │  MySQL 8.4   │
                       │  (4 schemas) │
                       └──────────────┘
```

**Inter-service communication:** Synchronous REST via **Spring Cloud OpenFeign** with Eureka-based service discovery. No asynchronous messaging is currently active (RabbitMQ is listed in the tech stack but not implemented).

**Service Discovery:** Netflix Eureka. All business services register with the Eureka server and resolve each other by service name.

**Configuration Management:** Spring Cloud Config Server backed by a remote Git repository (`https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`). Each service bootstraps configuration from this server.

**Distributed Tracing:** Micrometer Tracing with Brave bridge, reporting to Zipkin (port 9411).

**API Gateway:** Spring Cloud Gateway with OAuth2 resource server (JWT validation against Keycloak). Injects `X-Auth-Id` header into downstream requests via a `GlobalFilter`.

### 1.4 Infrastructure Components

| Component | Image / Version | Purpose |
|---|---|---|
| MySQL | `mysql:8.4.0` | Primary data store for all business services |
| Keycloak | `quay.io/keycloak/keycloak:23.0.7` | Identity & Access Management (OAuth2/OIDC) |
| Keycloak DB | `postgres:15` | Keycloak persistence |
| Zipkin | `openzipkin/zipkin:3` | Distributed tracing UI |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (`banking_core_service`)

**Flyway-managed schema** with 3 migration scripts.

#### `banking_core_user`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto) | |
| `first_name` | VARCHAR | |
| `last_name` | VARCHAR | |
| `email` | VARCHAR | |
| `identification_number` | VARCHAR | Unique national ID |

#### `banking_core_account`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto) | |
| `number` | VARCHAR | Account number (unique) |
| `type` | ENUM | `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | ENUM | `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `available_balance` | DECIMAL | |
| `actual_balance` | DECIMAL | |
| `user_id` | BIGINT (FK) | References `banking_core_user.id` |

#### `banking_core_transaction`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto) | |
| `amount` | DECIMAL | Negative for debits, positive for credits |
| `transaction_type` | ENUM | `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR | Counter-party account or reference |
| `transaction_id` | VARCHAR (UUID) | Unique transaction identifier |
| `account_id` | BIGINT (FK) | References `banking_core_account.id` |

#### `banking_core_utility_account`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto) | |
| `number` | VARCHAR | Utility provider account number |
| `provider_name` | VARCHAR | e.g., "Electricity", "Water" |

**Relationships:**
- `User` 1:N `BankAccount` (via `user_id` FK)
- `Transaction` 1:1 `BankAccount` (via `account_id` FK, with `CascadeType.ALL`)

### 2.2 User Service (`banking_core_user_service`)

#### `user`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto) | |
| `auth_id` | VARCHAR | Keycloak user ID |
| `identification` | VARCHAR | National ID (maps to core banking user) |
| `status` | ENUM | `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | TIMESTAMP | Audit field |
| `created_by` | VARCHAR | Audit field |
| `modified_date` | TIMESTAMP | Audit field |
| `modified_by` | VARCHAR | Audit field |
| `version` | BIGINT | Optimistic locking |

### 2.3 Fund Transfer Service (`banking_core_fund_transfer_service`)

#### `fund_transfer`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto) | |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | |
| `transaction_reference` | VARCHAR | Transaction ID from core banking |
| `status` | ENUM | `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` | TIMESTAMP | Audit field |
| `created_by` | VARCHAR | Audit field |
| `modified_date` | TIMESTAMP | Audit field |
| `modified_by` | VARCHAR | Audit field |
| `version` | BIGINT | Optimistic locking |

### 2.4 Utility Payment Service (`banking_core_utility_payment_service`)

#### `utility_payment`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto) | |
| `provider_id` | BIGINT | References utility provider |
| `amount` | DECIMAL | |
| `reference_number` | VARCHAR | Payment reference |
| `account` | VARCHAR | Payer's bank account number |
| `transaction_id` | VARCHAR | Transaction ID from core banking |
| `status` | ENUM | `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` | TIMESTAMP | Audit field |
| `created_by` | VARCHAR | Audit field |
| `modified_date` | TIMESTAMP | Audit field |
| `modified_by` | VARCHAR | Audit field |
| `version` | BIGINT | Optimistic locking |

---

## 3. API Surface Map

### 3.1 Core Banking Service (port 8092)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | - | `BankAccount` (id, number, type, status, availableBalance, actualBalance, user) |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | - | `UtilityAccount` (id, number, providerName) |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer | `{fromAccount, toAccount, amount}` | `{message, transactionId}` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` |
| `GET` | `/api/v1/user/{identification}` | Get user by ID number | - | `User` (id, firstName, lastName, email, identificationNumber, bankAccounts[]) |
| `GET` | `/api/v1/user` | List users (paginated) | - | `List<User>` |

### 3.2 User Service (port 8083)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Register new user | `{email, identification, password}` | `User` (id, email, identification, authId, status) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user status | `{status}` | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | - | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | - | `User` |

### 3.3 Fund Transfer Service (port 8084)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `{fromAccount, toAccount, amount, authID}` | `{message, transactionId}` |
| `GET` | `/api/v1/transfer` | List transfers (paginated) | - | `List<FundTransfer>` |

### 3.4 Utility Payment Service (port 8085)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` |
| `GET` | `/api/v1/utility-payment` | List payments (paginated) | - | `List<UtilityPayment>` |

### 3.5 API Gateway Routes (port 8082)

The gateway proxies to downstream services with path prefixes:

| Gateway Path Prefix | Target Service |
|---|---|
| `/user/**` | `internet-banking-user-service` |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` |
| `/banking-core/**` | `core-banking-service` |
| `/utility-payment/**` | `internet-banking-utility-payment-service` |

**Public endpoints (no auth required):**
- `POST /user/api/v1/bank-users/register`
- `GET /*/actuator/**`

All other endpoints require a valid JWT Bearer token.

### 3.6 Infrastructure Endpoints

| Service | Endpoint | Purpose |
|---|---|---|
| All services | `/actuator/health` | Health check |
| All services | `/actuator/info` | Application info (git properties) |
| Service Registry | `http://localhost:8081/` | Eureka dashboard |
| Zipkin | `http://localhost:9411/` | Trace explorer |

---

## 4. Key Business Logic Inventory

### 4.1 Fund Transfer Flow

1. **Fund Transfer Service** receives `POST /api/v1/transfer`
2. Creates a `FundTransferEntity` with `PENDING` status, persists to local DB
3. Calls **Core Banking Service** `POST /api/v1/transaction/fund-transfer` via Feign
4. **Core Banking Service**:
   a. Reads both source and destination `BankAccount` entities
   b. **Validates balance**: `actualBalance >= 0 AND actualBalance >= transferAmount`; throws `InsufficientFundsException` if violated
   c. Debits source account (`actualBalance -= amount`, `availableBalance = actualBalance - amount`)
   d. Creates a debit `TransactionEntity` (negative amount)
   e. Credits destination account (`actualBalance += amount`, `availableBalance = actualBalance + amount`)
   f. Creates a credit `TransactionEntity` (positive amount)
   g. Returns `{message, transactionId}`
5. **Fund Transfer Service** updates local entity to `SUCCESS` with transaction reference

**Known bug:** The `availableBalance` calculation is incorrect -- it subtracts amount from `actualBalance` *after* `actualBalance` was already reduced, resulting in double-deduction on the source account (and double-addition on the destination).

### 4.2 Utility Payment Flow

1. **Utility Payment Service** receives `POST /api/v1/utility-payment`
2. Creates `UtilityPaymentEntity` with `PROCESSING` status, persists to local DB
3. Calls **Core Banking Service** `POST /api/v1/transaction/util-payment` via Feign
4. **Core Banking Service**:
   a. Reads payer's `BankAccount` and validates balance
   b. Reads `UtilityAccount` by provider ID
   c. Debits payer's account
   d. Creates a `TransactionEntity` (UTILITY_PAYMENT type)
   e. Returns `{message, transactionId}`
5. **Utility Payment Service** updates local entity to `SUCCESS`

**Same balance calculation bug as fund transfer.**

### 4.3 User Registration Flow

1. **User Service** receives `POST /api/v1/bank-users/register`
2. Checks Keycloak for existing email -- throws `UserAlreadyRegisteredException` if found
3. Calls **Core Banking Service** `GET /api/v1/user/{identification}` to validate user exists in core system
4. Validates email matches core banking records -- throws `InvalidEmailException` if mismatch
5. Creates Keycloak user (disabled, email unverified) with provided password
6. On Keycloak 201 response, saves `UserEntity` locally with `PENDING` status
7. If core banking user not found, throws `InvalidBankingUserException`

### 4.4 User Approval Flow

1. Admin calls `PATCH /api/v1/bank-users/update/{id}` with `{status: "APPROVED"}`
2. If new status is `APPROVED`:
   a. Reads Keycloak user by `authId`
   b. Enables account and marks email as verified in Keycloak
3. Updates local `UserEntity` status

---

## 5. Integration Points

### 5.1 Keycloak (Identity Provider)

- **Version:** 23.0.7
- **Protocol:** OAuth2 / OpenID Connect
- **Used by:** API Gateway (JWT validation), User Service (admin client)
- **Realm:** `javatodev-internet-banking` (imported via `realm-export.json`)
- **Client:** `internet-banking-api-client` (client credentials grant)
- **Configuration properties:** `app.config.keycloak.server-url`, `app.config.keycloak.realm`, `app.config.keycloak.clientId`, `app.config.keycloak.client-secret`
- **Admin client library:** `keycloak-admin-client:24.0.4`
- **Operations:** Create user, update user, search by email, read user by ID

### 5.2 RabbitMQ

- **Status:** Listed in tech stack but **not implemented** in code
- **Intended use:** Notification service (PENDING development) would consume messages from fund transfer and payment services

### 5.3 Zipkin (Distributed Tracing)

- **Version:** 3
- **Port:** 9411
- **Integration:** All services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave`
- **Trace propagation:** Automatic via Spring Boot Actuator + Micrometer

### 5.4 Database Connections

- **Engine:** MySQL 8.4.0
- **Connection user:** `javatodev_development` / `oPItyPticIAt`
- **Root password:** `woVERANKliGharym`
- **Schemas:** Created via init script (`privileges.sql`)
  - `banking_core_service`
  - `banking_core_fund_transfer_service`
  - `banking_core_user_service`
  - `banking_core_utility_payment_service`
- **ORM:** Spring Data JPA with Hibernate
- **Migrations:** Flyway (core-banking-service only)
- **Test DB:** H2 in-memory (all services)

### 5.5 Spring Cloud Config Server

- **Git repository:** `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch:** `main`
- **Search path:** `configuration/`
- **Bootstrap profiles:** `default` (localhost), `dev` (192.168.1.5), `docker` (container hostname)
- All services connect to Config Server at bootstrap time to fetch externalized configuration

### 5.6 Netflix Eureka

- **Server port:** 8081
- **Self-registration:** Disabled (standalone mode)
- **Clients:** All 5 business services register with Eureka
- **Feign clients** resolve service names via Eureka registry

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build tool:** Gradle 8.6 (per-service `gradlew` wrappers)
- **No multi-module root build** -- each service is an independent Gradle project
- **Plugins:**
  - `org.springframework.boot:3.2.4`
  - `io.spring.dependency-management:1.1.4`
  - `com.gorylenko.gradle-git-properties:2.4.2` (5 of 6 services)
- **Java compatibility:** 21
- **Test framework:** JUnit 5 (JUnit Platform)

### 6.2 Docker

Each service has its own `Dockerfile`:

```dockerfile
FROM eclipse-temurin:21
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

- Uses `wait-for-it.sh` for startup ordering
- Docker profile activated via `-Dspring.profiles.active=docker`

### 6.3 Docker Compose

Two compose files in `docker-compose/`:

1. **`docker-compose.yml`** -- Full stack (all services + infrastructure)
2. **`docker-compose-support-apps.yml`** -- Infrastructure only (Zipkin, Keycloak, MySQL, Config Server, Service Registry)

**Network:** `javatodev_ib_network` (bridge, subnet `172.25.0.0/16`) with static IPs assigned to each container.

**Startup ordering:** `wait-for-it.sh` scripts ensure services start after Config Server, Service Registry, and MySQL are available.

### 6.4 CI/CD

- **No CI/CD pipeline** is present in the repository (`.github/` only contains `FUNDING.yml`)
- No GitHub Actions, Jenkins, or other CI configuration
- Build and deployment is manual

### 6.5 OpenAPI / Swagger

- Services include `springdoc-openapi-starter-webflux-ui:2.1.0`
- Controllers are annotated with `@Tag`, `@Operation` from `io.swagger.v3.oas.annotations`
- **Note:** The webflux UI starter is used in non-webflux (servlet) services, which is a mismatch

### 6.6 Postman Collection

A Postman collection is provided at `postman_collection/JAVA_TO_DEV_MICROSERVICES.postman_collection.json` with environment file `BANKING_CORE_MICROSERVICES_PROJECT.postman_environment.json` for testing.
