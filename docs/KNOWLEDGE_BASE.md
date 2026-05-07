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

### 1.1 High-Level Architecture

The application is a microservices-based internet banking platform built with **Java 21**, **Spring Boot 3.2.4**, and **Spring Cloud 2023.0.0**. It follows a standard Spring Cloud microservices pattern with centralized configuration, service discovery, and an API gateway.

### 1.2 Services

| Service | Port | Description |
|---|---|---|
| `internet-banking-api-gateway` | 8082 | Spring Cloud Gateway — single entry point for all client traffic; enforces OAuth2/JWT authentication via Keycloak |
| `internet-banking-service-registry` | 8081 | Netflix Eureka Server — dynamic service discovery |
| `internet-banking-config-server` | 8090 | Spring Cloud Config Server — centralized configuration management |
| `internet-banking-user-service` | 8083 | User registration, approval workflow, and Keycloak identity management |
| `internet-banking-fund-transfer-service` | 8084 | Account-to-account fund transfer orchestration |
| `internet-banking-utility-payment-service` | 8085 | Third-party utility bill payment processing |
| `core-banking-service` | 8092 | System of record — manages accounts, users, balances, and transaction ledger |

### 1.3 Communication Patterns

```
                          ┌─────────────────────┐
                          │     Keycloak IAM     │
                          │   (OAuth2 / OIDC)    │
                          └─────────┬────────────┘
                                    │ JWT validation
┌─────────┐   HTTP    ┌────────────▼────────────┐
│  Client  │─────────>│    API Gateway (:8082)   │
└─────────┘           └──┬─────────┬──────────┬──┘
                         │         │          │
              ┌──────────▼──┐ ┌────▼────┐ ┌───▼──────────┐
              │ User Service│ │  Fund   │ │   Utility    │
              │   (:8083)   │ │Transfer │ │   Payment    │
              │             │ │ (:8084) │ │   (:8085)    │
              └──────┬──────┘ └────┬────┘ └──────┬───────┘
                     │  OpenFeign  │  OpenFeign   │ OpenFeign
              ┌──────▼─────────────▼──────────────▼───────┐
              │         Core Banking Service (:8092)       │
              │     (Accounts, Users, Transactions)        │
              └─────────────────────┬─────────────────────┘
                                    │
                              ┌─────▼─────┐
                              │   MySQL    │
                              └───────────┘
```

**Synchronous communication:**
- All inter-service communication uses **Spring Cloud OpenFeign** (declarative REST clients) over HTTP.
- Services discover each other via **Netflix Eureka** service registry.
- The API Gateway routes requests based on path prefixes to downstream services.

**Asynchronous communication:**
- **RabbitMQ** is listed in the technology stack and referenced in the README for notification messaging, but a Notification Service is marked as **PENDING Development**. No RabbitMQ producer/consumer code exists in the current codebase.

**Authentication flow:**
- The API Gateway acts as an OAuth2 Resource Server, validating JWT tokens issued by Keycloak.
- Authenticated user identity is propagated downstream via the `X-Auth-Id` HTTP header (set by `GatewayConfiguration`).
- Downstream services extract this header using `AppAuthUserFilter` and store it in a thread-local `ApiRequestContextHolder`.

### 1.4 Infrastructure Components

| Component | Image / Version | Purpose | Docker IP |
|---|---|---|---|
| MySQL | Custom (Dockerfile in `docker-compose/mysql/`) | Primary database for all four business services (separate schemas) | 172.25.0.9 |
| PostgreSQL | postgres:15 | Keycloak's backing database | 172.25.0.10 |
| Keycloak | quay.io/keycloak/keycloak:23.0.7 | Identity and Access Management (OAuth2/OIDC) | 172.25.0.11 |
| Zipkin | openzipkin/zipkin:3 | Distributed tracing | 172.25.0.12 |

### 1.5 Configuration Management

- Each service uses **Spring Cloud Config** via a bootstrap context (`bootstrap.yml` / `bootstrap-docker.yml`).
- Local development points the Config Server to `http://localhost:8090`.
- Docker profile points to `http://internet-banking-config-server:8090`.
- Application-specific configuration (DB connections, Keycloak settings, gateway routes, tracing endpoints) is served by the Config Server at runtime. The config source (Git repo or native filesystem) is configured externally in the Config Server's own properties.

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL: `banking_core_service`)

#### `banking_core_user`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Internal user identifier |
| `first_name` | VARCHAR(255) | User's first name |
| `last_name` | VARCHAR(255) | User's last name |
| `email` | VARCHAR(255) | User's email address |
| `identification_number` | VARCHAR(255) | National ID / NIC number |

#### `banking_core_account`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Internal account identifier |
| `number` | VARCHAR(255) | Account number (string) |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `available_balance` | DECIMAL(19,2) | Available balance for transactions |
| `actual_balance` | DECIMAL(19,2) | Actual/ledger balance |
| `user_id` | BIGINT (FK -> banking_core_user.id) | Owning user |

#### `banking_core_transaction`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Internal transaction identifier |
| `amount` | DECIMAL(19,2) | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Reference (destination account number or utility ref) |
| `transaction_id` | VARCHAR(50) | UUID grouping related entries |
| `account_id` | BIGINT (FK -> banking_core_account.id) | Associated account |

#### `banking_core_utility_account`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Internal identifier |
| `number` | VARCHAR(255) | Utility provider account number |
| `provider_name` | VARCHAR(255) | Provider name (e.g., VODAFONE, AIRTEL) |

#### Relationships

```
banking_core_user 1──* banking_core_account
banking_core_account 1──1 banking_core_transaction
```

### 2.2 Internet Banking User Service (MySQL: `banking_core_user_service`)

#### `user`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Internal identifier |
| `auth_id` | VARCHAR | Keycloak user UUID |
| `identification` | VARCHAR | National ID linking to core banking user |
| `status` | VARCHAR (Enum) | `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | TIMESTAMP | Audit: creation timestamp |
| `created_by` | VARCHAR | Audit: creator |
| `modified_date` | TIMESTAMP | Audit: last modification |
| `modified_by` | VARCHAR | Audit: modifier |
| `version` | BIGINT | Optimistic locking version |

Uses JPA auditing (`AuditAware` mapped superclass with `@EntityListeners(AuditingEntityListener.class)`).

### 2.3 Internet Banking Fund Transfer Service (MySQL: `banking_core_fund_transfer_service`)

#### `fund_transfer`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Internal identifier |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `transaction_reference` | VARCHAR | Core banking transaction UUID |
| `status` | VARCHAR (Enum) | `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| Audit columns | | Same as user service (extends `AuditAware`) |

### 2.4 Internet Banking Utility Payment Service (MySQL: `banking_core_utility_payment_service`)

#### `utility_payment`

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Internal identifier |
| `provider_id` | BIGINT | Utility provider ID in core banking |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Bill reference number |
| `account` | VARCHAR | Source account number |
| `transaction_id` | VARCHAR | Core banking transaction UUID |
| `status` | VARCHAR (Enum) | `PROCESSING`, `SUCCESS`, `FAILED` |
| Audit columns | | Same as user service (extends `AuditAware`) |

---

## 3. API Surface Map

### 3.1 API Gateway Routes

All client requests pass through the API Gateway at port **8082**. Route prefixes:

| Prefix | Target Service |
|---|---|
| `/user/**` | `internet-banking-user-service` |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` |
| `/utility-payment/**` | `internet-banking-utility-payment-service` |
| `/banking-core/**` | `core-banking-service` |

Authentication: All endpoints require a valid JWT token except:
- `POST /user/api/v1/bank-users/register` (public)
- `/actuator/**` on all services (public)

### 3.2 Core Banking Service (`:8092`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | — | `BankAccount` (id, number, type, status, availableBalance, actualBalance, user) |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | — | `UtilityAccount` (id, number, providerName) |
| `GET` | `/api/v1/user/{identification}` | Get user by national ID | — | `User` (id, firstName, lastName, email, identificationNumber, bankAccounts[]) |
| `GET` | `/api/v1/user` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer at ledger level | `{fromAccount, toAccount, amount}` | `{message, transactionId}` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment at ledger level | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` |

### 3.3 Internet Banking User Service (`:8083`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Register new internet banking user | `{email, identification, password}` | `User` (id, email, identification, authId, status) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user status (e.g., approve) | `{status}` (PENDING/APPROVED/DISABLED/BLACKLIST) | `User` |
| `GET` | `/api/v1/bank-users` | List all users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | — | `User` |

### 3.4 Internet Banking Fund Transfer Service (`:8084`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `{fromAccount, toAccount, amount, authID}` | `{message, transactionId}` |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | Query: `page`, `size`, `sort` | `List<FundTransfer>` |

### 3.5 Internet Banking Utility Payment Service (`:8085`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | Query: `page`, `size`, `sort` | `List<UtilityPayment>` |

### 3.6 Infrastructure Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/actuator/health` | Health check (all services) |
| `GET` | `/actuator/info` | Application info including git properties |
| `GET` | `/:8081/` | Eureka dashboard |
| `GET` | `/:9411/` | Zipkin tracing UI |
| `GET` | `/:8080/` | Keycloak admin console |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

**Location:** `internet-banking-user-service` -> `UserService.createUser()`

1. Check if email is already registered in Keycloak. If so, throw `UserAlreadyRegisteredException`.
2. Call Core Banking Service via OpenFeign to verify user exists by national ID (`identification`).
3. Validate that the email in the request matches the email in core banking. If not, throw `InvalidEmailException`.
4. Create a Keycloak user representation (disabled, email not verified) with the provided password.
5. Call Keycloak Admin API to create the user. If HTTP 201 returned:
   - Read back the created Keycloak user to get the `authId` (Keycloak UUID).
   - Save a local `UserEntity` with status `PENDING`.
6. If the core banking user is not found, throw `InvalidBankingUserException`.

### 4.2 User Approval Flow

**Location:** `internet-banking-user-service` -> `UserService.updateUser()`

1. Find the local user by ID.
2. If the new status is `APPROVED`:
   - Read the Keycloak user by `authId`.
   - Set `enabled = true` and `emailVerified = true` in Keycloak.
   - Update the Keycloak user.
3. Update the local user's status.

### 4.3 Fund Transfer Rules

**Location:** `internet-banking-fund-transfer-service` -> `FundTransferService.fundTransfer()`

1. Save a local `FundTransferEntity` with status `PENDING`.
2. Call Core Banking Service's `/api/v1/transaction/fund-transfer` via OpenFeign.
3. On success, update local entity with `transactionReference` and status `SUCCESS`.
4. Return the transaction ID.

**Core Banking ledger logic** (`core-banking-service` -> `TransactionService.fundTransfer()`):

1. Look up both source and destination accounts.
2. Validate source account has sufficient funds (`actualBalance >= amount` and `actualBalance >= 0`).
3. Debit source: subtract `amount` from both `actualBalance` and `availableBalance`.
4. Record a debit `TransactionEntity` for the source account.
5. Credit destination: add `amount` to both `actualBalance` and `availableBalance`.
6. Record a credit `TransactionEntity` for the destination account.
7. Both entries share the same `transactionId` UUID.
8. The entire operation runs within a single `@Transactional` boundary.

**Known issue:** The `availableBalance` calculation in `internalFundTransfer()` and `utilPayment()` appears to have a bug. After setting `actualBalance = actualBalance - amount`, it then sets `availableBalance = actualBalance - amount` (double subtraction from actual). This may cause `availableBalance` to drift from the intended value.

### 4.4 Utility Payment Processing

**Location:** `internet-banking-utility-payment-service` -> `UtilityPaymentService.utilPayment()`

1. Save a local `UtilityPaymentEntity` with status `PROCESSING`.
2. Call Core Banking Service's `/api/v1/transaction/util-payment` via OpenFeign.
3. On success, update local entity with `transactionId` and status `SUCCESS`.

**Core Banking ledger logic** (`core-banking-service` -> `TransactionService.utilPayment()`):

1. Look up the source bank account and utility provider account.
2. Validate sufficient funds.
3. Debit the source account.
4. Record a `UTILITY_PAYMENT` transaction entry.
5. No actual call to an external payment provider (commented as future work).

### 4.5 Identity Propagation

1. API Gateway extracts the authenticated principal name from the JWT.
2. Sets it as the `X-Auth-Id` header on the proxied request.
3. Downstream services use `AppAuthUserFilter` to extract `X-Auth-Id` into a thread-local `ApiRequestContext`.
4. The `AuditorAwareConfig` uses this context to populate `createdBy` / `modifiedBy` audit fields.

---

## 5. Integration Points

### 5.1 Keycloak

| Aspect | Detail |
|---|---|
| **Version** | 23.0.7 |
| **Client library** | `keycloak-admin-client:24.0.4` |
| **Auth type** | `client_credentials` grant |
| **Configuration** | `app.config.keycloak.server-url`, `app.config.keycloak.realm`, `app.config.keycloak.clientId`, `app.config.keycloak.client-secret` (via Config Server) |
| **Operations** | Create user, update user (enable/verify), search user by email, read user by ID |
| **JWT validation** | API Gateway validates JWTs using `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` |
| **Realm import** | Docker volume mounts `docker-compose/keycloak/` for realm auto-import on startup |

### 5.2 RabbitMQ

- **Status:** Referenced in README for notification messaging but **not implemented** in the current codebase.
- No RabbitMQ dependency in any `build.gradle`.
- No producer/consumer code present.
- The Notification Service is marked as "PENDING Development."

### 5.3 Zipkin (Distributed Tracing)

| Aspect | Detail |
|---|---|
| **Version** | openzipkin/zipkin:3 |
| **Libraries** | `micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`, `feign-micrometer` |
| **Coverage** | All six Java services include tracing dependencies |
| **Endpoint** | Configured via Config Server (typically `http://zipkin:9411`) |

### 5.4 Database Connections

| Service | Database | Schema |
|---|---|---|
| Core Banking | MySQL | `banking_core_service` |
| User Service | MySQL | `banking_core_user_service` |
| Fund Transfer Service | MySQL | `banking_core_fund_transfer_service` |
| Utility Payment Service | MySQL | `banking_core_utility_payment_service` |
| Keycloak | PostgreSQL | `keycloak` |

- All MySQL services share a single MySQL instance with separate schemas.
- Database credentials are configured via Spring Cloud Config Server.
- Core Banking Service uses **Flyway** for schema migrations (`flyway-core:10.12.0`, `flyway-mysql:10.12.0`).
- Other services rely on JPA auto-DDL (Hibernate `ddl-auto`).

### 5.5 Service Discovery (Eureka)

- All business services register with Eureka (`spring-cloud-starter-netflix-eureka-client`).
- Service Registry runs as a standalone Eureka Server (`spring-cloud-starter-netflix-eureka-server`).
- Feign clients resolve service names (e.g., `core-banking-service`) through Eureka.

### 5.6 Spring Cloud Config Server

- All services fetch configuration from the Config Server during the bootstrap phase.
- Config Server is a standalone Spring Boot app with `@EnableConfigServer`.
- The actual configuration repository (Git or native) is defined externally.

---

## 6. Build and Deployment Pipeline

### 6.1 Build System

- **Build tool:** Gradle (each service has its own `build.gradle`, no multi-project root build)
- **Java version:** 21 (source compatibility)
- **Spring Boot version:** 3.2.4
- **Spring Cloud version:** 2023.0.0
- **Plugin:** `com.gorylenko.gradle-git-properties:2.4.2` generates `git.properties` for `/actuator/info`

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

- Uses `wait-for-it.sh` to ensure dependent services are ready before starting.
- Docker profile activates `bootstrap-docker.yml` for Docker-internal networking.

### 6.3 Docker Compose

**`docker-compose/docker-compose.yml`** — Full stack deployment:

- All 10 containers on a fixed-IP bridge network (`172.25.0.0/16`).
- Application services use `wait-for-it.sh` entrypoints to wait for Config Server, Service Registry, and MySQL.
- Named volumes for MySQL data (`mysqldata`) and PostgreSQL data (`postgres_data`).
- Custom MySQL image built from `docker-compose/mysql/Dockerfile` with `privileges.sql` for schema/user creation.

**`docker-compose/docker-compose-support-apps.yml`** — Infrastructure only:

- Zipkin, Keycloak, PostgreSQL, MySQL, Config Server, Service Registry.
- Useful for local development where business services run from IDE.

### 6.4 Build and Run Workflow

```bash
# 1. Build each service JAR
cd <service-directory>
./gradlew clean build

# 2. Build Docker images
docker build -t javatodev/<service-name> .

# 3. Start everything
cd docker-compose
docker-compose up -d
```

### 6.5 Test Data

Flyway migrations in Core Banking Service seed:
- **4 users** with national IDs
- **14 savings accounts** across the users
- **6 utility providers** (VODAFONE, VERIZON, SINGTEL, HUTCH, AIRTEL, GIO)

Keycloak realm is auto-imported with pre-configured realm, clients, and test users.

**Test credentials:** `ib_admin@javatodev.com / 5V7huE3G86uB`

### 6.6 CI/CD

- A `.github/` directory exists but workflows have been removed (commit message: "Remove GH Actions workflows").
- No active CI/CD pipeline is configured in the repository.
