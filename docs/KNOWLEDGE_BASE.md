# Application Knowledge Base

## 1. Architecture Overview

### 1.1 Services

| Service | Port | Description |
|---------|------|-------------|
| `internet-banking-service-registry` | 8081 | Netflix Eureka discovery server for dynamic service location |
| `internet-banking-config-server` | 8090 | Spring Cloud Config server backed by a Git repository |
| `internet-banking-api-gateway` | 8082 | Spring Cloud Gateway — single entry point, OAuth2 enforcement, route proxying |
| `internet-banking-user-service` | 8083 | User registration, profile management, Keycloak integration |
| `internet-banking-fund-transfer-service` | 8084 | Account-to-account fund transfer orchestration |
| `internet-banking-utility-payment-service` | 8085 | Third-party utility bill payment processing |
| `core-banking-service` | 8092 | System of record for accounts, users, transactions, and ledger operations |

### 1.2 Communication Patterns

```
┌─────────────┐       ┌──────────────────────┐       ┌─────────────────────┐
│   Client    │──────▶│  API Gateway (:8082)  │──────▶│  Downstream Services│
└─────────────┘       │  (OAuth2 + Routing)   │       └─────────────────────┘
                      └──────────────────────┘
                                │
         ┌──────────────────────┼──────────────────────────┐
         ▼                      ▼                          ▼
┌─────────────────┐  ┌──────────────────────┐  ┌────────────────────────────┐
│ User Service    │  │ Fund Transfer Service│  │ Utility Payment Service    │
│ (:8083)         │  │ (:8084)              │  │ (:8085)                    │
└────────┬────────┘  └──────────┬───────────┘  └─────────────┬──────────────┘
         │ OpenFeign            │ OpenFeign                   │ OpenFeign
         ▼                      ▼                             ▼
         ┌──────────────────────────────────────────────────────┐
         │              Core Banking Service (:8092)             │
         │     (Accounts, Users, Transactions, Ledger)          │
         └──────────────────────────────────────────────────────┘
```

- **Synchronous REST (OpenFeign):** All inter-service calls use Spring Cloud OpenFeign clients resolved through Eureka service discovery.
- **API Gateway routing:** The gateway strips service prefixes (`/user/`, `/fund-transfer/`, `/banking-core/`, `/utility-payment/`) and forwards to the appropriate downstream service.
- **Auth propagation:** The gateway extracts the authenticated principal name and injects it as an `X-Auth-Id` header on proxied requests. Downstream services read this header via `AppAuthUserFilter`.
- **RabbitMQ (planned):** The README references RabbitMQ for notification messages from fund-transfer and utility-payment services, but this is not yet implemented in code.

### 1.3 Infrastructure Components

| Component | Image / Technology | Purpose |
|-----------|-------------------|---------|
| MySQL 8.4 | Custom Dockerfile (`docker-compose/mysql/`) | Primary relational store for all business services |
| PostgreSQL 15 | `postgres:15` | Keycloak identity store |
| Keycloak 23.0.7 | `quay.io/keycloak/keycloak:23.0.7` | OAuth2/OIDC identity and access management |
| Zipkin 3 | `openzipkin/zipkin:3` | Distributed tracing collector and UI |
| Spring Cloud Config | Git-backed (`internet-banking-microservices-configurations` repo) | Centralized externalized configuration |
| Netflix Eureka | Embedded in `internet-banking-service-registry` | Service discovery |

### 1.4 Network Topology (Docker)

All containers run on a custom bridge network `javatodev_ib_network` (`172.25.0.0/16`) with fixed IPs:

| Container | IP |
|-----------|-----|
| core-banking-service | 172.25.0.2 |
| internet-banking-utility-payment-service | 172.25.0.3 |
| internet-banking-fund-transfer-service | 172.25.0.4 |
| internet-banking-user-service | 172.25.0.5 |
| internet-banking-api-gateway | 172.25.0.6 |
| internet-banking-service-registry | 172.25.0.7 |
| internet-banking-config-server | 172.25.0.8 |
| mysql_javatodev_app | 172.25.0.9 |
| keycloak_postgre_db | 172.25.0.10 |
| keycloak_web | 172.25.0.11 |
| openzipkin_server | 172.25.0.12 |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL — `banking_core_service`)

#### `banking_core_user`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `first_name` | VARCHAR(255) | |
| `last_name` | VARCHAR(255) | |
| `email` | VARCHAR(255) | |
| `identification_number` | VARCHAR(255) | National ID (e.g., NIC) |

#### `banking_core_account`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `number` | VARCHAR(255) | 12-digit account number |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `actual_balance` | DECIMAL(19,2) | Ledger balance |
| `available_balance` | DECIMAL(19,2) | Withdrawable balance |
| `user_id` | BIGINT (FK → `banking_core_user.id`) | Account owner |

#### `banking_core_transaction`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `amount` | DECIMAL(19,2) | Signed (negative for debits) |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Destination account or reference |
| `transaction_id` | VARCHAR(50) | UUID correlating debit/credit entries |
| `account_id` | BIGINT (FK → `banking_core_account.id`) | |

#### `banking_core_utility_account`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `number` | VARCHAR(255) | Provider account number |
| `provider_name` | VARCHAR(255) | e.g., VODAFONE, VERIZON |

#### Relationships

```
banking_core_user 1───* banking_core_account 1───* banking_core_transaction
banking_core_utility_account (standalone lookup table)
```

### 2.2 User Service (MySQL — `banking_core_user_service`)

#### `user`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `auth_id` | VARCHAR(255) | Keycloak user UUID |
| `identification` | VARCHAR(255) | National ID linking to core banking |
| `status` | VARCHAR(255) | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | TIMESTAMP | Audit field |
| `created_by` | VARCHAR(255) | Audit field |
| `modified_date` | TIMESTAMP | Audit field |
| `modified_by` | VARCHAR(255) | Audit field |
| `version` | BIGINT | Optimistic lock |

### 2.3 Fund Transfer Service (MySQL — `banking_core_fund_transfer_service`)

#### `fund_transfer`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `from_account` | VARCHAR(255) | Source account number |
| `to_account` | VARCHAR(255) | Destination account number |
| `amount` | DECIMAL(19,2) | Transfer amount |
| `transaction_reference` | VARCHAR(255) | UUID from core banking |
| `status` | VARCHAR(255) | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `modified_date` / `created_by` / `modified_by` / `version` | Audit fields | |

### 2.4 Utility Payment Service (MySQL — `banking_core_utility_payment_service`)

#### `utility_payment`

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `provider_id` | BIGINT | FK conceptually to `banking_core_utility_account.id` |
| `amount` | DECIMAL(19,2) | Payment amount |
| `reference_number` | VARCHAR(255) | Bill reference |
| `account` | VARCHAR(255) | Source bank account number |
| `transaction_id` | VARCHAR(255) | UUID from core banking |
| `status` | VARCHAR(255) | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `modified_date` / `created_by` / `modified_by` / `version` | Audit fields | |

---

## 3. API Surface Map

### 3.1 Core Banking Service (`:8092`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| GET | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | — | `BankAccount` |
| GET | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | — | `UtilityAccount` |
| GET | `/api/v1/user/{identification}` | Get user by identification number | — | `User` (with accounts) |
| GET | `/api/v1/user` | List users (paginated) | — | `List<User>` |
| POST | `/api/v1/transaction/fund-transfer` | Process fund transfer at ledger level | `FundTransferRequest` | `FundTransferResponse` |
| POST | `/api/v1/transaction/util-payment` | Process utility payment at ledger level | `UtilityPaymentRequest` | `UtilityPaymentResponse` |

**Request/Response Shapes:**

```json
// FundTransferRequest (core)
{ "fromAccount": "string", "toAccount": "string", "amount": 0.00 }

// FundTransferResponse (core)
{ "message": "string", "transactionId": "uuid-string" }

// UtilityPaymentRequest (core)
{ "providerId": 0, "amount": 0.00, "referenceNumber": "string", "account": "string" }

// UtilityPaymentResponse (core)
{ "message": "string", "transactionId": "uuid-string" }

// BankAccount
{ "id": 0, "number": "string", "type": "SAVINGS_ACCOUNT|FIXED_DEPOSIT|LOAN_ACCOUNT",
  "status": "ACTIVE|PENDING|DORMANT|BLOCKED", "availableBalance": 0.00, "actualBalance": 0.00,
  "user": { "firstName": "string", "lastName": "string", "email": "string", "identificationNumber": "string" } }
```

### 3.2 User Service (`:8083`)

Gateway prefix: `/user/`

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| POST | `/api/v1/bank-users/register` | Register a new banking user | `User` | `User` |
| PATCH | `/api/v1/bank-users/update/{id}` | Update user status | `UserUpdateRequest` | `User` |
| GET | `/api/v1/bank-users` | List users (paginated) | — | `List<User>` |
| GET | `/api/v1/bank-users/{id}` | Get user by ID | — | `User` |

**Request/Response Shapes:**

```json
// User (request for registration)
{ "email": "string", "identification": "string", "password": "string" }

// User (response)
{ "id": 0, "email": "string", "identification": "string", "authId": "keycloak-uuid",
  "status": "PENDING|APPROVED|DISABLED|BLACKLIST", "version": 0 }

// UserUpdateRequest
{ "status": "APPROVED|DISABLED|BLACKLIST" }
```

### 3.3 Fund Transfer Service (`:8084`)

Gateway prefix: `/fund-transfer/`

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| POST | `/api/v1/transfer` | Initiate fund transfer | `FundTransferRequest` | `FundTransferResponse` |
| GET | `/api/v1/transfer` | List transfers (paginated) | — | `List<FundTransfer>` |

**Request/Response Shapes:**

```json
// FundTransferRequest
{ "fromAccount": "string", "toAccount": "string", "amount": 0.00, "authID": "string" }

// FundTransferResponse
{ "message": "string", "transactionId": "uuid-string" }

// FundTransfer (list item)
{ "id": 0, "transactionReference": "uuid", "status": "SUCCESS|PENDING|FAILED",
  "fromAccount": "string", "toAccount": "string", "amount": 0.00, "version": 0 }
```

### 3.4 Utility Payment Service (`:8085`)

Gateway prefix: `/utility-payment/`

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| POST | `/api/v1/utility-payment` | Process utility payment | `UtilityPaymentRequest` | `UtilityPaymentResponse` |
| GET | `/api/v1/utility-payment` | List payments (paginated) | — | `List<UtilityPayment>` |

**Request/Response Shapes:**

```json
// UtilityPaymentRequest
{ "providerId": 0, "amount": 0.00, "referenceNumber": "string", "account": "string" }

// UtilityPaymentResponse
{ "message": "string", "transactionId": "uuid-string" }

// UtilityPayment (list item)
{ "providerId": 0, "amount": 0.00, "referenceNumber": "string", "account": "string",
  "status": "PENDING|PROCESSING|SUCCESS|FAILED" }
```

### 3.5 Service Registry (`:8081`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/` | Eureka Dashboard (Web UI) |
| GET | `/eureka/apps` | List registered service instances |

### 3.6 Config Server (`:8090`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/{application}/{profile}` | Fetch configuration for a service and profile |
| GET | `/{application}/{profile}/{label}` | Fetch configuration for a specific Git label |

### 3.7 Common Actuator Endpoints (all services)

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Health status |
| `/actuator/info` | Build/git info |
| `/actuator/metrics` | Micrometer metrics |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

1. Client sends `POST /user/api/v1/bank-users/register` (public — no OAuth required).
2. User Service checks Keycloak for existing email → throws `UserAlreadyRegisteredException` if found.
3. User Service calls Core Banking (`GET /api/v1/user/{identification}`) to validate the user exists in the banking core.
4. Validates the email matches the core banking record → throws `InvalidEmailException` if mismatched.
5. Creates a Keycloak user (disabled, email unverified) with the provided password.
6. Persists a local `UserEntity` with status `PENDING` and the Keycloak `authId`.

### 4.2 User Approval Flow

1. Admin sends `PATCH /user/api/v1/bank-users/update/{id}` with `{ "status": "APPROVED" }`.
2. User Service fetches the Keycloak user representation.
3. Sets `enabled=true` and `emailVerified=true` in Keycloak.
4. Updates local entity status to `APPROVED`.

### 4.3 Fund Transfer Rules

1. Client sends `POST /fund-transfer/api/v1/transfer`.
2. Fund Transfer Service persists a `FundTransferEntity` with status `PENDING`.
3. Delegates to Core Banking via Feign: `POST /api/v1/transaction/fund-transfer`.
4. Core Banking:
   - Reads both source and destination `BankAccount` objects.
   - **Balance validation:** `actualBalance >= 0` AND `actualBalance >= transferAmount`; otherwise throws `InsufficientFundsException`.
   - Debits the source account (subtracts from `actualBalance` and `availableBalance`).
   - Credits the destination account (adds to `actualBalance` and `availableBalance`).
   - Creates two `TransactionEntity` records (debit entry with negative amount, credit entry with positive amount) sharing the same `transactionId` UUID.
5. On success, Fund Transfer Service updates local entity to `SUCCESS` with the `transactionReference`.

### 4.4 Utility Payment Processing

1. Client sends `POST /utility-payment/api/v1/utility-payment`.
2. Utility Payment Service persists a `UtilityPaymentEntity` with status `PROCESSING`.
3. Delegates to Core Banking via Feign: `POST /api/v1/transaction/util-payment`.
4. Core Banking:
   - Reads the source `BankAccount`.
   - **Balance validation:** Same rules as fund transfer.
   - Reads the `UtilityAccount` by provider ID.
   - Debits the source account.
   - Creates one `TransactionEntity` record (debit with negative amount, type `UTILITY_PAYMENT`).
   - (Third-party API call placeholder — not yet implemented.)
5. On success, Utility Payment Service updates local entity to `SUCCESS` with the `transactionId`.

### 4.5 Authentication & Authorization

- **OAuth2 Resource Server:** The API Gateway validates JWT tokens issued by Keycloak.
- **JWK Set URI:** Configured via `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`.
- **Public endpoint:** `/user/api/v1/bank-users/register` is permitted without authentication.
- **Actuator endpoints:** Permitted without authentication for monitoring.
- **All other endpoints:** Require a valid Bearer token.
- **Keycloak realm:** `javatodev-internet-banking`
- **Keycloak client:** `internet-banking-core-client` (client_credentials grant for admin operations)

---

## 5. Integration Points

### 5.1 Keycloak (IAM)

| Property | Value |
|----------|-------|
| Server URL | `http://keycloak_web:8080` (Docker) / `http://localhost:8080` (local) |
| Realm | `javatodev-internet-banking` |
| Client ID | `internet-banking-core-client` |
| Grant Type | `client_credentials` |
| Integration | `keycloak-admin-client:24.0.4` in User Service |
| Operations | Create user, update user (enable/verify), search by email, read by auth ID |

### 5.2 RabbitMQ (Messaging — Planned)

- Referenced in README as the messaging backbone for notifications from Fund Transfer and Utility Payment services.
- **Not currently implemented** in the codebase.
- Intended for a future Notification Service that would consume messages and send user notifications.

### 5.3 Zipkin (Distributed Tracing)

| Property | Value |
|----------|-------|
| Collector URL | `http://openzipkin_server:9411` (Docker) / `http://localhost:9411` (local) |
| Libraries | `micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`, `feign-micrometer` |
| Coverage | All 5 application services (gateway, user, fund-transfer, utility-payment, core-banking) |

### 5.4 Database Connections

| Service | Database | Schema | Driver |
|---------|----------|--------|--------|
| core-banking-service | MySQL 8.4 | `banking_core_service` | `com.mysql:mysql-connector-j:8.4.0` |
| internet-banking-user-service | MySQL 8.4 | `banking_core_user_service` | `com.mysql:mysql-connector-j:8.4.0` |
| internet-banking-fund-transfer-service | MySQL 8.4 | `banking_core_fund_transfer_service` | `com.mysql:mysql-connector-j:8.4.0` |
| internet-banking-utility-payment-service | MySQL 8.4 | `banking_core_utility_payment_service` | `com.mysql:mysql-connector-j:8.4.0` |
| Keycloak | PostgreSQL 15 | `keycloak` | (internal to Keycloak) |

- **MySQL credentials:** `javatodev_development` / `oPItyPticIAt` (created via `privileges.sql` init script)
- **Database migrations:** Flyway (`flyway-core:10.12.0`, `flyway-mysql:10.12.0`) in Core Banking Service only.

### 5.5 Spring Cloud Config Server

| Property | Value |
|----------|-------|
| Git URI | `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` |
| Search path | `configuration` |
| Default label | `main` |
| Consumed by | All services via bootstrap context (`spring.cloud.config.uri`) |

### 5.6 Spring Cloud Eureka

| Property | Value |
|----------|-------|
| Server URL | `http://internet-banking-service-registry:8081/eureka` (Docker) |
| Registered services | `core-banking-service`, `internet-banking-user-service`, `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`, `internet-banking-api-gateway` |

---

## 6. Build and Deployment Pipeline

### 6.1 Build System

- **Build tool:** Gradle (Wrapper — `gradlew`)
- **Java version:** 21 (Eclipse Temurin 21.0.2)
- **Spring Boot:** 3.2.4 (via `org.springframework.boot` plugin)
- **Spring Cloud:** 2023.0.0 BOM
- **Plugins:** `gradle-git-properties` (2.4.2) for Git metadata in actuator info

### 6.2 Build Steps (per service)

```bash
./gradlew clean build        # Compile, test, package JAR
docker build -t javatodev/<service-name> .  # Build Docker image
```

### 6.3 Docker Images

All services use `eclipse-temurin:21.0.2_13-jre-alpine` as the base image:

```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh && apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

### 6.4 Startup Orchestration

Services use `wait-for-it.sh` scripts in Docker Compose entrypoints to ensure dependencies are ready:

1. **Config Server & Service Registry** start first (no dependencies).
2. **API Gateway** waits for: Service Registry + Config Server.
3. **Business Services** (user, fund-transfer, utility-payment, core-banking) wait for: Service Registry + Config Server + MySQL.

### 6.5 Configuration Profiles

| Profile | Config Source | Usage |
|---------|--------------|-------|
| (default) | `bootstrap.yml` → `http://localhost:8090` | Local development |
| `docker` | `bootstrap-docker.yml` → `http://internet-banking-config-server:8090` | Docker Compose deployment |
| `dev` | `bootstrap-dev.yml` | Development with custom config server |

### 6.6 Docker Compose Files

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Full stack — all infrastructure + all services |
| `docker-compose-support-apps.yml` | Infrastructure only (MySQL, Keycloak, Zipkin, Config Server, Service Registry) for local development of business services |

### 6.7 Test Infrastructure

- **Test framework:** JUnit 5 (JUnit Platform)
- **Test database:** H2 in-memory (`com.h2database:h2:2.2.224`) for unit/integration tests
- **Test configs:** Separate `src/test/resources/application.yml` per service
