# Application Knowledge Base

## 1. Architecture Overview

### System Diagram

```
┌─────────────┐       ┌──────────────────────────────┐
│   Client    │──────▶│   API Gateway (:8082)        │
└─────────────┘       │   OAuth2 Resource Server     │
                      └──────────────┬───────────────┘
                                     │ Routes via Eureka
                  ┌──────────────────┼──────────────────────┐
                  │                  │                       │
     ┌────────────▼───┐  ┌──────────▼─────────┐  ┌────────▼──────────────┐
     │ User Service   │  │ Fund Transfer Svc  │  │ Utility Payment Svc  │
     │ (:8083)        │  │ (:8084)            │  │ (:8085)              │
     └───────┬────────┘  └──────────┬─────────┘  └────────┬─────────────┘
             │                      │                       │
             │       ┌──────────────▼───────────────┐      │
             └──────▶│   Core Banking Service       │◀─────┘
                     │   (:8092)                    │
                     └──────────────┬───────────────┘
                                    │
                          ┌─────────▼─────────┐
                          │   MySQL Database   │
                          │   (:3306)          │
                          └───────────────────┘
```

### Services

| Service | Port | Purpose |
|---------|------|---------|
| `internet-banking-service-registry` | 8081 | Netflix Eureka discovery server |
| `internet-banking-config-server` | 8090 | Spring Cloud Config (Git-backed) |
| `internet-banking-api-gateway` | 8082 | Spring Cloud Gateway + OAuth2 security |
| `internet-banking-user-service` | 8083 | User registration, Keycloak integration |
| `internet-banking-fund-transfer-service` | 8084 | Account-to-account fund transfers |
| `internet-banking-utility-payment-service` | 8085 | Third-party utility bill payments |
| `core-banking-service` | 8092 | System of record: accounts, transactions, ledger |

### Communication Patterns

- **Service Discovery**: All services register with Eureka and discover each other via logical service names.
- **Inter-Service Communication**: OpenFeign declarative HTTP clients (synchronous REST calls).
- **API Gateway Routing**: Spring Cloud Gateway routes external traffic to internal services based on path prefixes.
- **Authentication Propagation**: Gateway extracts JWT principal and forwards `X-Auth-Id` header to downstream services.
- **Configuration**: All services fetch configuration from Config Server at bootstrap (Spring Cloud Config with Git backend).

### Infrastructure Components

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Service Registry | Netflix Eureka | Dynamic service discovery |
| Config Server | Spring Cloud Config | Centralized configuration (Git-backed) |
| API Gateway | Spring Cloud Gateway | Routing, security enforcement |
| Identity Provider | Keycloak 23.0.7 | OAuth2/OIDC, user management, realm import |
| Primary Database | MySQL (custom image) | Core banking data, fund transfers, utility payments |
| Keycloak Database | PostgreSQL 15 | Keycloak persistence |
| Distributed Tracing | Zipkin 3 | Request tracing across service boundaries |
| Container Orchestration | Docker Compose | Local development deployment |

---

## 2. Data Model Documentation

### Core Banking Service

#### `banking_core_user`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Internal user identifier |
| `first_name` | VARCHAR(255) | User's first name |
| `last_name` | VARCHAR(255) | User's last name |
| `email` | VARCHAR(255) | User's email address |
| `identification_number` | VARCHAR(255) | National ID / NIC number |

#### `banking_core_account`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Internal account identifier |
| `number` | VARCHAR(255) | Account number (12-digit string) |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `ACTIVE` |
| `actual_balance` | DECIMAL(19,2) | Ledger balance |
| `available_balance` | DECIMAL(19,2) | Available for withdrawal |
| `user_id` | BIGINT (FK → banking_core_user.id) | Account owner |

#### `banking_core_transaction`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Transaction record ID |
| `amount` | DECIMAL(19,2) | Transaction amount (negative=debit) |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Destination account or reference |
| `transaction_id` | VARCHAR(50) | UUID grouping related entries |
| `account_id` | BIGINT (FK → banking_core_account.id) | Associated account |

#### `banking_core_utility_account`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Utility provider ID |
| `number` | VARCHAR(255) | Provider account number |
| `provider_name` | VARCHAR(255) | Provider name (e.g., VODAFONE) |

### Internet Banking User Service

#### `user`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Internal ID |
| `auth_id` | VARCHAR(255) | Keycloak user UUID |
| `identification` | VARCHAR(255) | National ID linking to core banking |
| `status` | VARCHAR(255) | Enum: `PENDING`, `APPROVED` |
| `created_date` | TIMESTAMP | Audit: creation time |
| `created_by` | VARCHAR(255) | Audit: creator |
| `modified_date` | TIMESTAMP | Audit: last modification |
| `modified_by` | VARCHAR(255) | Audit: last modifier |
| `version` | BIGINT | Optimistic locking version |

### Internet Banking Fund Transfer Service

#### `fund_transfer`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Internal ID |
| `from_account` | VARCHAR(255) | Source account number |
| `to_account` | VARCHAR(255) | Destination account number |
| `amount` | DECIMAL(19,2) | Transfer amount |
| `transaction_reference` | VARCHAR(255) | Core banking transaction UUID |
| `status` | VARCHAR(255) | Enum: `PENDING`, `SUCCESS` |
| `created_date` / `modified_date` | TIMESTAMP | Audit timestamps |
| `created_by` / `modified_by` | VARCHAR(255) | Audit user |
| `version` | BIGINT | Optimistic locking |

### Internet Banking Utility Payment Service

#### `utility_payment`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Internal ID |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL(19,2) | Payment amount |
| `reference_number` | VARCHAR(255) | Bill reference |
| `account` | VARCHAR(255) | Source bank account number |
| `transaction_id` | VARCHAR(255) | Core banking transaction UUID |
| `status` | VARCHAR(255) | Enum: `PROCESSING`, `SUCCESS` |
| `created_date` / `modified_date` | TIMESTAMP | Audit timestamps |
| `created_by` / `modified_by` | VARCHAR(255) | Audit user |
| `version` | BIGINT | Optimistic locking |

### Entity Relationships

```
banking_core_user (1) ──── (N) banking_core_account
banking_core_account (1) ──── (N) banking_core_transaction
banking_core_utility_account (standalone - referenced by ID)
user (internet-banking) ──links via identification──▶ banking_core_user
user (internet-banking) ──links via auth_id──▶ Keycloak User
fund_transfer ──references──▶ banking_core_account (by number)
utility_payment ──references──▶ banking_core_utility_account (by provider_id)
```

---

## 3. API Surface Map

### API Gateway Routes (External)

All external requests go through the gateway at `:8082`. The gateway prefixes are:
- `/user/**` → `internet-banking-user-service`
- `/fund-transfer/**` → `internet-banking-fund-transfer-service`
- `/utility-payment/**` → `internet-banking-utility-payment-service`
- `/banking-core/**` → `core-banking-service`

### Core Banking Service (`/api/v1/`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| GET | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | - | `BankAccount` |
| GET | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | - | `UtilityAccount` |
| POST | `/api/v1/transaction/fund-transfer` | Process fund transfer | `FundTransferRequest` | `FundTransferResponse` |
| POST | `/api/v1/transaction/util-payment` | Process utility payment | `UtilityPaymentRequest` | `UtilityPaymentResponse` |
| GET | `/api/v1/user/{identification}` | Get user by identification number | - | `User` |
| GET | `/api/v1/user` | List users (paginated) | Pageable params | `Page<User>` |

### Internet Banking User Service (`/api/v1/bank-users/`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| POST | `/api/v1/bank-users/register` | Register new user | `User` (email, identification, password) | `User` |
| PATCH | `/api/v1/bank-users/update/{id}` | Update user (approve/reject) | `UserUpdateRequest` (status) | `User` |
| GET | `/api/v1/bank-users` | List all users (paginated) | Pageable params | `List<User>` |
| GET | `/api/v1/bank-users/{id}` | Get user by ID | - | `User` |

### Internet Banking Fund Transfer Service (`/api/v1/transfer/`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| POST | `/api/v1/transfer` | Initiate fund transfer | `FundTransferRequest` (fromAccount, toAccount, amount) | `FundTransferResponse` |
| GET | `/api/v1/transfer` | List all transfers (paginated) | Pageable params | `List<FundTransfer>` |

### Internet Banking Utility Payment Service (`/api/v1/utility-payment/`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| POST | `/api/v1/utility-payment` | Process utility payment | `UtilityPaymentRequest` (providerId, amount, referenceNumber, account) | `UtilityPaymentResponse` |
| GET | `/api/v1/utility-payment` | List all payments (paginated) | Pageable params | `List<UtilityPayment>` |

### Request/Response Shapes

#### `FundTransferRequest`
```json
{ "fromAccount": "100015003000", "toAccount": "100015003001", "amount": 500.00 }
```

#### `FundTransferResponse`
```json
{ "message": "Fund Transfer Successfully Completed", "transactionId": "uuid-string" }
```

#### `UtilityPaymentRequest`
```json
{ "providerId": 1, "amount": 100.00, "referenceNumber": "REF123", "account": "100015003000" }
```

#### `UtilityPaymentResponse`
```json
{ "message": "Utility Payment Successfully Processed", "transactionId": "uuid-string" }
```

#### `User` (Registration Request)
```json
{ "email": "user@example.com", "identification": "808829932V", "password": "secret" }
```

#### `UserUpdateRequest`
```json
{ "status": "APPROVED" }
```

---

## 4. Key Business Logic Inventory

### Fund Transfer Rules

1. **Balance Validation**: Source account must have `actualBalance >= transferAmount` and `actualBalance > 0`.
2. **Double-Entry Recording**: Two transaction records are created per transfer — a debit on the source account (negative amount) and a credit on the destination (positive amount), sharing the same `transactionId`.
3. **Balance Update**: Both `actualBalance` and `availableBalance` are updated on source and destination accounts.
4. **Transaction Lifecycle**: The fund transfer service saves with `PENDING` status, calls core banking, then updates to `SUCCESS` on completion.
5. **No Partial Transfers**: The entire operation runs within a `@Transactional` boundary in core banking.

### Utility Payment Processing

1. **Balance Validation**: Same as fund transfer — source account must have sufficient funds.
2. **Single-Sided Debit**: Only the source account is debited; no corresponding credit to a utility account balance.
3. **Reference Number**: Bills are identified by a provider-specific reference number.
4. **Transaction Lifecycle**: Saved as `PROCESSING`, then updated to `SUCCESS` after core banking confirms.

### User Management

1. **Registration Flow**:
   - Check if email already exists in Keycloak (reject if duplicate).
   - Verify user exists in core banking via identification number.
   - Validate email matches core banking record.
   - Create Keycloak user (disabled, email unverified).
   - Save local user record with `PENDING` status.
2. **Approval Flow**:
   - Admin updates user status to `APPROVED`.
   - Keycloak user is enabled and email marked as verified.
3. **Keycloak Integration**: Uses admin client with `client_credentials` grant to manage users programmatically.

### Authentication & Authorization

1. **Gateway Security**: All endpoints require JWT authentication except `/user/api/v1/bank-users/register` and actuator endpoints.
2. **Auth ID Propagation**: Gateway extracts principal name from JWT and forwards as `X-Auth-Id` header.
3. **Downstream Filtering**: Services use `AppAuthUserFilter` to extract `X-Auth-Id` and store in `ApiRequestContextHolder` (ThreadLocal).
4. **Auditing**: `AuditorAwareConfig` uses the context holder to populate `createdBy`/`modifiedBy` fields.

---

## 5. Integration Points

### Keycloak (Identity & Access Management)

- **Version**: 23.0.7
- **Connection**: Admin client via `client_credentials` grant
- **Configuration**: `app.config.keycloak.server-url`, `realm`, `clientId`, `client-secret`
- **Operations**: Create user, update user (enable/disable), search by email, read by auth ID
- **Realm**: Pre-imported via volume mount from `docker-compose/keycloak/`

### RabbitMQ

- **Status**: Referenced in the architecture description but **not implemented** in the current codebase.
- **No dependencies on RabbitMQ** exist in any `build.gradle` or service code.

### Zipkin (Distributed Tracing)

- **Version**: 3 (Docker image `openzipkin/zipkin:3`)
- **Port**: 9411
- **Integration**: Via `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` dependencies in all services.
- **Coverage**: All 6 microservices include tracing dependencies.

### Database Connections

| Service | Database | Driver |
|---------|----------|--------|
| Core Banking | MySQL | `mysql-connector-j:8.4.0` |
| User Service | MySQL | `mysql-connector-j:8.4.0` |
| Fund Transfer | MySQL | `mysql-connector-j:8.4.0` |
| Utility Payment | MySQL | `mysql-connector-j:8.4.0` |
| Keycloak | PostgreSQL 15 | Internal to Keycloak |

### External Configuration Repository

- **URL**: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch**: `main`
- **Path**: `configuration/`
- **Services fetch**: `{application-name}.yml` and `{application-name}-{profile}.yml`

---

## 6. Build and Deployment Pipeline Summary

### Build System

- **Build Tool**: Gradle (per-service, no multi-project root build)
- **Java Version**: 21 (source compatibility)
- **Spring Boot**: 3.2.4
- **Spring Cloud**: 2023.0.0

### Key Gradle Plugins

| Plugin | Version | Purpose |
|--------|---------|---------|
| `org.springframework.boot` | 3.2.4 | Spring Boot packaging |
| `io.spring.dependency-management` | 1.1.4 | BOM-based dependency management |
| `com.gorylenko.gradle-git-properties` | 2.4.2 | Git info in actuator `/info` endpoint |

### Docker Setup

Each service has a `Dockerfile` and a `wait-for-it.sh` script for startup ordering.

### Docker Compose Files

- **`docker-compose.yml`**: Full stack deployment (all services + infrastructure)
- **`docker-compose-support-apps.yml`**: Infrastructure only (MySQL, Keycloak, Zipkin, Config Server, Service Registry)

### Startup Order (via `wait-for-it.sh`)

1. Config Server + Service Registry (no dependencies)
2. MySQL database
3. Application services wait for: Service Registry → Config Server → MySQL

### Network Configuration

- **Network**: `javatodev_ib_network` (bridge driver, subnet `172.25.0.0/16`)
- **Fixed IPs**: Each container has a static IP address for predictable discovery

### Database Migrations

- **Tool**: Flyway (core-banking-service only)
- **Migrations**: 3 versioned SQL scripts for schema creation and seed data
- **Other services**: Rely on JPA `hibernate.ddl-auto` (configured via Config Server)

### Profiles

- **`default`**: Local development (localhost connections)
- **`dev`**: Development environment (configurable Config Server URL)
- **`docker`**: Docker Compose deployment (container hostnames)
