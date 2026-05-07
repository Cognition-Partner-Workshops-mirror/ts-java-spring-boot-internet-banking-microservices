# Application Knowledge Base

## 1. Architecture Overview

### System Architecture

This is a **Java 21 / Spring Boot 3.2.4** microservices-based internet banking platform built on **Spring Cloud 2023.0.0**. The system follows a standard microservices architecture with service discovery, centralized configuration, API gateway, and distributed tracing.

```
┌─────────────────────────────────────────────────────────────────────┐
│                         External Clients                             │
└─────────────────────────────┬───────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│              internet-banking-api-gateway (:8082)                     │
│              (Spring Cloud Gateway + OAuth2/Keycloak)                 │
└───┬──────────────┬──────────────┬──────────────┬────────────────────┘
    │              │              │              │
    ▼              ▼              ▼              ▼
┌────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────┐
│  User  │  │  Fund    │  │ Utility  │  │    Core      │
│Service │  │ Transfer │  │ Payment  │  │   Banking    │
│ (:8083)│  │ (:8084)  │  │ (:8085)  │  │   (:8092)   │
└───┬────┘  └────┬─────┘  └────┬─────┘  └──────────────┘
    │             │              │               ▲
    │             └──────────────┴───────────────┘
    │                    (OpenFeign calls)
    ▼
┌──────────┐
│ Keycloak │
│ (:8080)  │
└──────────┘
```

### Services (6 Total)

| Service | Port | Responsibility |
|---------|------|----------------|
| **internet-banking-api-gateway** | 8082 | Single entry point; routing, OAuth2 JWT validation, header injection |
| **internet-banking-user-service** | 8083 | User registration, profile management, Keycloak integration |
| **internet-banking-fund-transfer-service** | 8084 | Account-to-account fund transfers |
| **internet-banking-utility-payment-service** | 8085 | Third-party utility bill payments |
| **core-banking-service** | 8092 | System of record for accounts, users, transactions, and ledger operations |
| **internet-banking-service-registry** | 8081 | Netflix Eureka service discovery server |
| **internet-banking-config-server** | 8090 | Spring Cloud Config Server (Git-backed) |

### Communication Patterns

| Pattern | Technology | Usage |
|---------|-----------|-------|
| Synchronous REST | Spring Cloud OpenFeign | Service-to-service calls (fund-transfer → core-banking, utility-payment → core-banking, user-service → core-banking) |
| Service Discovery | Netflix Eureka | Dynamic service location; all services register with Eureka |
| Centralized Config | Spring Cloud Config Server | Git-backed configuration repository (`internet-banking-microservices-configurations`) |
| API Gateway | Spring Cloud Gateway | Route management, JWT validation, `X-Auth-Id` header injection |
| Distributed Tracing | Micrometer Tracing + Zipkin | Request tracking across service boundaries via Brave bridge |
| Message Queue | RabbitMQ | Mentioned in architecture but **not yet implemented** (notification service pending) |

### Infrastructure Components

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Identity Provider | Keycloak 23.0.7 | OAuth2/OIDC authentication, user realm management |
| Relational Database | MySQL (via Docker) | Persistent storage for all business services |
| Keycloak Database | PostgreSQL 15 | Keycloak identity store |
| Distributed Tracing | Zipkin 3 | Trace collection and visualization |
| Service Discovery | Netflix Eureka | Service registry |
| Configuration | Spring Cloud Config (Git) | Externalized configuration |
| Containerization | Docker + Docker Compose | Deployment orchestration |
| Build Tool | Gradle (per-service) | Build and dependency management |
| Schema Migration | Flyway 10.12.0 | Database schema versioning (core-banking-service only) |

---

## 2. Data Model Documentation

### Core Banking Service

#### `banking_core_user`
| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| `first_name` | VARCHAR(255) | User first name |
| `last_name` | VARCHAR(255) | User last name |
| `email` | VARCHAR(255) | User email address |
| `identification_number` | VARCHAR(255) | National ID / NIC number |

#### `banking_core_account`
| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| `number` | VARCHAR(255) | Account number (e.g., "100015003000") |
| `type` | VARCHAR(255) | Account type enum: `SAVINGS_ACCOUNT` |
| `status` | VARCHAR(255) | Account status enum: `ACTIVE`, `CLOSED` |
| `actual_balance` | DECIMAL(19,2) | Actual ledger balance |
| `available_balance` | DECIMAL(19,2) | Available balance for transactions |
| `user_id` | BIGINT (FK → banking_core_user) | Account owner |

#### `banking_core_transaction`
| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| `amount` | DECIMAL(19,2) | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | `FUND_TRANSFER` or `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Reference (destination account or utility ref) |
| `transaction_id` | VARCHAR(50) | UUID transaction identifier |
| `account_id` | BIGINT (FK → banking_core_account) | Associated account |

#### `banking_core_utility_account`
| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| `number` | VARCHAR(255) | Utility account number |
| `provider_name` | VARCHAR(255) | Provider name (e.g., VODAFONE, VERIZON) |

### Internet Banking User Service

#### `user`
| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| `auth_id` | VARCHAR | Keycloak user UUID |
| `identification` | VARCHAR | National ID linking to core banking user |
| `status` | VARCHAR (ENUM) | `PENDING`, `APPROVED` |
| `created_date` | TIMESTAMP | Audit: creation timestamp |
| `created_by` | VARCHAR | Audit: creator |
| `modified_date` | TIMESTAMP | Audit: last modification |
| `modified_by` | VARCHAR | Audit: modifier |
| `version` | BIGINT | Optimistic locking version |

### Internet Banking Fund Transfer Service

#### `fund_transfer`
| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `transaction_reference` | VARCHAR | Core banking transaction ID |
| `status` | VARCHAR (ENUM) | `PENDING`, `SUCCESS`, `FAILED` |
| `created_date` / `modified_date` | TIMESTAMP | Audit timestamps |
| `version` | BIGINT | Optimistic locking |

### Internet Banking Utility Payment Service

#### `utility_payment`
| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Customer reference number |
| `account` | VARCHAR | Source bank account number |
| `transaction_id` | VARCHAR | Core banking transaction ID |
| `status` | VARCHAR (ENUM) | `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `modified_date` | TIMESTAMP | Audit timestamps |
| `version` | BIGINT | Optimistic locking |

### Entity Relationships

```
banking_core_user (1) ──── (N) banking_core_account
banking_core_account (1) ──── (N) banking_core_transaction
banking_core_utility_account (standalone - referenced by provider_id)

user_service.user ─── (links via identification) ──→ core.banking_core_user
user_service.user ─── (links via auth_id) ──→ Keycloak user
fund_transfer ─── (references) ──→ core.banking_core_account (via account numbers)
utility_payment ─── (references) ──→ core.banking_core_utility_account (via provider_id)
```

---

## 3. API Surface Map

### Core Banking Service (`:8092`)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| GET | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | Path: account_number | `BankAccount{number, type, status, availableBalance, actualBalance}` |
| GET | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | Path: account_name | `UtilityAccount{id, number, providerName}` |
| POST | `/api/v1/transaction/fund-transfer` | Process fund transfer | `{fromAccount, toAccount, amount}` | `{message, transactionId}` |
| POST | `/api/v1/transaction/util-payment` | Process utility payment | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` |
| GET | `/api/v1/user/{identification}` | Get user by identification number | Path: identification | `User{id, firstName, lastName, email, identificationNumber, accounts[]}` |
| GET | `/api/v1/user` | Get paginated user list | Query: page, size, sort | `Page<User>` |

### Internet Banking User Service (`:8083`)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| POST | `/api/v1/bank-users/register` | Register new user | `{email, identification, password}` | `User{id, email, identification, authId, status}` |
| PATCH | `/api/v1/bank-users/update/{id}` | Update user (approve/reject) | `{status}` | `User{id, email, identification, authId, status}` |
| GET | `/api/v1/bank-users` | List all users (paginated) | Query: page, size, sort | `List<User>` |
| GET | `/api/v1/bank-users/{id}` | Get user by ID | Path: id | `User{id, email, identification, authId, status}` |

### Internet Banking Fund Transfer Service (`:8084`)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| POST | `/api/v1/transfer` | Initiate fund transfer | `{fromAccount, toAccount, amount, authID}` | `{message, transactionId}` |
| GET | `/api/v1/transfer` | List fund transfers (paginated) | Query: page, size, sort | `List<FundTransfer>` |

### Internet Banking Utility Payment Service (`:8085`)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| POST | `/api/v1/utility-payment` | Process utility payment | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` |
| GET | `/api/v1/utility-payment` | List utility payments (paginated) | Query: page, size, sort | `List<UtilityPayment>` |

### API Gateway Routes (`:8082`)

| Route Prefix | Target Service |
|--------------|----------------|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |
| `/banking-core/**` | core-banking-service |

### Infrastructure Endpoints

| Service | Endpoint | Purpose |
|---------|----------|---------|
| All services | `/actuator/health` | Health check |
| All services | `/actuator/info` | Application info (git properties) |
| Service Registry | `:8081/` | Eureka dashboard |
| Config Server | `:8090/{app}/{profile}` | Configuration retrieval |
| Zipkin | `:9411/` | Trace UI |
| Keycloak | `:8080/` | Admin console |

---

## 4. Key Business Logic Inventory

### Fund Transfer Rules (Core Banking Service)

1. **Balance Validation**: Before any transfer, the source account's `actualBalance` must be >= 0 AND >= transfer amount
2. **Insufficient Funds**: Throws `InsufficientFundsException` with `INSUFFICIENT_FUNDS` error code if validation fails
3. **Double-Entry Bookkeeping**: Each transfer creates TWO transaction records:
   - Debit record on source account (negative amount)
   - Credit record on destination account (positive amount)
4. **Balance Update**: Both `actualBalance` and `availableBalance` are updated on both accounts
5. **Transaction ID**: UUID generated for each transfer, shared across both debit and credit entries
6. **Atomicity**: Entire operation runs within `@Transactional` boundary

### Fund Transfer Flow (End-to-End)

1. Client → API Gateway (JWT validated)
2. API Gateway injects `X-Auth-Id` header → Fund Transfer Service
3. Fund Transfer Service saves initial record with `PENDING` status
4. Fund Transfer Service calls Core Banking via OpenFeign (`/api/v1/transaction/fund-transfer`)
5. Core Banking validates balances, executes transfer, returns transaction ID
6. Fund Transfer Service updates record to `SUCCESS` with transaction reference
7. Returns success response to client

### Utility Payment Processing

1. Client → API Gateway → Utility Payment Service
2. Payment saved with `PROCESSING` status
3. Utility Payment Service calls Core Banking via OpenFeign (`/api/v1/transaction/util-payment`)
4. Core Banking validates source account balance
5. Core Banking deducts from source account
6. Core Banking creates transaction record
7. Utility Payment Service updates to `SUCCESS` with transaction ID
8. Returns success response

### User Management / Registration Flow

1. **Registration Request**: Client posts email, identification (NIC), password
2. **Keycloak Check**: Verify email not already registered in Keycloak
3. **Core Banking Verification**: Fetch user from core banking by identification number
4. **Email Validation**: Ensure submitted email matches core banking record
5. **Keycloak User Creation**: Create user in Keycloak (disabled, email unverified)
6. **Local Record**: Save user entity with `PENDING` status and Keycloak `authId`
7. **Approval Flow**: Admin patches user status to `APPROVED`
8. **Keycloak Activation**: On approval, user is enabled and email verified in Keycloak

### Security Model

- **Gateway-level**: OAuth2 Resource Server with JWT validation (Keycloak JWK Set URI)
- **Public endpoints**: User registration (`/user/api/v1/bank-users/register`) and actuator endpoints
- **All other endpoints**: Require valid JWT token
- **Identity Propagation**: Gateway extracts principal name → `X-Auth-Id` header → downstream services
- **CSRF**: Disabled (stateless API)

---

## 5. Integration Points

### Keycloak (Identity & Access Management)

| Aspect | Detail |
|--------|--------|
| Version | 23.0.7 |
| Protocol | Admin REST API (client_credentials grant) |
| Client Library | `keycloak-admin-client:24.0.4` |
| Configuration | `app.config.keycloak.server-url`, `realm`, `clientId`, `client-secret` |
| Operations | Create user, read user by email/ID, update user (enable/verify) |
| Realm | Imported via `realm-export.json` on startup |

### RabbitMQ (Message Queue)

| Aspect | Detail |
|--------|--------|
| Status | **Mentioned in architecture but NOT implemented** |
| Intended Use | Push notifications for fund transfers and utility payments |
| Consumer | Notification service (pending development) |

### Zipkin (Distributed Tracing)

| Aspect | Detail |
|--------|--------|
| Version | 3 |
| Port | 9411 |
| Integration | Micrometer Tracing Bridge (Brave) via `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` |
| Coverage | All 4 business services + API Gateway |
| Feign Tracing | `feign-micrometer` for inter-service call tracing |

### Database Connections

| Service | Database | Schema |
|---------|----------|--------|
| core-banking-service | MySQL | `banking_core_service` |
| internet-banking-user-service | MySQL | `banking_core_user_service` |
| internet-banking-fund-transfer-service | MySQL | `banking_core_fund_transfer_service` |
| internet-banking-utility-payment-service | MySQL | `banking_core_utility_payment_service` |
| Keycloak | PostgreSQL | `keycloak` |

### Spring Cloud Config Server

| Aspect | Detail |
|--------|--------|
| Git Repository | `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` |
| Search Path | `configuration` |
| Branch | `main` |
| Bootstrap | Each service has `bootstrap.yml` (localhost) and `bootstrap-docker.yml` (container DNS) |

---

## 6. Build and Deployment Pipeline Summary

### Build System

- **Tool**: Gradle (per-service, no multi-project build)
- **Java**: 21 (Eclipse Temurin 21.0.2)
- **Spring Boot**: 3.2.4 via `org.springframework.boot` plugin
- **Dependency Management**: `io.spring.dependency-management` 1.1.4
- **Git Properties**: `com.gorylenko.gradle-git-properties` 2.4.2 (build metadata)

### Docker

- **Base Image**: `eclipse-temurin:21.0.2_13-jre-alpine`
- **Build Artifact**: Fat JAR (`*-0.0.1-SNAPSHOT.jar`)
- **Startup**: `wait-for-it.sh` scripts ensure infrastructure readiness before app startup
- **Profile**: `-Dspring.profiles.active=docker` activates Docker-specific config (bootstrap-docker.yml)

### Docker Compose Orchestration

- **Network**: Custom bridge network `javatodev_ib_network` (subnet `172.25.0.0/16`)
- **Fixed IPs**: Each container has a static IP on the bridge network
- **Startup Order**: Managed via `wait-for-it.sh` with 50-second timeouts:
  1. Eureka Registry (`:8081`)
  2. Config Server (`:8090`)
  3. MySQL (`:3306`)
  4. Application services

### CI/CD

- **GitHub Actions**: Previously present but removed (PAT scope compatibility)
- **No active CI pipeline** in the repository

### Deployment Files

| File | Purpose |
|------|---------|
| `docker-compose/docker-compose.yml` | Full stack deployment (infra + all services) |
| `docker-compose/docker-compose-support-apps.yml` | Infrastructure-only deployment (for local dev) |
| `docker-compose/mysql/privileges.sql` | MySQL user and database initialization |
| `docker-compose/keycloak/realm-export.json` | Keycloak realm pre-configuration |
| `*/Dockerfile` | Per-service container build |
| `*/wait-for-it.sh` | Startup dependency wait script |
