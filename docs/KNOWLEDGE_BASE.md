# Application Knowledge Base

## 1. Architecture Overview

### System Architecture

This is a **Java Spring Boot microservices** application implementing an Internet Banking system. The architecture follows the Spring Cloud ecosystem patterns with service discovery, centralized configuration, and API gateway.

### Services

| Service | Port | Role |
|---------|------|------|
| **internet-banking-api-gateway** | 8082 | Entry point for all client requests; routes traffic to downstream services, handles OAuth2/JWT authentication |
| **internet-banking-service-registry** | 8081 | Netflix Eureka server for service discovery and registration |
| **internet-banking-config-server** | 8090 | Spring Cloud Config server; serves externalized configuration from a Git repository |
| **internet-banking-user-service** | 8083 | User registration, management, and Keycloak integration |
| **internet-banking-fund-transfer-service** | 8084 | Processes fund transfers between bank accounts |
| **internet-banking-utility-payment-service** | 8085 | Processes utility bill payments |
| **core-banking-service** | 8092 | Core banking engine — manages accounts, users, transactions, and balances |

### Communication Patterns

| Pattern | Technology | Usage |
|---------|-----------|-------|
| Synchronous REST | Spring Cloud OpenFeign | Service-to-service calls (fund-transfer → core-banking, user-service → core-banking, utility-payment → core-banking) |
| Service Discovery | Netflix Eureka | All services register with the registry and discover each other by service name |
| API Gateway | Spring Cloud Gateway | Single entry point with route-based forwarding to downstream services |
| Centralized Config | Spring Cloud Config | All services pull configuration from a Git-backed config server |
| Distributed Tracing | Micrometer Tracing + Zipkin | Trace propagation across service boundaries via Brave bridge |
| Authentication Propagation | Custom Header (`X-Auth-Id`) | API Gateway extracts authenticated principal and forwards as header to downstream services |

### Infrastructure Components

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Database | MySQL 8.x | Persistent storage for all business services (4 schemas) |
| Identity Provider | Keycloak 23.0.7 | OAuth2/OIDC authentication and user management |
| Keycloak Database | PostgreSQL 15 | Backing store for Keycloak |
| Distributed Tracing | Zipkin 3 | Collects and visualizes distributed traces |
| Message Broker | RabbitMQ | Mentioned in architecture (notification service) — not yet implemented |

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
| identification_number | VARCHAR(255) | National identification number (NIC) |

#### `banking_core_account`
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| number | VARCHAR(255) | Account number (e.g., 100015003000) |
| type | VARCHAR(255) ENUM | Account type: `SAVINGS_ACCOUNT` |
| status | VARCHAR(255) ENUM | Account status: `ACTIVE`, `INACTIVE` |
| actual_balance | DECIMAL(19,2) | Ledger balance |
| available_balance | DECIMAL(19,2) | Available balance for transactions |
| user_id | BIGINT (FK → banking_core_user.id) | Account owner |

#### `banking_core_transaction`
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| amount | DECIMAL(19,2) | Transaction amount (negative for debits) |
| transaction_type | VARCHAR(30) | `FUND_TRANSFER` or `UTILITY_PAYMENT` |
| reference_number | VARCHAR(50) | Reference (destination account or payment ref) |
| transaction_id | VARCHAR(50) | UUID-based transaction identifier |
| account_id | BIGINT (FK → banking_core_account.id) | Associated account |

#### `banking_core_utility_account`
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| number | VARCHAR(255) | Utility provider account number |
| provider_name | VARCHAR(255) | Provider name (e.g., VODAFONE, VERIZON) |

### Fund Transfer Service

#### `fund_transfer`
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| from_account | VARCHAR | Source account number |
| to_account | VARCHAR | Destination account number |
| amount | DECIMAL | Transfer amount |
| transaction_reference | VARCHAR | Core banking transaction ID |
| status | VARCHAR ENUM | `PENDING`, `SUCCESS`, `FAILED` |
| created_at | TIMESTAMP | Audit: creation time |
| updated_at | TIMESTAMP | Audit: last update time |
| created_by | VARCHAR | Audit: creator |
| updated_by | VARCHAR | Audit: last updater |

### User Service

#### `user`
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| auth_id | VARCHAR | Keycloak user UUID |
| identification | VARCHAR | National identification number |
| status | VARCHAR ENUM | `PENDING`, `APPROVED` |
| created_at | TIMESTAMP | Audit: creation time |
| updated_at | TIMESTAMP | Audit: last update time |
| created_by | VARCHAR | Audit: creator |
| updated_by | VARCHAR | Audit: last updater |

### Utility Payment Service

#### `utility_payment`
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| provider_id | BIGINT | Utility provider ID |
| amount | DECIMAL | Payment amount |
| reference_number | VARCHAR | Payment reference number |
| account | VARCHAR | Source bank account number |
| transaction_id | VARCHAR | Core banking transaction ID |
| status | VARCHAR ENUM | `PROCESSING`, `SUCCESS`, `FAILED` |
| created_at | TIMESTAMP | Audit: creation time |
| updated_at | TIMESTAMP | Audit: last update time |
| created_by | VARCHAR | Audit: creator |
| updated_by | VARCHAR | Audit: last updater |

### Entity Relationships

```
banking_core_user (1) ──→ (N) banking_core_account
banking_core_account (1) ──→ (N) banking_core_transaction
banking_core_utility_account (standalone)
fund_transfer (standalone, references account numbers as strings)
user (references Keycloak auth_id and core-banking identification)
utility_payment (standalone, references account number and provider_id)
```

### Database Schemas

| Schema | Service |
|--------|---------|
| `banking_core_service` | core-banking-service |
| `banking_core_fund_transfer_service` | internet-banking-fund-transfer-service |
| `banking_core_user_service` | internet-banking-user-service |
| `banking_core_utility_payment_service` | internet-banking-utility-payment-service |

---

## 3. API Surface Map

### API Gateway Routes (Port 8082)

All requests pass through the gateway with prefix stripping:

| Route Prefix | Target Service |
|-------------|----------------|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |
| `/banking-core/**` | core-banking-service |

### Core Banking Service (Port 8092)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| GET | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | — | `BankAccount{number, type, status, availableBalance, actualBalance}` |
| GET | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | — | `UtilityAccount{id, number, providerName}` |
| POST | `/api/v1/transaction/fund-transfer` | Process fund transfer | `{fromAccount, toAccount, amount}` | `{message, transactionId}` |
| POST | `/api/v1/transaction/util-payment` | Process utility payment | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` |
| GET | `/api/v1/user/{identification}` | Get user by identification number | — | `User{id, firstName, lastName, email, identificationNumber, accounts[]}` |
| GET | `/api/v1/user` | Get paginated users | Pageable params | `Page<User>` |

### Internet Banking User Service (Port 8083)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| POST | `/api/v1/bank-users/register` | Register new user | `{email, identification, password}` | `User{id, email, identification, authId, status}` |
| PATCH | `/api/v1/bank-users/update/{id}` | Update user (approve/reject) | `{status}` | `User{id, email, identification, authId, status}` |
| GET | `/api/v1/bank-users` | List users (paginated) | Pageable params | `List<User>` |
| GET | `/api/v1/bank-users/{id}` | Get user by ID | — | `User{id, email, identification, authId, status}` |

### Internet Banking Fund Transfer Service (Port 8084)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| POST | `/api/v1/transfer` | Initiate fund transfer | `{fromAccount, toAccount, amount, authID}` | `{message, transactionId}` |
| GET | `/api/v1/transfer` | List fund transfers (paginated) | Pageable params | `List<FundTransfer>` |

### Internet Banking Utility Payment Service (Port 8085)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| POST | `/api/v1/utility-payment` | Process utility payment | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` |
| GET | `/api/v1/utility-payment` | List utility payments (paginated) | Pageable params | `List<UtilityPayment>` |

### Actuator Endpoints (All Services)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/actuator/health` | Health check |
| GET | `/actuator/info` | Application info (git properties) |

---

## 4. Key Business Logic Inventory

### Fund Transfer Rules

1. **Balance Validation**: Before any transfer, the source account's `actualBalance` must be >= 0 AND >= the transfer amount. Throws `InsufficientFundsException` otherwise.
2. **Account Lookup**: Both source and destination accounts must exist. Throws `EntityNotFoundException` if not found.
3. **Debit/Credit Flow**:
   - Debit source: `actualBalance -= amount`, `availableBalance = actualBalance - amount`
   - Credit destination: `actualBalance += amount`, `availableBalance = actualBalance + amount`
4. **Transaction Recording**: Two `TransactionEntity` records created per transfer (one debit, one credit) sharing the same `transactionId`.
5. **Two-Phase Processing**: The fund-transfer-service saves a `PENDING` record, calls core-banking, then updates to `SUCCESS` with the transaction reference.

### Payment Processing (Utility Payments)

1. **Balance Validation**: Same as fund transfer — source account must have sufficient funds.
2. **Provider Lookup**: Utility provider must exist by ID. Throws `EntityNotFoundException` if not found.
3. **Debit Flow**: Only debit from source account (no credit to utility provider within the system).
4. **Transaction Recording**: Single `TransactionEntity` with type `UTILITY_PAYMENT`.
5. **Two-Phase Processing**: Utility-payment-service saves as `PROCESSING`, calls core-banking, then updates to `SUCCESS`.

### User Management

1. **Registration Flow**:
   - Check Keycloak for existing email → reject if found (`UserAlreadyRegisteredException`)
   - Verify user exists in core-banking by identification number
   - Validate email matches core-banking record (`InvalidEmailException`)
   - Create user in Keycloak (disabled, email unverified)
   - Store local user record with `PENDING` status
2. **Approval Flow**:
   - Admin updates user status to `APPROVED`
   - Keycloak user is enabled and email marked as verified
3. **User Retrieval**: Combines local DB data with Keycloak data (email from Keycloak)

---

## 5. Integration Points

### Keycloak (Identity Provider)

- **Version**: 23.0.7
- **Connection**: Keycloak Admin Client (`org.keycloak:keycloak-admin-client:24.0.4`)
- **Usage**: User creation, update, read, email verification
- **Configuration**: Via `KeycloakProperties` (server URL, realm, client credentials)
- **Realm Data**: Pre-imported from `docker-compose/keycloak/` volume mount
- **OAuth2 Flow**: API Gateway validates JWT tokens against Keycloak's JWK set URI

### RabbitMQ (Message Broker)

- **Status**: Referenced in architecture documentation but **NOT implemented** in the current codebase
- **Intended Usage**: Push notification messages from fund-transfer and utility-payment services to a notification service

### Zipkin (Distributed Tracing)

- **Version**: 3
- **Port**: 9411
- **Integration**: Via `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave`
- **Coverage**: All business services report traces

### Database Connections

- **Type**: MySQL (via `com.mysql:mysql-connector-j:8.4.0`)
- **ORM**: Spring Data JPA with Hibernate
- **Migration**: Flyway (`org.flywaydb:flyway-core:10.12.0` + `flyway-mysql:10.12.0`) — only in core-banking-service
- **Schema Management**: Fund-transfer, user, and utility-payment services rely on JPA auto-DDL (Hibernate `ddl-auto`)

### Spring Cloud Config (Externalized Configuration)

- **Config Source**: Git repository at `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch**: `main`
- **Path**: `configuration/`
- **Bootstrap**: Services use `spring-cloud-starter-bootstrap` to fetch config on startup

### Netflix Eureka (Service Discovery)

- **Server**: `internet-banking-service-registry` on port 8081
- **Clients**: All business services register on startup
- **Usage**: Feign clients resolve service names (e.g., `core-banking-service`) via Eureka

---

## 6. Build and Deployment Pipeline Summary

### Build System

- **Build Tool**: Gradle (per-service `build.gradle`, no root multi-project build)
- **Java Version**: 21 (Eclipse Temurin)
- **Spring Boot**: 3.2.4
- **Spring Cloud**: 2023.0.0
- **Lombok**: Used across all services for boilerplate reduction
- **Git Properties Plugin**: `com.gorylenko.gradle-git-properties:2.4.2` for build metadata

### Docker

Each service has its own `Dockerfile`:
```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
COPY wait-for-it.sh wait-for-it.sh
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

### Docker Compose

- **Main file**: `docker-compose/docker-compose.yml` — Full stack deployment (all services + infrastructure)
- **Support file**: `docker-compose/docker-compose-support-apps.yml` — Infrastructure only (Zipkin, Keycloak, MySQL, Config Server, Service Registry)
- **Network**: Custom bridge network `javatodev_ib_network` (subnet `172.25.0.0/16`) with static IPs
- **Startup Order**: `wait-for-it.sh` scripts ensure services start after dependencies (registry, config server, MySQL)

### Build Commands

```bash
# Build a specific service
cd <service-directory>
./gradlew build

# Build Docker image
docker build -t javatodev/<service-name> .

# Run full stack
cd docker-compose
docker-compose up -d
```

### Profiles

- **Default**: Local development (connects to localhost services)
- **Docker**: Container deployment (uses Docker network hostnames and static IPs)
