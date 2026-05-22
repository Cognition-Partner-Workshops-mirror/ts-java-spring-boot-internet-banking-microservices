# Internet Banking Microservices — Knowledge Base

## 1. Architecture Overview

### System Summary

A **Java 21 / Spring Boot 3.2.4 / Spring Cloud 2023.0.0** microservices application that models an internet banking platform. The system provides user registration, account lookup, fund transfers, and utility bill payments through six independently deployable services, orchestrated via Docker Compose.

### Service Inventory

| Service | Port | Role | Communication |
|---------|------|------|---------------|
| `internet-banking-api-gateway` | 8082 | Edge gateway — routes traffic, enforces JWT auth via Keycloak | Spring Cloud Gateway (reactive) |
| `internet-banking-service-registry` | 8081 | Service discovery — Netflix Eureka Server | Eureka protocol |
| `internet-banking-config-server` | 8090 | Externalized configuration — serves properties from GitHub repo | Spring Cloud Config (HTTP) |
| `core-banking-service` | 8092 | System of record — users, accounts, ledger, transactions | REST (Spring MVC) |
| `internet-banking-fund-transfer-service` | 8084 | Orchestrates account-to-account fund transfers | OpenFeign → core-banking-service |
| `internet-banking-utility-payment-service` | 8085 | Orchestrates utility/bill payments | OpenFeign → core-banking-service |
| `internet-banking-user-service` | 8083 | User registration & management — bridges Keycloak IAM with core banking | OpenFeign → core-banking-service + Keycloak Admin Client |

### Communication Patterns

- **Client → Gateway**: All external traffic enters through the API Gateway (port 8082). JWT tokens are validated at this edge.
- **Gateway → Downstream**: The gateway injects an `X-Auth-Id` header (authenticated principal name) and routes requests based on path prefixes:
  - `/user/**` → user-service
  - `/fund-transfer/**` → fund-transfer-service
  - `/payment/**` → utility-payment-service
  - `/core/**` → core-banking-service (noted in README but not restricted)
- **Service → Service**: Synchronous REST via **OpenFeign**, resolved through **Eureka** service discovery. No asynchronous messaging is implemented (RabbitMQ is mentioned in README but absent from code).
- **Config Distribution**: All services fetch configuration from the Config Server at startup via `bootstrap.yml` (profiles: `dev`, `docker`). The Config Server reads from a GitHub repository.

### Architecture Diagram (Logical)

```
                          ┌──────────────────────┐
                          │   Keycloak (IAM)      │
                          │   Port 8080           │
                          │   PostgreSQL backend   │
                          └──────────┬─────────────┘
                                     │ JWT validation
                          ┌──────────▼─────────────┐
  Clients ───────────────►│  API Gateway (8082)     │
                          │  OAuth2 Resource Server │
                          │  X-Auth-Id injection    │
                          └──────────┬─────────────┘
                     ┌───────────────┼───────────────┐
                     ▼               ▼               ▼
           ┌─────────────┐ ┌─────────────┐ ┌─────────────────┐
           │ User Service │ │ Fund Xfer   │ │ Utility Payment │
           │   (8083)     │ │ Service     │ │ Service (8085)  │
           │              │ │  (8084)     │ │                 │
           └──────┬───────┘ └──────┬──────┘ └────────┬────────┘
                  │                │                  │
                  │  Feign/Eureka  │   Feign/Eureka   │  Feign/Eureka
                  │                │                  │
                  └────────────────▼──────────────────┘
                          ┌─────────────────┐
                          │ Core Banking    │
                          │ Service (8092)  │
                          │ (System of      │
                          │  Record)        │
                          └────────┬────────┘
                                   │
                          ┌────────▼────────┐
                          │   MySQL 8.4     │
                          │   4 schemas     │
                          └─────────────────┘

  ┌─────────────────┐     ┌─────────────────┐
  │ Config Server   │     │ Service Registry│
  │   (8090)        │     │  Eureka (8081)  │
  │ GitHub-backed   │     │                 │
  └─────────────────┘     └─────────────────┘

  ┌─────────────────┐
  │ Zipkin (9411)   │
  │ Distributed     │
  │ Tracing         │
  └─────────────────┘
```

---

## 2. Data Model Documentation

### core-banking-service Database (`banking_core_service`)

#### `banking_core_user`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal user ID |
| `first_name` | VARCHAR(255) | — | User's first name |
| `last_name` | VARCHAR(255) | — | User's last name |
| `email` | VARCHAR(255) | — | Email address |
| `identification_number` | VARCHAR(255) | — | National ID (NIC) |

#### `banking_core_account`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal account ID |
| `number` | VARCHAR(255) | — | Account number (e.g., 100015003000) |
| `type` | VARCHAR(255) | — | Enum: `SAVINGS_ACCOUNT`, etc. |
| `status` | VARCHAR(255) | — | Enum: `ACTIVE`, etc. |
| `actual_balance` | DECIMAL(19,2) | — | Ledger balance |
| `available_balance` | DECIMAL(19,2) | — | Available for withdrawal |
| `user_id` | BIGINT | FK → `banking_core_user.id` | Account owner |

#### `banking_core_utility_account`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal ID |
| `number` | VARCHAR(255) | — | Provider account number |
| `provider_name` | VARCHAR(255) | — | e.g., VODAFONE, VERIZON, AIRTEL |

#### `banking_core_transaction`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal ID |
| `amount` | DECIMAL(19,2) | — | Signed amount (negative = debit) |
| `transaction_type` | VARCHAR(30) | NOT NULL | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | NOT NULL | Destination account or ref |
| `transaction_id` | VARCHAR(50) | NOT NULL | UUID-based transaction identifier |
| `account_id` | BIGINT | FK → `banking_core_account.id` | Source account |

### fund-transfer-service Database (`banking_core_fund_transfer_service`)

#### `fund_transfer`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal ID |
| `from_account` | VARCHAR(255) | — | Source account number |
| `to_account` | VARCHAR(255) | — | Destination account number |
| `amount` | DECIMAL(19,2) | — | Transfer amount |
| `status` | VARCHAR(255) | — | Enum: `PENDING`, `SUCCESS` |
| `transaction_reference` | VARCHAR(255) | — | Core banking transaction ID |
| `created_date` | TIMESTAMP | — | Audit: creation time |
| `created_by` | VARCHAR(255) | — | Audit: creator |
| `modified_date` | TIMESTAMP | — | Audit: last modification |
| `modified_by` | VARCHAR(255) | — | Audit: modifier |
| `version` | BIGINT | — | Optimistic lock |

### user-service Database (`banking_core_user_service`)

#### `user`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal ID |
| `auth_id` | VARCHAR(255) | — | Keycloak user ID |
| `identification` | VARCHAR(255) | — | NIC number |
| `status` | VARCHAR(255) | — | Enum: `PENDING`, `APPROVED` |
| `created_date` | TIMESTAMP | — | Audit: creation time |
| `created_by` | VARCHAR(255) | — | Audit: creator |
| `modified_date` | TIMESTAMP | — | Audit: last modification |
| `modified_by` | VARCHAR(255) | — | Audit: modifier |
| `version` | BIGINT | — | Optimistic lock |

### utility-payment-service Database (`banking_core_utility_payment_service`)

#### `utility_payment`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal ID |
| `provider_id` | BIGINT | — | Utility provider ID |
| `amount` | DECIMAL(19,2) | — | Payment amount |
| `reference_number` | VARCHAR(255) | — | Bill reference number |
| `account` | VARCHAR(255) | — | Source bank account number |
| `transaction_id` | VARCHAR(255) | — | Core banking transaction ID |
| `status` | VARCHAR(255) | — | Enum: `PROCESSING`, `SUCCESS` |
| `created_date` | TIMESTAMP | — | Audit: creation time |
| `created_by` | VARCHAR(255) | — | Audit: creator |
| `modified_date` | TIMESTAMP | — | Audit: last modification |
| `modified_by` | VARCHAR(255) | — | Audit: modifier |
| `version` | BIGINT | — | Optimistic lock |

### Entity Relationship Summary

```
banking_core_user  1 ──── * banking_core_account  1 ──── * banking_core_transaction
                                                          (via account_id FK)

banking_core_utility_account  (standalone lookup table)

fund_transfer  (standalone per-service record, references account numbers as strings)
utility_payment (standalone per-service record, references account numbers as strings)
user (standalone per-service record, links to Keycloak via auth_id)
```

---

## 3. API Surface Map

### API Gateway Routes (Public-Facing)

| Gateway Path Prefix | Target Service | Auth Required |
|---------------------|----------------|---------------|
| `/user/**` | internet-banking-user-service | Yes (except `/user/api/v1/bank-users/register`) |
| `/fund-transfer/**` | internet-banking-fund-transfer-service | Yes |
| `/payment/**` | internet-banking-utility-payment-service | Yes |
| `/banking-core/**` | core-banking-service | Yes |
| `/actuator/**` (all services) | respective services | No |

### core-banking-service Endpoints

| Method | Path | Request Body | Response | Description |
|--------|------|-------------|----------|-------------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | — | `BankAccount` (id, number, type, status, availableBalance, actualBalance, user) | Fetch bank account by number |
| `GET` | `/api/v1/account/util-account/{account_name}` | — | `UtilityAccount` (id, number, providerName) | Fetch utility account by provider name |
| `POST` | `/api/v1/transaction/fund-transfer` | `{fromAccount, toAccount, amount}` | `{message, transactionId}` | Execute fund transfer with balance validation |
| `POST` | `/api/v1/transaction/util-payment` | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` | Execute utility payment with balance validation |
| `GET` | `/api/v1/user/{identification}` | — | `User` (id, firstName, lastName, email, identificationNumber, bankAccounts[]) | Look up user by NIC |
| `GET` | `/api/v1/user` | Pageable query params | `List<User>` | Paginated user list |

### internet-banking-user-service Endpoints

| Method | Path | Request Body | Response | Description |
|--------|------|-------------|----------|-------------|
| `POST` | `/api/v1/bank-users/register` | `{email, identification, password}` | `User` (id, email, identification, authId, status) | Register new user (creates in Keycloak + local DB) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `{status}` | `User` | Update user (e.g., approve registration) |
| `GET` | `/api/v1/bank-users` | Pageable query params | `List<User>` | Paginated user list (enriched from Keycloak) |
| `GET` | `/api/v1/bank-users/{id}` | — | `User` | Get user by internal ID |

### internet-banking-fund-transfer-service Endpoints

| Method | Path | Request Body | Response | Description |
|--------|------|-------------|----------|-------------|
| `POST` | `/api/v1/transfer` | `{fromAccount, toAccount, amount}` | `{message, transactionId}` | Initiate fund transfer (saves locally, delegates to core banking) |
| `GET` | `/api/v1/transfer` | Pageable query params | `List<FundTransfer>` | Paginated list of fund transfers |

### internet-banking-utility-payment-service Endpoints

| Method | Path | Request Body | Response | Description |
|--------|------|-------------|----------|-------------|
| `POST` | `/api/v1/utility-payment` | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` | Process utility payment (saves locally, delegates to core banking) |
| `GET` | `/api/v1/utility-payment` | Pageable query params | `List<UtilityPayment>` | Paginated list of utility payments |

### internet-banking-service-registry Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/` (Eureka Dashboard) | Service registry dashboard |

### internet-banking-config-server Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/{application}/{profile}` | Fetch configuration for a given service and profile |

---

## 4. Key Business Logic Inventory

### User Registration Flow (`internet-banking-user-service`)

1. Client POSTs to `/api/v1/bank-users/register` with email, identification (NIC), and password.
2. Service checks Keycloak for existing user with same email → throws `UserAlreadyRegisteredException` if found.
3. Calls core-banking-service via Feign to validate user exists by NIC → throws `InvalidBankingUserException` if not found.
4. Validates email matches core banking record → throws `InvalidEmailException` if mismatch.
5. Creates Keycloak user (disabled, email unverified) with provided password.
6. Saves local `UserEntity` with `status=PENDING` and Keycloak `authId`.
7. Admin later calls `PATCH /update/{id}` with `status=APPROVED` which enables the Keycloak user and marks email as verified.

### Fund Transfer Flow (`internet-banking-fund-transfer-service` → `core-banking-service`)

1. Client POSTs to `/api/v1/transfer` via gateway.
2. Fund-transfer-service saves a `FundTransferEntity` with `status=PENDING`.
3. Calls core-banking-service `POST /api/v1/transaction/fund-transfer` via Feign.
4. Core banking validates sender balance ≥ transfer amount.
5. Debits sender: `actualBalance -= amount`, `availableBalance = actualBalance - amount`.
6. Credits receiver: `actualBalance += amount`, `availableBalance = actualBalance + amount`.
7. Records two `TransactionEntity` rows (one per account) with UUID transaction ID.
8. Fund-transfer-service updates local entity with `status=SUCCESS` and the transaction reference.

### Utility Payment Flow (`internet-banking-utility-payment-service` → `core-banking-service`)

1. Client POSTs to `/api/v1/utility-payment` via gateway.
2. Utility-payment-service saves a `UtilityPaymentEntity` with `status=PROCESSING`.
3. Calls core-banking-service `POST /api/v1/transaction/util-payment` via Feign.
4. Core banking validates sender balance, looks up utility provider account.
5. Debits sender account and records a transaction.
6. Utility-payment-service updates local entity with `status=SUCCESS` and transaction ID.

### Balance Validation Rule

The `TransactionService.validateBalance()` method checks:
- `actualBalance >= 0` AND `actualBalance >= transferAmount`
- Throws `InsufficientFundsException` if either condition fails.

---

## 5. Integration Points

### Identity Provider — Keycloak 23.0.7

- **Protocol**: OIDC / OAuth 2.0
- **Realm**: Imported from `docker-compose/keycloak/` volume at startup
- **API Gateway integration**: JWT validation via `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`
- **User-service integration**: Keycloak Admin Client (client credentials grant) for CRUD operations on users
- **Configuration**: `app.config.keycloak.server-url`, `realm`, `clientId`, `client-secret` via Spring Cloud Config

### Database — MySQL 8.4

- Single MySQL instance with four schemas: `banking_core_service`, `banking_core_fund_transfer_service`, `banking_core_user_service`, `banking_core_utility_payment_service`
- Application user: `javatodev_development` with broad privileges
- Core banking schema uses **Flyway** migrations (3 migration scripts)
- Other services use Hibernate auto-DDL (JPA + `AuditAware` base class generates schema)

### Distributed Tracing — Zipkin 3

- All services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` dependencies
- Traces are exported to Zipkin at `172.25.0.12:9411`
- Feign calls instrumented via `feign-micrometer`

### Configuration — Spring Cloud Config Server

- Backed by GitHub repo: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- Branch: `main`, search path: `configuration`
- Services use `bootstrap.yml` with profiles `dev` and `docker` to fetch config at startup

### Service Discovery — Netflix Eureka

- Eureka server at `172.25.0.7:8081`
- All services register as Eureka clients
- Feign clients use Eureka service names for resolution (e.g., `core-banking-service`)

---

## 6. Build and Deployment Summary

### Build

- **Build tool**: Gradle (each service has its own independent `build.gradle`, no multi-module root project)
- **Java version**: 21 (source compatibility)
- **Spring Boot**: 3.2.4 via Spring Boot Gradle plugin
- **Spring Cloud**: 2023.0.0 via BOM
- **Build command**: `./gradlew clean build` (per service)

### Container Images

- **Base image**: `eclipse-temurin:21.0.2_13-jre-alpine`
- **Pattern**: Copy fat JAR as `app.jar`, include `wait-for-it.sh` for startup ordering
- **Shell requirement**: Alpine + `bash` (installed via `apk add`)

### Docker Compose Deployment

- **File**: `docker-compose/docker-compose.yml`
- **Network**: `javatodev_ib_network` (bridge, `172.25.0.0/16`)
- **Static IP assignments**: Each container gets a fixed IP on the bridge network
- **Startup ordering**: `wait-for-it.sh` scripts with 50-second timeout waiting for:
  1. Service Registry (8081)
  2. Config Server (8090)
  3. MySQL (3306)

### Container Map

| Container | Image | IP | Port |
|-----------|-------|----|------|
| `openzipkin_server` | `openzipkin/zipkin:3` | 172.25.0.12 | 9411 |
| `keycloak_web` | `keycloak:23.0.7` | 172.25.0.11 | 8080 |
| `keycloak_postgre_db` | `postgres:15` | 172.25.0.10 | 5432 (closed) |
| `mysql_javatodev_app` | Custom (MySQL 8.4) | 172.25.0.9 | 3306 |
| `internet-banking-config-server` | `javatodev/internet-banking-config-server` | 172.25.0.8 | 8090 |
| `internet-banking-service-registry` | `javatodev/internet-banking-service-registry` | 172.25.0.7 | 8081 |
| `internet-banking-api-gateway` | `javatodev/internet-banking-api-gateway` | 172.25.0.6 | 8082 |
| `internet-banking-user-service` | `javatodev/internet-banking-user-service` | 172.25.0.5 | 8083 |
| `internet-banking-fund-transfer-service` | `javatodev/internet-banking-fund-transfer-service` | 172.25.0.4 | 8084 |
| `internet-banking-utility-payment-service` | `javatodev/internet-banking-utility-payment-service` | 172.25.0.3 | 8085 |
| `core-banking-service` | `javatodev/core-banking-service` | 172.25.0.2 | 8092 |

### CI/CD

- **No CI/CD pipeline** is configured in the repository.
- No GitHub Actions, Jenkins, or other automation files are present.

### Test Data

- Pre-loaded via Flyway migration `V1.0.20210427174721__temp_data.sql`:
  - 4 users, 14 savings accounts, 6 utility provider accounts
- Keycloak realm imported with pre-configured users, roles, and OIDC clients
- Test credentials: `ib_admin@javatodev.com / 5V7huE3G86uB`
