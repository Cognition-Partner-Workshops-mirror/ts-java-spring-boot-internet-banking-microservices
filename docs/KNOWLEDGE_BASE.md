# Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Summary

This is a **Java 21 / Spring Boot 3.2.4** internet banking platform composed of **6 microservices** following a standard Spring Cloud architecture. The system uses Netflix Eureka for service discovery, Spring Cloud Config for centralized configuration, and Spring Cloud Gateway as the API entry point.

### 1.2 Microservices Inventory

| Service | Port | Description |
|---------|------|-------------|
| **internet-banking-service-registry** | 8081 | Netflix Eureka discovery server |
| **internet-banking-config-server** | 8090 | Spring Cloud Config server (Git-backed) |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway with OAuth2/Keycloak security |
| **internet-banking-user-service** | 8083 | User registration, profile management, Keycloak integration |
| **internet-banking-fund-transfer-service** | 8084 | Account-to-account fund transfers |
| **internet-banking-utility-payment-service** | 8085 | Third-party utility bill payments |
| **core-banking-service** | 8092 | System of record for accounts, users, and ledger transactions |

### 1.3 Communication Patterns

```
                        +-----------------+
                        |   Keycloak IAM  |
                        |   (Port 8080)   |
                        +--------+--------+
                                 |
                                 | JWT validation
                                 v
+-----------+    +------------------------------+    +---------------------+
|  Client   |--->|  API Gateway (8082)          |--->|  Service Registry   |
|           |    |  - OAuth2 Resource Server    |    |  Eureka (8081)      |
+-----------+    |  - X-Auth-Id header inject   |    +---------------------+
                 +----+--------+--------+-------+              ^
                      |        |        |                      | registers
                      v        v        v                      |
               +------+  +----+---+  +-+--------+    +--------+--------+
               | User |  | Fund   |  | Utility  |    | Config Server   |
               | Svc  |  | Xfer   |  | Payment  |    | (8090)          |
               | 8083 |  | 8084   |  | 8085     |    | Git-backed      |
               +--+---+  +---+----+  +----+-----+    +-----------------+
                  |           |            |
                  |     OpenFeign     OpenFeign
                  |           |            |
                  v           v            v
               +--+-----+----+------+-----+-+
               |       core-banking-service  |
               |          (8092)             |
               +-----------------------------+
                            |
                            v
                     +------+------+
                     |    MySQL    |
                     |  (4 DBs)   |
                     +-------------+
```

**Inter-service communication:**
- **Synchronous REST via OpenFeign**: User Service, Fund Transfer Service, and Utility Payment Service all call Core Banking Service via Feign clients resolved through Eureka.
- **Gateway routing**: API Gateway routes requests by path prefix to downstream services (e.g., `/user/**` -> User Service, `/fund-transfer/**` -> Fund Transfer Service).
- **Auth propagation**: Gateway extracts the JWT principal name and injects it as `X-Auth-Id` header. Downstream services read this header via `AppAuthUserFilter` servlet filter.

### 1.4 Infrastructure Components

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Service Discovery | Netflix Eureka | Dynamic service registration and lookup |
| Configuration | Spring Cloud Config | Centralized config via Git repository (`internet-banking-microservices-configurations`) |
| API Gateway | Spring Cloud Gateway | Single entry point, OAuth2 enforcement, header propagation |
| Identity Provider | Keycloak 23.0.7 | User authentication, realm/client management, JWT issuance |
| Database | MySQL 8.x | Persistent storage (4 separate databases) |
| Database (Keycloak) | PostgreSQL 15 | Keycloak internal storage |
| Distributed Tracing | Zipkin 3 | Request tracing across service boundaries (via Micrometer/Brave) |
| Schema Migration | Flyway 10.12 | Database version control (core-banking-service only) |
| Networking | Docker bridge (172.25.0.0/16) | Fixed-IP container networking |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL: `banking_core_service`)

#### `banking_core_user`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Internal user ID |
| `first_name` | VARCHAR(255) | User's first name |
| `last_name` | VARCHAR(255) | User's last name |
| `email` | VARCHAR(255) | Email address |
| `identification_number` | VARCHAR(255) | National ID / NIC number |

#### `banking_core_account`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Internal account ID |
| `number` | VARCHAR(255) | Account number (e.g., `100015003000`) |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `actual_balance` | DECIMAL(19,2) | Ledger balance |
| `available_balance` | DECIMAL(19,2) | Available/cleared balance |
| `user_id` | BIGINT (FK -> `banking_core_user.id`) | Account owner |

#### `banking_core_transaction`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Transaction record ID |
| `amount` | DECIMAL(19,2) | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Counterparty account or reference |
| `transaction_id` | VARCHAR(50) | UUID transaction identifier |
| `account_id` | BIGINT (FK -> `banking_core_account.id`) | Associated account |

#### `banking_core_utility_account`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Utility provider ID |
| `number` | VARCHAR(255) | Provider account number |
| `provider_name` | VARCHAR(255) | Provider name (e.g., `VODAFONE`, `AIRTEL`) |

**Relationships:**
- `banking_core_user` 1:N `banking_core_account`
- `banking_core_account` 1:N `banking_core_transaction`

### 2.2 Internet Banking User Service (MySQL: `banking_core_user_service`)

#### `user`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Internal user ID |
| `auth_id` | VARCHAR(255) | Keycloak user UUID |
| `identification` | VARCHAR(255) | National ID / NIC number |
| `status` | VARCHAR(255) | Enum: `PENDING`, `APPROVED` |
| `created_at` | TIMESTAMP | Audit: creation time (inherited from `AuditAware`) |
| `updated_at` | TIMESTAMP | Audit: last update time (inherited from `AuditAware`) |

### 2.3 Fund Transfer Service (MySQL: `banking_core_fund_transfer_service`)

#### `fund_transfer`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Transfer record ID |
| `transaction_reference` | VARCHAR(255) | UUID from core banking response |
| `from_account` | VARCHAR(255) | Source account number |
| `to_account` | VARCHAR(255) | Destination account number |
| `amount` | DECIMAL(19,2) | Transfer amount |
| `status` | VARCHAR(255) | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_at` / `updated_at` | TIMESTAMP | Audit fields (inherited from `AuditAware`) |

### 2.4 Utility Payment Service (MySQL: `banking_core_utility_payment_service`)

#### `utility_payment`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Payment record ID |
| `provider_id` | BIGINT | Utility provider reference |
| `amount` | DECIMAL(19,2) | Payment amount |
| `reference_number` | VARCHAR(255) | Bill reference number |
| `account` | VARCHAR(255) | Payer's account number |
| `transaction_id` | VARCHAR(255) | UUID from core banking response |
| `status` | VARCHAR(255) | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_at` / `updated_at` | TIMESTAMP | Audit fields (inherited from `AuditAware`) |

---

## 3. API Surface Map

### 3.1 API Gateway Routes (Port 8082)

All client-facing requests enter through the gateway. The gateway strips the prefix and forwards to the appropriate service via Eureka. JWT authentication is enforced on all routes except user registration and actuator endpoints.

### 3.2 Core Banking Service Endpoints (Port 8092)

| Method | Path | Request Body | Response | Description |
|--------|------|-------------|----------|-------------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | - | `BankAccount` | Retrieve bank account by number |
| `GET` | `/api/v1/account/util-account/{account_name}` | - | `UtilityAccount` | Retrieve utility account by provider name |
| `GET` | `/api/v1/user/{identification}` | - | `User` | Get user by identification number |
| `GET` | `/api/v1/user` | Pageable params | `List<User>` | List users (paginated) |
| `POST` | `/api/v1/transaction/fund-transfer` | `FundTransferRequest` | `FundTransferResponse` | Process fund transfer |
| `POST` | `/api/v1/transaction/util-payment` | `UtilityPaymentRequest` | `UtilityPaymentResponse` | Process utility payment |

**Key DTOs:**

```
FundTransferRequest { fromAccount: String, toAccount: String, amount: BigDecimal }
FundTransferResponse { message: String, transactionId: String }
UtilityPaymentRequest { providerId: Long, amount: BigDecimal, referenceNumber: String, account: String }
UtilityPaymentResponse { message: String, transactionId: String }
```

### 3.3 Internet Banking User Service Endpoints (Port 8083)

| Method | Path | Request Body | Response | Description |
|--------|------|-------------|----------|-------------|
| `POST` | `/api/v1/bank-users/register` | `User` | `User` | Register new banking user |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `UserUpdateRequest` | `User` | Update user (e.g., approve) |
| `GET` | `/api/v1/bank-users` | Pageable params | `List<User>` | List all users (paginated) |
| `GET` | `/api/v1/bank-users/{id}` | - | `User` | Get user by internal ID |

**Key DTOs:**

```
User { id: Long, email: String, identification: String, password: String, authId: String, status: Status }
UserUpdateRequest { status: Status }
Status: PENDING | APPROVED
```

### 3.4 Fund Transfer Service Endpoints (Port 8084)

| Method | Path | Request Body | Response | Description |
|--------|------|-------------|----------|-------------|
| `POST` | `/api/v1/transfer` | `FundTransferRequest` | `FundTransferResponse` | Initiate fund transfer |
| `GET` | `/api/v1/transfer` | Pageable params | `List<FundTransfer>` | List fund transfers (paginated) |

**Key DTOs:**

```
FundTransferRequest { fromAccount: String, toAccount: String, amount: BigDecimal }
FundTransferResponse { message: String, transactionId: String }
```

### 3.5 Utility Payment Service Endpoints (Port 8085)

| Method | Path | Request Body | Response | Description |
|--------|------|-------------|----------|-------------|
| `POST` | `/api/v1/utility-payment` | `UtilityPaymentRequest` | `UtilityPaymentResponse` | Process utility payment |
| `GET` | `/api/v1/utility-payment` | Pageable params | `List<UtilityPayment>` | List utility payments (paginated) |

**Key DTOs:**

```
UtilityPaymentRequest { providerId: Long, amount: BigDecimal, referenceNumber: String, account: String }
UtilityPaymentResponse { message: String, transactionId: String }
```

### 3.6 Infrastructure Endpoints

| Service | Path | Description |
|---------|------|-------------|
| All services | `/actuator/**` | Spring Boot Actuator (health, info, metrics) |
| Service Registry | `http://localhost:8081/` | Eureka dashboard |
| Config Server | `http://localhost:8090/{app}/{profile}` | Configuration retrieval |
| Zipkin | `http://localhost:9411` | Trace visualization UI |
| Keycloak | `http://localhost:8080` | IAM admin console |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

1. Client calls `POST /user/api/v1/bank-users/register` (public endpoint, no JWT required).
2. User Service checks if email already exists in Keycloak. If so, throws `UserAlreadyRegisteredException`.
3. User Service calls Core Banking Service via Feign to verify the user exists by identification number.
4. Validates that the email matches the core banking record.
5. Creates a Keycloak user with `enabled=false` and `emailVerified=false`.
6. Persists a local user record with `status=PENDING` and the Keycloak `authId`.
7. Admin later calls `PATCH /update/{id}` with `status=APPROVED` to enable the Keycloak user.

### 4.2 Fund Transfer Rules

1. Client calls Fund Transfer Service which persists a `PENDING` transfer record.
2. Fund Transfer Service delegates to Core Banking Service via Feign (`POST /api/v1/transaction/fund-transfer`).
3. Core Banking validates:
   - Both source and destination accounts exist (throws `EntityNotFoundException` otherwise).
   - Source account has sufficient funds: `actualBalance >= 0 AND actualBalance >= amount` (throws `InsufficientFundsException` otherwise).
4. Core Banking executes the transfer within a `@Transactional` boundary:
   - Debits source: `actualBalance -= amount`, `availableBalance = actualBalance - amount`.
   - Credits destination: `actualBalance += amount`, `availableBalance = actualBalance + amount`.
   - Creates two `TransactionEntity` records (one debit, one credit) with a shared `transactionId` UUID.
5. Fund Transfer Service updates local record to `SUCCESS` with the transaction reference.

### 4.3 Utility Payment Processing

1. Client calls Utility Payment Service which persists a `PROCESSING` payment record.
2. Utility Payment Service delegates to Core Banking Service via Feign (`POST /api/v1/transaction/util-payment`).
3. Core Banking validates source account balance (same rules as fund transfer).
4. Core Banking looks up the utility provider by `providerId`.
5. Debits the payer's account and creates a `UTILITY_PAYMENT` transaction record.
6. Utility Payment Service updates local record to `SUCCESS`.

### 4.4 User Management (Core Banking)

- Users in core banking are pre-seeded via Flyway migrations with identification numbers.
- The `readUser` endpoint retrieves users by `identificationNumber` (NIC).
- Pagination is supported via Spring Data `Pageable`.

---

## 5. Integration Points

### 5.1 Keycloak (IAM)

- **Version:** 23.0.7
- **Connection:** User Service connects via `keycloak-admin-client:24.0.4` using client credentials grant.
- **Configuration:** Server URL, realm, client ID, and client secret sourced from `app.config.keycloak.*` properties.
- **Operations:** Create user, update user (enable/verify email), search by email, read user by ID.
- **Realm data:** Pre-imported via volume mount (`docker-compose/keycloak/`).
- **JWT validation:** API Gateway validates JWTs using the `jwk-set-uri` from Keycloak.

### 5.2 RabbitMQ

- **Status:** Referenced in README as a planned integration for notification service.
- **Current state:** Not implemented in the codebase. No RabbitMQ dependencies in any `build.gradle` file. The notification service is listed as "PENDING Development."

### 5.3 Zipkin (Distributed Tracing)

- **Version:** 3
- **Connection:** All business services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` dependencies.
- **Configuration:** Tracing configuration is managed via Spring Cloud Config server (external Git repo).
- **Port:** 9411

### 5.4 Database Connections

| Service | Database | Schema | Driver |
|---------|----------|--------|--------|
| Core Banking | MySQL | `banking_core_service` | `mysql-connector-j:8.4.0` |
| User Service | MySQL | `banking_core_user_service` | `mysql-connector-j:8.4.0` |
| Fund Transfer | MySQL | `banking_core_fund_transfer_service` | `mysql-connector-j:8.4.0` |
| Utility Payment | MySQL | `banking_core_utility_payment_service` | `mysql-connector-j:8.4.0` |
| Keycloak | PostgreSQL 15 | `keycloak` | (internal) |

- All MySQL databases are created by the `privileges.sql` init script on the shared MySQL container.
- Connection details (URL, credentials) are managed via Spring Cloud Config.
- Test profiles use H2 in-memory databases.

### 5.5 Spring Cloud Config Server

- **Backend:** Git repository at `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch:** `main`
- **Path:** `configuration/`
- **Bootstrap:** Each service uses `bootstrap.yml` / `bootstrap-docker.yml` to locate the config server before loading its main configuration.

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build tool:** Gradle (wrapper included per service, no root-level multi-project build)
- **Java version:** 21 (`sourceCompatibility = '21'`)
- **Spring Boot:** 3.2.4
- **Spring Cloud:** 2023.0.0

Each service is an independent Gradle project with its own `build.gradle`, `gradlew`, and `settings.gradle`.

### 6.2 Docker

Each service has a `Dockerfile` and a `wait-for-it.sh` script for startup ordering. The Docker Compose file defines:

- **Support services:** Zipkin, Keycloak, PostgreSQL, MySQL
- **Application services:** Config Server, Service Registry, API Gateway, User Service, Fund Transfer Service, Utility Payment Service, Core Banking Service
- **Network:** Custom bridge network `javatodev_ib_network` with fixed IP assignments (`172.25.0.2` - `172.25.0.12`)
- **Startup ordering:** `wait-for-it.sh` ensures services start only after their dependencies (Service Registry, Config Server, MySQL) are available.
- **Profiles:** Docker containers use `-Dspring.profiles.active=docker` to load Docker-specific bootstrap configuration.

### 6.3 Configuration Profiles

| Profile | Bootstrap File | Config Server URI |
|---------|---------------|-------------------|
| default | `bootstrap.yml` | `http://localhost:8090` |
| dev | `bootstrap-dev.yml` | `http://localhost:8090` |
| docker | `bootstrap-docker.yml` | `http://internet-banking-config-server:8090` |

### 6.4 Testing

- **Framework:** JUnit 5 (JUnit Platform)
- **Test database:** H2 in-memory (configured in `src/test/resources/application.yml`)
- **Existing tests:** Core Banking Service has unit tests for `AccountService`, `TransactionService`, and `UserService` using Mockito.
- **Other services:** Only default Spring Boot context load tests exist.

### 6.5 OpenAPI / Swagger

All business services include `springdoc-openapi-starter-webflux-ui:2.1.0` with `@Tag` and `@Operation` annotations on controllers. Swagger UI is accessible at `/swagger-ui.html` per service.
