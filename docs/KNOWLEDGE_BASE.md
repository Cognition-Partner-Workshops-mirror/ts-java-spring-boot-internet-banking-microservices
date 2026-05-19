# Internet Banking Microservices — Application Knowledge Base

## 1. Architecture Overview

### 1.1 Service Inventory

| Service | Port | Type | Description |
|---|---|---|---|
| **internet-banking-service-registry** | 8081 | Infrastructure | Netflix Eureka server for service discovery |
| **internet-banking-config-server** | 8090 | Infrastructure | Spring Cloud Config server backed by a Git repository |
| **internet-banking-api-gateway** | 8082 | Infrastructure | Spring Cloud Gateway — single entry point with OAuth2/JWT security |
| **internet-banking-user-service** | 8083 | Business | User registration, approval, and profile management (integrates with Keycloak) |
| **internet-banking-fund-transfer-service** | 8084 | Business | Initiates and tracks fund transfers between bank accounts |
| **internet-banking-utility-payment-service** | 8085 | Business | Initiates and tracks utility bill payments |
| **core-banking-service** | 8092 | Business | Core banking engine — accounts, users, transactions, and balance management |

### 1.2 Communication Patterns

```
┌──────────┐      ┌──────────────────────┐      ┌──────────────────────────┐
│  Client   │─────▶│  API Gateway (8082)  │─────▶│  Business Services       │
│           │ JWT  │  OAuth2 Resource Srv │      │  (User / FundTransfer /  │
│           │      │                      │      │   UtilityPayment)        │
└──────────┘      └──────────────────────┘      └────────────┬─────────────┘
                                                             │ OpenFeign (REST)
                                                             ▼
                                                  ┌─────────────────────┐
                                                  │  core-banking-svc   │
                                                  │  (8092)             │
                                                  └─────────────────────┘
```

- **Client → API Gateway**: REST over HTTP with JWT bearer tokens (Keycloak-issued).
- **API Gateway → Services**: Proxied via Spring Cloud Gateway with route-based path prefixes. The gateway injects an `X-Auth-Id` header with the authenticated principal.
- **Service → Service**: Synchronous REST using **OpenFeign** clients, resolved via Eureka service names (`core-banking-service`).
- **Service → Config Server**: Each service fetches externalized configuration at startup via Spring Cloud Config (bootstrap.yml → `http://internet-banking-config-server:8090`).
- **Service → Service Registry**: All business services and the gateway register with Eureka on startup.

### 1.3 Infrastructure Components

| Component | Technology | Purpose |
|---|---|---|
| Service Discovery | Netflix Eureka | Dynamic service registration and lookup |
| Configuration Management | Spring Cloud Config (Git-backed) | Externalized, centralized configuration |
| API Gateway | Spring Cloud Gateway | Routing, security enforcement, header injection |
| Identity Provider | Keycloak 23.0.7 (PostgreSQL 15 backend) | OAuth2/OIDC, user management, realm/client config |
| Primary Database | MySQL (custom Docker image) | Persistent storage for all business services |
| Distributed Tracing | Zipkin 3 + Micrometer Tracing (Brave) | Request tracing across microservices |
| Containerization | Docker + Docker Compose | Local development and deployment orchestration |

---

## 2. Data Model Documentation

### 2.1 core-banking-service (MySQL — `banking_core_service`)

#### `banking_core_user`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `first_name` | VARCHAR(255) | | User's first name |
| `last_name` | VARCHAR(255) | | User's last name |
| `email` | VARCHAR(255) | | User email address |
| `identification_number` | VARCHAR(255) | | Government-issued ID (e.g., NIC) |

#### `banking_core_account`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `number` | VARCHAR(255) | | Account number (e.g., `100015003000`) |
| `type` | VARCHAR(255) | | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `actual_balance` | DECIMAL(19,2) | | Ledger balance |
| `available_balance` | DECIMAL(19,2) | | Available (spendable) balance |
| `user_id` | BIGINT | FK → `banking_core_user.id` | Account owner |

#### `banking_core_transaction`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `amount` | DECIMAL(19,2) | | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | NOT NULL | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | NOT NULL | Destination account number or reference |
| `transaction_id` | VARCHAR(50) | NOT NULL | UUID identifying the transaction |
| `account_id` | BIGINT | FK → `banking_core_account.id` | Associated account |

#### `banking_core_utility_account`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `number` | VARCHAR(255) | | Utility provider account number |
| `provider_name` | VARCHAR(255) | | Provider name (e.g., `VODAFONE`, `AIRTEL`) |

**Relationships:**
- `banking_core_user` 1 ──▶ N `banking_core_account` (via `user_id`)
- `banking_core_account` 1 ──▶ 1 `banking_core_transaction` (via `account_id`)

### 2.2 internet-banking-fund-transfer-service (MySQL — `banking_core_fund_transfer_service`)

#### `fund_transfer`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `from_account` | VARCHAR(255) | | Source account number |
| `to_account` | VARCHAR(255) | | Destination account number |
| `amount` | DECIMAL(19,2) | | Transfer amount |
| `transaction_reference` | VARCHAR(255) | | UUID from core banking |
| `status` | VARCHAR(255) | | Enum: `PENDING`, `SUCCESS`, `FAILED`, `PROCESSING` |
| `created_date` | TIMESTAMP | | Audit: creation time |
| `created_by` | VARCHAR(255) | | Audit: creator |
| `modified_date` | TIMESTAMP | | Audit: last modified time |
| `modified_by` | VARCHAR(255) | | Audit: last modifier |
| `version` | BIGINT | | Optimistic locking version |

### 2.3 internet-banking-user-service (MySQL — `banking_core_user_service`)

#### `user`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `auth_id` | VARCHAR(255) | | Keycloak user UUID |
| `identification` | VARCHAR(255) | | Government-issued ID (cross-references core-banking-service) |
| `status` | VARCHAR(255) | | Enum: `PENDING`, `APPROVED` |
| `created_date` | TIMESTAMP | | Audit field |
| `created_by` | VARCHAR(255) | | Audit field |
| `modified_date` | TIMESTAMP | | Audit field |
| `modified_by` | VARCHAR(255) | | Audit field |
| `version` | BIGINT | | Optimistic locking version |

### 2.4 internet-banking-utility-payment-service (MySQL — `banking_core_utility_payment_service`)

#### `utility_payment`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `provider_id` | BIGINT | | Utility provider reference ID |
| `amount` | DECIMAL(19,2) | | Payment amount |
| `reference_number` | VARCHAR(255) | | Customer reference number |
| `account` | VARCHAR(255) | | Source bank account number |
| `transaction_id` | VARCHAR(255) | | UUID from core banking |
| `status` | VARCHAR(255) | | Enum: `PROCESSING`, `SUCCESS`, `FAILED`, `PENDING` |
| `created_date` | TIMESTAMP | | Audit field |
| `created_by` | VARCHAR(255) | | Audit field |
| `modified_date` | TIMESTAMP | | Audit field |
| `modified_by` | VARCHAR(255) | | Audit field |
| `version` | BIGINT | | Optimistic locking version |

---

## 3. API Surface Map

### 3.1 API Gateway Routes (port 8082)

All requests are routed through the gateway with path-prefix stripping. The gateway enforces JWT authentication on all routes **except**:
- `POST /user/api/v1/bank-users/register` (public)
- `/actuator/**`, `/user/actuator/**`, `/fund-transfer/actuator/**`, `/banking-core/actuator/**`, `/utility-payment/actuator/**` (public)

### 3.2 core-banking-service (port 8092)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | — | `BankAccount` | Get bank account by account number |
| `GET` | `/api/v1/account/util-account/{account_name}` | — | `UtilityAccount` | Get utility account by provider name |
| `GET` | `/api/v1/user/{identification}` | — | `User` | Get user by identification number |
| `GET` | `/api/v1/user` | `Pageable` (query params) | `List<User>` | Get paginated list of users |
| `POST` | `/api/v1/transaction/fund-transfer` | `FundTransferRequest { fromAccount, toAccount, amount }` | `FundTransferResponse { message, transactionId }` | Execute a fund transfer |
| `POST` | `/api/v1/transaction/util-payment` | `UtilityPaymentRequest { providerId, amount, referenceNumber, account }` | `UtilityPaymentResponse { message, transactionId }` | Execute a utility payment |

### 3.3 internet-banking-user-service (port 8083)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | `User { email, identification, password }` | `User` | Register a new banking user (creates Keycloak account) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `UserUpdateRequest { status }` | `User` | Update user status (e.g., approve user → enables Keycloak account) |
| `GET` | `/api/v1/bank-users` | `Pageable` (query params) | `List<User>` | Get paginated list of registered users |
| `GET` | `/api/v1/bank-users/{id}` | — | `User` | Get user by internal ID |

### 3.4 internet-banking-fund-transfer-service (port 8084)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | `FundTransferRequest { fromAccount, toAccount, amount, authID }` | `FundTransferResponse { message, transactionId }` | Initiate a fund transfer (delegates to core-banking-service) |
| `GET` | `/api/v1/transfer` | `Pageable` (query params) | `List<FundTransfer>` | Get paginated list of fund transfers |

### 3.5 internet-banking-utility-payment-service (port 8085)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | `UtilityPaymentRequest { providerId, amount, referenceNumber, account }` | `UtilityPaymentResponse { message, transactionId }` | Process a utility payment (delegates to core-banking-service) |
| `GET` | `/api/v1/utility-payment` | `Pageable` (query params) | `List<UtilityPayment>` | Get paginated list of utility payments |

---

## 4. Key Business Logic Inventory

### 4.1 Fund Transfer Flow

1. **Client** sends `POST /api/v1/transfer` to the **fund-transfer-service**.
2. Fund-transfer-service creates a `FundTransferEntity` with status `PENDING` and persists it.
3. Fund-transfer-service calls **core-banking-service** via Feign at `POST /api/v1/transaction/fund-transfer`.
4. Core-banking-service:
   a. Reads the source and destination `BankAccount` from the database.
   b. **Validates balance**: source account's `actualBalance` must be ≥ transfer `amount` and > 0. If not, throws `InsufficientFundsException`.
   c. Debits the source account (subtracts from `actualBalance` and `availableBalance`).
   d. Credits the destination account (adds to `actualBalance` and `availableBalance`).
   e. Creates two `TransactionEntity` records (one debit, one credit) with type `FUND_TRANSFER`.
   f. Returns `FundTransferResponse { message, transactionId }`.
5. Fund-transfer-service updates its entity to `SUCCESS` with the `transactionReference`.

**Business Rules:**
- Insufficient funds → HTTP 400 with error code `BANKING-CORE-SERVICE-1001`.
- Account not found → HTTP 400 with error code `BANKING-CORE-SERVICE-1000`.
- The entire core-banking operation is wrapped in `@Transactional`.

### 4.2 Utility Payment Flow

1. **Client** sends `POST /api/v1/utility-payment` to the **utility-payment-service**.
2. Utility-payment-service creates a `UtilityPaymentEntity` with status `PROCESSING` and persists it.
3. Utility-payment-service calls **core-banking-service** via Feign at `POST /api/v1/transaction/util-payment`.
4. Core-banking-service:
   a. Reads the source `BankAccount` and validates balance (same rules as fund transfer).
   b. Reads the `UtilityAccount` by `providerId`.
   c. Debits the source account.
   d. Creates a `TransactionEntity` with type `UTILITY_PAYMENT`.
   e. Returns `UtilityPaymentResponse { message, transactionId }`.
5. Utility-payment-service updates its entity to `SUCCESS` with the `transactionId`.

### 4.3 User Registration Flow

1. **Client** sends `POST /api/v1/bank-users/register` (unauthenticated endpoint).
2. User-service checks if the email is already registered in Keycloak → throws `UserAlreadyRegisteredException` if duplicate.
3. User-service calls **core-banking-service** via Feign at `GET /api/v1/user/{identification}` to validate the user exists in the core banking system.
4. Validates that the email matches the core banking record → throws `InvalidEmailException` if mismatch.
5. Creates a Keycloak user representation (disabled, email unverified) with the supplied password.
6. Calls the Keycloak Admin API to create the user; expects HTTP 201.
7. Persists a local `UserEntity` with status `PENDING` and the Keycloak `authId`.

### 4.4 User Approval Flow

1. Admin sends `PATCH /api/v1/bank-users/update/{id}` with `{ "status": "APPROVED" }`.
2. User-service reads the `UserEntity`, then calls Keycloak Admin API to enable the user and mark email as verified.
3. Persists the updated status.

---

## 5. Integration Points

### 5.1 Keycloak (Identity Provider)

- **Version**: 23.0.7
- **Connection**: Keycloak Admin Client SDK (`org.keycloak:keycloak-admin-client:24.0.4`)
- **Configuration** (externalized via Config Server):
  - `app.config.keycloak.server-url` — Keycloak base URL
  - `app.config.keycloak.realm` — Target realm
  - `app.config.keycloak.clientId` — Client ID for admin operations
  - `app.config.keycloak.client-secret` — Client secret
  - Grant type: `client_credentials`
- **Used by**: `internet-banking-user-service` only
- **Operations**: Create user, read user by email/ID, update user (enable/disable)
- **JWT Validation**: The API Gateway validates JWTs via `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` (pointing to Keycloak's JWKS endpoint).

### 5.2 RabbitMQ (Message Broker)

- **Status**: Referenced in the README as part of the architecture for notification services.
- **Actual implementation**: **Not present in the codebase**. No RabbitMQ dependency exists in any `build.gradle`. The notification service is listed as "PENDING Development."

### 5.3 Zipkin (Distributed Tracing)

- **Version**: Zipkin 3 (Docker image `openzipkin/zipkin:3`)
- **Port**: 9411
- **Integration**: All business services and the gateway include `io.micrometer:micrometer-tracing-bridge-brave` and `io.zipkin.reporter2:zipkin-reporter-brave` as dependencies. Trace context propagation is automatic via Micrometer/Brave.
- **Configuration**: Tracing configuration is externalized via the Config Server.

### 5.4 Database Connections

| Service | Database | Schema | Driver | Migration Tool |
|---|---|---|---|---|
| core-banking-service | MySQL | `banking_core_service` | `com.mysql:mysql-connector-j:8.4.0` | Flyway (`org.flywaydb:flyway-core:10.12.0`) |
| internet-banking-fund-transfer-service | MySQL | `banking_core_fund_transfer_service` | `com.mysql:mysql-connector-j:8.4.0` | JPA DDL Auto (implied) |
| internet-banking-user-service | MySQL | `banking_core_user_service` | `com.mysql:mysql-connector-j:8.4.0` | JPA DDL Auto (implied) |
| internet-banking-utility-payment-service | MySQL | `banking_core_utility_payment_service` | `com.mysql:mysql-connector-j:8.4.0` | JPA DDL Auto (implied) |

**Database credentials** are managed via the MySQL Docker image init script (`privileges.sql`):
- User: `javatodev_development`
- Databases are created at container startup.

### 5.5 Spring Cloud Config (Git-backed)

- **Config Server**: Fetches configuration from `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` (branch: `main`, path: `configuration/`).
- **Client Services**: Each service connects to the config server at bootstrap via `spring.cloud.config.uri`.
- **Profile Activation**: Docker profile (`-Dspring.profiles.active=docker`) switches to container-friendly URLs.

### 5.6 Netflix Eureka (Service Discovery)

- **Server**: `internet-banking-service-registry` on port 8081.
- **Clients**: All business services and the gateway register with Eureka. Feign clients resolve service names (e.g., `core-banking-service`) via Eureka.

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build Tool**: Gradle 8.6 (per-service wrapper — no root `build.gradle`)
- **Shared Library**: `banking-common` (Gradle composite build — must be built first)
- **Java**: 21 (source compatibility)
- **Spring Boot**: 3.2.4
- **Spring Cloud**: 2023.0.0

### 6.2 Per-Service Build

Each service has its own `gradlew`:
```bash
cd <service-dir> && ./gradlew build
```

### 6.3 Docker Images

Each service has a `Dockerfile` based on `eclipse-temurin:21.0.2_13-jre-alpine`:
1. Copies the fat JAR from `build/libs/`.
2. Includes `wait-for-it.sh` for startup ordering.
3. Sets `spring.profiles.active=docker` at entrypoint.

### 6.4 Docker Compose

Two compose files exist in `docker-compose/`:

| File | Purpose |
|---|---|
| `docker-compose.yml` | Full stack: infrastructure (Keycloak, PostgreSQL, MySQL, Zipkin) + all application services |
| `docker-compose-support-apps.yml` | Infrastructure only (for local development — run services from IDE) |

**Network**: Custom bridge `javatodev_ib_network` with subnet `172.25.0.0/16`. Each service has a static IP.

**Startup Ordering**: Application services use `wait-for-it.sh` to wait for:
1. Service Registry (8081)
2. Config Server (8090)
3. MySQL (3306) — for database-dependent services

### 6.5 CI/CD

- No CI/CD pipeline configuration exists in the repository (no GitHub Actions, Jenkinsfile, or similar).
- The `.github/` directory contains only `FUNDING.yml`.

### 6.6 Schema Management

- **core-banking-service**: Uses **Flyway** with SQL migrations in `src/main/resources/db/migration/`:
  - `V1.0.20210427174638` — Base tables (user, account, utility account)
  - `V1.0.20210427174721` — Seed data (test users, accounts, utility providers)
  - `V1.0.20210429210839` — Transaction table
- **Other services**: Rely on JPA/Hibernate DDL auto-generation (no explicit migration tool).

### 6.7 Testing

- **core-banking-service**: Has unit tests for `AccountService`, `UserService`, and `TransactionService` using JUnit 5 + Mockito. Uses H2 in-memory database for the test profile.
- **Other services**: Only contain empty Spring Boot context-load tests (`*ApplicationTests.java`).
