# Application Knowledge Base

## 1. Architecture Overview

### System Summary

This is a Java 21 / Spring Boot 3.2.4 internet banking platform composed of **6 microservices** communicating over REST (OpenFeign) and discovered via Netflix Eureka. An API Gateway enforces OAuth2/JWT security via Keycloak, and a centralized Config Server provides environment-specific configuration from a Git repository.

### Service Inventory

| Service | Port | Role |
|---|---|---|
| `internet-banking-api-gateway` | 8082 | Single entry point; routes traffic, enforces OAuth2 JWT authentication, injects `X-Auth-Id` header |
| `internet-banking-service-registry` | 8081 | Netflix Eureka server for service discovery |
| `internet-banking-config-server` | 8090 | Spring Cloud Config Server backed by a remote Git repo |
| `core-banking-service` | 8092 | System of record: accounts, users, transactions, utility accounts |
| `internet-banking-user-service` | 8083 | User registration/management with Keycloak integration |
| `internet-banking-fund-transfer-service` | 8084 | Orchestrates account-to-account fund transfers |
| `internet-banking-utility-payment-service` | 8085 | Processes utility bill payments |

### Communication Patterns

```
                        +-----------------------+
    Client Request ---->| API Gateway (8082)    |
                        | OAuth2 JWT validation |
                        | X-Auth-Id injection   |
                        +----------+------------+
                                   |
                    +--------------+--------------+
                    |              |              |
              +-----v----+  +-----v------+  +----v-------+
              | User Svc  |  | Fund Xfer  |  | Util Pay   |
              | (8083)    |  | Svc (8084) |  | Svc (8085) |
              +-----+-----+  +-----+------+  +-----+------+
                    |               |               |
                    |   OpenFeign   |   OpenFeign    |
                    v               v               v
              +---------------------------------------------+
              |        Core Banking Service (8092)          |
              |  Accounts | Users | Transactions | Utility  |
              +---------------------------------------------+
                                   |
                              +----v----+
                              |  MySQL  |
                              +---------+
```

- **Synchronous REST (OpenFeign):** All inter-service calls use Spring Cloud OpenFeign clients resolved via Eureka service names.
  - `internet-banking-user-service` -> `core-banking-service` (user lookup by identification)
  - `internet-banking-fund-transfer-service` -> `core-banking-service` (account lookup, fund transfer execution)
  - `internet-banking-utility-payment-service` -> `core-banking-service` (account lookup, utility payment execution)
- **RabbitMQ:** Listed in the tech stack for future notification service; not yet implemented in the current codebase.
- **Service Discovery:** All services register with Eureka; Feign clients resolve service names dynamically.
- **Configuration:** Bootstrap context loads config from the Config Server before application startup.

### Infrastructure Components

| Component | Image/Technology | Purpose |
|---|---|---|
| MySQL | Custom build from `docker-compose/mysql` | Application database (4 schemas) |
| PostgreSQL 15 | `postgres:15` | Keycloak backend database |
| Keycloak 23.0.7 | `quay.io/keycloak/keycloak:23.0.7` | IAM: OAuth2/OIDC provider |
| Zipkin 3 | `openzipkin/zipkin:3` | Distributed tracing |
| Docker Compose | `docker-compose.yml` | Full orchestration |

### Docker Network

All containers run on a custom bridge network `javatodev_ib_network` (`172.25.0.0/16`) with static IPs assigned to each service. Services use `wait-for-it.sh` scripts to ensure dependencies (Config Server, Service Registry, MySQL) are healthy before startup.

---

## 2. Data Model Documentation

### Core Banking Service (MySQL: `banking_core_service`)

#### `banking_core_user`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `first_name` | VARCHAR(255) | |
| `last_name` | VARCHAR(255) | |
| `email` | VARCHAR(255) | |
| `identification_number` | VARCHAR(255) | National ID / NIC |

#### `banking_core_account`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `number` | VARCHAR(255) | Account number |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `ACTIVE` |
| `available_balance` | DECIMAL(19,2) | |
| `actual_balance` | DECIMAL(19,2) | |
| `user_id` | BIGINT (FK -> `banking_core_user.id`) | |

#### `banking_core_utility_account`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `number` | VARCHAR(255) | Provider account number |
| `provider_name` | VARCHAR(255) | e.g., VODAFONE, VERIZON |

#### `banking_core_transaction`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `amount` | DECIMAL(19,2) | Negative for debits |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Target account or reference |
| `transaction_id` | VARCHAR(50) | UUID |
| `account_id` | BIGINT (FK -> `banking_core_account.id`) | |

**Relationships:**
- `banking_core_user` 1:N `banking_core_account`
- `banking_core_account` 1:N `banking_core_transaction` (mapped as `@OneToOne` in JPA but logically 1:N)

### Internet Banking User Service (MySQL: `banking_core_user_service`)

#### `user`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `auth_id` | VARCHAR(255) | Keycloak user UUID |
| `identification` | VARCHAR(255) | NIC / National ID |
| `status` | VARCHAR(255) | Enum: `PENDING`, `APPROVED` |
| `created_at` | TIMESTAMP | Audit field (from `AuditAware`) |
| `updated_at` | TIMESTAMP | Audit field (from `AuditAware`) |

### Internet Banking Fund Transfer Service (MySQL: `banking_core_fund_transfer_service`)

#### `fund_transfer`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `transaction_reference` | VARCHAR(255) | Core banking transaction ID |
| `from_account` | VARCHAR(255) | Source account number |
| `to_account` | VARCHAR(255) | Destination account number |
| `amount` | DECIMAL(19,2) | |
| `status` | VARCHAR(255) | Enum: `PENDING`, `SUCCESS` |
| `created_at` | TIMESTAMP | Audit field |
| `updated_at` | TIMESTAMP | Audit field |

### Internet Banking Utility Payment Service (MySQL: `banking_core_utility_payment_service`)

#### `utility_payment`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL(19,2) | |
| `reference_number` | VARCHAR(255) | Customer reference |
| `account` | VARCHAR(255) | Payer bank account |
| `transaction_id` | VARCHAR(255) | Core banking transaction ID |
| `status` | VARCHAR(255) | Enum: `PROCESSING`, `SUCCESS` |
| `created_at` | TIMESTAMP | Audit field |
| `updated_at` | TIMESTAMP | Audit field |

### Schema Migration

- **Flyway** manages migrations for `core-banking-service` only (3 migration files under `src/main/resources/db/migration/`).
- Other services rely on JPA auto-DDL (Hibernate `ddl-auto`) for schema creation.

---

## 3. API Surface Map

### Core Banking Service (`/api/v1/...`)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | - | `BankAccount` | Retrieve bank account by number |
| `GET` | `/api/v1/account/util-account/{account_name}` | - | `UtilityAccount` | Retrieve utility account by provider name |
| `POST` | `/api/v1/transaction/fund-transfer` | `FundTransferRequest { fromAccount, toAccount, amount }` | `FundTransferResponse { message, transactionId }` | Execute fund transfer |
| `POST` | `/api/v1/transaction/util-payment` | `UtilityPaymentRequest { providerId, amount, referenceNumber, account }` | `UtilityPaymentResponse { message, transactionId }` | Execute utility payment |
| `GET` | `/api/v1/user/{identification}` | - | `User` | Read user by identification number |
| `GET` | `/api/v1/user` | Pageable params | `List<User>` | List users (paginated) |

### Internet Banking User Service (`/api/v1/bank-users/...`)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | `User { firstName, lastName, email, password, identification }` | `User` | Register new user (creates in Keycloak + local DB) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `UserUpdateRequest { status }` | `User` | Update user status (approval enables Keycloak account) |
| `GET` | `/api/v1/bank-users` | Pageable params | `List<User>` | List registered users |
| `GET` | `/api/v1/bank-users/{id}` | - | `User` | Read user by ID |

### Internet Banking Fund Transfer Service (`/api/v1/transfer/...`)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | `FundTransferRequest { fromAccount, toAccount, amount, authID }` | `FundTransferResponse { message, transactionId }` | Initiate fund transfer |
| `GET` | `/api/v1/transfer` | Pageable params | `List<FundTransfer>` | List fund transfers |

### Internet Banking Utility Payment Service (`/api/v1/utility-payment/...`)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | `UtilityPaymentRequest { providerId, amount, referenceNumber, account }` | `UtilityPaymentResponse { message, transactionId }` | Process utility payment |
| `GET` | `/api/v1/utility-payment` | Pageable params | `List<UtilityPayment>` | List utility payments |

### API Gateway Routes (Prefix-Based)

| Route Prefix | Target Service |
|---|---|
| `/user/**` | `internet-banking-user-service` |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` |
| `/banking-core/**` | `core-banking-service` |
| `/utility-payment/**` | `internet-banking-utility-payment-service` |

**Public Endpoints (no JWT required):**
- `/user/api/v1/bank-users/register`
- `/actuator/**` and all service actuator paths

---

## 4. Key Business Logic Inventory

### User Registration Flow
1. Client calls `POST /user/api/v1/bank-users/register` (public, no auth required)
2. User Service checks Keycloak for existing email registration
3. User Service calls Core Banking Service to verify user exists by NIC identification
4. Validates that the email matches the core banking record
5. Creates user in Keycloak (disabled, email unverified)
6. Saves local user record with `PENDING` status and Keycloak `authId`

### User Approval Flow
1. Admin calls `PATCH /user/api/v1/bank-users/update/{id}` with `status: APPROVED`
2. User Service enables Keycloak account and marks email as verified
3. Updates local user status to `APPROVED`

### Fund Transfer Flow
1. Client calls `POST /fund-transfer/api/v1/transfer` with JWT
2. Fund Transfer Service saves transfer record with `PENDING` status
3. Calls Core Banking Service `POST /api/v1/transaction/fund-transfer`
4. Core Banking validates source account balance
5. Debits source account, credits destination account
6. Creates two transaction records (debit + credit) with same `transactionId`
7. Fund Transfer Service updates local record to `SUCCESS` with transaction reference

### Utility Payment Flow
1. Client calls `POST /utility-payment/api/v1/utility-payment` with JWT
2. Utility Payment Service saves payment record with `PROCESSING` status
3. Calls Core Banking Service `POST /api/v1/transaction/util-payment`
4. Core Banking validates payer account balance
5. Debits payer account
6. Creates transaction record
7. Utility Payment Service updates local record to `SUCCESS` with transaction ID

### Balance Validation Rules
- Rejects if `actualBalance < 0`
- Rejects if `actualBalance < transferAmount`
- Throws `InsufficientFundsException` with error code `INSUFFICIENT_FUNDS`

### Balance Update Logic (Potential Bug)
In `TransactionService.internalFundTransfer()` and `utilPayment()`:
- `availableBalance` is set to `actualBalance - amount` **after** `actualBalance` has already been decremented
- This effectively double-subtracts the amount from `availableBalance`

---

## 5. Integration Points

### Keycloak (IAM / OAuth2)
- **Version:** 23.0.7
- **Connection:** User Service connects via `keycloak-admin-client:24.0.4`
- **Configuration:** `app.config.keycloak.*` properties (server-url, realm, clientId, client-secret)
- **Operations:** Create user, update user, search by email, read user by ID
- **Realm:** Imported from `docker-compose/keycloak/realm-export.json` on startup
- **API Gateway:** Validates JWT tokens using `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`
- **Singleton Pattern:** `KeycloakProperties` uses a non-thread-safe static singleton for the Keycloak client instance

### RabbitMQ
- Listed in the tech stack and README but **not implemented** in the current codebase
- Intended for the Notification Service (marked as "PENDING Development")

### Zipkin (Distributed Tracing)
- **Version:** 3
- **Port:** 9411
- **Integration:** Via `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave`
- Present in all business services (core, user, fund-transfer, utility-payment) and the API Gateway

### Database Connections
| Service | Database | Schema |
|---|---|---|
| `core-banking-service` | MySQL | `banking_core_service` |
| `internet-banking-user-service` | MySQL | `banking_core_user_service` |
| `internet-banking-fund-transfer-service` | MySQL | `banking_core_fund_transfer_service` |
| `internet-banking-utility-payment-service` | MySQL | `banking_core_utility_payment_service` |
| Keycloak | PostgreSQL 15 | `keycloak` |

### Spring Cloud Config Server
- **Git Backend:** `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch:** `main`
- **Search Path:** `configuration`
- All business services and the API Gateway fetch configuration at startup via bootstrap context

### Service Discovery (Eureka)
- Services register with Eureka using their `spring.application.name`
- Feign clients resolve target services by Eureka service name (e.g., `core-banking-service`)

---

## 6. Build and Deployment Pipeline Summary

### Build Tool
- **Gradle** with Spring Boot plugin (`3.2.4`) and Spring Dependency Management (`1.1.4`)
- Java 21 (source compatibility)
- Each service is an independent Gradle project (no multi-project build)

### Docker
- Each service has its own `Dockerfile` based on `eclipse-temurin:21.0.2_13-jre-alpine`
- Build produces a fat JAR (`*-0.0.1-SNAPSHOT.jar`) copied into the image
- `wait-for-it.sh` scripts ensure startup ordering
- Spring profile `docker` is activated via `-Dspring.profiles.active=docker`

### Docker Compose
- **Full stack:** `docker-compose/docker-compose.yml` (all services + infrastructure)
- **Support only:** `docker-compose/docker-compose-support-apps.yml` (Zipkin, Keycloak, MySQL, Config Server, Service Registry)
- Static IP assignments on bridge network `172.25.0.0/16`

### MySQL Initialization
- `docker-compose/mysql/privileges.sql` creates a dedicated DB user and 4 schemas
- `core-banking-service` uses Flyway for schema migrations and seed data

### Test Framework
- JUnit 5 (`useJUnitPlatform()`) configured in all services
- H2 in-memory database for test dependencies
- Mockito for unit tests
- Only `core-banking-service` has actual test implementations (3 test classes)

### Dependencies Across Services
All business services share:
- `spring-boot-starter-web`
- `spring-boot-starter-data-jpa`
- `spring-cloud-starter-netflix-eureka-client`
- `spring-boot-starter-actuator`
- `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave`
- `spring-cloud-starter-config` + `spring-cloud-starter-bootstrap`
- `springdoc-openapi-starter-webflux-ui:2.1.0`
- `mysql-connector-j:8.4.0`
- Lombok (compile-only)

### Postman Collection
- Located at `postman_collection/`
- Includes environment configuration for `LOCAL_DOCKER_SETUP`
- Test credentials: `ib_admin@javatodev.com / 5V7huE3G86uB`
