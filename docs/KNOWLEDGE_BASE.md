# Knowledge Base — Internet Banking Microservices

## 1. Architecture Overview

### Services (7 total, 4 business + 3 infrastructure)

| Service | Port | Type | Description |
|---|---|---|---|
| `core-banking-service` | 8092 | Business | System of record — manages accounts, users, transactions in MySQL |
| `internet-banking-user-service` | 8083 | Business | User registration/management, integrates with Keycloak for identity |
| `internet-banking-fund-transfer-service` | 8084 | Business | Orchestrates account-to-account fund transfers via core service |
| `internet-banking-utility-payment-service` | 8085 | Business | Processes utility bill payments via core service |
| `internet-banking-api-gateway` | 8082 | Infrastructure | Spring Cloud Gateway, OAuth2 security entry point |
| `internet-banking-config-server` | 8090 | Infrastructure | Spring Cloud Config Server, fetches config from GitHub |
| `internet-banking-service-registry` | 8081 | Infrastructure | Netflix Eureka service discovery |

### Communication Patterns

- All inter-service communication is **synchronous via OpenFeign** (no async/messaging)
- Three Feign clients exist:
  - `internet-banking-fund-transfer-service` → `core-banking-service` (BankingCoreFeignClient at `internet-banking-fund-transfer-service/src/main/java/com/javatodev/finance/service/rest/client/BankingCoreFeignClient.java`)
  - `internet-banking-utility-payment-service` → `core-banking-service` (BankingCoreRestClient at `internet-banking-utility-payment-service/src/main/java/com/javatodev/finance/service/rest/BankingCoreRestClient.java`)
  - `internet-banking-user-service` → `core-banking-service` (BankingCoreRestClient at `internet-banking-user-service/src/main/java/com/javatodev/finance/service/rest/BankingCoreRestClient.java`)
- Service discovery via Eureka; API Gateway routes to downstream services
- RabbitMQ is mentioned in README but NOT actually used in the codebase

### Infrastructure Components

- MySQL 8 (single instance `mysql_core_db` on 172.25.0.9:3306) — shared by all business services
- Keycloak 23.0.7 (on 172.25.0.11:8080) with PostgreSQL 15 backend — OAuth2/OIDC identity provider
- Zipkin 3 (on 172.25.0.12:9411) — distributed tracing
- Docker Compose with static IPs on bridge network `javatodev_ib_network` (subnet 172.25.0.0/16)

---

## 2. Data Model Documentation

### Core Banking Service (MySQL `banking_core_service` database)

Tables managed via Flyway migrations in `core-banking-service/src/main/resources/db/migration/`:

- **`banking_core_user`**: id (PK, bigint), email, first_name, identification_number, last_name
  - Entity: `core-banking-service/src/main/java/com/javatodev/finance/model/entity/UserEntity.java`
  - Has `@OneToMany` to BankAccountEntity

- **`banking_core_account`**: id (PK, bigint), actual_balance (decimal 19,2), available_balance (decimal 19,2), number (varchar), status (enum: ACTIVE), type (enum: SAVINGS_ACCOUNT), user_id (FK → banking_core_user)
  - Entity: `core-banking-service/src/main/java/com/javatodev/finance/model/entity/BankAccountEntity.java`
  - Enums: AccountType, AccountStatus

- **`banking_core_utility_account`**: id (PK, bigint), number (varchar), provider_name (varchar)
  - Entity: `core-banking-service/src/main/java/com/javatodev/finance/model/entity/UtilityAccountEntity.java`

- **`banking_core_transaction`**: id (PK, bigint), amount (decimal 19,2), transaction_type (varchar 30), reference_number (varchar 50), transaction_id (varchar 50), account_id (FK → banking_core_account)
  - Entity: `core-banking-service/src/main/java/com/javatodev/finance/model/entity/TransactionEntity.java`
  - NOTE: Uses `@OneToOne` with `CascadeType.ALL` to BankAccountEntity — this is a bug (should be `@ManyToOne`)

### Fund Transfer Service (own database)

- **`fund_transfer`**: id, transactionReference, fromAccount, toAccount, amount, status (enum: PENDING/SUCCESS)
  - Entity: `internet-banking-fund-transfer-service/src/main/java/com/javatodev/finance/model/entity/FundTransferEntity.java`
  - Extends `AuditAware` (createdAt, updatedAt fields)

### User Service (own database)

- **`user`**: id, authId (Keycloak user ID), identification, status (enum: PENDING/APPROVED)
  - Entity: `internet-banking-user-service/src/main/java/com/javatodev/finance/model/entity/UserEntity.java`
  - Extends `AuditAware`

### Utility Payment Service (own database)

- **`utility_payment`**: id, providerId, amount, referenceNumber, account, transactionId, status (enum: PROCESSING/SUCCESS)
  - Entity: `internet-banking-utility-payment-service/src/main/java/com/javatodev/finance/model/entity/UtilityPaymentEntity.java`
  - Extends `AuditAware`

---

## 3. API Surface Map

### Core Banking Service (`/api/v1/`)

| Method | Path | Controller | Description |
|---|---|---|---|
| GET | `/api/v1/account/bank-account/{account_number}` | AccountController | Get bank account by number |
| GET | `/api/v1/account/util-account/{account_name}` | AccountController | Get utility account by provider name |
| POST | `/api/v1/transaction/fund-transfer` | TransactionController | Process fund transfer (body: FundTransferRequest) |
| POST | `/api/v1/transaction/util-payment` | TransactionController | Process utility payment (body: UtilityPaymentRequest) |
| GET | `/api/v1/user/{identification}` | UserController | Get user by identification number |
| GET | `/api/v1/user` | UserController | Get paginated user list |

### User Service (`/api/v1/bank-users/`)

| Method | Path | Controller | Description |
|---|---|---|---|
| POST | `/api/v1/bank-users/register` | UserController | Register new user (body: User DTO) |
| PATCH | `/api/v1/bank-users/update/{id}` | UserController | Update user (body: UserUpdateRequest) |
| GET | `/api/v1/bank-users` | UserController | Get paginated user list |
| GET | `/api/v1/bank-users/{id}` | UserController | Get user by ID |

### Fund Transfer Service (`/api/v1/transfer`)

| Method | Path | Controller | Description |
|---|---|---|---|
| POST | `/api/v1/transfer` | FundTransferController | Initiate fund transfer (body: FundTransferRequest) |
| GET | `/api/v1/transfer` | FundTransferController | Get paginated transfer list |

### Utility Payment Service (`/api/v1/utility-payment`)

| Method | Path | Controller | Description |
|---|---|---|---|
| POST | `/api/v1/utility-payment` | UtilityPaymentController | Process utility payment (body: UtilityPaymentRequest) |
| GET | `/api/v1/utility-payment` | UtilityPaymentController | Get paginated payment list |

### Request/Response Shapes

- **FundTransferRequest**: `{ fromAccount, toAccount, amount }`
- **FundTransferResponse**: `{ message, transactionId }`
- **UtilityPaymentRequest**: `{ account, providerId, amount, referenceNumber }`
- **UtilityPaymentResponse**: `{ message, transactionId }`
- **User DTO**: `{ email, identification, password, authId, status }`
- **UserUpdateRequest**: `{ status }`
- **ErrorResponse**: `{ code, message }`

---

## 4. Key Business Logic Inventory

### Fund Transfer Rules
*In `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java`:*

- Validates sender has sufficient balance (actualBalance >= amount AND actualBalance >= 0)
- Deducts from sender's actualBalance and availableBalance
- Credits to receiver's actualBalance and availableBalance
- Creates two TransactionEntity records (debit + credit) with same transactionId (UUID)
- **BUG**: availableBalance is set to `actualBalance.subtract(amount)` AFTER actualBalance was already subtracted, causing double-deduction

### Fund Transfer Orchestration
*In `internet-banking-fund-transfer-service/src/main/java/com/javatodev/finance/service/FundTransferService.java`:*

- Saves a PENDING FundTransferEntity locally
- Calls core-banking-service via Feign to execute the transfer
- Updates local entity to SUCCESS with transaction reference
- No compensation/rollback if Feign call fails after local save

### Utility Payment Processing
*In `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java`:*

- Validates payer has sufficient balance
- Looks up utility account by provider ID
- Deducts from payer's account
- Records transaction with UTILITY_PAYMENT type
- Same availableBalance double-deduction bug as fund transfer

### User Management
*In `internet-banking-user-service/src/main/java/com/javatodev/finance/service/UserService.java`:*

- **Registration**: checks email not already in Keycloak, validates user exists in core banking by identification number, validates email matches, creates Keycloak user (disabled, unverified), saves local UserEntity with PENDING status
- **Approval**: admin updates status to APPROVED, which enables the Keycloak user and marks email as verified
- **Read**: fetches local entity, enriches with Keycloak data (email)

---

## 5. Integration Points

- **Keycloak**: User service uses `keycloak-admin-client:24.0.4` to manage users. API Gateway uses OAuth2 resource server for JWT validation. Config in `KeycloakProperties` / `KeycloakManager`.
- **Zipkin**: All services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` dependencies for distributed tracing.
- **MySQL**: Core banking service uses Flyway for migrations. All business services connect to MySQL (connection details from Spring Cloud Config).
- **Eureka**: All business services register with Eureka. Feign clients use service names for discovery.
- **Spring Cloud Config**: Config server fetches from `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` (external public repo).
- **RabbitMQ**: Referenced in README but NOT implemented in the codebase.

---

## 6. Build and Deployment Pipeline

- **Build**: Gradle with Spring Boot 3.2.4, Spring Cloud 2023.0.0, Java 21. Each service has its own `build.gradle` (no multi-project Gradle build).
- **Docker**: Each service has a `Dockerfile` based on `eclipse-temurin:21.0.2_13-jre-alpine`. Uses `wait-for-it.sh` for startup ordering.
- **Docker Compose**: Single `docker-compose.yml` orchestrates all services with static IPs. Uses `wait-for-it.sh` entrypoints for dependency ordering.
- **No CI/CD pipeline** defined (no `.github/workflows/`, Jenkinsfile, etc.)
