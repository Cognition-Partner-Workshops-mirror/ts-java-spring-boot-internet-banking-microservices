# Knowledge Base — Internet Banking Microservices

## Architecture Overview

### Services

| # | Service | Directory | Port | Responsibility |
|---|---------|-----------|------|---------------|
| 1 | **Core Banking Service** | `core-banking-service/` | 8092 | Central banking core: manages users, bank accounts, utility accounts, and processes fund-transfer / utility-payment transactions against the database |
| 2 | **Fund Transfer Service** | `internet-banking-fund-transfer-service/` | 8084 | Accepts fund-transfer requests from clients, persists a local `FundTransferEntity`, delegates the actual balance mutation to Core Banking via Feign, and records the outcome |
| 3 | **User Service** | `internet-banking-user-service/` | 8083 | User registration & management: validates against Core Banking, provisions users in Keycloak, and stores a local `UserEntity` with status workflow (PENDING -> APPROVED) |
| 4 | **Utility Payment Service** | `internet-banking-utility-payment-service/` | 8085 | Accepts utility-payment requests, persists a local `UtilityPaymentEntity`, delegates the actual payment to Core Banking via Feign, and records the outcome |
| 5 | **API Gateway** | `internet-banking-api-gateway/` | 8082 | Spring Cloud Gateway — single entry point, OAuth2 resource-server (Keycloak JWT), route proxying to downstream services via Eureka, injects `X-Auth-Id` header via `GlobalFilter` |
| 6 | **Service Registry** | `internet-banking-service-registry/` | 8081 | Netflix Eureka server for service discovery |
| 7 | **Config Server** | `internet-banking-config-server/` | 8090 | Spring Cloud Config Server backed by a Git repository (`internet-banking-microservices-configurations`) |

### Communication Patterns

```
Client
  │
  ▼
API Gateway (8082)  ── OAuth2 JWT validation (Keycloak)
  │
  ├──► Fund Transfer Service (8084) ──Feign──► Core Banking Service (8092)
  ├──► User Service (8083)          ──Feign──► Core Banking Service (8092)
  ├──►                              ──REST──► Keycloak Admin API
  ├──► Utility Payment Service (8085)──Feign──► Core Banking Service (8092)
  └──► Core Banking Service (8092)
                                        │
                                        ▼
                                     MySQL (3306)
```

- **Synchronous (Feign):** Fund Transfer Service, User Service, and Utility Payment Service all use OpenFeign clients annotated with `@FeignClient(name = "core-banking-service")` to call Core Banking endpoints. Service resolution is via Eureka.
- **Gateway Routing:** The API Gateway routes requests to downstream services by service name. It extracts the authenticated principal and forwards it as the `X-Auth-Id` HTTP header.
- **Keycloak REST API:** The User Service uses the `keycloak-admin-client` library to create, read, and update users in Keycloak.

> **Note:** Despite the task description mentioning RabbitMQ, there is **no RabbitMQ dependency or messaging code** in the current codebase. All inter-service communication is synchronous via Feign/REST.

---

## Data Model

### Core Banking Service (MySQL — shared DB for all JPA services)

#### `banking_core_user`
| Field | Type | Notes |
|-------|------|-------|
| `id` | Long (PK, auto) | |
| `firstName` | String | |
| `lastName` | String | |
| `email` | String | |
| `identificationNumber` | String | Unique national/tax ID |
| `accounts` | OneToMany → `BankAccountEntity` | `mappedBy = "user"`, LAZY, CASCADE ALL |

#### `banking_core_bank_account`
| Field | Type | Notes |
|-------|------|-------|
| `id` | Long (PK, auto) | |
| `number` | String | Account number (unique lookup) |
| `type` | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` | |
| `status` | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` | |
| `availableBalance` | BigDecimal | |
| `actualBalance` | BigDecimal | |
| `user` | ManyToOne → `UserEntity` | FK `user_id` |

#### `banking_core_transaction`
| Field | Type | Notes |
|-------|------|-------|
| `id` | Long (PK, auto) | |
| `transactionId` | String | UUID |
| `referenceNumber` | String | Target account or reference |
| `amount` | BigDecimal | Negative for debits |
| `transactionType` | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` | |
| `account` | ManyToOne → `BankAccountEntity` | FK `bank_account_id` |

#### `banking_core_utility_account`
| Field | Type | Notes |
|-------|------|-------|
| `id` | Long (PK, auto) | |
| `number` | String | |
| `providerName` | String | Lookup key |

### Fund Transfer Service (own DB/schema)

#### `fund_transfer`
| Field | Type | Notes |
|-------|------|-------|
| `id` | Long (PK, auto) | |
| `transactionReference` | String | Set from Core Banking response |
| `fromAccount` | String | |
| `toAccount` | String | |
| `amount` | BigDecimal | |
| `status` | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` | |
| _Audit fields_ | `createdDate`, `createdBy`, `modifiedDate`, `modifiedBy`, `version` | Via `AuditAware` `@MappedSuperclass` |

### User Service (own DB/schema)

#### `user`
| Field | Type | Notes |
|-------|------|-------|
| `id` | Long (PK, auto) | |
| `authId` | String | Keycloak user ID |
| `identification` | String | Maps to Core Banking `identificationNumber` |
| `status` | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` | |
| _Audit fields_ | via `AuditAware` | |

### Utility Payment Service (own DB/schema)

#### `utility_payment`
| Field | Type | Notes |
|-------|------|-------|
| `id` | Long (PK, auto) | |
| `providerId` | Long | |
| `amount` | BigDecimal | |
| `referenceNumber` | String | |
| `account` | String | Source bank account |
| `transactionId` | String | From Core Banking response |
| `status` | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` | |
| _Audit fields_ | via `AuditAware` | |

### Database Topology

All four JPA services (`core-banking-service`, `internet-banking-fund-transfer-service`, `internet-banking-user-service`, `internet-banking-utility-payment-service`) connect to the **same MySQL instance** (`mysql_core_db:3306`) via Docker Compose. Each service uses JPA auto-DDL so tables are co-located in the same database. This is a **shared-database** pattern — services do not have isolated databases.

---

## API Surface Map

### Core Banking Service (`/api/v1/...`)

| Endpoint | Method | Request Body | Response | Controller |
|----------|--------|-------------|----------|------------|
| `/api/v1/account/bank-account/{account_number}` | GET | — | `BankAccount` (id, number, type, status, availableBalance, actualBalance) | `AccountController` |
| `/api/v1/account/util-account/{account_name}` | GET | — | `UtilityAccount` (id, number, providerName) | `AccountController` |
| `/api/v1/transaction/fund-transfer` | POST | `FundTransferRequest` {fromAccount, toAccount, amount} | `FundTransferResponse` {message, transactionId} | `TransactionController` |
| `/api/v1/transaction/util-payment` | POST | `UtilityPaymentRequest` {providerId, amount, referenceNumber, account} | `UtilityPaymentResponse` {message, transactionId} | `TransactionController` |
| `/api/v1/user/{identification}` | GET | — | `User` (id, firstName, lastName, email, identificationNumber, bankAccounts[]) | `UserController` |
| `/api/v1/user` | GET | — (Pageable query params) | `List<User>` | `UserController` |

### Fund Transfer Service (`/api/v1/transfer`)

| Endpoint | Method | Request Body | Response | Controller |
|----------|--------|-------------|----------|------------|
| `/api/v1/transfer` | POST | `FundTransferRequest` {fromAccount, toAccount, amount, authID} | `FundTransferResponse` {message, transactionId} | `FundTransferController` |
| `/api/v1/transfer` | GET | — (Pageable query params) | `List<FundTransfer>` | `FundTransferController` |

### User Service (`/api/v1/bank-users`)

| Endpoint | Method | Request Body | Response | Controller |
|----------|--------|-------------|----------|------------|
| `/api/v1/bank-users/register` | POST | `User` {email, identification, password} | `User` (id, email, identification, authId, status) | `UserController` |
| `/api/v1/bank-users/update/{id}` | PATCH | `UserUpdateRequest` {status} | `User` | `UserController` |
| `/api/v1/bank-users` | GET | — (Pageable query params) | `List<User>` | `UserController` |
| `/api/v1/bank-users/{id}` | GET | — | `User` | `UserController` |

### Utility Payment Service (`/api/v1/utility-payment`)

| Endpoint | Method | Request Body | Response | Controller |
|----------|--------|-------------|----------|------------|
| `/api/v1/utility-payment` | POST | `UtilityPaymentRequest` {providerId, amount, referenceNumber, account} | `UtilityPaymentResponse` {message, transactionId} | `UtilityPaymentController` |
| `/api/v1/utility-payment` | GET | — (Pageable query params) | `List<UtilityPayment>` | `UtilityPaymentController` |

### API Gateway Routes

The gateway proxies all requests to downstream services by their Eureka-registered names. It also exposes:
- `/actuator/**` endpoints (permitted without auth)
- User registration endpoint `/user/api/v1/bank-users/register` (permitted without auth)
- All other endpoints require a valid OAuth2 JWT token

---

## Business Logic Inventory

### Fund Transfer Flow

1. Client sends `POST /api/v1/transfer` to Fund Transfer Service
2. Service persists a `FundTransferEntity` with status `PENDING`
3. Service calls Core Banking `POST /api/v1/transaction/fund-transfer` via Feign
4. Core Banking's `TransactionService.fundTransfer()`:
   - Reads both from/to accounts
   - Validates sender has sufficient balance (`actualBalance >= amount`)
   - Calls `internalFundTransfer()`:
     - Deducts amount from sender's `actualBalance` and `availableBalance`
     - Creates debit `TransactionEntity` for sender
     - Adds amount to receiver's `actualBalance` and `availableBalance`
     - Creates credit `TransactionEntity` for receiver
   - Returns transaction ID
5. Fund Transfer Service updates local entity with `transactionReference` and status `SUCCESS`

**Business Rules:**
- Sender's `actualBalance` must be >= 0 AND >= transfer amount
- Both accounts must exist (throws `EntityNotFoundException`)
- Transaction is `@Transactional` at the Core Banking level

### Utility Payment Flow

1. Client sends `POST /api/v1/utility-payment` to Utility Payment Service
2. Service persists a `UtilityPaymentEntity` with status `PROCESSING`
3. Service calls Core Banking `POST /api/v1/transaction/util-payment` via Feign
4. Core Banking's `TransactionService.utilPayment()`:
   - Validates sender has sufficient balance
   - Looks up utility account by provider ID
   - Deducts amount from sender's balances
   - Records a `TransactionEntity` of type `UTILITY_PAYMENT`
   - Returns transaction ID
5. Utility Payment Service updates local entity with `transactionId` and status `SUCCESS`

### User Registration / Keycloak Sync

1. Client sends `POST /api/v1/bank-users/register` with email, identification, password
2. User Service checks Keycloak for existing user with that email (throws `UserAlreadyRegisteredException` if exists)
3. Calls Core Banking `GET /api/v1/user/{identification}` via Feign to validate the banking user exists
4. Validates the email matches the Core Banking record (throws `InvalidEmailException` if mismatch)
5. Creates a `UserRepresentation` in Keycloak (disabled, email not verified, with password credential)
6. If Keycloak returns 201, reads back the user to get the `authId`
7. Persists a local `UserEntity` with status `PENDING`

**User Approval:**
- `PATCH /api/v1/bank-users/update/{id}` with `{status: "APPROVED"}`
- Enables the Keycloak user and marks email as verified
- Updates local entity status

---

## Integration Points

### Keycloak (Identity Provider)

- **Library:** `keycloak-admin-client:24.0.4`
- **Configuration:** `KeycloakProperties` reads `app.config.keycloak.server-url`, `realm`, `clientId`, `client-secret` from Spring Cloud Config
- **Auth:** Client credentials grant (`grantType = "client_credentials"`)
- **Usage:** `KeycloakUserService` creates, reads, and updates users via the Keycloak Admin REST API
- **Docker:** Keycloak 23.0.7 with PostgreSQL 15 backend, port 8080, realm import from `docker-compose/keycloak/`

### MySQL

- **Driver:** `com.mysql:mysql-connector-j:8.4.0`
- **Docker:** Single MySQL instance (`mysql_core_db`), port 3306, root password in docker-compose
- **Usage:** All four JPA services share this instance; connection details come from Spring Cloud Config

### Zipkin (Distributed Tracing)

- **Libraries:** `micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`, `feign-micrometer`
- **Docker:** Zipkin 3, port 9411
- **Coverage:** Tracing dependencies are present in API Gateway, Fund Transfer Service, User Service, and Utility Payment Service. Core Banking Service does **not** include tracing libraries.

### Spring Cloud Config Server

- **Port:** 8090
- **Backend:** Git repository at `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` (branch `main`, path `configuration/`)
- **Bootstrap:** Each service has `bootstrap.yml` (localhost), `bootstrap-dev.yml` (192.168.1.5), and `bootstrap-docker.yml` (config server container name)

### Eureka Service Registry

- **Port:** 8081
- **Usage:** All business services register with Eureka; Feign clients resolve service names through Eureka

---

## Build & Deployment

### Gradle Multi-Project Structure

Each service is an **independent Gradle project** (separate `build.gradle`, `settings.gradle`, `gradlew`). There is **no top-level Gradle wrapper or multi-project build** — each service must be built individually.

| Service | Spring Boot | Spring Cloud | Java |
|---------|-------------|-------------|------|
| All services | 3.2.4 | 2023.0.0 | 21 |

Common plugins:
- `org.springframework.boot` 3.2.4
- `io.spring.dependency-management` 1.1.4
- `com.gorylenko.gradle-git-properties` 2.4.2 (on fund-transfer, user, utility-payment, config-server)

### Docker

Each service has a `Dockerfile` using `eclipse-temurin:21.0.2_13-jre-alpine` with `wait-for-it.sh` for startup ordering.

### Docker Compose Topology (`docker-compose/docker-compose.yml`)

```
                    ┌─────────────────────────┐
                    │   Custom Bridge Network  │
                    │   172.25.0.0/16          │
                    └─────────────────────────┘
                              │
  ┌───────────────────────────┼───────────────────────────┐
  │                           │                           │
  ▼                           ▼                           ▼
Zipkin (172.25.0.12)   Keycloak (172.25.0.11)   MySQL (172.25.0.9)
  :9411                  :8080                    :3306
                           │
                    PostgreSQL (172.25.0.10)
                      :5432

Config Server (172.25.0.8) :8090
Service Registry (172.25.0.7) :8081

API Gateway (172.25.0.6) :8082
  waits-for: service-registry, config-server

User Service (172.25.0.5) :8083
  waits-for: service-registry, config-server, mysql

Fund Transfer Service (172.25.0.4) :8084
  waits-for: service-registry, config-server, mysql

Utility Payment Service (172.25.0.3) :8085
  waits-for: service-registry, config-server, mysql

Core Banking Service (172.25.0.2) :8092
  waits-for: service-registry, config-server, mysql
```

Startup ordering is enforced via `wait-for-it.sh` in each container's entrypoint. All containers are on a single custom bridge network with static IP assignments.
