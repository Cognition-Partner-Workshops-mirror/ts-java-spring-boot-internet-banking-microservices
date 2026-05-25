# Internet Banking Microservices — Application Knowledge Base

## 1. Architecture Overview

### High-Level Architecture

This is a **Java 21 / Spring Boot 3.2.4 / Spring Cloud 2023.0.0** internet banking platform built on a microservices architecture. It provides user management, fund transfers, utility payments, and core banking operations with centralized security through Keycloak and observability via Zipkin.

### Services

| Service | Port | Responsibility |
|---------|------|----------------|
| `internet-banking-api-gateway` | 8082 | Central edge router; OAuth2/JWT validation via Keycloak; adds `X-Auth-Id` header to downstream requests |
| `internet-banking-service-registry` | 8081 | Netflix Eureka server for dynamic service discovery |
| `internet-banking-config-server` | 8090 | Spring Cloud Config Server; reads configuration from a GitHub repository |
| `core-banking-service` | 8092 | System of record for users, accounts, transactions; processes fund transfers and utility payments at the ledger level |
| `internet-banking-user-service` | 8083 | User profile management; orchestrates Keycloak IAM for registration and user lifecycle |
| `internet-banking-fund-transfer-service` | 8084 | Orchestrates account-to-account money movement; delegates to core-banking-service via Feign |
| `internet-banking-utility-payment-service` | 8085 | Manages bill payments to external service providers; delegates to core-banking-service via Feign |

### Communication Patterns

- **Client → Gateway:** All external REST traffic enters through the API Gateway (port 8082).
- **Gateway → Downstream:** Spring Cloud Gateway routes requests by path prefix, appending `X-Auth-Id` header with the authenticated principal.
- **Inter-service (Feign):** Fund-transfer-service and utility-payment-service call core-banking-service synchronously using Spring Cloud OpenFeign with Eureka-based service discovery.
- **User-service → Core-banking:** Feign client validates user identification during registration.
- **User-service → Keycloak:** Direct REST API calls via Keycloak Admin Client library.
- **All services → Config Server:** Bootstrap configuration loaded from Spring Cloud Config Server (GitHub-backed).
- **All services → Eureka:** Register and discover services via Netflix Eureka client.

> **Note:** RabbitMQ is mentioned in the README as a future notification mechanism, but is **not implemented** in the codebase.

### Infrastructure Components

| Component | Technology | Purpose |
|-----------|------------|---------|
| Database | MySQL 8.4 | Primary persistence for all 4 application services (4 schemas) |
| Identity & Access | Keycloak 23.0.7 (PostgreSQL 15 backend) | OAuth2/OIDC provider; user/role management |
| Distributed Tracing | Zipkin 3 | Collects distributed traces via Micrometer/Brave |
| Service Discovery | Netflix Eureka | Runtime service registry |
| Configuration | Spring Cloud Config Server (Git-backed) | Externalized properties management |
| Containerization | Docker / Docker Compose | Orchestration of all services and infrastructure |

---

## 2. Data Model Documentation

### Core Banking Service (schema: `banking_core_service`)

#### `banking_core_user`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Internal user identifier |
| `first_name` | VARCHAR(255) | User's first name |
| `last_name` | VARCHAR(255) | User's last name |
| `email` | VARCHAR(255) | User's email address |
| `identification_number` | VARCHAR(255) | National Identity Card (NIC) number |

#### `banking_core_account`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Internal account identifier |
| `number` | VARCHAR(255) | Account number |
| `type` | VARCHAR(255) | Account type enum (SAVINGS, CURRENT, etc.) |
| `status` | VARCHAR(255) | Account status enum (ACTIVE, INACTIVE, etc.) |
| `actual_balance` | DECIMAL(19,2) | Actual ledger balance |
| `available_balance` | DECIMAL(19,2) | Available balance for transactions |
| `user_id` | BIGINT (FK → banking_core_user.id) | Owner of the account |

#### `banking_core_utility_account`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Internal identifier |
| `number` | VARCHAR(255) | Utility account number |
| `provider_name` | VARCHAR(255) | Name of the utility provider |

#### `banking_core_transaction`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Internal identifier |
| `amount` | DECIMAL(19,2) | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | Enum: FUND_TRANSFER, UTILITY_PAYMENT |
| `reference_number` | VARCHAR(50) | Reference (destination account or utility ref) |
| `transaction_id` | VARCHAR(50) | UUID transaction identifier |
| `account_id` | BIGINT (FK → banking_core_account.id) | Associated account |

### Fund Transfer Service (schema: `banking_core_fund_transfer_service`)

#### `fund_transfer`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Internal identifier |
| `transaction_reference` | VARCHAR | Core banking transaction ID |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `status` | VARCHAR | Enum: PENDING, SUCCESS, FAILED |
| `created_at` | TIMESTAMP | Audit: creation timestamp |
| `updated_at` | TIMESTAMP | Audit: last update timestamp |
| `created_by` | VARCHAR | Audit: creator (from X-Auth-Id) |
| `updated_by` | VARCHAR | Audit: last updater |

### User Service (schema: `banking_core_user_service`)

#### `user`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Internal identifier |
| `auth_id` | VARCHAR | Keycloak user ID |
| `identification` | VARCHAR | NIC number |
| `status` | VARCHAR | Enum: PENDING, APPROVED, REJECTED |
| `created_at` | TIMESTAMP | Audit: creation timestamp |
| `updated_at` | TIMESTAMP | Audit: last update timestamp |
| `created_by` | VARCHAR | Audit: creator |
| `updated_by` | VARCHAR | Audit: last updater |

### Utility Payment Service (schema: `banking_core_utility_payment_service`)

#### `utility_payment`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Internal identifier |
| `provider_id` | BIGINT | Utility provider reference |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Bill reference number |
| `account` | VARCHAR | Payer's account number |
| `transaction_id` | VARCHAR | Core banking transaction ID |
| `status` | VARCHAR | Enum: PROCESSING, SUCCESS, FAILED |
| `created_at` | TIMESTAMP | Audit: creation timestamp |
| `updated_at` | TIMESTAMP | Audit: last update timestamp |
| `created_by` | VARCHAR | Audit: creator |
| `updated_by` | VARCHAR | Audit: last updater |

### Entity Relationships

```
banking_core_user 1──∗ banking_core_account 1──1 banking_core_transaction
banking_core_utility_account (standalone reference table)
fund_transfer (standalone, references accounts by number string)
utility_payment (standalone, references provider by ID and account by number string)
user (standalone, references Keycloak by auth_id string)
```

---

## 3. API Surface Map

### API Gateway Routes

| Path Prefix | Target Service |
|-------------|---------------|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |
| `/banking-core/**` | core-banking-service |

### Core Banking Service (port 8092)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| GET | `/api/v1/account/bank-account/{account_number}` | — | `BankAccount` (number, type, status, actualBalance, availableBalance) | Retrieve bank account by account number |
| GET | `/api/v1/account/util-account/{account_name}` | — | `UtilityAccount` (id, number, providerName) | Retrieve utility account by provider name |
| GET | `/api/v1/user/{identification}` | — | `User` (id, firstName, lastName, email, identificationNumber, accounts[]) | Retrieve user by NIC |
| GET | `/api/v1/user` | Pageable params | `List<User>` | Paginated user list |
| POST | `/api/v1/transaction/fund-transfer` | `{fromAccount, toAccount, amount}` | `{message, transactionId}` | Process fund transfer at ledger level |
| POST | `/api/v1/transaction/util-payment` | `{amount, account, providerId, referenceNumber}` | `{message, transactionId}` | Process utility payment at ledger level |

### Internet Banking User Service (port 8083)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| POST | `/api/v1/bank-users/register` | `{email, password, identification}` | `User` (id, authId, identification, status) | Register new banking user (creates in Keycloak + local DB) |
| PATCH | `/api/v1/bank-users/update/{id}` | `{status}` | `User` | Update user status (APPROVED enables Keycloak account) |
| GET | `/api/v1/bank-users` | Pageable params | `List<User>` | Paginated user list (enriched with Keycloak email) |
| GET | `/api/v1/bank-users/{id}` | — | `User` | Retrieve user by internal ID |

### Internet Banking Fund Transfer Service (port 8084)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| POST | `/api/v1/transfer` | `{fromAccount, toAccount, amount}` | `{message, transactionId}` | Initiate fund transfer (persists locally, delegates to core-banking) |
| GET | `/api/v1/transfer` | Pageable params | `List<FundTransfer>` | Paginated fund transfer history |

### Internet Banking Utility Payment Service (port 8085)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| POST | `/api/v1/utility-payment` | `{amount, account, providerId, referenceNumber}` | `{message, transactionId}` | Process utility payment (persists locally, delegates to core-banking) |
| GET | `/api/v1/utility-payment` | Pageable params | `List<UtilityPayment>` | Paginated payment history |

---

## 4. Key Business Logic Inventory

### User Registration Flow

1. Client POSTs to `/user/api/v1/bank-users/register` (public endpoint — no JWT required).
2. User-service checks if email already exists in Keycloak → throws `UserAlreadyRegisteredException` if found.
3. User-service calls core-banking-service to validate NIC exists → throws `InvalidBankingUserException` if not found.
4. Validates email matches the core-banking record → throws `InvalidEmailException` on mismatch.
5. Creates user in Keycloak (disabled, email unverified) with provided password.
6. Persists local user entity with `status=PENDING`.
7. Admin later approves user via PATCH (`status=APPROVED`), which enables the Keycloak account.

### Fund Transfer Flow

1. Client POSTs to `/fund-transfer/api/v1/transfer` (JWT required).
2. Fund-transfer-service persists a `FundTransferEntity` with `status=PENDING`.
3. Calls core-banking-service `/api/v1/transaction/fund-transfer` via Feign.
4. Core-banking validates source account has sufficient funds (`actualBalance >= amount`).
5. Core-banking debits source account, credits destination account, records two `TransactionEntity` records.
6. Returns `transactionId` to fund-transfer-service.
7. Fund-transfer-service updates local record with `transactionReference` and `status=SUCCESS`.

### Utility Payment Flow

1. Client POSTs to `/utility-payment/api/v1/utility-payment` (JWT required).
2. Utility-payment-service persists a `UtilityPaymentEntity` with `status=PROCESSING`.
3. Calls core-banking-service `/api/v1/transaction/util-payment` via Feign.
4. Core-banking validates funds, debits the account, records a `TransactionEntity`.
5. Returns `transactionId`.
6. Utility-payment-service updates local record with `transactionId` and `status=SUCCESS`.

### Balance Validation Rule

- A transaction is rejected with `InsufficientFundsException` if:
  - `actualBalance < 0`, OR
  - `actualBalance < requestedAmount`

### Authentication & Authorization

- Gateway validates JWT tokens issued by Keycloak.
- Only `/user/api/v1/bank-users/register` and `/actuator/**` endpoints are publicly accessible.
- All other routes require a valid Bearer token.
- Gateway extracts the principal name and forwards it as `X-Auth-Id` header to downstream services.
- Downstream services extract `X-Auth-Id` via `AppAuthUserFilter` for audit purposes.

---

## 5. Integration Points

### Keycloak (Identity Provider)

- **Version:** 23.0.7
- **Connection:** User-service uses `keycloak-admin-client:24.0.4` library with client credentials grant.
- **Realm:** Configured via Spring Cloud Config (`app.config.keycloak.*` properties).
- **Operations:** Create user, read user, update user (enable/verify email), search by email.
- **Gateway integration:** JWT validation via `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`.

### MySQL (Primary Database)

- **Version:** 8.4
- **Schemas:** `banking_core_service`, `banking_core_fund_transfer_service`, `banking_core_user_service`, `banking_core_utility_payment_service`
- **Driver:** `com.mysql:mysql-connector-j:8.4.0`
- **Migrations:** Flyway (core-banking-service only); other services use JPA auto-DDL (Hibernate `ddl-auto`).
- **Connection:** Static IP `172.25.0.9:3306` in Docker network.

### Zipkin (Distributed Tracing)

- **Version:** 3
- **Integration:** Micrometer Tracing with Brave bridge (`micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`).
- **All services** report traces to Zipkin at `172.25.0.12:9411`.

### Spring Cloud Config Server (Configuration Management)

- **Git Repository:** `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch:** `main`
- **Path:** `configuration/`
- **Usage:** All downstream services load configuration at bootstrap via `spring.cloud.config.uri`.

### Netflix Eureka (Service Discovery)

- **Server:** `internet-banking-service-registry` at port 8081.
- **Clients:** All application services register themselves and discover others via Eureka.
- **Feign integration:** `@FeignClient(value = "core-banking-service")` uses Eureka for URL resolution.

### RabbitMQ (Message Broker — NOT IMPLEMENTED)

- Mentioned in README for a future Notification Service.
- No RabbitMQ dependency or configuration exists in the codebase.

---

## 6. Build and Deployment Pipeline Summary

### Build System

- **Gradle 8.6** (per-service wrapper — no root-level multi-project build).
- Each service is built independently: `./gradlew clean build`.
- No shared library exists in the repository (code duplication across services).

### Docker Images

- Each service has its own `Dockerfile` based on `eclipse-temurin:21.0.2_13-jre-alpine`.
- Images include `wait-for-it.sh` script for startup ordering.
- Pre-built images published to Docker Hub under `javatodev/` namespace.

### Docker Compose Deployment

- **Full stack:** `docker-compose/docker-compose.yml` — all services + infrastructure.
- **Support only:** `docker-compose/docker-compose-support-apps.yml` — MySQL, Keycloak, PostgreSQL, Zipkin, Config Server, Service Registry.
- **Network:** Custom bridge `javatodev_ib_network` (172.25.0.0/16) with static IPs.
- **Startup ordering:** `wait-for-it.sh` ensures services start only after dependencies are ready (50s timeout).
- **Profiles:** Services activate `docker` Spring profile at runtime (`-Dspring.profiles.active=docker`).

### CI/CD Pipeline

- **No CI/CD pipeline** is configured in the repository.
- No GitHub Actions, Jenkins, or other automation present.

### Local Development

- Run support apps via `docker-compose-support-apps.yml`.
- Start individual services with `./gradlew bootRun` using `dev` profile.
- Test credentials: `ib_admin@javatodev.com / 5V7huE3G86uB`.
