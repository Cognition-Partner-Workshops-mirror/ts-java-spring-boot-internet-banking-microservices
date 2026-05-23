# Internet Banking Microservices — Knowledge Base

## 1. Architecture Overview

### 1.1 High-Level Architecture

This is a **Java 21 / Spring Boot 3.2.4 / Spring Cloud 2023.0.0** internet banking platform built on a microservices architecture. The system is composed of **6 independently deployable services** that communicate synchronously via REST (OpenFeign) and are orchestrated through Docker Compose.

```
┌─────────────┐
│   Client     │
└──────┬───────┘
       │ HTTPS (JWT Bearer)
┌──────▼───────────────────────────────────────────────────┐
│           API Gateway (8082)                             │
│   Spring Cloud Gateway + OAuth2 Resource Server          │
│   Routes: /user/** /fund-transfer/** /payment/** /core/**│
└──┬──────────┬──────────────┬───────────────┬─────────────┘
   │          │              │               │
┌──▼──┐  ┌───▼────┐  ┌──────▼──────┐  ┌─────▼─────┐
│User │  │Fund    │  │Utility     │  │Core       │
│Svc  │  │Transfer│  │Payment Svc │  │Banking Svc│
│8083 │  │Svc 8084│  │8085        │  │8092       │
└──┬──┘  └───┬────┘  └──────┬─────┘  └─────┬─────┘
   │         │              │               │
   │    ┌────▼──────────────▼───────────────▼──┐
   │    │         Core Banking Service          │
   │    │        (Feign REST Calls)             │
   │    └──────────────────────────────────────┘
   │
┌──▼──────────┐
│  Keycloak   │
│  (IAM 8080) │
└─────────────┘
```

### 1.2 Services Summary

| Service | Port | Type | Purpose |
|---------|------|------|---------|
| `internet-banking-api-gateway` | 8082 | Infrastructure | Central entry point; JWT validation via Keycloak; routes requests to downstream services |
| `internet-banking-service-registry` | 8081 | Infrastructure | Netflix Eureka server for dynamic service discovery |
| `internet-banking-config-server` | 8090 | Infrastructure | Spring Cloud Config Server; reads config from [GitHub repo](https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git) |
| `core-banking-service` | 8092 | Business | System of record for users, bank accounts, utility accounts, and transactions |
| `internet-banking-user-service` | 8083 | Business | User registration, profile management; integrates with Keycloak for IAM |
| `internet-banking-fund-transfer-service` | 8084 | Business | Orchestrates account-to-account fund transfers through core-banking-service |
| `internet-banking-utility-payment-service` | 8085 | Business | Orchestrates utility bill payments through core-banking-service |

### 1.3 Communication Patterns

- **Client → Gateway**: HTTP/REST with JWT Bearer token (OAuth2)
- **Gateway → Downstream Services**: HTTP routing via Spring Cloud Gateway with `X-Auth-Id` header injection
- **Business Services → Core Banking**: Synchronous REST via **OpenFeign** with Eureka-based service discovery
- **User Service → Keycloak**: Direct Keycloak Admin Client API calls for user provisioning
- **No asynchronous messaging**: RabbitMQ is mentioned in the README but not implemented in code

### 1.4 Technology Stack

| Category | Technology | Version |
|----------|-----------|---------|
| Language | Java | 21 |
| Framework | Spring Boot | 3.2.4 |
| Cloud | Spring Cloud | 2023.0.0 |
| Gateway | Spring Cloud Gateway | (managed by Spring Cloud BOM) |
| Service Discovery | Netflix Eureka | (managed by Spring Cloud BOM) |
| Config Management | Spring Cloud Config Server | (managed by Spring Cloud BOM) |
| Inter-Service Communication | OpenFeign | (managed by Spring Cloud BOM) |
| IAM | Keycloak | 23.0.7 |
| Database (Application) | MySQL | 8.4.0 |
| Database (Keycloak) | PostgreSQL | 15 |
| DB Migration | Flyway | 10.12.0 |
| Distributed Tracing | Zipkin + Micrometer Tracing (Brave) | 3 |
| ORM | Spring Data JPA (Hibernate) | (managed by Spring Boot BOM) |
| API Docs | SpringDoc OpenAPI | 2.1.0 |
| Build Tool | Gradle | 8.6 (per-service wrapper) |
| Containerization | Docker + Docker Compose | - |
| Container Base Image | eclipse-temurin:21.0.2_13-jre-alpine | - |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service Database (`banking_core_service`)

#### `banking_core_user`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Primary key |
| `first_name` | VARCHAR(255) | | User's first name |
| `last_name` | VARCHAR(255) | | User's last name |
| `email` | VARCHAR(255) | | User's email |
| `identification_number` | VARCHAR(255) | | National Identity Card (NIC) number |

#### `banking_core_account`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Primary key |
| `number` | VARCHAR(255) | | Account number (e.g., `100015003000`) |
| `type` | VARCHAR(255) | ENUM: SAVINGS_ACCOUNT, FIXED_DEPOSIT, LOAN_ACCOUNT | Account type |
| `status` | VARCHAR(255) | ENUM: PENDING, ACTIVE, DORMANT, BLOCKED | Account status |
| `actual_balance` | DECIMAL(19,2) | | Actual balance |
| `available_balance` | DECIMAL(19,2) | | Available balance |
| `user_id` | BIGINT | FK → `banking_core_user.id` | Owner of the account |

#### `banking_core_transaction`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Primary key |
| `amount` | DECIMAL(19,2) | | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | ENUM: FUND_TRANSFER, UTILITY_PAYMENT | Type of transaction |
| `reference_number` | VARCHAR(50) | NOT NULL | Reference (e.g., target account number) |
| `transaction_id` | VARCHAR(50) | NOT NULL | UUID-based transaction identifier |
| `account_id` | BIGINT | FK → `banking_core_account.id` | Source account |

#### `banking_core_utility_account`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Primary key |
| `number` | VARCHAR(255) | | Utility provider account number |
| `provider_name` | VARCHAR(255) | | Utility provider name (e.g., VODAFONE, VERIZON) |

### 2.2 Fund Transfer Service Database (`banking_core_fund_transfer_service`)

#### `fund_transfer`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Primary key |
| `transaction_reference` | VARCHAR(255) | | UUID transaction reference from core banking |
| `from_account` | VARCHAR(255) | | Source account number |
| `to_account` | VARCHAR(255) | | Destination account number |
| `amount` | DECIMAL(19,2) | | Transfer amount |
| `status` | VARCHAR(255) | ENUM: PENDING, PROCESSING, SUCCESS, FAILED | Transfer status |
| `created_on` / `updated_on` | DATETIME | | Audit timestamps (via `AuditAware`) |

### 2.3 Utility Payment Service Database (`banking_core_utility_payment_service`)

#### `utility_payment`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Primary key |
| `provider_id` | BIGINT | | Utility provider ID |
| `amount` | DECIMAL(19,2) | | Payment amount |
| `reference_number` | VARCHAR(255) | | Bill reference number |
| `account` | VARCHAR(255) | | Source bank account number |
| `transaction_id` | VARCHAR(255) | | UUID transaction ID from core banking |
| `status` | VARCHAR(255) | ENUM: PENDING, PROCESSING, SUCCESS, FAILED | Payment status |
| `created_on` / `updated_on` | DATETIME | | Audit timestamps (via `AuditAware`) |

### 2.4 User Service Database (`banking_core_user_service`)

#### `user`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Primary key |
| `auth_id` | VARCHAR(255) | | Keycloak user UUID |
| `identification` | VARCHAR(255) | | NIC identification number |
| `status` | VARCHAR(255) | ENUM: PENDING, APPROVED, DISABLED, BLACKLIST | User approval status |
| `created_on` / `updated_on` | DATETIME | | Audit timestamps (via `AuditAware`) |

### 2.5 Entity Relationships Diagram

```
banking_core_user (1) ──── (N) banking_core_account (1) ──── (1) banking_core_transaction
                                                        │
                                                        └──── banking_core_utility_account (referenced by providerId)
```

---

## 3. API Surface Map

### 3.1 API Gateway Routes

| Gateway Path Prefix | Target Service | Description |
|---------------------|----------------|-------------|
| `/user/**` | internet-banking-user-service | User management endpoints |
| `/fund-transfer/**` | internet-banking-fund-transfer-service | Fund transfer endpoints |
| `/payment/**` | internet-banking-utility-payment-service | Utility payment endpoints |
| `/core/**` | core-banking-service | Core banking endpoints |

### 3.2 Core Banking Service (port 8092)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | — | `BankAccount` | Look up bank account by number |
| `GET` | `/api/v1/account/util-account/{account_name}` | — | `UtilityAccount` | Look up utility account by provider name |
| `GET` | `/api/v1/user/{identification}` | — | `User` | Read user by NIC identification |
| `GET` | `/api/v1/user` | — (Pageable params) | `List<User>` | List users (paginated) |
| `POST` | `/api/v1/transaction/fund-transfer` | `FundTransferRequest` | `FundTransferResponse` | Process fund transfer at ledger level |
| `POST` | `/api/v1/transaction/util-payment` | `UtilityPaymentRequest` | `UtilityPaymentResponse` | Process utility payment at ledger level |

### 3.3 Internet Banking User Service (port 8083)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `POST` | `/api/v1/bank-users/register` | `User` (email, identification, password) | `User` | Register new internet banking user (creates in Keycloak + local DB) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `UserUpdateRequest` (status) | `User` | Update user status (e.g., approve); enables Keycloak account on APPROVED |
| `GET` | `/api/v1/bank-users` | — (Pageable params) | `List<User>` | List all registered internet banking users |
| `GET` | `/api/v1/bank-users/{id}` | — | `User` | Read single user by ID |

### 3.4 Internet Banking Fund Transfer Service (port 8084)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `POST` | `/api/v1/transfer` | `FundTransferRequest` (fromAccount, toAccount, amount) | `FundTransferResponse` | Initiate fund transfer; persists locally then delegates to core-banking |
| `GET` | `/api/v1/transfer` | — (Pageable params) | `List<FundTransfer>` | List all fund transfers (paginated) |

### 3.5 Internet Banking Utility Payment Service (port 8085)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `POST` | `/api/v1/utility-payment` | `UtilityPaymentRequest` (providerId, amount, referenceNumber, account) | `UtilityPaymentResponse` | Process utility payment; persists locally then delegates to core-banking |
| `GET` | `/api/v1/utility-payment` | — (Pageable params) | `List<UtilityPayment>` | List all utility payments (paginated) |

### 3.6 Request/Response DTOs

#### FundTransferRequest
```json
{ "fromAccount": "100015003000", "toAccount": "100015003001", "amount": 500.00 }
```

#### FundTransferResponse
```json
{ "message": "Fund Transfer Successfully Completed", "transactionId": "uuid-string" }
```

#### UtilityPaymentRequest
```json
{ "providerId": 1, "amount": 100.00, "referenceNumber": "REF123", "account": "100015003000" }
```

#### UtilityPaymentResponse
```json
{ "message": "Utility Payment Successfully Processed", "transactionId": "uuid-string" }
```

#### User (User Service)
```json
{ "id": 1, "email": "user@email.com", "identification": "808829932V", "password": "***", "authId": "keycloak-uuid", "status": "PENDING" }
```

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

1. Check Keycloak for existing email → throw `UserAlreadyRegisteredException` if found
2. Validate user against core-banking-service by NIC identification
3. Verify email matches core banking record → throw `InvalidEmailException` if mismatch
4. Create user in Keycloak (disabled, email unverified, with password)
5. Retrieve Keycloak-assigned `authId`
6. Save user locally with `PENDING` status
7. Admin approves user via `PATCH /update/{id}` → enables Keycloak account and sets `emailVerified=true`

### 4.2 Fund Transfer Flow

1. Client sends `POST /api/v1/transfer` to Fund Transfer Service
2. Service persists a `FundTransferEntity` with `PENDING` status
3. Delegates to `core-banking-service` via Feign: `POST /api/v1/transaction/fund-transfer`
4. Core banking validates sender balance, debits sender, credits receiver, records two `TransactionEntity` records
5. Fund transfer service updates local record to `SUCCESS` with transaction reference
6. Returns response to client

### 4.3 Utility Payment Flow

1. Client sends `POST /api/v1/utility-payment` to Utility Payment Service
2. Service persists a `UtilityPaymentEntity` with `PROCESSING` status
3. Delegates to `core-banking-service` via Feign: `POST /api/v1/transaction/util-payment`
4. Core banking validates sender balance, debits sender, records `TransactionEntity`
5. Utility payment service updates local record to `SUCCESS` with transaction ID
6. Returns response to client

### 4.4 Balance Validation

- Before any fund transfer or utility payment, the core banking service validates:
  - `actualBalance >= 0`
  - `actualBalance >= requestedAmount`
- Throws `InsufficientFundsException` if validation fails

### 4.5 Authentication & Authorization

- API Gateway acts as OAuth2 Resource Server validating JWT tokens from Keycloak
- The `/user/api/v1/bank-users/register` endpoint is public (permitAll)
- All actuator endpoints are public
- All other routes require a valid JWT
- Gateway injects `X-Auth-Id` header (JWT subject) into downstream requests
- Downstream services read `X-Auth-Id` via `AppAuthUserFilter` (servlet filter)

---

## 5. Integration Points

### 5.1 Keycloak (Identity Provider)

- **URL**: `http://keycloak_web:8080` (Docker) / `http://localhost:8080` (local)
- **Realm**: Configured via `app.config.keycloak.realm` property
- **Admin Client**: Uses `client_credentials` grant type
- **Integration**: Keycloak Admin Client SDK (`keycloak-admin-client:24.0.4`)
- **Operations**: Create user, read user by email/ID, update user (enable/verify)
- **JWT Validation**: Gateway validates JWTs using Keycloak's JWK Set URI

### 5.2 MySQL (Application Database)

- **Version**: MySQL 8.4.0
- **Host**: `172.25.0.9` (Docker network)
- **4 Schemas**:
  - `banking_core_service` — Core banking data (users, accounts, transactions, utility accounts)
  - `banking_core_fund_transfer_service` — Fund transfer records
  - `banking_core_user_service` — Internet banking user records
  - `banking_core_utility_payment_service` — Utility payment records
- **Credentials**: `javatodev_development` / `oPItyPticIAt` (created via `privileges.sql`)
- **Schema Migrations**: Flyway (core-banking-service only; other services use JPA auto-DDL)

### 5.3 PostgreSQL (Keycloak Database)

- **Version**: PostgreSQL 15
- **Host**: `172.25.0.10`
- **Database**: `keycloak`
- **Credentials**: `keycloak` / `password`

### 5.4 Zipkin (Distributed Tracing)

- **URL**: `http://172.25.0.12:9411`
- **Integration**: Micrometer Tracing Bridge (Brave) + Zipkin Reporter
- **Coverage**: All 4 business services and the API gateway include tracing dependencies

### 5.5 Spring Cloud Config Server

- **URL**: `http://internet-banking-config-server:8090` (Docker)
- **Git Source**: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch**: `main`
- **Search Path**: `configuration/`
- **Profile**: `docker` (activated via `-Dspring.profiles.active=docker`)

### 5.6 Netflix Eureka (Service Discovery)

- **URL**: `http://internet-banking-service-registry:8081/eureka` (Docker)
- **All business services and the gateway** register as Eureka clients
- **Feign clients** use Eureka service names (e.g., `core-banking-service`) for resolution

---

## 6. Build & Deployment Summary

### 6.1 Build System

- Each service has its own **Gradle 8.6** wrapper (`gradlew`)
- **No multi-module root build** — services are built independently
- Build command per service: `./gradlew clean build`
- Common plugins: `spring-boot`, `spring-dependency-management`, `gradle-git-properties`

### 6.2 Docker

- Each service has a `Dockerfile` based on `eclipse-temurin:21.0.2_13-jre-alpine`
- Includes `wait-for-it.sh` for startup ordering (50s timeout per dependency)
- Services are published to Docker Hub under `javatodev/` namespace

### 6.3 Docker Compose

Two compose files in `docker-compose/`:

- **`docker-compose-support-apps.yml`**: Infrastructure only (MySQL, Keycloak, PostgreSQL, Zipkin, Config Server, Service Registry)
- **`docker-compose.yml`**: Full stack (all 6 services + infrastructure)

#### Network Configuration

- Custom bridge network: `javatodev_ib_network` (`172.25.0.0/16`)
- Static IP assignments for all containers
- Named volumes: `postgres_data`, `mysqldata`

### 6.4 Startup Order

Services use `wait-for-it.sh` to enforce startup dependencies:

1. MySQL + PostgreSQL + Keycloak + Zipkin (no dependencies)
2. Config Server (no dependencies)
3. Service Registry (no dependencies)
4. API Gateway (waits for Service Registry + Config Server)
5. Business Services (wait for Service Registry + Config Server + MySQL)

### 6.5 Test Data

- **4 core banking users** with NIC identifiers (pre-seeded via Flyway migration)
- **14 savings accounts** spread across users (balances ranging from 12,000 to 889,000.33)
- **6 utility provider accounts** (VODAFONE, VERIZON, SINGTEL, HUTCH, AIRTEL, GIO)
- **Keycloak realm** imported from `docker-compose/keycloak/realm-export.json`
- **Test credentials**: `ib_admin@javatodev.com` / `5V7huE3G86uB`

### 6.6 CI/CD

- **No CI/CD pipeline** is configured in the repository
- `.github/` contains only `FUNDING.yml`

### 6.7 Postman Collection

- Collection: `JAVA_TO_DEV_MICROSERVICES.postman_collection.json`
- Environment: `BANKING_CORE_MICROSERVICES_PROJECT.postman_environment.json`
- Intended for use with `LOCAL_DOCKER_SETUP` environment
