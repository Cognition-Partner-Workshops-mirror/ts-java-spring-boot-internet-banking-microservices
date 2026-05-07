# Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Architecture

The Internet Banking application follows a **Spring Cloud microservices architecture** with 6 independently deployable services, a centralized configuration server, and a service registry.

```
                        +-----------------------+
                        |   API Gateway (:8082) |
                        |  (OAuth2 + JWT)       |
                        +----------+------------+
                                   |
                   +---------------+---------------+
                   |               |               |
          +--------v---+  +-------v------+  +-----v-----------+
          | User Svc   |  | Fund Xfer    |  | Utility Payment |
          | (:8083)    |  | Svc (:8084)  |  | Svc (:8085)     |
          +-----+------+  +------+-------+  +------+----------+
                |                |                  |
                |         +------v-------+          |
                +-------->| Core Banking |<---------+
                          | Svc (:8092)  |
                          +------+-------+
                                 |
                          +------v-------+
                          |    MySQL     |
                          |   (:3306)    |
                          +--------------+

  +---------------------+    +-------------------+    +-----------+
  | Config Server       |    | Service Registry  |    | Keycloak  |
  | (:8090)             |    | (Eureka) (:8081)  |    | (:8080)   |
  +---------------------+    +-------------------+    +-----------+

  +---------------------+
  | Zipkin (:9411)      |
  +---------------------+
```

### 1.2 Microservices Inventory

| Service | Port | Type | Database | Description |
|---------|------|------|----------|-------------|
| `internet-banking-api-gateway` | 8082 | Infrastructure | None | Spring Cloud Gateway; OAuth2 resource server with Keycloak JWT validation |
| `internet-banking-service-registry` | 8081 | Infrastructure | None | Netflix Eureka server for service discovery |
| `internet-banking-config-server` | 8090 | Infrastructure | None | Spring Cloud Config Server backed by a Git repository |
| `core-banking-service` | 8092 | Domain | MySQL (`banking_core_service`) | System of record for users, accounts, transactions, and utility accounts |
| `internet-banking-user-service` | 8083 | Domain | MySQL (`banking_core_user_service`) | User registration/management; integrates with Keycloak for IAM |
| `internet-banking-fund-transfer-service` | 8084 | Domain | MySQL (`banking_core_fund_transfer_service`) | Orchestrates fund transfers between bank accounts |
| `internet-banking-utility-payment-service` | 8085 | Domain | MySQL (`banking_core_utility_payment_service`) | Orchestrates utility bill payments |

### 1.3 Communication Patterns

| Pattern | Technology | Usage |
|---------|-----------|-------|
| **Synchronous REST** | Spring Cloud OpenFeign | All inter-service calls (user-service -> core-banking, fund-transfer -> core-banking, utility-payment -> core-banking) |
| **Service Discovery** | Netflix Eureka | All services register with and discover peers through the Eureka server |
| **Centralized Config** | Spring Cloud Config | All business services fetch configuration at startup from a remote Git-backed config server via bootstrap context |
| **API Gateway** | Spring Cloud Gateway | Single entry point; routes traffic to downstream services; enforces OAuth2 JWT authentication |
| **Auth Token Propagation** | Custom `X-Auth-Id` Header | API Gateway extracts the authenticated principal and injects it as `X-Auth-Id` header to downstream services |
| **Distributed Tracing** | Micrometer Tracing + Zipkin | All services export trace spans to Zipkin for end-to-end request visibility |

### 1.4 Infrastructure Components

| Component | Image / Version | Purpose |
|-----------|----------------|---------|
| **MySQL** | Custom (docker-compose/mysql) | Relational database for all four domain services (4 separate schemas) |
| **PostgreSQL 15** | `postgres:15` | Keycloak's backing database |
| **Keycloak 23.0.7** | `quay.io/keycloak/keycloak:23.0.7` | Identity and Access Management; manages users, roles, and OAuth2 clients |
| **Zipkin 3** | `openzipkin/zipkin:3` | Distributed tracing UI and collector |
| **Docker Compose** | v3.6 | Orchestrates the full stack on a custom bridge network (`172.25.0.0/16`) |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL: `banking_core_service`)

#### `banking_core_user`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal user ID |
| `first_name` | VARCHAR(255) | | User's first name |
| `last_name` | VARCHAR(255) | | User's last name |
| `email` | VARCHAR(255) | | User's email address |
| `identification_number` | VARCHAR(255) | | National ID / NIC number (used for cross-service lookup) |

#### `banking_core_account`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal account ID |
| `number` | VARCHAR(255) | | Account number (used as lookup key) |
| `type` | VARCHAR(255) | ENUM: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` | Account type |
| `status` | VARCHAR(255) | ENUM: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` | Account status |
| `actual_balance` | DECIMAL(19,2) | | Ledger balance |
| `available_balance` | DECIMAL(19,2) | | Available (spendable) balance |
| `user_id` | BIGINT | FK -> `banking_core_user.id` | Owner relationship |

#### `banking_core_transaction`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal transaction ID |
| `amount` | DECIMAL(19,2) | | Transaction amount (negative for debits, positive for credits) |
| `transaction_type` | VARCHAR(30) | NOT NULL; ENUM: `FUND_TRANSFER`, `UTILITY_PAYMENT` | Type discriminator |
| `reference_number` | VARCHAR(50) | NOT NULL | Counter-party account number or reference |
| `transaction_id` | VARCHAR(50) | NOT NULL | UUID grouping both legs of a transfer |
| `account_id` | BIGINT | FK -> `banking_core_account.id` | Associated account |

#### `banking_core_utility_account`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal utility account ID |
| `number` | VARCHAR(255) | | Utility provider account number |
| `provider_name` | VARCHAR(255) | | Utility provider name (e.g., VODAFONE, AIRTEL) |

**Relationships:**
- `banking_core_user` 1:N `banking_core_account` (via `user_id` FK)
- `banking_core_account` 1:N `banking_core_transaction` (via `account_id` FK)
- `banking_core_utility_account` is standalone (referenced by provider ID)

### 2.2 Internet Banking User Service (MySQL: `banking_core_user_service`)

#### `user` (extends `AuditAware`)
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK) | Internal user ID |
| `auth_id` | VARCHAR | Keycloak user UUID |
| `identification` | VARCHAR | National ID / NIC (maps to core banking `identification_number`) |
| `status` | ENUM: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` | Registration status |
| `created_date` | INSTANT | Audit: creation timestamp |
| `created_by` | VARCHAR | Audit: creator |
| `modified_date` | INSTANT | Audit: last modification timestamp |
| `modified_by` | VARCHAR | Audit: last modifier |
| `version` | BIGINT | Optimistic locking version |

### 2.3 Fund Transfer Service (MySQL: `banking_core_fund_transfer_service`)

#### `fund_transfer` (extends `AuditAware`)
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK) | Internal transfer ID |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `status` | ENUM: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` | Transfer status |
| `transaction_reference` | VARCHAR | Transaction ID returned from core banking |
| Audit fields inherited from `AuditAware` | | |

### 2.4 Utility Payment Service (MySQL: `banking_core_utility_payment_service`)

#### `utility_payment` (extends `AuditAware`)
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK) | Internal payment ID |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Customer reference number |
| `account` | VARCHAR | Debiting bank account number |
| `transaction_id` | VARCHAR | Transaction ID returned from core banking |
| `status` | ENUM: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` | Payment status |
| Audit fields inherited from `AuditAware` | | |

---

## 3. API Surface Map

### 3.1 API Gateway Routes

All requests enter through the API Gateway (`:8082`). Route prefixes are stripped before forwarding:

| Gateway Path Prefix | Target Service | Auth Required |
|---------------------|---------------|---------------|
| `/user/**` | `internet-banking-user-service` | Yes (except `/user/api/v1/bank-users/register`) |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` | Yes |
| `/banking-core/**` | `core-banking-service` | Yes |
| `/utility-payment/**` | `internet-banking-utility-payment-service` | Yes |
| `/actuator/**` | Local actuator | No |
| `/{service}/actuator/**` | Per-service actuators | No |

### 3.2 Core Banking Service Endpoints

| Method | Path | Description | Request Body | Response Body |
|--------|------|-------------|-------------|---------------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by account number | - | `BankAccount { id, number, type, status, availableBalance, actualBalance, user }` |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | - | `UtilityAccount { id, number, providerName }` |
| `GET` | `/api/v1/user/{identification}` | Get user by identification number | - | `User { id, firstName, lastName, email, identificationNumber, bankAccounts[] }` |
| `GET` | `/api/v1/user` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |

### 3.3 Internet Banking User Service Endpoints

| Method | Path | Description | Request Body | Response Body |
|--------|------|-------------|-------------|---------------|
| `POST` | `/api/v1/bank-users/register` | Register a new banking user | `{ email, identification, password }` | `User { id, email, identification, authId, status }` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user (e.g., approve) | `{ status }` | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | - | `User` |

### 3.4 Fund Transfer Service Endpoints

| Method | Path | Description | Request Body | Response Body |
|--------|------|-------------|-------------|---------------|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `{ fromAccount, toAccount, amount, authID }` | `{ message, transactionId }` |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | Query: `page`, `size`, `sort` | `List<FundTransfer>` |

### 3.5 Utility Payment Service Endpoints

| Method | Path | Description | Request Body | Response Body |
|--------|------|-------------|-------------|---------------|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | Query: `page`, `size`, `sort` | `List<UtilityPayment>` |

### 3.6 Feign Client Mapping (Inter-Service Calls)

| Caller Service | Feign Client | Target Service | Endpoint Called |
|---------------|-------------|----------------|-----------------|
| `internet-banking-user-service` | `BankingCoreRestClient` | `core-banking-service` | `GET /api/v1/user/{identification}` |
| `internet-banking-fund-transfer-service` | `BankingCoreFeignClient` | `core-banking-service` | `GET /api/v1/account/bank-account/{account_number}` |
| `internet-banking-fund-transfer-service` | `BankingCoreFeignClient` | `core-banking-service` | `POST /api/v1/transaction/fund-transfer` |
| `internet-banking-utility-payment-service` | `BankingCoreRestClient` | `core-banking-service` | `GET /api/v1/account/bank-account/{account_number}` |
| `internet-banking-utility-payment-service` | `BankingCoreRestClient` | `core-banking-service` | `POST /api/v1/transaction/util-payment` |

---

## 4. Key Business Logic Inventory

### 4.1 Fund Transfer Rules (`TransactionService.fundTransfer`)

1. **Account Lookup**: Resolve both source (`fromAccount`) and destination (`toAccount`) bank accounts via `AccountService`
2. **Balance Validation**: Source account's `actualBalance` must be >= 0 AND >= the transfer `amount`; otherwise throw `InsufficientFundsException`
3. **Debit Source**: Subtract `amount` from source account's `actualBalance` and `availableBalance`
4. **Record Debit Transaction**: Persist a `FUND_TRANSFER` transaction with negative amount for the source account
5. **Credit Destination**: Add `amount` to destination account's `actualBalance` and `availableBalance`
6. **Record Credit Transaction**: Persist a `FUND_TRANSFER` transaction with positive amount for the destination account
7. **Return**: UUID-based `transactionId` linking both legs

**Important**: The entire operation runs within a `@Transactional` boundary at the core-banking level.

### 4.2 Utility Payment Rules (`TransactionService.utilPayment`)

1. **Account Lookup**: Resolve the debiting bank account
2. **Balance Validation**: Same as fund transfer validation
3. **Provider Lookup**: Resolve the utility provider by `providerId`
4. **Debit Account**: Subtract `amount` from the bank account's `actualBalance` and `availableBalance`
5. **Record Transaction**: Persist a `UTILITY_PAYMENT` transaction with negative amount
6. **Return**: UUID-based `transactionId`

**Note**: No actual third-party API call is made; the comment indicates this is a placeholder.

### 4.3 User Registration Flow (`UserService.createUser`)

1. **Duplicate Check**: Search Keycloak for existing user by email; throw `UserAlreadyRegisteredException` if found
2. **Core Banking Verification**: Call core-banking-service to verify the user exists by `identification` (NIC)
3. **Email Match**: Verify that the email in the request matches the core banking record; throw `InvalidEmailException` if not
4. **Keycloak Registration**: Create user in Keycloak with email, name, and temporary password (initially disabled, email unverified)
5. **Local Record**: Retrieve the Keycloak-assigned UUID (`authId`), persist a local `UserEntity` with `PENDING` status
6. **Return**: The newly created user DTO

### 4.4 User Approval Flow (`UserService.updateUser`)

1. If `status == APPROVED`:
   - Fetch the Keycloak user representation by `authId`
   - Enable the user and mark email as verified in Keycloak
2. Update the local user entity's status
3. Return the updated user

### 4.5 Fund Transfer Orchestration (`FundTransferService.fundTransfer`)

1. Persist a local `FundTransferEntity` with `PENDING` status
2. Call core-banking-service's fund transfer endpoint via Feign
3. Update local entity with the returned `transactionReference` and set status to `SUCCESS`
4. Return response

### 4.6 Utility Payment Orchestration (`UtilityPaymentService.utilPayment`)

1. Persist a local `UtilityPaymentEntity` with `PROCESSING` status
2. Call core-banking-service's utility payment endpoint via Feign
3. Update local entity with the returned `transactionId` and set status to `SUCCESS`
4. Return response

---

## 5. Integration Points

### 5.1 Keycloak Integration

- **Service**: `internet-banking-user-service`
- **Library**: `keycloak-admin-client:24.0.4`
- **Configuration**: Externalized via `app.config.keycloak.*` properties (server-url, realm, clientId, client-secret)
- **Operations**: User CRUD (create, read by ID, read by email, update enable/verify status)
- **Singleton Pattern**: `KeycloakProperties` maintains a static Keycloak client instance (thread-safety concern)
- **Realm Import**: Docker Compose mounts `docker-compose/keycloak/realm-export.json` for automatic realm provisioning

### 5.2 RabbitMQ Integration

- **Status**: Referenced in README and architecture diagrams but **NOT implemented** in the current codebase
- **Intended Use**: Notification service would consume messages from RabbitMQ for post-transaction notifications
- **No RabbitMQ dependency** exists in any `build.gradle`

### 5.3 Zipkin / Distributed Tracing

- **All services** include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` dependencies
- **Feign tracing**: `feign-micrometer` dependency enables trace propagation across Feign calls
- **Zipkin Server**: Runs at `172.25.0.12:9411` in Docker Compose
- **Configuration**: Tracing config is managed via the external Spring Cloud Config repository

### 5.4 Database Connections

| Service | Database | Schema | Migration |
|---------|----------|--------|-----------|
| `core-banking-service` | MySQL | `banking_core_service` | Flyway (3 migration files) |
| `internet-banking-user-service` | MySQL | `banking_core_user_service` | JPA auto (no Flyway) |
| `internet-banking-fund-transfer-service` | MySQL | `banking_core_fund_transfer_service` | JPA auto (no Flyway) |
| `internet-banking-utility-payment-service` | MySQL | `banking_core_utility_payment_service` | JPA auto (no Flyway) |
| Keycloak | PostgreSQL | `keycloak` | Keycloak-managed |

### 5.5 Spring Cloud Config

- **Config Server**: Fetches configuration from Git repo `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch**: `main`, search path: `configuration`
- **Consumers**: All business services use `spring-cloud-starter-bootstrap` to fetch config at startup
- **Profiles**: `default` (localhost), `docker` (Docker Compose), `dev` (development)

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build Tool**: Gradle (per-service `build.gradle`, no multi-project root build)
- **Java Version**: 21 (Eclipse Temurin `21.0.2_13-jre-alpine` for Docker)
- **Spring Boot**: 3.2.4
- **Spring Cloud**: 2023.0.0
- **Git Properties Plugin**: `com.gorylenko.gradle-git-properties:2.4.2` (5 of 6 services)

### 6.2 Docker

Each service has its own `Dockerfile`:
- **Base Image**: `eclipse-temurin:21.0.2_13-jre-alpine`
- **Pattern**: Copy fat JAR as `app.jar`, copy `wait-for-it.sh` for startup ordering
- **Entrypoint**: `java -jar -Dspring.profiles.active=docker /app.jar`

### 6.3 Docker Compose

Two compose files in `docker-compose/`:

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Full stack: all infrastructure + all microservices |
| `docker-compose-support-apps.yml` | Infrastructure only: MySQL, PostgreSQL, Keycloak, Zipkin, Config Server, Service Registry |

**Network**: Custom bridge network `javatodev_ib_network` with subnet `172.25.0.0/16` and static IP assignments per container.

**Startup Ordering**: `wait-for-it.sh` scripts ensure services wait for:
1. Service Registry (`:8081`)
2. Config Server (`:8090`)
3. MySQL (`:3306`) (for database-backed services)

### 6.4 Build Workflow

```bash
# Build a single service
cd core-banking-service
./gradlew build

# Build Docker image
docker build -t javatodev/core-banking-service .

# Run full stack
cd docker-compose
docker-compose up -d
```

### 6.5 CI/CD

- **GitHub Actions**: No workflows present in the repository (removed)
- **No automated CI pipeline** currently configured
