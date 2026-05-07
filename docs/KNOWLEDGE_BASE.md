# Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Summary

The Internet Banking Concept application is a microservices-based banking platform built with **Java 21** and **Spring Boot 3.2.4** (Spring Cloud 2023.0.0). It models a simplified internet banking system with user registration, fund transfers between accounts, and utility bill payments.

### 1.2 Microservices Inventory

| Service | Port | Purpose | Database |
|---|---|---|---|
| **internet-banking-service-registry** | 8081 | Netflix Eureka discovery server | None |
| **internet-banking-config-server** | 8090 | Spring Cloud Config (Git-backed) | None |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway + OAuth2 resource server | None |
| **core-banking-service** | 8092 | System of record: accounts, users, ledger transactions | MySQL (`banking_core_service`) |
| **internet-banking-user-service** | 8083 | User registration, profile management, Keycloak integration | MySQL (`internet_banking_user_service`) |
| **internet-banking-fund-transfer-service** | 8084 | Account-to-account fund transfers | MySQL (`internet_banking_fund_transfer_service`) |
| **internet-banking-utility-payment-service** | 8085 | Third-party utility bill payments | MySQL (`internet_banking_utility_payment_service`) |

### 1.3 Communication Patterns

```
                         ┌─────────────────────┐
                         │   API Gateway (8082) │
                         │  OAuth2 + Routing    │
                         └──────────┬───────────┘
                                    │
               ┌────────────────────┼────────────────────┐
               │                    │                     │
    ┌──────────▼──────┐  ┌─────────▼──────┐  ┌──────────▼──────────┐
    │  User Service   │  │ Fund Transfer  │  │  Utility Payment    │
    │     (8083)      │  │   Service      │  │    Service          │
    │                 │  │   (8084)       │  │    (8085)           │
    └────────┬────────┘  └───────┬────────┘  └──────────┬──────────┘
             │                   │                       │
             │  OpenFeign        │  OpenFeign             │  OpenFeign
             │                   │                       │
             └───────────────────┼───────────────────────┘
                                 │
                      ┌──────────▼──────────┐
                      │  Core Banking       │
                      │  Service (8092)     │
                      │  (System of Record) │
                      └─────────────────────┘
```

- **Synchronous REST** — All inter-service communication uses Spring Cloud OpenFeign declarative HTTP clients, routed via Eureka service discovery.
- **Service Discovery** — Netflix Eureka (service registry at port 8081). All business services register as Eureka clients.
- **Centralized Config** — Spring Cloud Config Server fetches properties from a Git repository: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` (branch `main`, path `configuration/`).
- **API Gateway** — Spring Cloud Gateway routes external traffic to downstream services with path-based routing and OAuth2 JWT enforcement via Keycloak.
- **Identity Propagation** — The gateway extracts the authenticated principal name and injects it as the `X-Auth-Id` HTTP header into downstream requests. Each downstream service has an `AppAuthUserFilter` that reads this header and stores it in a thread-local `ApiRequestContextHolder`.

### 1.4 Infrastructure Components

| Component | Image / Tech | Purpose | Docker IP |
|---|---|---|---|
| **Keycloak** | `quay.io/keycloak/keycloak:23.0.7` | IAM — OAuth2 / OIDC provider | 172.25.0.11 |
| **PostgreSQL** | `postgres:15` | Keycloak's backing store | 172.25.0.10 |
| **MySQL** | Custom `mysql` Dockerfile | Shared database server for all business services | 172.25.0.9 |
| **Zipkin** | `openzipkin/zipkin:3` | Distributed tracing collector/UI | 172.25.0.12 |

> **Note:** RabbitMQ is mentioned in the README for a future Notification Service but is **not present** in the current Docker Compose or codebase.

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL — `banking_core_service`)

#### `banking_core_user`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `first_name` | VARCHAR(255) | |
| `last_name` | VARCHAR(255) | |
| `email` | VARCHAR(255) | |
| `identification_number` | VARCHAR(255) | National ID / NIC |

#### `banking_core_account`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `number` | VARCHAR(255) | Account number (12-digit string) |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `available_balance` | DECIMAL(19,2) | |
| `actual_balance` | DECIMAL(19,2) | |
| `user_id` | BIGINT (FK → `banking_core_user.id`) | ManyToOne |

#### `banking_core_utility_account`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `number` | VARCHAR(255) | Provider account number |
| `provider_name` | VARCHAR(255) | e.g., VODAFONE, VERIZON |

#### `banking_core_transaction`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `amount` | DECIMAL(19,2) | Negative for debits |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Target account number or reference |
| `transaction_id` | VARCHAR(50) | UUID correlation ID |
| `account_id` | BIGINT (FK → `banking_core_account.id`) | OneToOne mapping |

**Relationships:**
- `banking_core_user` 1:N `banking_core_account` (a user can have multiple accounts)
- `banking_core_account` 1:1 `banking_core_transaction` (each transaction record links to one account)

### 2.2 Internet Banking User Service (MySQL — `internet_banking_user_service`)

#### `user`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `auth_id` | VARCHAR(255) | Keycloak user UUID |
| `identification` | VARCHAR(255) | NIC / National ID |
| `status` | VARCHAR(255) | Enum: `PENDING`, `APPROVED` |
| `created_at` | DATETIME | Audit (via `AuditAware` superclass) |
| `updated_at` | DATETIME | Audit (via `AuditAware` superclass) |

### 2.3 Internet Banking Fund Transfer Service (MySQL — `internet_banking_fund_transfer_service`)

#### `fund_transfer`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `transaction_reference` | VARCHAR(255) | UUID from core banking |
| `from_account` | VARCHAR(255) | Source account number |
| `to_account` | VARCHAR(255) | Target account number |
| `amount` | DECIMAL(19,2) | |
| `status` | VARCHAR(255) | Enum: `PENDING`, `SUCCESS`, `FAILED` |
| `created_at` | DATETIME | Audit |
| `updated_at` | DATETIME | Audit |

### 2.4 Internet Banking Utility Payment Service (MySQL — `internet_banking_utility_payment_service`)

#### `utility_payment`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL(19,2) | |
| `reference_number` | VARCHAR(255) | Bill reference |
| `account` | VARCHAR(255) | Payer account number |
| `transaction_id` | VARCHAR(255) | UUID from core banking |
| `status` | VARCHAR(255) | Enum: `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_at` | DATETIME | Audit |
| `updated_at` | DATETIME | Audit |

### 2.5 Schema Management

- **Core Banking Service** uses **Flyway** for database migrations (`src/main/resources/db/migration/`).
- Other services rely on **JPA/Hibernate auto-DDL** (no Flyway migrations present).

---

## 3. API Surface Map

All endpoints are exposed through the **API Gateway** (port 8082) with path prefixes defined in gateway routing configuration.

### 3.1 Core Banking Service (`/banking-core/api/v1/...`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | — | `BankAccount` DTO |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | — | `UtilityAccount` DTO |
| `GET` | `/api/v1/user/{identification}` | Get user by identification number | — | `User` DTO |
| `GET` | `/api/v1/user` | List users (paginated) | — | `List<User>` |
| `POST` | `/api/v1/transaction/fund-transfer` | Execute a fund transfer | `FundTransferRequest` | `FundTransferResponse` |
| `POST` | `/api/v1/transaction/util-payment` | Execute a utility payment | `UtilityPaymentRequest` | `UtilityPaymentResponse` |

**Request/Response Shapes:**

```json
// FundTransferRequest
{ "fromAccount": "string", "toAccount": "string", "amount": 0.00 }

// FundTransferResponse
{ "message": "string", "transactionId": "uuid-string" }

// UtilityPaymentRequest
{ "providerId": 0, "amount": 0.00, "referenceNumber": "string", "account": "string" }

// UtilityPaymentResponse
{ "message": "string", "transactionId": "uuid-string" }
```

### 3.2 Internet Banking User Service (`/user/api/v1/...`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Register a new user (public) | `User` DTO | `User` DTO |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user (e.g., approve) | `UserUpdateRequest` | `User` DTO |
| `GET` | `/api/v1/bank-users` | List users (paginated) | — | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | — | `User` DTO |

**Request/Response Shapes:**

```json
// User (Registration Request)
{ "email": "string", "identification": "string", "password": "string" }

// UserUpdateRequest
{ "status": "APPROVED" }

// User (Response)
{ "id": 0, "email": "string", "identification": "string", "authId": "string", "status": "PENDING" }
```

### 3.3 Internet Banking Fund Transfer Service (`/fund-transfer/api/v1/...`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate a fund transfer | `FundTransferRequest` | `FundTransferResponse` |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | — | `List<FundTransfer>` |

**Request/Response Shapes:**

```json
// FundTransferRequest
{ "fromAccount": "string", "toAccount": "string", "amount": 0.00, "authID": "string" }

// FundTransferResponse
{ "message": "string", "transactionId": "uuid-string" }
```

### 3.4 Internet Banking Utility Payment Service (`/utility-payment/api/v1/...`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process a utility payment | `UtilityPaymentRequest` | `UtilityPaymentResponse` |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | — | `List<UtilityPayment>` |

**Request/Response Shapes:**

```json
// UtilityPaymentRequest
{ "providerId": 0, "amount": 0.00, "referenceNumber": "string", "account": "string" }

// UtilityPaymentResponse
{ "message": "string", "transactionId": "uuid-string" }
```

### 3.5 Infrastructure Endpoints

| Service | Endpoint | Description |
|---|---|---|
| Service Registry | `GET /` (port 8081) | Eureka dashboard |
| Config Server | `GET /{application}/{profile}` (port 8090) | Configuration properties |
| All services | `GET /actuator/**` | Spring Boot Actuator (health, info, etc.) |

### 3.6 Authentication Flow

1. Client obtains a JWT access token from Keycloak (`POST /realms/{realm}/protocol/openid-connect/token`).
2. Client sends requests to the API Gateway with `Authorization: Bearer <token>`.
3. Gateway validates the JWT against Keycloak's JWK endpoint.
4. Gateway extracts the principal name and injects it as `X-Auth-Id` header to downstream services.
5. The `/user/api/v1/bank-users/register` endpoint is publicly accessible (no JWT required).
6. All `/actuator/**` endpoints are publicly accessible.

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

**Location:** `internet-banking-user-service` → `UserService.createUser()`

1. Check if email already exists in Keycloak — throw `UserAlreadyRegisteredException` if found.
2. Validate the user's identification number against the core banking system via Feign (`BankingCoreRestClient.readUser(identification)`).
3. Verify the email matches core banking records — throw `InvalidEmailException` if mismatch.
4. Create a Keycloak user representation with credentials (password), initially set to `enabled=false`, `emailVerified=false`.
5. Call Keycloak Admin API to create the user. If HTTP 201 returned:
   - Retrieve the newly created Keycloak user to get the `authId` (Keycloak UUID).
   - Save a local `UserEntity` with status `PENDING`.
6. If core banking user not found, throw `InvalidBankingUserException`.

### 4.2 User Approval Flow

**Location:** `internet-banking-user-service` → `UserService.updateUser()`

1. When status is changed to `APPROVED`:
   - Enable the Keycloak user (`enabled=true`, `emailVerified=true`).
   - Update local entity status to `APPROVED`.

### 4.3 Fund Transfer Flow

**Location:** `internet-banking-fund-transfer-service` → `FundTransferService.fundTransfer()`

1. Save a local `FundTransferEntity` with status `PENDING`.
2. Call core banking service via Feign (`BankingCoreFeignClient.fundTransfer(request)`).
3. **Core Banking Processing** (`TransactionService.fundTransfer()`):
   - Read both from/to bank accounts.
   - Validate the source account has sufficient funds (`actualBalance >= amount`).
   - Debit the source account (`actualBalance - amount`, `availableBalance = actualBalance - amount`).
   - Credit the destination account (`actualBalance + amount`, `availableBalance = actualBalance + amount`).
   - Create two transaction records (debit + credit) with the same `transactionId`.
4. Update local entity with `transactionReference` and status `SUCCESS`.

### 4.4 Utility Payment Flow

**Location:** `internet-banking-utility-payment-service` → `UtilityPaymentService.utilPayment()`

1. Save a local `UtilityPaymentEntity` with status `PROCESSING`.
2. Call core banking service via Feign (`BankingCoreRestClient.utilityPayment(request)`).
3. **Core Banking Processing** (`TransactionService.utilPayment()`):
   - Read the payer's bank account.
   - Validate sufficient funds.
   - Read the utility account by provider ID.
   - Debit the payer's account.
   - Create a transaction record with type `UTILITY_PAYMENT`.
4. Update local entity with `transactionId` and status `SUCCESS`.

### 4.5 Balance Validation Rules

**Location:** `core-banking-service` → `TransactionService.validateBalance()`

- Throws `InsufficientFundsException` if `actualBalance < 0` OR `actualBalance < requestedAmount`.

---

## 5. Integration Points

### 5.1 Keycloak (IAM)

- **Version:** 23.0.7
- **Connection:** `internet-banking-user-service` communicates via the Keycloak Admin Client SDK (`keycloak-admin-client:24.0.4`).
- **Configuration:** Server URL, realm, client ID, and client secret are loaded from externalized config properties (`app.config.keycloak.*`).
- **Singleton Pattern:** `KeycloakProperties` maintains a static singleton `Keycloak` instance (not thread-safe).
- **Operations:** Create user, update user (enable/verify), search user by email, read user by ID.
- **Realm Import:** A pre-configured realm JSON is volume-mounted from `docker-compose/keycloak/` into the Keycloak container.

### 5.2 RabbitMQ

- **Status:** Referenced in README but **not implemented** in the current codebase.
- **Planned Use:** Push notification messages for fund transfers and utility payments.

### 5.3 Zipkin (Distributed Tracing)

- **Version:** Zipkin 3 (Docker image)
- **Integration:** All business services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` dependencies.
- **Port:** 9411

### 5.4 Database Connections

| Service | Database | Driver |
|---|---|---|
| core-banking-service | MySQL (`banking_core_service`) | `mysql-connector-j:8.4.0` |
| internet-banking-user-service | MySQL (`internet_banking_user_service`) | `mysql-connector-j:8.4.0` |
| internet-banking-fund-transfer-service | MySQL (`internet_banking_fund_transfer_service`) | `mysql-connector-j:8.4.0` |
| internet-banking-utility-payment-service | MySQL (`internet_banking_utility_payment_service`) | `mysql-connector-j:8.4.0` |
| Keycloak | PostgreSQL (`keycloak`) | Built into Keycloak image |

Database connection strings and credentials are managed via the Spring Cloud Config Server (externalized Git-backed configuration).

### 5.5 Netflix Eureka

- All business services register with Eureka at startup.
- Feign clients use Eureka service names (`core-banking-service`) for load-balanced discovery rather than hardcoded URLs.

### 5.6 Spring Cloud Config Server

- **Git Repository:** `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Path:** `configuration/`
- **Branch:** `main`
- Each service uses a `bootstrap.yml` to point to the config server (`http://localhost:8090` for local dev, `http://internet-banking-config-server:8090` for Docker).

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Gradle** (Wrapper) with Spring Boot plugin `3.2.4` and Spring Dependency Management `1.1.4`.
- Each microservice is an independent Gradle project (no multi-module root build).
- The `gradle-git-properties` plugin (`2.4.2`) generates `git.properties` for build metadata (used in all services except service-registry).

### 6.2 Docker

Each service has its own `Dockerfile`:

```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh
RUN apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

- **Base Image:** Eclipse Temurin JRE 21 (Alpine).
- **wait-for-it.sh:** Bash script ensuring infrastructure dependencies (service registry, config server, MySQL) are healthy before the application starts.
- **Profile:** `docker` profile is activated via `-Dspring.profiles.active=docker`, which loads `bootstrap-docker.yml` pointing to Docker hostnames.

### 6.3 Docker Compose

Two compose files under `docker-compose/`:

1. **`docker-compose.yml`** — Full stack: all 6 microservices + Keycloak + PostgreSQL + MySQL + Zipkin.
2. **`docker-compose-support-apps.yml`** — Infrastructure only: Keycloak, PostgreSQL, MySQL, Zipkin, Config Server, Service Registry (for local development of business services).

**Custom Docker Network:** `javatodev_ib_network` (bridge, subnet `172.25.0.0/16`) with static IP assignments for each container.

### 6.4 Build & Deploy Steps

```bash
# 1. Build each service JAR
cd <service-dir> && ./gradlew clean build

# 2. Build Docker images
docker build -t javatodev/<service-name> .

# 3. Start everything
cd docker-compose && docker-compose up -d
```

### 6.5 Test Data

Flyway seeds the core banking database with:
- **4 test users** (Sam Silva, Guru Darmaraj, Ragu Sivaraj, Randor Manoon)
- **14 savings accounts** with balances ranging from 12,000 to 889,000.33
- **6 utility providers** (Vodafone, Verizon, Singtel, Hutch, Airtel, GIO)
- **Test credentials:** `ib_admin@javatodev.com` / `5V7huE3G86uB`

### 6.6 CI/CD

- No CI/CD pipeline definitions found in the repository (`.github/workflows/` is empty or removed).
- The `draw_io/` directory contains architecture diagrams.
- A Postman collection is available for manual API testing.
