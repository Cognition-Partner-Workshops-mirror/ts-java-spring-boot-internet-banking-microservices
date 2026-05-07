# Application Knowledge Base

## Table of Contents

- [1. Architecture Overview](#1-architecture-overview)
- [2. Data Model Documentation](#2-data-model-documentation)
- [3. API Surface Map](#3-api-surface-map)
- [4. Key Business Logic Inventory](#4-key-business-logic-inventory)
- [5. Integration Points](#5-integration-points)
- [6. Build and Deployment Pipeline](#6-build-and-deployment-pipeline)

---

## 1. Architecture Overview

### System Summary

This is a Java 21 / Spring Boot 3.2.4 internet banking platform composed of **6 microservices** following a classic Spring Cloud architecture pattern. Services communicate synchronously via **OpenFeign** REST clients and discover each other through **Netflix Eureka**. All external traffic enters through a **Spring Cloud Gateway** that enforces **OAuth2/JWT** authentication via **Keycloak**.

### Services

| Service | Port | Description |
|---|---|---|
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway. Single entry point for all client traffic. Enforces OAuth2 JWT authentication, routes requests to downstream services, and injects `X-Auth-Id` header with the authenticated principal. |
| **internet-banking-service-registry** | 8081 | Netflix Eureka Server. Service discovery registry; all other services register here as Eureka clients. |
| **internet-banking-config-server** | 8090 | Spring Cloud Config Server. Serves centralized configuration from a remote Git repository (`internet-banking-microservices-configurations`). |
| **core-banking-service** | 8092 | System of record. Manages bank accounts, users, utility accounts, and processes fund transfer/utility payment transactions at the ledger level. Uses MySQL with Flyway migrations. |
| **internet-banking-user-service** | 8083 | User profile management. Handles registration, approval, and updates. Integrates with Keycloak for identity management and with core-banking-service (via Feign) for user verification. Uses MySQL. |
| **internet-banking-fund-transfer-service** | 8084 | Fund transfer orchestration. Accepts transfer requests, persists them locally, delegates actual ledger operations to core-banking-service via Feign. Uses MySQL. |
| **internet-banking-utility-payment-service** | 8085 | Utility bill payment orchestration. Accepts payment requests, persists them locally, delegates actual ledger operations to core-banking-service via Feign. Uses MySQL. |

### Communication Patterns

```
                    +-----------+
  Client ---------> | API       |
  (JWT Bearer)      | Gateway   |
                    | (8082)    |
                    +-----+-----+
                          |
            +-------------+-------------+
            |             |             |
     +------v------+ +---v-------+ +---v-----------+
     | User        | | Fund      | | Utility       |
     | Service     | | Transfer  | | Payment       |
     | (8083)      | | (8084)    | | (8085)        |
     +------+------+ +-----+-----+ +-------+-------+
            |               |               |
            +-------+-------+-------+-------+
                    |               |
             +------v------+ +-----v------+
             | Core Banking| | Core       |
             | (8092)      | | Banking    |
             +-------------+ +------------+
```

- **Client -> API Gateway**: HTTP/REST with JWT Bearer token (Keycloak-issued).
- **API Gateway -> Downstream Services**: Proxied HTTP with `X-Auth-Id` header injected from JWT principal.
- **User/Fund-Transfer/Utility-Payment -> Core Banking**: Synchronous REST via OpenFeign clients, resolved through Eureka service discovery.
- **User Service -> Keycloak**: Direct Admin Client API calls for user CRUD in Keycloak realm.
- **All Services -> Config Server**: Bootstrap-phase HTTP fetch for externalized configuration.
- **All Services -> Service Registry (Eureka)**: Heartbeat registration and discovery.
- **All Services -> Zipkin**: Distributed tracing spans via Micrometer Brave bridge.

### Infrastructure Components

| Component | Technology | Docker IP | Port |
|---|---|---|---|
| Service Registry | Netflix Eureka | 172.25.0.7 | 8081 |
| Config Server | Spring Cloud Config | 172.25.0.8 | 8090 |
| API Gateway | Spring Cloud Gateway | 172.25.0.6 | 8082 |
| Identity Provider | Keycloak 23.0.7 | 172.25.0.11 | 8080 |
| Keycloak Database | PostgreSQL 15 | 172.25.0.10 | 5432 (closed) |
| Application Database | MySQL (custom image) | 172.25.0.9 | 3306 |
| Distributed Tracing | Zipkin 3 | 172.25.0.12 | 9411 |
| Docker Network | Bridge (172.25.0.0/16) | - | - |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL: `banking_core_service`)

#### `banking_core_user`

| Column | Type | Description |
|---|---|---|
| `id` | bigint (PK, auto-increment) | Internal user ID |
| `first_name` | varchar(255) | User's first name |
| `last_name` | varchar(255) | User's last name |
| `email` | varchar(255) | User's email address |
| `identification_number` | varchar(255) | National ID / NIC number (used as a lookup key) |

#### `banking_core_account`

| Column | Type | Description |
|---|---|---|
| `id` | bigint (PK, auto-increment) | Internal account ID |
| `number` | varchar(255) | Account number (e.g., `100015003000`) |
| `type` | varchar(255) | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | varchar(255) | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `available_balance` | decimal(19,2) | Available balance |
| `actual_balance` | decimal(19,2) | Actual/ledger balance |
| `user_id` | bigint (FK -> `banking_core_user.id`) | Owning user |

**Relationships**: Many accounts belong to one user (`@ManyToOne`).

#### `banking_core_transaction`

| Column | Type | Description |
|---|---|---|
| `id` | bigint (PK, auto-increment) | Internal transaction ID |
| `amount` | decimal(19,2) | Transaction amount (negative for debits) |
| `transaction_type` | varchar(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | varchar(50) | Reference (destination account number or utility ref) |
| `transaction_id` | varchar(50) | UUID grouping related debit/credit entries |
| `account_id` | bigint (FK -> `banking_core_account.id`) | Associated account |

**Relationships**: One-to-one with `BankAccountEntity` (via `@OneToOne`; conceptually many transactions per account).

#### `banking_core_utility_account`

| Column | Type | Description |
|---|---|---|
| `id` | bigint (PK, auto-increment) | Internal utility account ID |
| `number` | varchar(255) | Utility provider account number |
| `provider_name` | varchar(255) | Provider name (e.g., `VODAFONE`, `AIRTEL`) |

### 2.2 User Service (MySQL: `banking_core_user_service`)

#### `user` (JPA-managed, no Flyway)

| Column | Type | Description |
|---|---|---|
| `id` | bigint (PK, auto-increment) | Internal user ID |
| `auth_id` | varchar(255) | Keycloak user UUID (maps to Keycloak identity) |
| `identification` | varchar(255) | National ID / NIC (links to core-banking user) |
| `status` | varchar(255) | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_at` | datetime | Audit: creation timestamp (from `AuditAware`) |
| `updated_at` | datetime | Audit: last update timestamp (from `AuditAware`) |

### 2.3 Fund Transfer Service (MySQL: `banking_core_fund_transfer_service`)

#### `fund_transfer` (JPA-managed, no Flyway)

| Column | Type | Description |
|---|---|---|
| `id` | bigint (PK, auto-increment) | Internal transfer ID |
| `from_account` | varchar(255) | Source account number |
| `to_account` | varchar(255) | Destination account number |
| `amount` | decimal(19,2) | Transfer amount |
| `status` | varchar(255) | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `transaction_reference` | varchar(255) | Transaction UUID from core-banking |
| `created_at` | datetime | Audit: creation timestamp |
| `updated_at` | datetime | Audit: last update timestamp |

### 2.4 Utility Payment Service (MySQL: `banking_core_utility_payment_service`)

#### `utility_payment` (JPA-managed, no Flyway)

| Column | Type | Description |
|---|---|---|
| `id` | bigint (PK, auto-increment) | Internal payment ID |
| `provider_id` | bigint | Utility provider ID |
| `amount` | decimal(19,2) | Payment amount |
| `reference_number` | varchar(255) | Customer reference number |
| `account` | varchar(255) | Source bank account number |
| `transaction_id` | varchar(255) | Transaction UUID from core-banking |
| `status` | varchar(255) | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_at` | datetime | Audit: creation timestamp |
| `updated_at` | datetime | Audit: last update timestamp |

### Entity Relationship Diagram (Logical)

```
banking_core_user 1---* banking_core_account 1---1 banking_core_transaction
                                                    (conceptually 1---*)

banking_core_utility_account (standalone, referenced by provider_id)

[user_service].user ----(auth_id)----> Keycloak User
[user_service].user ----(identification)----> banking_core_user.identification_number

[fund_transfer_service].fund_transfer ----(from_account/to_account)----> banking_core_account.number

[utility_payment_service].utility_payment ----(account)----> banking_core_account.number
[utility_payment_service].utility_payment ----(provider_id)----> banking_core_utility_account.id
```

---

## 3. API Surface Map

### 3.1 API Gateway Routes (Port 8082)

All requests pass through the gateway. Route prefixes (from Spring Cloud Config, not visible in local files but inferred from security config and `api_test.http`):

| Route Prefix | Target Service |
|---|---|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |
| `/banking-core/**` | core-banking-service |

**Security**: All endpoints require JWT Bearer authentication except:
- `POST /user/api/v1/bank-users/register` (public)
- `/actuator/**`, `/user/actuator/**`, `/fund-transfer/actuator/**`, `/banking-core/actuator/**`, `/utility-payment/actuator/**` (public)

### 3.2 Core Banking Service (Port 8092)

#### Account Controller (`/api/v1/account`)

| Method | Path | Description | Request | Response |
|---|---|---|---|---|
| `GET` | `/bank-account/{account_number}` | Get bank account by number | Path: `account_number` (String) | `BankAccount { id, number, type, status, availableBalance, actualBalance, user }` |
| `GET` | `/util-account/{account_name}` | Get utility account by provider name | Path: `account_name` (String) | `UtilityAccount { id, number, providerName }` |

#### Transaction Controller (`/api/v1/transaction`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/fund-transfer` | Process fund transfer | `FundTransferRequest { fromAccount, toAccount, amount }` | `FundTransferResponse { message, transactionId }` |
| `POST` | `/util-payment` | Process utility payment | `UtilityPaymentRequest { providerId, amount, referenceNumber, account }` | `UtilityPaymentResponse { message, transactionId }` |

#### User Controller (`/api/v1/user`)

| Method | Path | Description | Request | Response |
|---|---|---|---|---|
| `GET` | `/{identification}` | Get user by identification number | Path: `identification` (String) | `User { id, firstName, lastName, email, identificationNumber, accounts[] }` |
| `GET` | `/` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |

### 3.3 Internet Banking User Service (Port 8083)

#### User Controller (`/api/v1/bank-users`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/register` | Register new banking user | `User { email, identification, password }` | `User { id, email, identification, authId, status }` |
| `PATCH` | `/update/{id}` | Update user (e.g., approve) | `UserUpdateRequest { status }` | `User { id, email, identification, authId, status }` |
| `GET` | `/` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |
| `GET` | `/{id}` | Get user by ID | Path: `id` (Long) | `User { id, email, identification, authId, status }` |

### 3.4 Internet Banking Fund Transfer Service (Port 8084)

#### Fund Transfer Controller (`/api/v1/transfer`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/` | Initiate fund transfer | `FundTransferRequest { fromAccount, toAccount, amount }` | `FundTransferResponse { message, transactionId }` |
| `GET` | `/` | List fund transfers (paginated) | Query: `page`, `size`, `sort` | `List<FundTransfer>` |

### 3.5 Internet Banking Utility Payment Service (Port 8085)

#### Utility Payment Controller (`/api/v1/utility-payment`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/` | Process utility payment | `UtilityPaymentRequest { providerId, amount, referenceNumber, account }` | `UtilityPaymentResponse { message, transactionId }` |
| `GET` | `/` | List utility payments (paginated) | Query: `page`, `size`, `sort` | `List<UtilityPayment>` |

### 3.6 Service Registry (Port 8081)

| Method | Path | Description |
|---|---|---|
| `GET` | `/` | Eureka Dashboard (built-in) |

### 3.7 Config Server (Port 8090)

| Method | Path | Description |
|---|---|---|
| `GET` | `/{application}/{profile}` | Fetch config for a given service and profile |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

**Location**: `internet-banking-user-service` -> `UserService.createUser()`

1. Check if email already exists in Keycloak. If yes, throw `UserAlreadyRegisteredException`.
2. Call core-banking-service (`GET /api/v1/user/{identification}`) to verify the user exists in the banking core.
3. Validate that the email matches the core banking record. If not, throw `InvalidEmailException`.
4. Create user in Keycloak (disabled, email unverified) with the provided password.
5. If Keycloak returns 201, re-read the user from Keycloak to get the `authId`.
6. Persist user entity locally with status `PENDING`.
7. If the identification is not found in core banking, throw `InvalidBankingUserException`.

### 4.2 User Approval Flow

**Location**: `internet-banking-user-service` -> `UserService.updateUser()`

1. Look up user by ID; throw `EntityNotFoundException` if not found.
2. If new status is `APPROVED`, enable the Keycloak user and mark email as verified.
3. Update local user entity status.

### 4.3 Fund Transfer Rules

**Location**: `internet-banking-fund-transfer-service` -> `FundTransferService.fundTransfer()` and `core-banking-service` -> `TransactionService.fundTransfer()`

**Orchestration Layer** (fund-transfer-service):
1. Create a `FundTransferEntity` with status `PENDING` and persist.
2. Call core-banking-service (`POST /api/v1/transaction/fund-transfer`) via Feign.
3. On success, update entity with `transaction_reference` and status `SUCCESS`.

**Core Layer** (core-banking-service):
1. Read both from/to bank accounts (throw `EntityNotFoundException` if missing).
2. Validate sender has sufficient funds: `actualBalance >= amount` and `actualBalance >= 0`. Throw `InsufficientFundsException` if not.
3. Debit sender: `actualBalance -= amount`, `availableBalance = actualBalance - amount`.
4. Record debit transaction entry (negative amount).
5. Credit receiver: `actualBalance += amount`, `availableBalance = actualBalance + amount`.
6. Record credit transaction entry (positive amount).
7. Both entries share the same `transactionId` UUID.
8. Entire operation is `@Transactional`.

### 4.4 Utility Payment Processing

**Location**: `internet-banking-utility-payment-service` -> `UtilityPaymentService.utilPayment()` and `core-banking-service` -> `TransactionService.utilPayment()`

**Orchestration Layer** (utility-payment-service):
1. Create a `UtilityPaymentEntity` with status `PROCESSING` and persist.
2. Call core-banking-service (`POST /api/v1/transaction/util-payment`) via Feign.
3. On success, update entity with `transactionId` and status `SUCCESS`.

**Core Layer** (core-banking-service):
1. Read payer's bank account (throw `EntityNotFoundException` if missing).
2. Validate sufficient funds.
3. Read utility account by `providerId`.
4. Debit payer: `actualBalance -= amount`, `availableBalance = actualBalance - amount`.
5. Record transaction entry (negative amount, type `UTILITY_PAYMENT`).
6. **Note**: No credit to utility provider account is recorded (placeholder for third-party API).

### 4.5 Balance Validation Rule

**Location**: `core-banking-service` -> `TransactionService.validateBalance()`

- Throws `InsufficientFundsException` if `actualBalance < 0` OR `actualBalance < requestedAmount`.
- Error code: `BANKING-CORE-SERVICE-1001`.

---

## 5. Integration Points

### 5.1 Keycloak (Identity & Access Management)

- **Version**: 23.0.7
- **Connection**: Admin Client API from `internet-banking-user-service`
- **Configuration**: `KeycloakProperties` reads from Spring Cloud Config:
  - `app.config.keycloak.server-url`
  - `app.config.keycloak.realm`
  - `app.config.keycloak.clientId`
  - `app.config.keycloak.client-secret`
- **Grant Type**: `client_credentials`
- **Operations**: Create user, update user, search user by email, read user by ID
- **Realm Data**: Pre-imported via Docker volume mount (`./keycloak` -> `/opt/keycloak/data/import`)
- **JWT Validation**: API Gateway validates JWTs using `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`

### 5.2 RabbitMQ (Message Queue)

- **Status**: Referenced in README as a technology but **not implemented** in the current codebase.
- **Intended Use**: Fund transfer and utility payment services were planned to push notification messages to RabbitMQ for a Notification service.
- **Notification Service**: Marked as "PENDING Development" in README.
- **No RabbitMQ dependency** exists in any `build.gradle` file.

### 5.3 Zipkin (Distributed Tracing)

- **Version**: Zipkin 3
- **Connection**: All business services include Micrometer Brave bridge dependencies.
- **Dependencies** (in each service's `build.gradle`):
  - `io.micrometer:micrometer-tracing-bridge-brave`
  - `io.zipkin.reporter2:zipkin-reporter-brave`
  - `io.github.openfeign:feign-micrometer`
- **Configuration**: Delivered via Spring Cloud Config (Zipkin URL, sampling rate).
- **Docker**: Runs at `172.25.0.12:9411`.

### 5.4 Database Connections

| Service | Database | Schema | Migration |
|---|---|---|---|
| core-banking-service | MySQL | `banking_core_service` | Flyway (3 migration files) |
| internet-banking-user-service | MySQL | `banking_core_user_service` | JPA auto (no Flyway) |
| internet-banking-fund-transfer-service | MySQL | `banking_core_fund_transfer_service` | JPA auto (no Flyway) |
| internet-banking-utility-payment-service | MySQL | `banking_core_utility_payment_service` | JPA auto (no Flyway) |

- **MySQL credentials**: Created via `privileges.sql` - user `javatodev_development` with broad permissions.
- **Test databases**: All services use H2 in-memory for tests.

### 5.5 Spring Cloud Config (Externalized Configuration)

- **Config Server** reads from: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch**: `main`, **Search path**: `configuration`
- **Bootstrap**: Each service uses `bootstrap.yml` (localhost) or `bootstrap-docker.yml` (Docker IP) to locate the Config Server.
- **Profiles**: `dev` (local), `docker` (containerized).

### 5.6 Netflix Eureka (Service Discovery)

- **Server**: `internet-banking-service-registry` on port 8081.
- **Clients**: All business services register as Eureka clients.
- **Feign Integration**: `@FeignClient(name = "core-banking-service")` resolves via Eureka.

---

## 6. Build and Deployment Pipeline

### 6.1 Build System

- **Build Tool**: Gradle (per-service `build.gradle`, no root-level multi-project build)
- **Java**: 21 (source compatibility)
- **Spring Boot**: 3.2.4
- **Spring Cloud**: 2023.0.0
- **Plugin**: `com.gorylenko.gradle-git-properties` (embeds git info in `/actuator/info`)

### 6.2 Docker

Each service has a `Dockerfile`:
```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh && apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

Build flow: `./gradlew build` -> `docker build` -> `docker-compose up`.

### 6.3 Docker Compose

Two compose files in `docker-compose/`:

| File | Purpose |
|---|---|
| `docker-compose.yml` | Full stack: infrastructure + all 6 services |
| `docker-compose-support-apps.yml` | Infrastructure only: Zipkin, Keycloak, PostgreSQL, MySQL, Config Server, Service Registry |

- **Network**: Custom bridge `javatodev_ib_network` with static IPs (172.25.0.0/16).
- **Startup Ordering**: `wait-for-it.sh` scripts ensure services wait for Config Server, Service Registry, and MySQL before starting.
- **Volumes**: `postgres_data` (Keycloak DB), `mysqldata` (application DB).

### 6.4 CI/CD

- **GitHub Actions**: Previously present but removed (commit message: "Remove GH Actions workflows").
- **No active CI/CD pipeline** in the current repository.

### 6.5 Test Configuration

- **Test Framework**: JUnit 5 (via `spring-boot-starter-test`)
- **Test Database**: H2 in-memory (dependency in `build.gradle`, config in `src/test/resources/application.yml`)
- **Flyway**: Disabled in test profile
- **Unit Tests Present**: Only in `core-banking-service` (`AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`)
- **Other Services**: Only have empty Spring Boot context-load tests (e.g., `InternetBankingFundTransferServiceApplicationTests`)
