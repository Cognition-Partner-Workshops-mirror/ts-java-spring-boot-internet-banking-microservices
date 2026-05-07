# Application Knowledge Base

## 1. Architecture Overview

### System Summary

Internet Banking Concept is a Java 21 / Spring Boot 3.2.4 microservices application implementing core internet banking operations: user registration, fund transfers, and utility payments. It follows a standard Spring Cloud architecture with centralized configuration, service discovery, and an API gateway.

### Microservices Inventory

| Service | Port | Purpose | Database |
|---|---|---|---|
| **internet-banking-config-server** | 8090 | Centralized configuration via Spring Cloud Config (Git-backed) | None |
| **internet-banking-service-registry** | 8081 | Service discovery via Netflix Eureka Server | None |
| **internet-banking-api-gateway** | 8082 | API Gateway via Spring Cloud Gateway; OAuth2 security enforcement | None |
| **internet-banking-user-service** | 8083 | User registration, approval, and management; Keycloak integration | MySQL (`banking_core_user_service`) |
| **internet-banking-fund-transfer-service** | 8084 | Fund transfer orchestration between accounts | MySQL (`banking_core_fund_transfer_service`) |
| **internet-banking-utility-payment-service** | 8085 | Utility bill payment processing | MySQL (`banking_core_utility_payment_service`) |
| **core-banking-service** | 8092 | Core banking ledger: accounts, users, transactions, balances | MySQL (`banking_core_service`) |

> **Note:** A Notification Service is referenced in documentation but **not yet implemented** (marked PENDING).

### Communication Patterns

```
                          +-----------------------+
                          | Spring Cloud Config   |
                          | Server (Git-backed)   |
                          +----------+------------+
                                     |
                                     | config fetch on startup
                                     v
+--------+    OAuth2/JWT    +--------+--------+    Eureka    +------------------+
| Client | ───────────────> | API Gateway     | <──────────> | Service Registry |
+--------+                  | (port 8082)     |              | (Eureka Server)  |
                            +---+----+----+---+              +------------------+
                                |    |    |
               +----------------+    |    +----------------+
               |                     |                     |
               v                     v                     v
    +----------+------+  +----------+--------+  +----------+----------+
    | User Service    |  | Fund Transfer     |  | Utility Payment     |
    | (port 8083)     |  | Service (8084)    |  | Service (8085)      |
    +--------+--------+  +--------+----------+  +--------+------------+
             |                    |                       |
             |  OpenFeign        |  OpenFeign             |  OpenFeign
             v                    v                       v
         +---+--------------------+------------------------+---+
         |              Core Banking Service (port 8092)       |
         +-----------------------------------------------------+
```

**Inter-service communication:**

| From | To | Protocol | Mechanism |
|---|---|---|---|
| User Service | Core Banking Service | HTTP/REST | OpenFeign (`BankingCoreRestClient`) |
| Fund Transfer Service | Core Banking Service | HTTP/REST | OpenFeign (`BankingCoreFeignClient`) |
| Utility Payment Service | Core Banking Service | HTTP/REST | OpenFeign (`BankingCoreRestClient`) |
| API Gateway | All downstream services | HTTP | Spring Cloud Gateway routing via Eureka |
| All services | Config Server | HTTP | Spring Cloud Config bootstrap |
| All services | Service Registry | HTTP | Eureka client registration |

**Authentication flow:**
1. API Gateway acts as OAuth2 Resource Server validating JWTs against Keycloak's JWK endpoint
2. Gateway extracts authenticated principal and forwards it as `X-Auth-Id` HTTP header to downstream services
3. Downstream services read `X-Auth-Id` via `AppAuthUserFilter` servlet filter and store it in `ApiRequestContextHolder` (thread-local)
4. User registration endpoint (`/user/api/v1/bank-users/register`) is explicitly permitted without authentication

### Infrastructure Components

| Component | Technology | Purpose | Docker IP |
|---|---|---|---|
| Identity Provider | Keycloak 23.0.7 | OAuth2/OIDC authentication, user management | 172.25.0.11 |
| Keycloak Database | PostgreSQL 15 | Keycloak persistence | 172.25.0.10 |
| Application Database | MySQL (custom image) | All service data storage | 172.25.0.9 |
| Distributed Tracing | Zipkin 3 | Request tracing across services | 172.25.0.12 |
| Message Broker | RabbitMQ | Notification events (referenced but not implemented) | Not in compose |

---

## 2. Data Model Documentation

### Core Banking Service

#### `banking_core_user`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Unique user ID |
| `first_name` | VARCHAR(255) | | User's first name |
| `last_name` | VARCHAR(255) | | User's last name |
| `email` | VARCHAR(255) | | User's email address |
| `identification_number` | VARCHAR(255) | | National ID / identification number |

#### `banking_core_account`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Unique account ID |
| `number` | VARCHAR(255) | | Account number (e.g. `100015003000`) |
| `type` | VARCHAR(255) | | Enum: `SAVINGS_ACCOUNT` |
| `status` | VARCHAR(255) | | Enum: `ACTIVE` |
| `available_balance` | DECIMAL(19,2) | | Available balance for transactions |
| `actual_balance` | DECIMAL(19,2) | | Ledger/actual balance |
| `user_id` | BIGINT | FK -> `banking_core_user.id` | Owning user |

#### `banking_core_transaction`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Unique transaction ID |
| `amount` | DECIMAL(19,2) | | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | NOT NULL | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | NOT NULL | Reference (destination account number or utility ref) |
| `transaction_id` | VARCHAR(50) | NOT NULL | UUID transaction identifier |
| `account_id` | BIGINT | FK -> `banking_core_account.id` | Associated account |

#### `banking_core_utility_account`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Unique utility account ID |
| `number` | VARCHAR(255) | | Utility provider account number |
| `provider_name` | VARCHAR(255) | | Provider name (e.g. `VODAFONE`, `VERIZON`) |

**Relationships:**
- `banking_core_user` 1:N `banking_core_account` (via `user_id` FK)
- `banking_core_account` 1:N `banking_core_transaction` (via `account_id` FK)
- `banking_core_utility_account` is standalone (no FK relationships)

### Internet Banking User Service

#### `user`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK) | Auto-generated ID |
| `auth_id` | VARCHAR | Keycloak user ID reference |
| `identification` | VARCHAR | National ID linking to core banking user |
| `status` | VARCHAR (Enum) | `PENDING`, `APPROVED` |
| `created_by` | VARCHAR | Audit: creator |
| `created_date` | TIMESTAMP | Audit: creation time |
| `last_modified_by` | VARCHAR | Audit: last modifier |
| `last_modified_date` | TIMESTAMP | Audit: last modification time |

*Extends `AuditAware` base class for JPA auditing.*

### Internet Banking Fund Transfer Service

#### `fund_transfer`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK) | Auto-generated ID |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `transaction_reference` | VARCHAR | UUID from core banking response |
| `status` | VARCHAR (Enum) | `PENDING`, `SUCCESS` |
| `created_by` / `created_date` / `last_modified_by` / `last_modified_date` | | JPA audit fields |

### Internet Banking Utility Payment Service

#### `utility_payment`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK) | Auto-generated ID |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Customer reference number |
| `account` | VARCHAR | Source bank account number |
| `transaction_id` | VARCHAR | UUID from core banking response |
| `status` | VARCHAR (Enum) | `PROCESSING`, `SUCCESS` |
| `created_by` / `created_date` / `last_modified_by` / `last_modified_date` | | JPA audit fields |

---

## 3. API Surface Map

### Core Banking Service (port 8092)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | - | `BankAccount` | Retrieve bank account by account number |
| `GET` | `/api/v1/account/util-account/{account_name}` | - | `UtilityAccount` | Retrieve utility account by provider name |
| `GET` | `/api/v1/user/{identification}` | - | `User` | Read user by identification number |
| `GET` | `/api/v1/user` | Pageable query params | `List<User>` | Read paginated users |
| `POST` | `/api/v1/transaction/fund-transfer` | `FundTransferRequest` | `FundTransferResponse` | Process fund transfer in core ledger |
| `POST` | `/api/v1/transaction/util-payment` | `UtilityPaymentRequest` | `UtilityPaymentResponse` | Process utility payment in core ledger |

**DTOs:**

```
FundTransferRequest { fromAccount: String, toAccount: String, amount: BigDecimal }
FundTransferResponse { message: String, transactionId: String }
UtilityPaymentRequest { account: String, providerId: Long, amount: BigDecimal, referenceNumber: String }
UtilityPaymentResponse { message: String, transactionId: String }
BankAccount { number: String, type: AccountType, status: AccountStatus, availableBalance: BigDecimal, actualBalance: BigDecimal }
UtilityAccount { id: Long, number: String, providerName: String }
User { id: Long, firstName: String, lastName: String, email: String, identificationNumber: String, accounts: List<BankAccount> }
```

### Internet Banking User Service (port 8083)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | `User` | `User` | Register new banking user (creates Keycloak user) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `UserUpdateRequest` | `User` | Update user (e.g. approve registration) |
| `GET` | `/api/v1/bank-users` | Pageable query params | `List<User>` | List all users (enriched from Keycloak) |
| `GET` | `/api/v1/bank-users/{id}` | - | `User` | Read user by database ID |

**DTOs:**

```
User { id: Long, authId: String, identification: String, email: String, password: String, status: Status }
UserUpdateRequest { status: Status }
Status: PENDING | APPROVED
```

### Internet Banking Fund Transfer Service (port 8084)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | `FundTransferRequest` | `FundTransferResponse` | Initiate fund transfer |
| `GET` | `/api/v1/transfer` | Pageable query params | `List<FundTransfer>` | List all fund transfers |

**DTOs:**

```
FundTransferRequest { fromAccount: String, toAccount: String, amount: BigDecimal }
FundTransferResponse { message: String, transactionId: String }
FundTransfer { id: Long, fromAccount: String, toAccount: String, amount: BigDecimal, status: TransactionStatus, transactionReference: String }
```

### Internet Banking Utility Payment Service (port 8085)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | `UtilityPaymentRequest` | `UtilityPaymentResponse` | Process utility payment |
| `GET` | `/api/v1/utility-payment` | Pageable query params | `List<UtilityPayment>` | List all utility payments |

**DTOs:**

```
UtilityPaymentRequest { providerId: Long, amount: BigDecimal, referenceNumber: String, account: String }
UtilityPaymentResponse { message: String, transactionId: String }
UtilityPayment { id: Long, providerId: Long, amount: BigDecimal, referenceNumber: String, account: String, transactionId: String, status: TransactionStatus }
```

### API Gateway Route Prefixes

All downstream service APIs are accessed through the gateway (port 8082) with the following route prefixes:

| Prefix | Target Service |
|---|---|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |
| `/banking-core/**` | core-banking-service |

---

## 4. Key Business Logic Inventory

### Fund Transfer Flow

1. **Client** sends `POST /fund-transfer/api/v1/transfer` through API Gateway
2. **Fund Transfer Service** saves a `FundTransferEntity` with status `PENDING`
3. **Fund Transfer Service** calls **Core Banking Service** via OpenFeign: `POST /api/v1/transaction/fund-transfer`
4. **Core Banking Service** `TransactionService.fundTransfer()`:
   - Reads both source and destination `BankAccount` records
   - **Validates** source account has sufficient balance (`actualBalance >= amount`)
   - Debits source account: `actualBalance -= amount`, `availableBalance = actualBalance - amount`
   - Credits destination account: `actualBalance += amount`, `availableBalance = actualBalance + amount`
   - Creates two `TransactionEntity` records (debit and credit) with same `transactionId`
   - Returns `transactionId` to caller
5. **Fund Transfer Service** updates entity with `transactionReference` and sets status to `SUCCESS`

**Business rules:**
- Balance validation: `actualBalance >= 0 AND actualBalance >= transferAmount`
- Transfers are `@Transactional` at the core banking level
- No idempotency mechanism (duplicate submissions will create duplicate transfers)
- No amount limits or rate limiting

### Utility Payment Flow

1. **Client** sends `POST /utility-payment/api/v1/utility-payment` through API Gateway
2. **Utility Payment Service** saves a `UtilityPaymentEntity` with status `PROCESSING`
3. **Utility Payment Service** calls **Core Banking Service** via OpenFeign: `POST /api/v1/transaction/util-payment`
4. **Core Banking Service** `TransactionService.utilPayment()`:
   - Reads source `BankAccount` and validates balance
   - Reads `UtilityAccount` by provider ID
   - Debits source account balance
   - Creates a `TransactionEntity` record with type `UTILITY_PAYMENT`
   - Returns `transactionId`
5. **Utility Payment Service** updates entity with `transactionId` and sets status to `SUCCESS`

**Known bug:** In `utilPayment()`, `availableBalance` is set to `actualBalance - amount` *after* `actualBalance` was already decremented, resulting in a double-deduction of available balance.

### User Registration Flow

1. **Client** sends `POST /user/api/v1/bank-users/register` (no auth required)
2. **User Service** checks Keycloak for existing user with same email
3. **User Service** calls **Core Banking Service** via OpenFeign to verify identification exists in core banking
4. **User Service** validates email matches core banking record
5. **User Service** creates Keycloak user (disabled, email unverified)
6. **User Service** saves local `UserEntity` with status `PENDING` and Keycloak `authId`
7. **Admin** approves user via `PATCH /user/api/v1/bank-users/update/{id}` with `{ "status": "APPROVED" }`
8. On approval, User Service enables the Keycloak user and sets `emailVerified = true`

---

## 5. Integration Points

### Keycloak (Identity Provider)

- **Version:** 23.0.7
- **Connection:** User Service connects via `keycloak-admin-client:24.0.4`
- **Auth type:** Client credentials grant (`client_credentials`)
- **Configuration:** `app.config.keycloak.server-url`, `realm`, `clientId`, `client-secret` (via Spring Cloud Config)
- **Operations:** Create user, update user (enable/verify), search by email, read by ID
- **Realm:** Pre-configured via `realm-export.json` import on Keycloak startup

### MySQL

- **Connection:** All four business services connect to the same MySQL instance with separate databases
- **Databases:** `banking_core_service`, `banking_core_fund_transfer_service`, `banking_core_user_service`, `banking_core_utility_payment_service`
- **User:** `javatodev_development` (created via `privileges.sql`)
- **Schema management:** Core Banking Service uses Flyway migrations; other services use JPA `ddl-auto`

### Zipkin (Distributed Tracing)

- **Version:** 3 (via Docker image `openzipkin/zipkin:3`)
- **Integration:** `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` in all services
- **Port:** 9411

### RabbitMQ (Message Broker)

- **Status:** Referenced in README but **not configured** in docker-compose or any service dependencies
- **Intended use:** Push notification events from Fund Transfer and Utility Payment services to a Notification Service (not implemented)

### Spring Cloud Config Server

- **Git repository:** `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch:** `main`
- **Search path:** `configuration`
- **Profiles:** `docker` profile used in Docker Compose deployments

### Eureka Service Registry

- **Type:** Netflix Eureka Server
- **All business services** register as Eureka clients
- **API Gateway** discovers downstream services via Eureka for dynamic routing

---

## 6. Build and Deployment Pipeline Summary

### Build System

- **Build tool:** Gradle (per-service, no multi-project build)
- **Java version:** 21 (source compatibility)
- **Spring Boot:** 3.2.4
- **Spring Cloud:** 2023.0.0
- **Plugin:** `com.gorylenko.gradle-git-properties` for Git info in actuator

Each service is an independent Gradle project with its own `build.gradle` and `settings.gradle`. There is **no root-level Gradle wrapper** or multi-project build.

### Docker

- **Base image:** `eclipse-temurin:21.0.2_13-jre-alpine`
- **Pattern:** Each service has a `Dockerfile` that copies the fat JAR from `build/libs/` and uses `wait-for-it.sh` for startup ordering
- **Startup ordering:** Services wait for Service Registry and Config Server (and MySQL where applicable) before starting via `wait-for-it.sh` with 50-second timeouts

### Docker Compose

Two compose files in `docker-compose/`:

| File | Contents |
|---|---|
| `docker-compose.yml` | Full stack: all infrastructure + all 6 microservices |
| `docker-compose-support-apps.yml` | Infrastructure only: Zipkin, Keycloak, PostgreSQL, MySQL, Config Server, Service Registry |

**Network:** Custom bridge network `javatodev_ib_network` with subnet `172.25.0.0/16` and static IP assignments per container.

### CI/CD

- **GitHub Actions:** No workflows present (`.github/` contains only `FUNDING.yml`)
- **No automated CI/CD pipeline** is configured in the repository

### Test Infrastructure

| Service | Test Files | Test Type |
|---|---|---|
| Core Banking Service | `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest` | Unit tests (Mockito, JUnit 5) |
| All other services | `*ApplicationTests` only | Spring context load tests only |

- Test database: H2 in-memory (for core-banking-service)
- Flyway disabled in test profile
- No integration tests, contract tests, or end-to-end tests
