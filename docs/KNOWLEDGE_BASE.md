# Application Knowledge Base

## 1. Architecture Overview

### Services (7 total)

| Service | Port | Role |
|---|---|---|
| internet-banking-config-server | 8090 | Spring Cloud Config Server, pulls config from GitHub repo `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` |
| internet-banking-service-registry | 8081 | Netflix Eureka discovery server |
| internet-banking-api-gateway | 8082 | Spring Cloud Gateway, OAuth2 resource server with Keycloak JWT validation, routes to downstream services |
| internet-banking-user-service | 8083 | User registration/management, integrates with Keycloak Admin API |
| internet-banking-fund-transfer-service | 8084 | Account-to-account fund transfers |
| internet-banking-utility-payment-service | 8085 | Utility bill payments |
| core-banking-service | 8092 | System of record -- manages accounts, users, transactions in MySQL |

### Communication Patterns

- **Synchronous REST via OpenFeign:** User Service -> Core Banking, Fund Transfer Service -> Core Banking, Utility Payment Service -> Core Banking
- The fund-transfer-service uses a `@FeignClient` interface (`BankingCoreFeignClient`) while user-service and utility-payment-service use a `BankingCoreRestClient` (also Feign-based)
- API Gateway propagates authenticated user identity via `X-Auth-Id` HTTP header (see `GatewayConfiguration.java`)
- Downstream services extract this header via `AppAuthUserFilter` servlet filter for JPA auditing

### Infrastructure Components

- **MySQL** (single instance, 4 databases): `banking_core_service`, `banking_core_fund_transfer_service`, `banking_core_user_service`, `banking_core_utility_payment_service`
- **Keycloak 23.0.7** (backed by PostgreSQL 15) for OAuth2/OIDC
- **Zipkin 3** for distributed tracing
- **Docker Compose** with static IPs on bridge network `javatodev_ib_network` (subnet 172.25.0.0/16)

---

## 2. Data Model Documentation

### Core Banking Service (4 entities)

- **`BankAccountEntity`** (table: `banking_core_account`): id, number, type (ENUM: AccountType), status (ENUM: AccountStatus), availableBalance, actualBalance, user_id (FK -> UserEntity)
- **`UserEntity`** (table: `banking_core_user`): id, firstName, lastName, email, identificationNumber. Has @OneToMany to BankAccountEntity
- **`TransactionEntity`** (table: `banking_core_transaction`): id, amount, transactionType (ENUM: TransactionType -- FUND_TRANSFER, UTILITY_PAYMENT), referenceNumber, transactionId, account_id (FK -> BankAccountEntity). NOTE: Uses @OneToOne with CascadeType.ALL which is incorrect -- should be @ManyToOne
- **`UtilityAccountEntity`** (table: `banking_core_utility_account`): id, number, providerName

### User Service (1 entity)

- **`UserEntity`** (table: `user`): id, authId (Keycloak user ID), identification, status (ENUM: Status -- PENDING, APPROVED). Extends `AuditAware` (createdBy, createdDate, lastModifiedBy, lastModifiedDate)

### Fund Transfer Service (1 entity)

- **`FundTransferEntity`** (table: `fund_transfer`): id, transactionReference, fromAccount, toAccount, amount, status (ENUM: TransactionStatus -- PENDING, SUCCESS, PROCESSING). Extends `AuditAware`

### Utility Payment Service (1 entity)

- **`UtilityPaymentEntity`** (table: `utility_payment`): id, providerId, amount, referenceNumber, account, transactionId, status (ENUM: TransactionStatus). Extends `AuditAware`

### Database Migrations

Flyway is used **only** in core-banking-service with 3 migration scripts:

- `V1.0.20210427174638__create_base_table_structure.sql`
- `V1.0.20210427174721__temp_data.sql`
- `V1.0.20210429210839__create_transaction_table.sql`

Other services rely on JPA auto-DDL (`hibernate.ddl-auto`).

---

## 3. API Surface Map

### Core Banking Service (base: `/api/v1`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| GET | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | -- | BankAccount DTO |
| GET | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | -- | UtilityAccount DTO |
| POST | `/api/v1/transaction/fund-transfer` | Process fund transfer | FundTransferRequest {fromAccount, toAccount, amount} | FundTransferResponse {message, transactionId} |
| POST | `/api/v1/transaction/util-payment` | Process utility payment | UtilityPaymentRequest {providerId, amount, referenceNumber, account} | UtilityPaymentResponse {message, transactionId} |
| GET | `/api/v1/user/{identification}` | Get user by identification number | -- | User DTO |
| GET | `/api/v1/user` | Get paginated users | Pageable params | List\<User\> |

### User Service (base: `/api/v1/bank-users`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| POST | `/api/v1/bank-users/register` | Register new user (publicly accessible, no auth required) | User {email, identification, password} | User DTO |
| PATCH | `/api/v1/bank-users/update/{id}` | Update user | UserUpdateRequest {status} | User DTO |
| GET | `/api/v1/bank-users` | List users (paginated) | -- | List\<User\> |
| GET | `/api/v1/bank-users/{id}` | Get user by ID | -- | User DTO |

### Fund Transfer Service (base: `/api/v1/transfer`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| POST | `/api/v1/transfer` | Initiate fund transfer | FundTransferRequest {fromAccount, toAccount, amount, authID} | FundTransferResponse |
| GET | `/api/v1/transfer` | List fund transfers (paginated) | -- | List\<FundTransfer\> |

### Utility Payment Service (base: `/api/v1/utility-payment`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| POST | `/api/v1/utility-payment` | Process utility payment | UtilityPaymentRequest {providerId, amount, referenceNumber, account} | UtilityPaymentResponse |
| GET | `/api/v1/utility-payment` | List utility payments (paginated) | -- | List\<UtilityPayment\> |

### API Gateway Routes (prefixed paths)

| Path Prefix | Target Service |
|---|---|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/banking-core/**` | core-banking-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |

---

## 4. Key Business Logic Inventory

### Fund Transfer Rules (`TransactionService.java` in core-banking-service)

1. Validates both source and destination accounts exist
2. Validates source account has sufficient balance (`actualBalance >= transfer amount` AND `actualBalance > 0`)
3. Deducts from source, credits to destination
4. Creates two `TransactionEntity` records (debit + credit) with same `transactionId`
5. **BUG:** `availableBalance` is set to `actualBalance - amount` AFTER `actualBalance` was already reduced, causing double-subtraction

### Utility Payment Processing (`TransactionService.java` in core-banking-service)

1. Validates source account exists and has sufficient balance
2. Validates utility provider exists by ID
3. Deducts amount from source account
4. **BUG:** Same double-subtraction bug -- line 63-64: `fromAccount.setActualBalance(subtract)` then `fromAccount.setAvailableBalance(actualBalance.subtract(amount))` where `actualBalance` was already reduced
5. Creates `TransactionEntity` with `UTILITY_PAYMENT` type

### User Management (`UserService.java` in user-service)

1. **Registration flow:** Check Keycloak for existing email -> Fetch user from core-banking-service by identification -> Validate email matches -> Create Keycloak user (disabled, unverified) -> Save local `UserEntity` with PENDING status
2. **Approval flow:** Admin updates user status to APPROVED -> Enables Keycloak user and marks email as verified
3. **User listing** enriches local DB records with Keycloak user data (email)

---

## 5. Integration Points

| Integration | Details |
|---|---|
| **Keycloak** | User service uses `keycloak-admin-client:24.0.4` via `KeycloakManager` to create/read/update users. Gateway validates JWT tokens against Keycloak's JWK endpoint. |
| **Zipkin** | All services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` for distributed tracing. Zipkin runs on port 9411. |
| **MySQL** | Single MySQL instance with 4 databases. Core banking uses Flyway migrations; other services use JPA auto-DDL. |
| **Spring Cloud Config** | External config from GitHub repo. Services use `spring-cloud-starter-bootstrap` to fetch config on startup. |
| **Eureka** | All business services register with Eureka. Feign clients use service names for discovery. |
| **RabbitMQ** | Mentioned in README as planned for notification service but **NOT** actually implemented in the codebase. |
| **Prometheus** | Listed in README tech stack but **no** actual Prometheus configuration exists. |

---

## 6. Build and Deployment Pipeline

- **Build:** Gradle with `spring-boot` plugin 3.2.4, `spring-dependency-management` 1.1.4, `gradle-git-properties` 2.4.2. No multi-project build -- each service is an independent Gradle project.
- **Docker Compose:** Single `docker-compose.yml` orchestrates all 11 containers. Uses `wait-for-it.sh` scripts for startup ordering. Services use `-Dspring.profiles.active=docker` profile.
- **No CI/CD pipeline:** No GitHub Actions, Jenkinsfile, or other CI configuration exists in the repository.
- **No Dockerfiles for services:** Docker images are referenced as pre-built `javatodev/*` images. Only MySQL and Keycloak have custom Dockerfiles in the docker-compose directory.
