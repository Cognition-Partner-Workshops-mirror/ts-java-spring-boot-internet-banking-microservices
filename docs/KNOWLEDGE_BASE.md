# Application Knowledge Base

## 1. Architecture Overview

### System Summary

This is a Java 21 / Spring Boot 3.2.4 internet banking application built on a microservices architecture using Spring Cloud 2023.0.0. The system consists of 6 microservices that collaborate to provide banking operations including user management, fund transfers, and utility payments.

### Services

| Service | Port | Description |
|---------|------|-------------|
| **core-banking-service** | 8092 | Core banking engine — manages accounts, users, and processes financial transactions (fund transfers, utility payments). Acts as the system of record. |
| **internet-banking-user-service** | 8083 | Handles internet banking user registration, approval workflows, and user profile management. Integrates with Keycloak for identity management. |
| **internet-banking-fund-transfer-service** | 8084 | Orchestrates fund transfer requests — persists transfer records locally and delegates actual transaction processing to core-banking-service. |
| **internet-banking-utility-payment-service** | 8085 | Orchestrates utility payment requests — persists payment records locally and delegates actual processing to core-banking-service. |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway — single entry point for all client requests. Handles OAuth2/JWT authentication via Keycloak and routes requests to downstream services. |
| **internet-banking-service-registry** | 8081 | Netflix Eureka server for service discovery. All services register here and discover each other by service name. |
| **internet-banking-config-server** | 8090 | Spring Cloud Config Server — provides externalized configuration from a Git repository to all services. |

### Communication Patterns

| Pattern | Technology | Usage |
|---------|-----------|-------|
| Synchronous REST | Spring Cloud OpenFeign | Service-to-service calls (fund-transfer → core-banking, user-service → core-banking, utility-payment → core-banking) |
| Service Discovery | Netflix Eureka | All services register with and discover peers via Eureka |
| API Gateway | Spring Cloud Gateway | Client-facing single entry point with JWT validation |
| Centralized Config | Spring Cloud Config | Git-backed configuration distribution to all services |
| Distributed Tracing | Micrometer Tracing + Zipkin | Request tracing across service boundaries |
| Message Queue | RabbitMQ | Mentioned in architecture (notification service) but not yet implemented |

### Infrastructure Components

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Database | MySQL 8.4.0 | Persistent storage (4 separate databases) |
| Identity Provider | Keycloak 23.0.7 | OAuth2/OIDC authentication and user management |
| Keycloak DB | PostgreSQL 15 | Keycloak's backing store |
| Tracing | Zipkin 3 | Distributed tracing visualization |
| Container Orchestration | Docker Compose | Local development and deployment |
| Schema Migration | Flyway 10.12.0 | Database schema versioning (core-banking-service only) |

### Network Architecture (Docker Compose)

All containers run on a custom bridge network (`javatodev_ib_network`, subnet `172.25.0.0/16`) with fixed IP assignments. Services use `wait-for-it.sh` scripts to handle startup ordering (wait for service-registry and config-server before starting).

---

## 2. Data Model Documentation

### Core Banking Service

#### `banking_core_user`
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| first_name | VARCHAR(255) | User's first name |
| last_name | VARCHAR(255) | User's last name |
| email | VARCHAR(255) | User's email address |
| identification_number | VARCHAR(255) | National ID / identification number |

#### `banking_core_account`
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| number | VARCHAR(255) | Account number (e.g., "100015003000") |
| type | VARCHAR(255) | Account type enum: `SAVINGS_ACCOUNT`, etc. |
| status | VARCHAR(255) | Account status enum: `ACTIVE`, `INACTIVE` |
| actual_balance | DECIMAL(19,2) | Current actual balance |
| available_balance | DECIMAL(19,2) | Available balance |
| user_id | BIGINT (FK → banking_core_user.id) | Account owner |

#### `banking_core_utility_account`
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| number | VARCHAR(255) | Utility provider account number |
| provider_name | VARCHAR(255) | Provider name (e.g., VODAFONE, VERIZON) |

#### `banking_core_transaction`
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| amount | DECIMAL(19,2) | Transaction amount (negative for debits) |
| transaction_type | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| reference_number | VARCHAR(50) | Reference (destination account number or utility ref) |
| transaction_id | VARCHAR(50) | UUID-based transaction identifier |
| account_id | BIGINT (FK → banking_core_account.id) | Associated account |

### Internet Banking User Service

#### `user`
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| auth_id | VARCHAR | Keycloak user ID reference |
| identification | VARCHAR | Links to core banking user's identification_number |
| status | VARCHAR (Enum) | `PENDING`, `APPROVED` |
| created_date | TIMESTAMP | Audit field (inherited from AuditAware) |
| last_modified_date | TIMESTAMP | Audit field (inherited from AuditAware) |

### Internet Banking Fund Transfer Service

#### `fund_transfer`
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| transaction_reference | VARCHAR | UUID from core-banking-service |
| from_account | VARCHAR | Source account number |
| to_account | VARCHAR | Destination account number |
| amount | DECIMAL | Transfer amount |
| status | VARCHAR (Enum) | `PENDING`, `SUCCESS`, `PROCESSING` |
| created_date | TIMESTAMP | Audit field (inherited from AuditAware) |
| last_modified_date | TIMESTAMP | Audit field (inherited from AuditAware) |

### Internet Banking Utility Payment Service

#### `utility_payment`
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| provider_id | BIGINT | Utility provider ID |
| amount | DECIMAL | Payment amount |
| reference_number | VARCHAR | Customer reference number |
| account | VARCHAR | Paying account number |
| transaction_id | VARCHAR | UUID from core-banking-service |
| status | VARCHAR (Enum) | `PROCESSING`, `SUCCESS` |
| created_date | TIMESTAMP | Audit field (inherited from AuditAware) |
| last_modified_date | TIMESTAMP | Audit field (inherited from AuditAware) |

### Entity Relationships Diagram (Textual)

```
core-banking-service:
  banking_core_user (1) ──< banking_core_account (many)
  banking_core_account (1) ──< banking_core_transaction (many)
  banking_core_utility_account (standalone)

internet-banking-user-service:
  user → references core banking user via identification_number
  user.auth_id → references Keycloak user ID

internet-banking-fund-transfer-service:
  fund_transfer → references core banking accounts via from_account/to_account

internet-banking-utility-payment-service:
  utility_payment → references core banking account via account field
  utility_payment → references utility provider via provider_id
```

---

## 3. API Surface Map

### Core Banking Service (Port 8092)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| GET | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | — | `BankAccount { number, type, status, availableBalance, actualBalance }` |
| GET | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | — | `UtilityAccount { id, number, providerName }` |
| POST | `/api/v1/transaction/fund-transfer` | Process fund transfer | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| POST | `/api/v1/transaction/util-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| GET | `/api/v1/user/{identification}` | Get user by identification number | — | `User { id, firstName, lastName, email, identificationNumber, accounts[] }` |
| GET | `/api/v1/user` | List users (paginated) | Pageable params | `List<User>` |

### Internet Banking User Service (Port 8083)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| POST | `/api/v1/bank-users/register` | Register new internet banking user | `{ email, password, identification }` | `User { id, authId, identification, status }` |
| PATCH | `/api/v1/bank-users/update/{id}` | Update user (approve/reject) | `{ status }` | `User` |
| GET | `/api/v1/bank-users` | List users (paginated) | Pageable params | `List<User>` |
| GET | `/api/v1/bank-users/{id}` | Get user by ID | — | `User` |

### Internet Banking Fund Transfer Service (Port 8084)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| POST | `/api/v1/transfer` | Initiate fund transfer | `{ fromAccount, toAccount, amount, authID }` | `{ message, transactionId }` |
| GET | `/api/v1/transfer` | List fund transfers (paginated) | Pageable params | `List<FundTransfer>` |

### Internet Banking Utility Payment Service (Port 8085)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| POST | `/api/v1/utility-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| GET | `/api/v1/utility-payment` | List utility payments (paginated) | Pageable params | `List<UtilityPayment>` |

### API Gateway Routes (Port 8082)

| Route Prefix | Target Service |
|--------------|---------------|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |
| `/banking-core/**` | core-banking-service |

### Actuator Endpoints (All Services)

All services expose Spring Boot Actuator at `/actuator/**` (permitted without authentication at the gateway level).

---

## 4. Key Business Logic Inventory

### Fund Transfer Rules

1. **Balance Validation**: Before any transfer, the source account's `actualBalance` must be >= 0 AND >= transfer amount. Throws `InsufficientFundsException` otherwise.
2. **Two-Phase Processing** (Fund Transfer Service):
   - Phase 1: Persist `FundTransferEntity` with status `PENDING`
   - Phase 2: Delegate to core-banking-service via Feign; on success, update status to `SUCCESS` with transaction reference
3. **Core Banking Processing** (`TransactionService.internalFundTransfer`):
   - Debit source account (subtract amount from `actualBalance` and `availableBalance`)
   - Record debit transaction (amount stored as negative)
   - Credit destination account (add amount to `actualBalance` and `availableBalance`)
   - Record credit transaction (amount stored as positive)
   - Return UUID transaction ID

### Utility Payment Processing

1. **Balance Validation**: Same as fund transfer — source account must have sufficient funds.
2. **Two-Phase Processing** (Utility Payment Service):
   - Phase 1: Persist `UtilityPaymentEntity` with status `PROCESSING`
   - Phase 2: Delegate to core-banking-service via Feign; on success, update status to `SUCCESS`
3. **Core Banking Processing** (`TransactionService.utilPayment`):
   - Validate utility account exists by provider ID
   - Debit source account
   - Record transaction with type `UTILITY_PAYMENT`
   - Return UUID transaction ID

### User Management

1. **Registration Workflow**:
   - Verify email not already registered in Keycloak
   - Verify user exists in core banking system (by identification number)
   - Validate email matches core banking record
   - Create user in Keycloak (disabled, email unverified)
   - Persist local user record with status `PENDING`
2. **Approval Workflow**:
   - Admin updates user status to `APPROVED`
   - Enables user in Keycloak and marks email as verified
   - User can now authenticate and access the system

### Authentication Flow

1. Client authenticates via Keycloak to obtain JWT token
2. Client sends requests to API Gateway with Bearer token
3. Gateway validates JWT using Keycloak's JWK set URI
4. Gateway extracts principal name and forwards as `X-Auth-Id` header
5. Downstream services read `X-Auth-Id` via `AppAuthUserFilter` for audit/context

---

## 5. Integration Points

### Keycloak (Identity Provider)

- **Version**: 23.0.7
- **Connection**: HTTP REST API via `keycloak-admin-client` library (v24.0.4)
- **Configuration Properties**:
  - `app.config.keycloak.server-url` — Keycloak base URL
  - `app.config.keycloak.realm` — Target realm
  - `app.config.keycloak.clientId` — Client ID for admin operations
  - `app.config.keycloak.client-secret` — Client secret
- **Usage**: User creation, user updates (enable/disable), user search by email, user lookup by ID
- **Auth Method**: Client credentials grant (`client_credentials`)

### RabbitMQ (Message Queue)

- **Status**: Referenced in architecture documentation but NOT implemented in code
- **Intended Use**: Notification service consuming messages for email/SMS notifications after fund transfers and utility payments

### Zipkin (Distributed Tracing)

- **Version**: 3
- **Port**: 9411
- **Integration**: Via `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave`
- **Coverage**: All 6 services include tracing dependencies; traces propagate across Feign calls

### Database Connections

| Service | Database | Technology |
|---------|----------|-----------|
| core-banking-service | `banking_core_service` (MySQL) | Spring Data JPA + Flyway |
| internet-banking-user-service | `banking_core_user_service` (MySQL) | Spring Data JPA |
| internet-banking-fund-transfer-service | `banking_core_fund_transfer_service` (MySQL) | Spring Data JPA |
| internet-banking-utility-payment-service | `banking_core_utility_payment_service` (MySQL) | Spring Data JPA |

- **MySQL Version**: 8.4.0
- **Connection User**: `javatodev_development` (all privileges on `*.*`)
- **Test Database**: H2 in-memory (used in test profiles)

### Spring Cloud Config Server

- **Git Repository**: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch**: `main`
- **Search Path**: `configuration`
- **Bootstrap**: All services connect to `http://localhost:8090` at startup for configuration

### Service Discovery (Eureka)

- **Server Port**: 8081
- **Client Registration**: All business services register with Eureka
- **Feign Integration**: Feign clients resolve service names through Eureka (e.g., `@FeignClient(name = "core-banking-service")`)

---

## 6. Build and Deployment Pipeline Summary

### Build System

- **Build Tool**: Gradle (per-service `build.gradle`, no root-level multi-project build)
- **Java Version**: 21
- **Spring Boot**: 3.2.4
- **Spring Cloud**: 2023.0.0
- **Key Plugins**:
  - `org.springframework.boot` (3.2.4)
  - `io.spring.dependency-management` (1.1.4)
  - `com.gorylenko.gradle-git-properties` (2.4.2) — generates git info for actuator

### Docker Compose Deployment

Two compose files:
1. **`docker-compose.yml`** — Full stack (all services + infrastructure)
2. **`docker-compose-support-apps.yml`** — Infrastructure only (Zipkin, Keycloak, MySQL, Config Server, Service Registry)

### Deployment Flow

1. Build each service independently: `./gradlew bootJar`
2. Build Docker images (using `javatodev/<service-name>` naming convention)
3. Start infrastructure via `docker-compose-support-apps.yml`
4. Start application services via `docker-compose.yml`
5. Services start with `wait-for-it.sh` ensuring dependencies are ready

### CI/CD

- **GitHub Actions**: No workflows configured (previously removed)
- **No automated build pipeline** exists in the repository

### Configuration Management

- Configurations are externalized in a separate Git repository
- Each service has profile-specific bootstrap files:
  - `bootstrap.yml` — default (localhost config server)
  - `bootstrap-dev.yml` — development profile
  - `bootstrap-docker.yml` — Docker profile (uses container names)
- Docker deployments use `-Dspring.profiles.active=docker` to activate Docker-specific configuration
