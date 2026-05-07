# Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Summary

This is a **Java 21 / Spring Boot 3.2.4** internet banking application composed of **6 microservices** following a standard Spring Cloud architecture pattern. The system simulates core banking operations including user management, fund transfers, and utility payments.

### 1.2 Microservices Inventory

| Service | Port | Description |
|---|---|---|
| **core-banking-service** | 8092 | Acts as the internal banking core — manages accounts, users, and processes transactions (fund transfers, utility payments). This is the system-of-record for financial data. |
| **internet-banking-user-service** | 8083 | Internet banking user registration and management. Integrates with Keycloak for identity management and delegates to core-banking-service for user validation. |
| **internet-banking-fund-transfer-service** | 8084 | Orchestrates fund transfer requests from the internet banking layer. Persists transfer records locally and delegates actual transaction processing to core-banking-service. |
| **internet-banking-utility-payment-service** | 8085 | Orchestrates utility payment requests. Persists payment records locally and delegates actual payment processing to core-banking-service. |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway — single entry point for all client requests. Handles OAuth2/JWT authentication via Keycloak and routes requests to downstream services. |
| **internet-banking-config-server** | 8090 | Spring Cloud Config Server — provides externalized configuration from a Git repository for all services. |
| **internet-banking-service-registry** | 8081 | Netflix Eureka Server — service discovery registry. All services register here and discover each other by logical name. |

### 1.3 Communication Patterns

```
┌─────────────┐     JWT/OAuth2     ┌──────────────────┐
│   Client     │ ────────────────► │   API Gateway     │
│  (Browser)   │                   │   (port 8082)     │
└─────────────┘                   └────────┬──────────┘
                                           │ Routes via Eureka
                    ┌──────────────────────┼──────────────────────┐
                    │                      │                      │
                    ▼                      ▼                      ▼
          ┌─────────────────┐  ┌──────────────────┐  ┌─────────────────────┐
          │  User Service   │  │ Fund Transfer    │  │ Utility Payment     │
          │  (port 8083)    │  │ Service (8084)   │  │ Service (8085)      │
          └───────┬─────────┘  └────────┬─────────┘  └──────────┬──────────┘
                  │ OpenFeign           │ OpenFeign              │ OpenFeign
                  ▼                     ▼                        ▼
          ┌──────────────────────────────────────────────────────────┐
          │                  Core Banking Service                    │
          │                    (port 8092)                           │
          └──────────────────────────────────────────────────────────┘
```

- **Client → Gateway**: HTTP/REST over HTTPS with JWT Bearer tokens (Keycloak-issued).
- **Gateway → Downstream Services**: HTTP routing via Spring Cloud Gateway with Eureka-based service discovery. The gateway injects an `X-Auth-Id` header containing the authenticated principal's name.
- **Internet Banking Services → Core Banking**: Synchronous REST calls via **Spring Cloud OpenFeign** clients. Services discover `core-banking-service` by its Eureka-registered name.
- **Service Discovery**: All services register with the **Eureka Service Registry** and use it for name-based resolution (no hardcoded URLs between services).
- **Configuration**: All services fetch configuration from the **Config Server** at startup via Spring Cloud Config (bootstrap.yml points to `http://localhost:8090` or `http://internet-banking-config-server:8090` for Docker).

### 1.4 Infrastructure Components

| Component | Technology | Purpose |
|---|---|---|
| Service Registry | Netflix Eureka | Service discovery and registration |
| Config Server | Spring Cloud Config | Externalized configuration from Git repo |
| API Gateway | Spring Cloud Gateway | Request routing, authentication, header injection |
| Identity Provider | Keycloak 23.0.7 | OAuth2/OIDC authentication, user identity management |
| Database | MySQL 8 | Persistent storage for all business services |
| Distributed Tracing | Zipkin 3 + Micrometer Brave | Request tracing across microservices |
| Message Queue | RabbitMQ (planned) | Async notification delivery (not yet implemented) |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL: `banking_core_service`)

#### `banking_core_user`
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `first_name` | VARCHAR(255) | nullable | User's first name |
| `last_name` | VARCHAR(255) | nullable | User's last name |
| `email` | VARCHAR(255) | nullable | User's email address |
| `identification_number` | VARCHAR(255) | nullable | National ID / identification number |

#### `banking_core_account`
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `number` | VARCHAR(255) | nullable | Account number (e.g., `100015003000`) |
| `type` | VARCHAR(255) | nullable | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | nullable | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `actual_balance` | DECIMAL(19,2) | nullable | Actual ledger balance |
| `available_balance` | DECIMAL(19,2) | nullable | Available balance for transactions |
| `user_id` | BIGINT | FK → `banking_core_user.id` | Account owner |

#### `banking_core_transaction`
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `amount` | DECIMAL(19,2) | nullable | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | NOT NULL | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | NOT NULL | Reference (destination account or utility ref) |
| `transaction_id` | VARCHAR(50) | NOT NULL | UUID-based transaction identifier |
| `account_id` | BIGINT | FK → `banking_core_account.id` | Associated account |

#### `banking_core_utility_account`
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `number` | VARCHAR(255) | nullable | Utility provider account number |
| `provider_name` | VARCHAR(255) | nullable | Provider name (e.g., `VODAFONE`, `AIRTEL`) |

#### Relationships
```
banking_core_user (1) ──────< (N) banking_core_account
banking_core_account (1) ──────< (1) banking_core_transaction
```

### 2.2 Internet Banking User Service (MySQL: `banking_core_user_service`)

#### `user` (JPA-managed, auto-created via Hibernate DDL)
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK) | Surrogate key |
| `auth_id` | VARCHAR | Keycloak user UUID |
| `identification` | VARCHAR | National ID (links to core banking user) |
| `status` | VARCHAR (Enum) | `PENDING`, `APPROVED` |
| `created_by` | VARCHAR | Audit: creator |
| `created_at` | TIMESTAMP | Audit: creation time |
| `updated_by` | VARCHAR | Audit: last modifier |
| `updated_at` | TIMESTAMP | Audit: last update time |

### 2.3 Internet Banking Fund Transfer Service (MySQL: `banking_core_fund_transfer_service`)

#### `fund_transfer` (JPA-managed)
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK) | Surrogate key |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `status` | VARCHAR (Enum) | `PENDING`, `SUCCESS`, `FAILED` |
| `transaction_reference` | VARCHAR | Core banking transaction ID |
| `created_by` / `created_at` | audit fields | Audit trail |
| `updated_by` / `updated_at` | audit fields | Audit trail |

### 2.4 Internet Banking Utility Payment Service (MySQL: `banking_core_utility_payment_service`)

#### `utility_payment` (JPA-managed)
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK) | Surrogate key |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Customer reference number |
| `account` | VARCHAR | Source bank account number |
| `transaction_id` | VARCHAR | Core banking transaction ID |
| `status` | VARCHAR (Enum) | `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_by` / `created_at` | audit fields | Audit trail |
| `updated_by` / `updated_at` | audit fields | Audit trail |

---

## 3. API Surface Map

### 3.1 API Gateway Routes

All requests enter through the gateway (port `8082`) and are routed by path prefix:

| Route Prefix | Target Service | Auth Required |
|---|---|---|
| `/user/**` | internet-banking-user-service | No (registration), Yes (others) |
| `/fund-transfer/**` | internet-banking-fund-transfer-service | Yes |
| `/utility-payment/**` | internet-banking-utility-payment-service | Yes |
| `/banking-core/**` | core-banking-service | Yes |
| `/actuator/**` | Various | No |

### 3.2 Core Banking Service Endpoints (port 8092)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | — | `BankAccount` |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | — | `UtilityAccount` |
| `GET` | `/api/v1/user/{identification}` | Get user by identification number | — | `User` |
| `GET` | `/api/v1/user` | List users (paginated) | — | `List<User>` |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer | `FundTransferRequest` | `FundTransferResponse` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `UtilityPaymentRequest` | `UtilityPaymentResponse` |

#### Request/Response Shapes

**FundTransferRequest**
```json
{
  "fromAccount": "string",
  "toAccount": "string",
  "amount": 0.00
}
```

**FundTransferResponse**
```json
{
  "message": "string",
  "transactionId": "string (UUID)"
}
```

**UtilityPaymentRequest**
```json
{
  "providerId": 0,
  "amount": 0.00,
  "referenceNumber": "string",
  "account": "string"
}
```

**UtilityPaymentResponse**
```json
{
  "message": "string",
  "transactionId": "string (UUID)"
}
```

**BankAccount**
```json
{
  "id": 0,
  "number": "string",
  "type": "SAVINGS_ACCOUNT | FIXED_DEPOSIT | LOAN_ACCOUNT",
  "status": "PENDING | ACTIVE | DORMANT | BLOCKED",
  "availableBalance": 0.00,
  "actualBalance": 0.00,
  "user": { "id": 0, "firstName": "string", "lastName": "string", "email": "string", "identificationNumber": "string" }
}
```

### 3.3 Internet Banking User Service Endpoints (port 8083)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Register new internet banking user | `User` | `User` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user (e.g., approve) | `UserUpdateRequest` | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | — | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | — | `User` |

**User (Request/Response)**
```json
{
  "id": 0,
  "email": "string",
  "identification": "string",
  "password": "string (write-only)",
  "authId": "string",
  "status": "PENDING | APPROVED"
}
```

**UserUpdateRequest**
```json
{
  "status": "PENDING | APPROVED"
}
```

### 3.4 Internet Banking Fund Transfer Service Endpoints (port 8084)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `FundTransferRequest` | `FundTransferResponse` |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | — | `List<FundTransfer>` |

### 3.5 Internet Banking Utility Payment Service Endpoints (port 8085)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `UtilityPaymentRequest` | `UtilityPaymentResponse` |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | — | `List<UtilityPayment>` |

### 3.6 Infrastructure Endpoints

| Service | Endpoint | Purpose |
|---|---|---|
| Service Registry | `GET /` (port 8081) | Eureka dashboard |
| Config Server | `GET /{application}/{profile}` (port 8090) | Retrieve configuration |
| All services | `GET /actuator/**` | Health checks, metrics, info |

---

## 4. Key Business Logic Inventory

### 4.1 Fund Transfer Rules (`TransactionService.fundTransfer`)

1. **Account Lookup**: Both source (`fromAccount`) and destination (`toAccount`) accounts are fetched from the database. If either is not found, an `EntityNotFoundException` is thrown.
2. **Balance Validation**: The source account's `actualBalance` must be ≥ the transfer `amount` and must be non-negative. Violations throw `InsufficientFundsException`.
3. **Debit Source**: Source account's `actualBalance` and `availableBalance` are decremented by the transfer amount.
4. **Credit Destination**: Destination account's `actualBalance` and `availableBalance` are incremented.
5. **Transaction Records**: Two `TransactionEntity` records are created — one debit (negative amount) on the source account, one credit (positive amount) on the destination account — both sharing the same `transactionId` (UUID).
6. **Atomicity**: The entire operation runs within a `@Transactional` boundary.

### 4.2 Utility Payment Processing (`TransactionService.utilPayment`)

1. **Account Lookup**: The payer's bank account is fetched; utility provider account is fetched by `providerId`.
2. **Balance Validation**: Same rules as fund transfer.
3. **Debit**: Payer's account balances are decremented.
4. **Transaction Record**: A single `TransactionEntity` is created with type `UTILITY_PAYMENT`.
5. **No Provider Credit**: The utility provider's account is looked up but not credited (simulated — real integration would call a third-party API).

### 4.3 User Registration Flow (`UserService.createUser` in user-service)

1. **Duplicate Check**: Keycloak is queried to ensure the email is not already registered.
2. **Core Banking Validation**: The user's `identification` is validated against the core banking service via Feign. The email must match the core banking record.
3. **Keycloak Registration**: A new Keycloak user is created with email, name, and password credentials. The user starts as `emailVerified=false`, `enabled=false`.
4. **Local Persistence**: On successful Keycloak creation (HTTP 201), the user is saved locally with `status=PENDING`.

### 4.4 User Approval Flow (`UserService.updateUser`)

1. When `status` is set to `APPROVED`, the corresponding Keycloak user is updated to `enabled=true`, `emailVerified=true`.
2. The local user entity's status is updated to `APPROVED`.

### 4.5 Internet Banking Service Orchestration Pattern

Both the Fund Transfer Service and Utility Payment Service follow the same pattern:
1. **Save initial record** with `PENDING`/`PROCESSING` status.
2. **Call core-banking-service** via Feign to process the actual transaction.
3. **Update local record** with `SUCCESS` status and the core banking transaction reference.
4. **Return response** to the caller.

> **Note**: There is no compensation logic (saga) if the Feign call succeeds but the local update fails, or vice versa.

---

## 5. Integration Points

### 5.1 Keycloak (Identity Provider)

- **Version**: 23.0.7
- **Connection**: User service connects via `keycloak-admin-client:24.0.4` using client credentials grant.
- **Configuration Properties** (from Spring Cloud Config):
  - `app.config.keycloak.server-url` — Keycloak base URL
  - `app.config.keycloak.realm` — Target realm
  - `app.config.keycloak.clientId` — Admin client ID
  - `app.config.keycloak.client-secret` — Admin client secret
- **Operations**: Create user, update user, search users by email, read user by auth ID.
- **Gateway Integration**: The API Gateway validates JWT tokens using `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`.

### 5.2 RabbitMQ (Message Queue)

- **Status**: Referenced in architecture documentation but **not yet implemented** in code.
- **Intended Use**: Notification service would consume messages from RabbitMQ to send notifications for fund transfers and utility payments.

### 5.3 Zipkin (Distributed Tracing)

- **Version**: Zipkin 3
- **Port**: 9411
- **Integration**: Via `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` dependencies in all business services.
- **Coverage**: All 4 business services + API Gateway include tracing dependencies. Feign calls are traced via `feign-micrometer`.

### 5.4 Database Connections

| Service | Database | Driver |
|---|---|---|
| core-banking-service | `banking_core_service` (MySQL) | `mysql-connector-j:8.4.0` |
| internet-banking-user-service | `banking_core_user_service` (MySQL) | `mysql-connector-j:8.4.0` |
| internet-banking-fund-transfer-service | `banking_core_fund_transfer_service` (MySQL) | `mysql-connector-j:8.4.0` |
| internet-banking-utility-payment-service | `banking_core_utility_payment_service` (MySQL) | `mysql-connector-j:8.4.0` |

- Database credentials are managed via Spring Cloud Config (externalized configuration).
- Core banking service uses **Flyway** (`flyway-core:10.12.0`, `flyway-mysql:10.12.0`) for schema migrations.
- Other services rely on Hibernate `ddl-auto` for schema management.

### 5.5 Spring Cloud Config (Externalized Configuration)

- **Config Repository**: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch**: `main`
- **Search Path**: `configuration/`
- Each service fetches its configuration by `spring.application.name` (e.g., `core-banking-service.yml`).

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build Tool**: Gradle (per-service, no multi-project root build file)
- **Java**: 21 (source compatibility)
- **Spring Boot**: 3.2.4
- **Spring Cloud**: 2023.0.0
- Each service is an independent Gradle project with its own `build.gradle`, `gradlew`, and `settings.gradle`.

### 6.2 Docker

Each service has its own `Dockerfile`:
```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
COPY wait-for-it.sh wait-for-it.sh
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

- Base image: `eclipse-temurin:21.0.2_13-jre-alpine`
- Startup script: `wait-for-it.sh` ensures dependent services (Eureka, Config Server, MySQL) are available before the application starts.
- Docker profile: Each service activates `spring.profiles.active=docker` which switches the Config Server URI to the Docker network hostname.

### 6.3 Docker Compose

Two compose files in `docker-compose/`:

1. **`docker-compose.yml`** — Full stack (all 6 services + infrastructure)
2. **`docker-compose-support-apps.yml`** — Infrastructure only (Zipkin, Keycloak, MySQL, Config Server, Service Registry)

**Network**: All containers are on a custom bridge network (`javatodev_ib_network`, subnet `172.25.0.0/16`) with static IP assignments.

**Startup Order** (managed via `wait-for-it.sh`):
1. MySQL, Keycloak DB (PostgreSQL), Zipkin
2. Keycloak (depends on PostgreSQL)
3. Config Server
4. Service Registry
5. API Gateway, User Service, Fund Transfer Service, Utility Payment Service, Core Banking Service (all wait for Config Server + Service Registry + MySQL)

### 6.4 Database Initialization

- **MySQL**: Custom Dockerfile in `docker-compose/mysql/` runs `privileges.sql` on first startup to create the dev user (`javatodev_development`) and the 4 application databases.
- **Keycloak**: Realm data is imported from `docker-compose/keycloak/` volume mount on startup.
- **Core Banking**: Flyway migrations create tables and seed test data (4 users, 14 accounts, 6 utility providers).

### 6.5 Profiles

| Profile | Config Server URI | Usage |
|---|---|---|
| (default) | `http://localhost:8090` | Local development |
| `dev` | `http://192.168.1.5:8090` | Development environment |
| `docker` | `http://internet-banking-config-server:8090` | Docker Compose deployment |
