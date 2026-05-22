# Knowledge Base — Internet Banking Microservices

## Architecture Overview

### Services

| Service | Port | Description |
|---------|------|-------------|
| internet-banking-api-gateway | 8082 | Spring Cloud Gateway, OAuth2/JWT via Keycloak |
| internet-banking-service-registry | 8081 | Netflix Eureka for service discovery |
| internet-banking-config-server | 8090 | Spring Cloud Config, reads from GitHub repo |
| internet-banking-user-service | 8083 | User profile management and Keycloak IAM orchestration |
| internet-banking-fund-transfer-service | 8084 | Orchestrates account-to-account fund transfers |
| internet-banking-utility-payment-service | 8085 | Manages bill payments to external service providers |
| core-banking-service | 8092 | Core engine: users, accounts, transactions, ledger |

### Communication

- **Synchronous REST via OpenFeign**: All inter-service communication uses synchronous HTTP calls.
  - `internet-banking-fund-transfer-service` uses `BankingCoreFeignClient` (declarative Feign interface)
  - `internet-banking-user-service` uses `BankingCoreRestClient` (RestTemplate-based)
  - `internet-banking-utility-payment-service` uses `BankingCoreRestClient` (RestTemplate-based)
  - All target `core-banking-service` for ledger operations.

### Infrastructure

| Component | Purpose |
|-----------|---------|
| **Eureka** | Service discovery — all services register as Eureka clients; Feign clients use service names (e.g., "core-banking-service") for lookup |
| **Spring Cloud Config Server** | Centralized configuration using `bootstrap.yml` pattern; three profiles: `default`, `dev`, `docker` |
| **Keycloak 23.0.7** | OAuth2/OIDC identity provider; realm: `javatodev-internet-banking`; backed by PostgreSQL 15 |
| **Zipkin 3** | Distributed tracing via Micrometer Brave bridge (`micrometer-tracing-bridge-brave` + `zipkin-reporter-brave`) |
| **MySQL** | Domain data store — 4 schemas: `banking_core_service`, `banking_core_fund_transfer_service`, `banking_core_user_service`, `banking_core_utility_payment_service` |
| **PostgreSQL 15** | Keycloak persistence |

### Gateway Security

- `GatewayConfiguration` implements a `GlobalFilter` that extracts the authenticated user's subject from the JWT and injects it as an `X-Auth-Id` header on downstream requests.
- `SecurityConfiguration` permits `/user/api/v1/bank-users/register` and `/actuator/**` without authentication.
- All other routes require a valid OAuth2 JWT token.

---

## Data Model Documentation

### core-banking-service

#### `UserEntity` → table: `banking_core_user`

| Field | Type | Description |
|-------|------|-------------|
| id | Long | Primary key |
| firstName | String | User's first name |
| lastName | String | User's last name |
| email | String | User's email address |
| identificationNumber | String | National identification (NIC) |
| **Relationships** | | OneToMany → `BankAccountEntity` |

#### `BankAccountEntity` → table: `banking_core_account`

| Field | Type | Description |
|-------|------|-------------|
| id | Long | Primary key |
| number | String | Account number |
| type | Enum | `SAVINGS_ACCOUNT` |
| status | Enum | `ACTIVE` |
| availableBalance | BigDecimal | Available balance for transactions |
| actualBalance | BigDecimal | Actual ledger balance |
| **Relationships** | | ManyToOne → `UserEntity` |

#### `TransactionEntity` → table: `banking_core_transaction`

| Field | Type | Description |
|-------|------|-------------|
| id | Long | Primary key |
| amount | BigDecimal | Transaction amount |
| transactionType | Enum | `FUND_TRANSFER` or `UTILITY_PAYMENT` |
| referenceNumber | String | External reference |
| transactionId | String | Internal transaction identifier |
| **Relationships** | | OneToOne → `BankAccountEntity` |

#### `UtilityAccountEntity` → table: `banking_core_utility_account`

| Field | Type | Description |
|-------|------|-------------|
| id | Long | Primary key |
| number | String | Utility account number |
| providerName | String | Name of the utility provider |

#### Migrations
- Flyway migrations located in `src/main/resources/db/migration/`

---

### internet-banking-fund-transfer-service

#### `FundTransferEntity` extends `AuditAware` → table: `fund_transfer`

| Field | Type | Description |
|-------|------|-------------|
| id | Long | Primary key |
| transactionReference | String | Reference from core-banking |
| fromAccount | String | Source account number |
| toAccount | String | Destination account number |
| amount | BigDecimal | Transfer amount |
| status | Enum | `PENDING`, `SUCCESS`, `FAILED` |

---

### internet-banking-user-service

#### `UserEntity` extends `AuditAware` → table: `user`

| Field | Type | Description |
|-------|------|-------------|
| id | Long | Primary key |
| authId | String | Keycloak subject ID |
| identification | String | National identification (NIC) |
| status | Enum | `PENDING`, `APPROVED` |

---

### internet-banking-utility-payment-service

#### `UtilityPaymentEntity` extends `AuditAware` → table: `utility_payment`

| Field | Type | Description |
|-------|------|-------------|
| id | Long | Primary key |
| providerId | Long | Utility provider ID |
| amount | BigDecimal | Payment amount |
| referenceNumber | String | Payment reference |
| account | String | Source bank account number |
| transactionId | String | Transaction ID from core-banking |
| status | Enum | `PROCESSING`, `SUCCESS` |

---

## API Surface Map

### User Service (internet-banking-user-service) — base: `/api/v1/bank-users`

| Method | Endpoint | Description | Request Body |
|--------|----------|-------------|--------------|
| POST | `/api/v1/bank-users/register` | Register a new user | User DTO (email, password, identification) |
| PATCH | `/api/v1/bank-users/update/{id}` | Update user (e.g., approve) | UserUpdateRequest (status) |
| GET | `/api/v1/bank-users` | List users (paginated) | — |
| GET | `/api/v1/bank-users/{id}` | Get user by ID | — |

### Fund Transfer Service — base: `/api/v1/transfer`

| Method | Endpoint | Description | Request Body |
|--------|----------|-------------|--------------|
| POST | `/api/v1/transfer` | Initiate fund transfer | FundTransferRequest (fromAccount, toAccount, amount) |
| GET | `/api/v1/transfer` | List transfers (paginated) | — |

### Utility Payment Service — base: `/api/v1/utility-payment`

| Method | Endpoint | Description | Request Body |
|--------|----------|-------------|--------------|
| POST | `/api/v1/utility-payment` | Process utility payment | UtilityPaymentRequest (providerId, amount, referenceNumber, account) |
| GET | `/api/v1/utility-payment` | List payments (paginated) | — |

### Core Banking Service

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/user/{identification}` | Get user by identification number (NIC) |
| GET | `/api/v1/user` | List users (paginated) |
| GET | `/api/v1/account/bank-account/{account_number}` | Get bank account by account number |
| GET | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name |
| POST | `/api/v1/transaction/fund-transfer` | Execute fund transfer (debit + credit) |
| POST | `/api/v1/transaction/util-payment` | Execute utility payment |

---

## Key Business Logic Inventory

### User Registration Flow

1. Check Keycloak for existing email (reject if duplicate)
2. Verify NIC (identification number) against `core-banking-service` user records
3. Provision Keycloak user (disabled, email unverified)
4. Save local `UserEntity` with `PENDING` status

### User Approval Flow

1. PATCH request with `status=APPROVED`
2. Enable the Keycloak user account
3. Set `emailVerified=true` in Keycloak

### Fund Transfer Flow

1. `FundTransferService` saves a new `FundTransferEntity` with `PENDING` status
2. Calls `core-banking-service` via Feign client (`BankingCoreFeignClient`)
3. Core-banking validates balances (`actualBalance >= 0` AND `actualBalance >= amount`)
4. Debits from-account (subtract amount from both `actualBalance` and `availableBalance`)
5. Credits to-account (add amount to both `actualBalance` and `availableBalance`)
6. Creates 2 `TransactionEntity` records (debit + credit)
7. Returns `transactionId` to fund-transfer-service
8. `FundTransferService` marks entity as `SUCCESS`

### Utility Payment Flow

1. `UtilityPaymentService` saves a new `UtilityPaymentEntity` with `PROCESSING` status
2. Calls `core-banking-service` via REST client
3. Core-banking validates balance
4. Subtracts amount from user's bank account
5. Records a single `TransactionEntity`
6. Returns `transactionId`
7. `UtilityPaymentService` marks entity as `SUCCESS`
8. **Note**: No actual third-party call is made (placeholder in code)

### Balance Validation

- Checks: `actualBalance >= 0` AND `actualBalance >= amount`
- Throws `InsufficientFundsException` if validation fails

---

## Integration Points

### Keycloak

- **User Service**: Uses `keycloak-admin-client 24.0.4` via `KeycloakManager` to create/read/update users in the `javatodev-internet-banking` realm.
- **API Gateway**: Validates JWTs via `spring-security-oauth2-resource-server` with Keycloak as the issuer.

### Zipkin

- All services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` for distributed tracing.
- Trace data is sent to the Zipkin collector.

### Eureka

- All services register as Eureka clients.
- Feign clients use logical service names (e.g., `"core-banking-service"`) resolved via Eureka.

### Config Server

- Services use `bootstrap.yml` to locate the config server at startup.
- Three profiles: `default`, `dev`, `docker`.
- Config server reads properties from a GitHub repository.

### MySQL

- Single MySQL container with 4 schemas created via `privileges.sql`.
- Connection via `mysql-connector-j 8.4.0`.
- Flyway manages schema migrations in `core-banking-service`.

---

## Build & Deployment

### Build System

- **Gradle** with Spring Boot 3.2.4 plugin, Spring Cloud 2023.0.0 BOM
- Each service is an independent Gradle project (no multi-module build)
- Gradle wrapper version: 8.6

### Docker Compose Orchestration

- `docker-compose/docker-compose.yml` — Full stack (all services + infrastructure)
- `docker-compose/docker-compose-support-apps.yml` — Infrastructure only (MySQL, Keycloak, PostgreSQL, Zipkin)
- `wait-for-it.sh` scripts ensure startup ordering (50s timeout)
- Static IPs on `javatodev_ib_network` (172.25.0.0/16)

### Docker Images

- Pre-built images from `javatodev/` Docker Hub namespace
- Base image: `eclipse-temurin:21-jre-alpine`

### CI/CD

- **No CI/CD pipeline exists** (only `.github/FUNDING.yml` present)
- Build is manual: `./gradlew clean build` per service, then `docker build`
