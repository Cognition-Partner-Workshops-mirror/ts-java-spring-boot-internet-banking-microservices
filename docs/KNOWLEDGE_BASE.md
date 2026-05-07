# Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Summary

The Internet Banking system is a Java 21 / Spring Boot 3.2.4 microservices application that simulates core internet banking operations: user registration, fund transfers, and utility payments. It follows a Spring Cloud architecture with centralized configuration, service discovery, an API gateway, and distributed tracing.

### 1.2 Microservices Inventory

| Service | Port | Description | Database |
|---|---|---|---|
| **core-banking-service** | 8092 | Core banking engine — accounts, users, transactions | `banking_core_service` (MySQL) |
| **internet-banking-user-service** | 8083 | User registration/management with Keycloak integration | `banking_core_user_service` (MySQL) |
| **internet-banking-fund-transfer-service** | 8084 | Fund transfer orchestration between accounts | `banking_core_fund_transfer_service` (MySQL) |
| **internet-banking-utility-payment-service** | 8085 | Utility bill payment processing | `banking_core_utility_payment_service` (MySQL) |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway — routing, security, auth | None |
| **internet-banking-service-registry** | 8081 | Netflix Eureka service registry | None |
| **internet-banking-config-server** | 8090 | Spring Cloud Config Server (Git-backed) | None |

### 1.3 Communication Patterns

```
                          ┌───────────────┐
                          │   Keycloak    │
                          │  (Auth/IdP)   │
                          └──────┬────────┘
                                 │ OAuth2 / OIDC
                                 ▼
┌──────────┐    HTTP    ┌────────────────────┐
│  Client   │──────────►│  API Gateway       │
└──────────┘            │  (Spring Cloud)    │
                        └──┬───┬───┬────────┘
                           │   │   │
              ┌────────────┘   │   └────────────┐
              ▼                ▼                 ▼
     ┌────────────┐  ┌──────────────┐  ┌──────────────────┐
     │ User       │  │ Fund Transfer│  │ Utility Payment  │
     │ Service    │  │ Service      │  │ Service          │
     └─────┬──────┘  └──────┬───────┘  └──────┬───────────┘
           │                │                  │
           │   OpenFeign    │   OpenFeign      │   OpenFeign
           └───────┐        │        ┌─────────┘
                   ▼        ▼        ▼
              ┌──────────────────────────┐
              │   Core Banking Service   │
              └──────────┬───────────────┘
                         │
                    ┌────┴────┐
                    │  MySQL  │
                    └─────────┘
```

- **Client → API Gateway**: All external traffic enters through the gateway on port 8082.
- **Gateway → Downstream Services**: The gateway routes requests using Eureka service discovery and injects an `X-Auth-Id` header from the JWT principal.
- **Business Services → Core Banking**: The user, fund-transfer, and utility-payment services call core-banking-service via **Spring Cloud OpenFeign** (service-name-based discovery).
- **All Services → Service Registry**: Every service registers with Eureka on startup.
- **All Services → Config Server**: Configuration is fetched from a centralized Git-backed Spring Cloud Config Server at bootstrap time.

### 1.4 Infrastructure Components

| Component | Technology | Purpose |
|---|---|---|
| Service Registry | Netflix Eureka | Service discovery |
| API Gateway | Spring Cloud Gateway | Routing, security enforcement |
| Config Server | Spring Cloud Config | Centralized Git-backed configuration |
| Identity Provider | Keycloak 23.0.7 | OAuth2/OIDC authentication, user realm management |
| Database | MySQL 8.4.0 | Persistent storage for all business services |
| Distributed Tracing | Zipkin 3 + Micrometer Brave | Request tracing across services |
| Container Runtime | Docker / Docker Compose | Local development and deployment |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service

#### `banking_core_user` (UserEntity)

| Field | Type | Notes |
|---|---|---|
| `id` | Long (PK, auto) | |
| `firstName` | String | |
| `lastName` | String | |
| `email` | String | |
| `identificationNumber` | String | National ID / passport |

#### `banking_core_account` (BankAccountEntity)

| Field | Type | Notes |
|---|---|---|
| `id` | Long (PK, auto) | |
| `number` | String | Account number (unique lookup) |
| `type` | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` | |
| `status` | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` | |
| `availableBalance` | BigDecimal | |
| `actualBalance` | BigDecimal | |
| `user_id` | FK → `banking_core_user.id` | ManyToOne |

#### `banking_core_transaction` (TransactionEntity)

| Field | Type | Notes |
|---|---|---|
| `id` | Long (PK, auto) | |
| `amount` | BigDecimal | Negative for debits, positive for credits |
| `transactionType` | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` | |
| `referenceNumber` | String | Target account number or utility reference |
| `transactionId` | String (UUID) | Unique transaction identifier |
| `account_id` | FK → `banking_core_account.id` | OneToOne (CascadeType.ALL) |

#### `banking_core_utility_account` (UtilityAccountEntity)

| Field | Type | Notes |
|---|---|---|
| `id` | Long (PK, auto) | |
| `number` | String | Provider account number |
| `providerName` | String | Utility provider name (unique lookup) |

### 2.2 Internet Banking User Service

#### `user` (UserEntity extends AuditAware)

| Field | Type | Notes |
|---|---|---|
| `id` | Long (PK, auto) | |
| `authId` | String | Keycloak user ID |
| `identification` | String | Links to core banking user identification |
| `status` | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` | |
| `createdDate` | Instant | Audit field |
| `createdBy` | String | Audit field |
| `modifiedDate` | Instant | Audit field |
| `modifiedBy` | String | Audit field |
| `version` | long | Optimistic locking |

### 2.3 Fund Transfer Service

#### `fund_transfer` (FundTransferEntity extends AuditAware)

| Field | Type | Notes |
|---|---|---|
| `id` | Long (PK, auto) | |
| `transactionReference` | String | From core banking response |
| `fromAccount` | String | Source account number |
| `toAccount` | String | Destination account number |
| `amount` | BigDecimal | |
| `status` | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` | |
| Audit fields | (inherited) | createdDate, createdBy, modifiedDate, modifiedBy, version |

### 2.4 Utility Payment Service

#### `utility_payment` (UtilityPaymentEntity extends AuditAware)

| Field | Type | Notes |
|---|---|---|
| `id` | Long (PK, auto) | |
| `providerId` | Long | Utility provider ID |
| `amount` | BigDecimal | |
| `referenceNumber` | String | Bill reference |
| `account` | String | Payer's bank account number |
| `transactionId` | String | From core banking response |
| `status` | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` | |
| Audit fields | (inherited) | createdDate, createdBy, modifiedDate, modifiedBy, version |

---

## 3. API Surface Map

### 3.1 API Gateway Routes (Port 8082)

All external requests are routed through the gateway. Paths are prefixed by service:

| Prefix | Target Service |
|---|---|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |
| `/banking-core/**` | core-banking-service |

**Security**: All endpoints require a valid OAuth2 JWT except:
- `POST /user/api/v1/bank-users/register` (public)
- `/actuator/**` endpoints (public)

### 3.2 Core Banking Service (Port 8092)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | — | `BankAccount` | Get bank account by number |
| `GET` | `/api/v1/account/util-account/{account_name}` | — | `UtilityAccount` | Get utility account by provider name |
| `GET` | `/api/v1/user/{identification}` | — | `User` | Get user by identification number |
| `GET` | `/api/v1/user` | Pageable params | `List<User>` | List users (paginated) |
| `POST` | `/api/v1/transaction/fund-transfer` | `FundTransferRequest` | `FundTransferResponse` | Process fund transfer |
| `POST` | `/api/v1/transaction/util-payment` | `UtilityPaymentRequest` | `UtilityPaymentResponse` | Process utility payment |

**Request/Response Shapes:**

```
FundTransferRequest { fromAccount: String, toAccount: String, amount: BigDecimal }
FundTransferResponse { message: String, transactionId: String }

UtilityPaymentRequest { providerId: Long, amount: BigDecimal, referenceNumber: String, account: String }
UtilityPaymentResponse { message: String, transactionId: String }

BankAccount { id: Long, number: String, type: AccountType, status: AccountStatus, availableBalance: BigDecimal, actualBalance: BigDecimal, user: User }
User { id: Long, firstName: String, lastName: String, email: String, identificationNumber: String, bankAccounts: List<BankAccount> }
UtilityAccount { id: Long, number: String, providerName: String }
```

### 3.3 Internet Banking User Service (Port 8083)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | `User` | `User` | Register new banking user |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `UserUpdateRequest` | `User` | Update user (approve/disable) |
| `GET` | `/api/v1/bank-users` | Pageable params | `List<User>` | List all users (paginated) |
| `GET` | `/api/v1/bank-users/{id}` | — | `User` | Get user by ID |

**Request/Response Shapes:**

```
User { id: Long, email: String, identification: String, password: String, authId: String, status: Status }
UserUpdateRequest { status: Status }
Status: PENDING | APPROVED | DISABLED | BLACKLIST
```

### 3.4 Fund Transfer Service (Port 8084)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | `FundTransferRequest` | `FundTransferResponse` | Initiate fund transfer |
| `GET` | `/api/v1/transfer` | Pageable params | `List<FundTransfer>` | List fund transfers (paginated) |

**Request/Response Shapes:**

```
FundTransferRequest { fromAccount: String, toAccount: String, amount: BigDecimal, authID: String }
FundTransferResponse { message: String, transactionId: String }
FundTransfer { id: Long, transactionReference: String, status: String, fromAccount: String, toAccount: String, amount: BigDecimal }
```

### 3.5 Utility Payment Service (Port 8085)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | `UtilityPaymentRequest` | `UtilityPaymentResponse` | Process utility payment |
| `GET` | `/api/v1/utility-payment` | Pageable params | `List<UtilityPayment>` | List utility payments (paginated) |

**Request/Response Shapes:**

```
UtilityPaymentRequest { providerId: Long, amount: BigDecimal, referenceNumber: String, account: String }
UtilityPaymentResponse { message: String, transactionId: String }
```

### 3.6 Service Registry (Port 8081)

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/` | Eureka dashboard |
| `GET` | `/eureka/apps` | List registered services |

### 3.7 Config Server (Port 8090)

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/{application}/{profile}` | Fetch configuration for a service/profile |
| `GET` | `/{application}-{profile}.yml` | Raw YAML configuration |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

**Location**: `internet-banking-user-service` → `UserService.createUser()`

1. Check if email is already registered in Keycloak; if so, throw `UserAlreadyRegisteredException`.
2. Call core-banking-service via Feign to verify the user exists by identification number.
3. Validate that the email matches the core banking record; if not, throw `InvalidEmailException`.
4. Create the user in Keycloak (disabled, email unverified) with provided password.
5. If Keycloak returns 201, persist the user locally with `PENDING` status and the Keycloak `authId`.
6. If user not found in core banking, throw `InvalidBankingUserException`.

### 4.2 User Approval Flow

**Location**: `internet-banking-user-service` → `UserService.updateUser()`

1. Find user by ID; throw `EntityNotFoundException` if missing.
2. If new status is `APPROVED`, enable the user and mark email as verified in Keycloak.
3. Persist the updated status.

### 4.3 Fund Transfer Flow

**Location**: `internet-banking-fund-transfer-service` → `FundTransferService.fundTransfer()`

1. Save a new `FundTransferEntity` with status `PENDING`.
2. Call core-banking-service's `/api/v1/transaction/fund-transfer` via Feign.
3. **Core Banking** (`TransactionService.fundTransfer()`):
   a. Read both source and destination bank accounts.
   b. Validate source account has sufficient balance (`actualBalance >= amount`).
   c. Debit source account (`actualBalance - amount`, `availableBalance - amount`).
   d. Record debit transaction entry.
   e. Credit destination account (`actualBalance + amount`, `availableBalance + amount`).
   f. Record credit transaction entry.
   g. Return transaction ID.
4. Update fund transfer entity with transaction reference and `SUCCESS` status.

### 4.4 Utility Payment Flow

**Location**: `internet-banking-utility-payment-service` → `UtilityPaymentService.utilPayment()`

1. Save a new `UtilityPaymentEntity` with status `PROCESSING`.
2. Call core-banking-service's `/api/v1/transaction/util-payment` via Feign.
3. **Core Banking** (`TransactionService.utilPayment()`):
   a. Read the payer's bank account.
   b. Validate sufficient balance.
   c. Read the utility provider account.
   d. Debit the payer's bank account.
   e. Record the transaction entry with `UTILITY_PAYMENT` type.
   f. Return transaction ID.
4. Update utility payment entity with transaction ID and `SUCCESS` status.

### 4.5 Balance Validation Rules

**Location**: `core-banking-service` → `TransactionService.validateBalance()`

- Throws `InsufficientFundsException` if:
  - `actualBalance < 0`, OR
  - `actualBalance < requestedAmount`

---

## 5. Integration Points

### 5.1 Keycloak (Identity Provider)

- **Version**: 23.0.7
- **Connection**: `internet-banking-user-service` connects via `keycloak-admin-client:24.0.4`
- **Configuration**: `app.config.keycloak.server-url`, `realm`, `clientId`, `client-secret`
- **Grant Type**: `client_credentials`
- **Operations**: Create user, update user (enable/disable), search by email, read by auth ID
- **Gateway Integration**: API Gateway validates JWT tokens using `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`
- **Realm Data**: Pre-loaded via `realm-export.json` volume mount in Docker Compose

### 5.2 RabbitMQ

- **Status**: Referenced in architecture docs but **NOT implemented** in current codebase.
- **Intended Use**: Push notification messages from fund-transfer and utility-payment services.
- **Notification Service**: Listed as "PENDING Development" in README.

### 5.3 Zipkin (Distributed Tracing)

- **Version**: Zipkin 3
- **Port**: 9411
- **Integration**: All services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` dependencies.
- **Configuration**: Managed via centralized config server properties.

### 5.4 Database Connections

- **Engine**: MySQL 8.4.0
- **Driver**: `com.mysql:mysql-connector-j:8.4.0`
- **Schema Migration**: Flyway (`flyway-core:10.12.0`, `flyway-mysql:10.12.0`) — only in core-banking-service.
- **Databases**:
  - `banking_core_service` — core-banking-service
  - `banking_core_user_service` — internet-banking-user-service
  - `banking_core_fund_transfer_service` — internet-banking-fund-transfer-service
  - `banking_core_utility_payment_service` — internet-banking-utility-payment-service
- **User**: `javatodev_development` with broad privileges on all databases.

### 5.5 Spring Cloud Config (Git-backed)

- **Repository**: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch**: `main`
- **Search Path**: `configuration/`
- **Bootstrap**: Each service fetches its configuration at startup using its `spring.application.name`.

### 5.6 Netflix Eureka (Service Discovery)

- **Server Port**: 8081
- **Registration**: All business services register on startup.
- **Discovery**: OpenFeign clients use service names (e.g., `core-banking-service`) for routing.

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build Tool**: Gradle (per-service, no multi-project build)
- **Java Version**: 21 (Eclipse Temurin)
- **Spring Boot**: 3.2.4
- **Spring Cloud**: 2023.0.0
- **Each service** is an independent Gradle project with its own `build.gradle`, `settings.gradle`, and Gradle wrapper.
- **Git Properties Plugin**: `com.gorylenko.gradle-git-properties:2.4.2` generates `git.properties` at build time.

### 6.2 Docker Setup

- **Base Image**: `eclipse-temurin:21.0.2_13-jre-alpine`
- **Each service** has a `Dockerfile` that:
  1. Copies the built JAR as `app.jar`
  2. Copies `wait-for-it.sh` for dependency ordering
  3. Installs bash (Alpine)
  4. Runs with `-Dspring.profiles.active=docker`

### 6.3 Docker Compose

Two compose files in `docker-compose/`:

1. **`docker-compose.yml`** — Full stack: all 7 services + MySQL + Keycloak + PostgreSQL (for Keycloak) + Zipkin
2. **`docker-compose-support-apps.yml`** — Infrastructure only: Config Server, Service Registry, MySQL, Keycloak, PostgreSQL, Zipkin

**Network**: Custom bridge network `javatodev_ib_network` (`172.25.0.0/16`) with static IPs.

**Startup Order**: `wait-for-it.sh` scripts ensure services wait for Config Server, Service Registry, and MySQL before starting.

### 6.4 CI/CD

- **No CI/CD pipeline** is configured. The `.github/` directory contains only a `FUNDING.yml` file.
- No GitHub Actions workflows, Jenkinsfile, or other CI configuration exists.

### 6.5 Build Commands

```bash
# Build a single service
cd core-banking-service && ./gradlew build

# Build Docker image (after Gradle build)
docker build -t javatodev/core-banking-service .

# Start full stack
cd docker-compose && docker-compose up -d
```
