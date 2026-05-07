# Application Knowledge Base

## 1. Architecture Overview

### System Summary

This is a **Java 21 / Spring Boot 3.2.4** internet banking application built on a microservices architecture using **Spring Cloud 2023.0.0**. The system comprises 6 deployable microservices that collectively provide banking operations including user management, fund transfers, and utility payments.

### Microservices

| Service | Port | Description |
|---------|------|-------------|
| **core-banking-service** | 8092 | Core banking engine: accounts, users, transactions, balance management |
| **internet-banking-user-service** | 8083 | User registration & management, Keycloak integration |
| **internet-banking-fund-transfer-service** | 8084 | Fund transfer orchestration between accounts |
| **internet-banking-utility-payment-service** | 8085 | Utility bill payment processing |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway with OAuth2/JWT security |
| **internet-banking-service-registry** | 8081 | Netflix Eureka service discovery |
| **internet-banking-config-server** | 8090 | Spring Cloud Config Server (Git-backed) |

### Communication Patterns

```
                        +-----------------+
                        |   Keycloak      |
                        |   (Auth/IAM)    |
                        +--------+--------+
                                 |
                                 | JWT validation
                                 v
+----------+          +-------------------+
|  Client  | ------> |   API Gateway     |
+----------+          | (Spring Cloud GW) |
                      +--------+----------+
                               |
                    +----------+-----------+
                    |          |           |
                    v          v           v
            +-------+   +--------+   +---------+
            | User  |   | Fund   |   | Utility |
            | Svc   |   | Xfer   |   | Payment |
            +---+---+   +---+----+   +----+----+
                |           |              |
                |   OpenFeign (sync REST)  |
                |           |              |
                +-----------+--------------+
                            |
                            v
                    +-------+--------+
                    | Core Banking   |
                    | Service        |
                    +-------+--------+
                            |
                            v
                    +-------+--------+
                    |     MySQL      |
                    +----------------+
```

**Synchronous Communication:**
- **OpenFeign** clients for inter-service REST calls
- User Service -> Core Banking Service (user lookup by identification)
- Fund Transfer Service -> Core Banking Service (account lookup, fund transfer execution)
- Utility Payment Service -> Core Banking Service (account lookup, payment execution)

**Service Discovery:**
- Netflix Eureka for service registration and discovery
- All business services register with the Service Registry
- Feign clients use Eureka service names for load-balanced calls

**Configuration Management:**
- Spring Cloud Config Server backed by a Git repository
- Repository: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- Profile-based configuration: `default`, `dev`, `docker`

### Infrastructure Components

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Database | MySQL 8.4.0 | Persistent storage for all business services |
| Identity Provider | Keycloak 23.0.7 | OAuth2/OIDC authentication & authorization |
| Distributed Tracing | Zipkin 3 | Request tracing across services |
| Service Discovery | Netflix Eureka | Service registration and discovery |
| API Gateway | Spring Cloud Gateway | Request routing, security enforcement |
| Config Store | Spring Cloud Config (Git) | Externalized configuration |
| Tracing Bridge | Micrometer Tracing (Brave) | Trace propagation |
| Metrics | Spring Boot Actuator | Health checks and metrics |
| Schema Migration | Flyway 10.12.0 | Database versioning (core-banking-service) |

---

## 2. Data Model Documentation

### Core Banking Service

#### `banking_core_user`
| Field | Type | Description |
|-------|------|-------------|
| id | BIGINT (PK, AUTO_INCREMENT) | Internal user ID |
| first_name | VARCHAR(255) | User's first name |
| last_name | VARCHAR(255) | User's last name |
| email | VARCHAR(255) | User's email address |
| identification_number | VARCHAR(255) | National ID / identification number |

#### `banking_core_account`
| Field | Type | Description |
|-------|------|-------------|
| id | BIGINT (PK, AUTO_INCREMENT) | Internal account ID |
| number | VARCHAR(255) | Account number (e.g., "100015003000") |
| type | VARCHAR(255) | Account type enum: `SAVINGS_ACCOUNT` |
| status | VARCHAR(255) | Account status enum: `ACTIVE` |
| actual_balance | DECIMAL(19,2) | Actual balance |
| available_balance | DECIMAL(19,2) | Available balance |
| user_id | BIGINT (FK -> banking_core_user.id) | Owning user |

#### `banking_core_transaction`
| Field | Type | Description |
|-------|------|-------------|
| id | BIGINT (PK, AUTO_INCREMENT) | Transaction ID |
| amount | DECIMAL(19,2) | Transaction amount (negative for debits) |
| transaction_type | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| reference_number | VARCHAR(50) | Reference (target account or bill ref) |
| transaction_id | VARCHAR(50) | UUID transaction identifier |
| account_id | BIGINT (FK -> banking_core_account.id) | Associated account |

#### `banking_core_utility_account`
| Field | Type | Description |
|-------|------|-------------|
| id | BIGINT (PK, AUTO_INCREMENT) | Utility account ID |
| number | VARCHAR(255) | Utility provider account number |
| provider_name | VARCHAR(255) | Provider name (e.g., "VODAFONE") |

### Internet Banking User Service

#### `user`
| Field | Type | Description |
|-------|------|-------------|
| id | BIGINT (PK, AUTO_INCREMENT) | Internal ID |
| auth_id | VARCHAR | Keycloak user UUID |
| identification | VARCHAR | Links to core banking user |
| status | VARCHAR (Enum) | `PENDING`, `APPROVED` |
| created_at | TIMESTAMP | Audit field (from AuditAware) |
| updated_at | TIMESTAMP | Audit field (from AuditAware) |

### Internet Banking Fund Transfer Service

#### `fund_transfer`
| Field | Type | Description |
|-------|------|-------------|
| id | BIGINT (PK, AUTO_INCREMENT) | Internal ID |
| transaction_reference | VARCHAR | UUID from core banking |
| from_account | VARCHAR | Source account number |
| to_account | VARCHAR | Destination account number |
| amount | DECIMAL | Transfer amount |
| status | VARCHAR (Enum) | `PENDING`, `SUCCESS`, `PROCESSING` |
| created_at | TIMESTAMP | Audit field |
| updated_at | TIMESTAMP | Audit field |

### Internet Banking Utility Payment Service

#### `utility_payment`
| Field | Type | Description |
|-------|------|-------------|
| id | BIGINT (PK, AUTO_INCREMENT) | Internal ID |
| provider_id | BIGINT | Utility provider ID |
| amount | DECIMAL | Payment amount |
| reference_number | VARCHAR | Bill reference number |
| account | VARCHAR | Source bank account number |
| transaction_id | VARCHAR | UUID from core banking |
| status | VARCHAR (Enum) | `PROCESSING`, `SUCCESS` |
| created_at | TIMESTAMP | Audit field |
| updated_at | TIMESTAMP | Audit field |

### Entity Relationships

```
banking_core_user (1) ---< (N) banking_core_account
banking_core_account (1) ---< (N) banking_core_transaction
banking_core_utility_account (standalone)

user_service.user --[auth_id]--> Keycloak User
user_service.user --[identification]--> banking_core_user.identification_number

fund_transfer --[from_account/to_account]--> banking_core_account.number
utility_payment --[account]--> banking_core_account.number
utility_payment --[provider_id]--> banking_core_utility_account.id
```

---

## 3. API Surface Map

### Core Banking Service (Port 8092)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| GET | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | Path: account_number | `BankAccount{number, type, status, availableBalance, actualBalance}` |
| GET | `/api/v1/account/util-account/{account_name}` | Get utility account by provider | Path: account_name | `UtilityAccount{id, number, providerName}` |
| POST | `/api/v1/transaction/fund-transfer` | Process fund transfer | `{fromAccount, toAccount, amount}` | `{message, transactionId}` |
| POST | `/api/v1/transaction/util-payment` | Process utility payment | `{account, providerId, amount, referenceNumber}` | `{message, transactionId}` |
| GET | `/api/v1/user/{identification}` | Get user by identification | Path: identification | `User{id, firstName, lastName, email, identificationNumber, accounts[]}` |
| GET | `/api/v1/user` | List users (paginated) | Query: page, size, sort | `Page<User>` |

### Internet Banking User Service (Port 8083)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| POST | `/api/v1/bank-users/register` | Register new user | `{email, identification, password}` | `User{id, email, identification, authId, status}` |
| PATCH | `/api/v1/bank-users/update/{id}` | Update user (approve/status) | `{status}` | `User{id, email, identification, authId, status}` |
| GET | `/api/v1/bank-users` | List all users (paginated) | Query: page, size, sort | `List<User>` |
| GET | `/api/v1/bank-users/{id}` | Get user by ID | Path: id | `User{id, email, identification, authId, status}` |

### Internet Banking Fund Transfer Service (Port 8084)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| POST | `/api/v1/transfer` | Initiate fund transfer | `{fromAccount, toAccount, amount, authID}` | `{message, transactionId}` |
| GET | `/api/v1/transfer` | List fund transfers (paginated) | Query: page, size, sort | `List<FundTransfer>` |

### Internet Banking Utility Payment Service (Port 8085)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| POST | `/api/v1/utility-payment` | Process utility payment | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` |
| GET | `/api/v1/utility-payment` | List utility payments (paginated) | Query: page, size, sort | `List<UtilityPayment>` |

### API Gateway Routes (Port 8082)

| Route Prefix | Target Service |
|-------------|----------------|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/banking-core/**` | core-banking-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |

### Actuator Endpoints (All Services)

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Health check |
| `/actuator/info` | Application info (git properties) |

---

## 4. Key Business Logic Inventory

### Fund Transfer Rules

1. **Balance Validation**: Source account must have `actualBalance >= transferAmount` and balance must be non-negative
2. **Account Lookup**: Both source and destination accounts are validated against core banking
3. **Transaction Flow**:
   - Fund Transfer Service saves a `PENDING` record
   - Calls Core Banking Service to execute the transfer
   - Core Banking deducts from source, adds to destination
   - Two transaction records created (debit and credit)
   - Fund Transfer Service updates status to `SUCCESS`
4. **Transaction ID**: UUID-based unique identifier for each transfer
5. **Insufficient Funds**: Throws `InsufficientFundsException` with error code

### Utility Payment Processing

1. **Payment Flow**:
   - Utility Payment Service saves a `PROCESSING` record
   - Calls Core Banking Service to execute payment
   - Core Banking validates balance, deducts amount
   - Creates transaction record with type `UTILITY_PAYMENT`
   - Service updates local record to `SUCCESS`
2. **Provider Lookup**: Validates utility provider exists by ID
3. **Reference Number**: External bill reference tracked for reconciliation

### User Management

1. **Registration Flow**:
   - Validate email not already registered in Keycloak
   - Lookup user in Core Banking by identification number
   - Validate email matches Core Banking record
   - Create user in Keycloak (disabled, email unverified)
   - Save user locally with `PENDING` status
2. **Approval Flow**:
   - Admin updates user status to `APPROVED`
   - Keycloak user is enabled and email marked as verified
3. **Authentication**: Gateway extracts `X-Auth-Id` header from JWT principal and passes to downstream services

### Error Handling Patterns

- Custom `SimpleBankingGlobalException` hierarchy
- `GlobalExceptionHandler` (`@ControllerAdvice`) in each service
- Error codes defined in `GlobalErrorCode` enum
- Structured `ErrorResponse{code, message}` for known exceptions
- Generic catch-all returns `400 Bad Request` for unexpected exceptions

---

## 5. Integration Points

### Keycloak (Identity Provider)

- **Version**: 23.0.7
- **Port**: 8080
- **Admin Credentials**: admin / password (development)
- **Integration**: User Service uses `keycloak-admin-client:24.0.4`
- **Authentication Flow**: Client credentials grant (`client_credentials`)
- **Configuration Properties**:
  - `app.config.keycloak.server-url`
  - `app.config.keycloak.realm`
  - `app.config.keycloak.clientId`
  - `app.config.keycloak.client-secret`
- **API Gateway**: OAuth2 Resource Server with JWT validation (`jwk-set-uri`)
- **Realm**: Pre-configured with realm export on startup

### RabbitMQ (Message Broker)

- **Status**: Referenced in architecture but **not yet implemented** in code
- **Planned Use**: Notification service will consume messages for fund transfers and utility payments
- **No dependencies** in current `build.gradle` files

### Zipkin (Distributed Tracing)

- **Version**: 3
- **Port**: 9411
- **Integration**: Micrometer Tracing with Brave bridge
- **Dependencies** (all services):
  - `io.micrometer:micrometer-tracing-bridge-brave`
  - `io.zipkin.reporter2:zipkin-reporter-brave`
  - `io.github.openfeign:feign-micrometer`

### Database (MySQL)

- **Version**: MySQL 8.4.0
- **Port**: 3306
- **Root Password**: `woVERANKliGharym`
- **App User**: `javatodev_development` / `oPItyPticIAt`
- **Databases**:
  - `banking_core_service` - Core banking data
  - `banking_core_fund_transfer_service` - Fund transfer records
  - `banking_core_user_service` - User service data
  - `banking_core_utility_payment_service` - Utility payment records
- **Schema Management**: Flyway (core-banking-service only)
- **Other Services**: JPA auto DDL (likely `spring.jpa.hibernate.ddl-auto=update`)

### Keycloak Database (PostgreSQL)

- **Version**: PostgreSQL 15
- **Purpose**: Keycloak persistence
- **Credentials**: keycloak / password

### Spring Cloud Config Server

- **Port**: 8090
- **Git Repository**: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch**: `main`
- **Search Path**: `configuration`

### Service Registry (Eureka)

- **Port**: 8081
- **All services register** on startup
- **Feign clients** use service names for discovery

---

## 6. Build and Deployment Pipeline Summary

### Build System

- **Build Tool**: Gradle (per-service `build.gradle`, no multi-project root build)
- **Java**: 21 (Eclipse Temurin 21.0.2_13-jre-alpine for Docker)
- **Spring Boot**: 3.2.4
- **Spring Cloud**: 2023.0.0
- **Plugins**:
  - `org.springframework.boot` (3.2.4)
  - `io.spring.dependency-management` (1.1.4)
  - `com.gorylenko.gradle-git-properties` (2.4.2)

### Docker

- **Base Image**: `eclipse-temurin:21.0.2_13-jre-alpine`
- **Pattern**: Each service has its own `Dockerfile`
- **Build artifacts**: `build/libs/{service-name}-0.0.1-SNAPSHOT.jar`
- **Startup**: `wait-for-it.sh` for service dependency ordering
- **Active Profile**: `docker` (set via `-Dspring.profiles.active=docker`)

### Docker Compose

- **Network**: Custom bridge `javatodev_ib_network` (subnet 172.25.0.0/16)
- **Static IPs**: Each container has a fixed IP address
- **Dependency Ordering**: `wait-for-it.sh` scripts with 50s timeouts
- **Boot Order**:
  1. MySQL, Keycloak DB, Zipkin
  2. Config Server, Service Registry
  3. API Gateway, Business Services

### Deployment Files

```
docker-compose/
  docker-compose.yml                    # Full stack deployment
  docker-compose-support-apps.yml       # Infrastructure only (no business services)
  mysql/
    Dockerfile                          # Custom MySQL with init scripts
    privileges.sql                      # DB creation and user grants
  keycloak/
    Dockerfile                          # Keycloak with realm import
    realm-export.json                   # Pre-configured realm data
```

### Test Data

- 4 pre-seeded users with identification numbers
- 14 bank accounts with balances ranging from 12,000 to 889,000.33
- 6 utility provider accounts (VODAFONE, VERIZON, SINGTEL, HUTCH, AIRTEL, GIO)
- Default test credentials: `ib_admin@javatodev.com` / `5V7huE3G86uB`
