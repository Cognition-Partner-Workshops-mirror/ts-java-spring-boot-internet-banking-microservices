# Internet Banking Microservices - Application Knowledge Base

## 1. Architecture Overview

### 1.1 Technology Stack

| Component | Version |
|---|---|
| Java | 21 |
| Spring Boot | 3.2.4 |
| Spring Cloud | 2023.0.0 |
| MySQL | 8.4 |
| Keycloak | 23.0.7 |
| PostgreSQL (Keycloak) | 15 |
| Zipkin | 3 |
| Gradle | 8.6 |
| Docker Base Image | eclipse-temurin:21.0.2_13-jre-alpine |

### 1.2 Services

The application consists of **6 microservices** organized in independent Gradle projects (no multi-module root build):

| Service | Port | Description | Key Dependencies |
|---|---|---|---|
| `internet-banking-api-gateway` | 8082 | Spring Cloud Gateway, OAuth2/JWT entry point | Spring Cloud Gateway, OAuth2 Resource Server, Security, WebFlux |
| `internet-banking-service-registry` | 8081 | Netflix Eureka service discovery server | Eureka Server, Actuator |
| `internet-banking-config-server` | 8090 | Centralized configuration via Spring Cloud Config | Spring Cloud Config Server |
| `core-banking-service` | 8092 | Core ledger: users, accounts, transactions | Spring Data JPA, Flyway, MySQL Connector |
| `internet-banking-fund-transfer-service` | 8084 | Orchestrates fund transfers between accounts | OpenFeign, Spring Data JPA, MySQL |
| `internet-banking-utility-payment-service` | 8085 | Orchestrates utility bill payments | OpenFeign, Spring Data JPA, MySQL |

Additionally, `internet-banking-user-service` is defined with port **8083** in the Docker Compose configuration but the controller lives in the user-service module and manages user registration/lifecycle via Keycloak.

### 1.3 Communication Patterns

```
                          +-----------+
                          | Keycloak  |
                          | (23.0.7)  |
                          +-----+-----+
                                |
                          JWT validation
                                |
  Client --> [API Gateway :8082] --+--> [User Service :8083]
                                   +--> [Fund Transfer Service :8084]
                                   +--> [Utility Payment Service :8085]
                                   +--> [Core Banking Service :8092]
                                              ^
                                              |
                          Synchronous REST (OpenFeign)
                                              |
                    +-------------------------+-------------------------+
                    |                         |                         |
          [User Service]          [Fund Transfer Service]   [Utility Payment Service]
```

- **Synchronous REST via OpenFeign**: All inter-service communication uses Spring Cloud OpenFeign with Eureka service discovery. There is no asynchronous messaging despite RabbitMQ being mentioned in the README.
- **Service Discovery**: Netflix Eureka (`internet-banking-service-registry`). All services register as Eureka clients.
- **Centralized Config**: `internet-banking-config-server` reads configuration from a GitHub repository (`internet-banking-microservices-configurations`). Services use `bootstrap.yml` with `spring.cloud.config.uri` to connect.
- **API Gateway Routing**: Spring Cloud Gateway routes requests by path prefix:

| Gateway Path Prefix | Target Service |
|---|---|
| `/user/**` | `internet-banking-user-service` |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` |
| `/payment/**` | `internet-banking-utility-payment-service` |
| `/core/**` | `core-banking-service` |

- **Authentication Header Propagation**: The gateway extracts the authenticated principal name from the JWT token and forwards it as an `X-Auth-Id` HTTP header to downstream services via a `GlobalFilter`.

### 1.4 Configuration Strategy

Each service has three bootstrap configuration profiles:

| File | Purpose |
|---|---|
| `bootstrap.yml` | Default: points Config Server to `http://localhost:8090` |
| `bootstrap-dev.yml` | Local development override |
| `bootstrap-docker.yml` | Docker Compose override (uses container hostname) |

The Config Server itself reads from: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` (branch: `main`, search path: `configuration`).

---

## 2. Data Model Documentation

### 2.1 Database Overview

All application databases run on a single **MySQL 8.4** instance. Four separate schemas are created via `privileges.sql`:

| Schema | Service | Migration Strategy |
|---|---|---|
| `banking_core_service` | core-banking-service | Flyway |
| `banking_core_fund_transfer_service` | internet-banking-fund-transfer-service | JPA auto-DDL (Hibernate) |
| `banking_core_user_service` | internet-banking-user-service | JPA auto-DDL (Hibernate) |
| `banking_core_utility_payment_service` | internet-banking-utility-payment-service | JPA auto-DDL (Hibernate) |

### 2.2 Schema: `banking_core_service`

#### `banking_core_user`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Primary key |
| `first_name` | VARCHAR(255) | | User's first name |
| `last_name` | VARCHAR(255) | | User's last name |
| `email` | VARCHAR(255) | | User's email address |
| `identification_number` | VARCHAR(255) | | National ID (NIC) |

#### `banking_core_account`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Primary key |
| `number` | VARCHAR(255) | | Account number (e.g., `100015003000`) |
| `type` | VARCHAR(255) | | Enum: `SAVINGS_ACCOUNT` |
| `status` | VARCHAR(255) | | Enum: `ACTIVE` |
| `actual_balance` | DECIMAL(19,2) | | Ledger balance |
| `available_balance` | DECIMAL(19,2) | | Available balance |
| `user_id` | BIGINT | FK -> `banking_core_user.id` | Owner reference |

#### `banking_core_transaction`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Primary key |
| `amount` | DECIMAL(19,2) | | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | NOT NULL | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | NOT NULL | Counter-party account or reference |
| `transaction_id` | VARCHAR(50) | NOT NULL | UUID linking related entries |
| `account_id` | BIGINT | FK -> `banking_core_account.id` | Account reference |

#### `banking_core_utility_account`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Primary key |
| `number` | VARCHAR(255) | | Utility account number |
| `provider_name` | VARCHAR(255) | | Provider (e.g., `VODAFONE`, `AIRTEL`) |

**Relationships**:
- `banking_core_user` 1:N `banking_core_account` (via `user_id` FK)
- `banking_core_account` 1:N `banking_core_transaction` (via `account_id` FK)

### 2.3 Schema: `banking_core_fund_transfer_service`

#### `fund_transfer`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Primary key |
| `transaction_reference` | VARCHAR(255) | | UUID from core-banking response |
| `from_account` | VARCHAR(255) | | Source account number |
| `to_account` | VARCHAR(255) | | Destination account number |
| `amount` | DECIMAL(19,2) | | Transfer amount |
| `status` | VARCHAR(255) | | Enum: `PENDING`, `SUCCESS` |
| `created_date` | TIMESTAMP | Auto (AuditAware) | Creation timestamp |
| `created_by` | VARCHAR(255) | Auto (AuditAware) | Creator ID |
| `modified_date` | TIMESTAMP | Auto (AuditAware) | Last modification |
| `modified_by` | VARCHAR(255) | Auto (AuditAware) | Last modifier |
| `version` | BIGINT | @Version | Optimistic lock |

### 2.4 Schema: `banking_core_user_service`

#### `user`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Primary key |
| `auth_id` | VARCHAR(255) | | Keycloak user UUID |
| `identification` | VARCHAR(255) | | NIC linking to core-banking user |
| `status` | VARCHAR(255) | | Enum: `PENDING`, `APPROVED` |
| `created_date` | TIMESTAMP | Auto (AuditAware) | |
| `created_by` | VARCHAR(255) | Auto (AuditAware) | |
| `modified_date` | TIMESTAMP | Auto (AuditAware) | |
| `modified_by` | VARCHAR(255) | Auto (AuditAware) | |
| `version` | BIGINT | @Version | |

### 2.5 Schema: `banking_core_utility_payment_service`

#### `utility_payment`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Primary key |
| `provider_id` | BIGINT | | Utility provider ID |
| `amount` | DECIMAL(19,2) | | Payment amount |
| `reference_number` | VARCHAR(255) | | Bill reference |
| `account` | VARCHAR(255) | | Paying bank account number |
| `transaction_id` | VARCHAR(255) | | UUID from core-banking response |
| `status` | VARCHAR(255) | | Enum: `PROCESSING`, `SUCCESS` |
| `created_date` | TIMESTAMP | Auto (AuditAware) | |
| `created_by` | VARCHAR(255) | Auto (AuditAware) | |
| `modified_date` | TIMESTAMP | Auto (AuditAware) | |
| `modified_by` | VARCHAR(255) | Auto (AuditAware) | |
| `version` | BIGINT | @Version | |

### 2.6 Seed Data

The core-banking-service Flyway migration (`V1.0.20210427174721__temp_data.sql`) inserts:
- **4 users** (Sam Silva, Guru Darmaraj, Ragu Sivaraj, Randor Manoon)
- **14 savings accounts** across those users (balances ranging from 12,000 to 889,000.33)
- **6 utility providers** (VODAFONE, VERIZON, SINGTEL, HUTCH, AIRTEL, GIO)

---

## 3. API Surface Map

### 3.1 Core Banking Service (`:8092`)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | - | `BankAccount` | Get bank account by number |
| `GET` | `/api/v1/account/util-account/{account_name}` | - | `UtilityAccount` | Get utility account by provider name |
| `POST` | `/api/v1/transaction/fund-transfer` | `FundTransferRequest` | `FundTransferResponse` | Process fund transfer |
| `POST` | `/api/v1/transaction/util-payment` | `UtilityPaymentRequest` | `UtilityPaymentResponse` | Process utility payment |
| `GET` | `/api/v1/user/{identification}` | - | `User` | Get user by NIC identification |
| `GET` | `/api/v1/user` | `?page=&size=` | `List<User>` | List users (paginated) |

**Request/Response Shapes:**

```
FundTransferRequest:
  fromAccount: String
  toAccount: String
  amount: BigDecimal

FundTransferResponse:
  message: String
  transactionId: String

UtilityPaymentRequest:
  providerId: Long
  amount: BigDecimal
  referenceNumber: String
  account: String

UtilityPaymentResponse:
  message: String
  transactionId: String
```

### 3.2 Internet Banking User Service (`:8083`)

| Method | Endpoint | Request Body | Response | Auth | Description |
|---|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | `User` | `User` | **Public** | Register new user |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `UserUpdateRequest` | `User` | JWT | Update user status |
| `GET` | `/api/v1/bank-users` | `?page=&size=` | `List<User>` | JWT | List users (paginated) |
| `GET` | `/api/v1/bank-users/{id}` | - | `User` | JWT | Get user by ID |

**Request/Response Shapes:**

```
User (registration request):
  email: String
  identification: String
  password: String

UserUpdateRequest:
  status: Status (PENDING | APPROVED)

User (response):
  id: Long
  authId: String
  identification: String
  status: Status
  email: String (enriched from Keycloak)
```

### 3.3 Internet Banking Fund Transfer Service (`:8084`)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | `FundTransferRequest` | `FundTransferResponse` | Initiate fund transfer |
| `GET` | `/api/v1/transfer` | `?page=&size=` | `List<FundTransfer>` | List transfers (paginated) |

**Request/Response Shapes:**

```
FundTransferRequest:
  fromAccount: String
  toAccount: String
  amount: BigDecimal
  authID: String

FundTransferResponse:
  message: String
  transactionId: String
```

### 3.4 Internet Banking Utility Payment Service (`:8085`)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | `UtilityPaymentRequest` | `UtilityPaymentResponse` | Process utility payment |
| `GET` | `/api/v1/utility-payment` | `?page=&size=` | `List<UtilityPayment>` | List payments (paginated) |

**Request/Response Shapes:**

```
UtilityPaymentRequest:
  providerId: Long
  amount: BigDecimal
  referenceNumber: String
  account: String

UtilityPaymentResponse:
  message: String
  transactionId: String
```

### 3.5 API Gateway Routes (`:8082`)

All requests pass through the gateway with the following path prefix mappings:

| Client Path | Routed To |
|---|---|
| `/user/api/v1/bank-users/**` | `internet-banking-user-service` |
| `/fund-transfer/api/v1/transfer/**` | `internet-banking-fund-transfer-service` |
| `/payment/api/v1/utility-payment/**` | `internet-banking-utility-payment-service` |
| `/core/api/v1/**` | `core-banking-service` |

**Public Endpoints** (no JWT required):
- `POST /user/api/v1/bank-users/register`
- `/actuator/**` and service-prefixed actuator paths

---

## 4. Key Business Logic Inventory

### 4.1 Fund Transfer Flow

```
Client -> Gateway -> Fund Transfer Service -> Core Banking Service
```

1. **Fund Transfer Service** receives `FundTransferRequest` (fromAccount, toAccount, amount)
2. Creates `FundTransferEntity` with status `PENDING` and persists to local DB
3. Makes **synchronous Feign call** to Core Banking Service (`POST /api/v1/transaction/fund-transfer`)
4. **Core Banking Service**:
   a. Reads both bank accounts via `AccountService`
   b. Validates sender has sufficient balance (`actualBalance >= amount`)
   c. Executes `internalFundTransfer()` within a `@Transactional` context:
      - Debits sender: `actualBalance -= amount`, `availableBalance = actualBalance - amount`
      - Saves debit transaction record
      - Credits receiver: `actualBalance += amount`, `availableBalance = actualBalance + amount`
      - Saves credit transaction record
   d. Returns `FundTransferResponse` with generated `transactionId` (UUID)
5. **Fund Transfer Service** updates entity to status `SUCCESS` with transaction reference

**Known Bug - Double Deduction**: In `TransactionService.internalFundTransfer()` (line 91), `availableBalance` is set to `actualBalance - amount`, but `actualBalance` was already debited on line 90. This causes the available balance to be reduced by **twice** the transfer amount. The same pattern exists for credits (line 100) where the receiver gets double the intended credit to `availableBalance`.

### 4.2 Utility Payment Flow

```
Client -> Gateway -> Utility Payment Service -> Core Banking Service
```

1. **Utility Payment Service** receives `UtilityPaymentRequest` (providerId, amount, referenceNumber, account)
2. Creates `UtilityPaymentEntity` with status `PROCESSING` and persists
3. Makes **synchronous Feign call** to Core Banking Service (`POST /api/v1/transaction/util-payment`)
4. **Core Banking Service**:
   a. Reads the bank account and validates balance
   b. Reads utility account to validate provider exists
   c. Debits the bank account: `actualBalance -= amount`, `availableBalance = actualBalance - amount`
   d. Records the transaction
   e. Returns `UtilityPaymentResponse` with `transactionId`
5. **Utility Payment Service** updates entity to `SUCCESS`

**Known Bug - Same Double Deduction**: `TransactionService.utilPayment()` (line 64) has the identical double-deduction bug as fund transfers.

### 4.3 User Registration Flow

```
Client -> Gateway -> User Service -> Core Banking Service + Keycloak
```

1. **User Service** receives registration request (email, identification, password)
2. Checks Keycloak for existing user with same email (rejects duplicates)
3. **Feign call** to Core Banking Service (`GET /api/v1/user/{identification}`) to verify NIC exists
4. Validates email matches the core banking record
5. Creates Keycloak user:
   - Sets email, first/last name from core banking data
   - Sets `emailVerified=false`, `enabled=false`
   - Sets temporary password
6. On successful Keycloak creation (HTTP 201):
   - Reads back the Keycloak user to obtain `authId`
   - Saves `UserEntity` with status `PENDING`
7. **Admin Approval** via `PATCH /api/v1/bank-users/update/{id}`:
   - If `status=APPROVED`: enables Keycloak user and sets `emailVerified=true`
   - Updates local entity status

### 4.4 Authentication Flow

1. Client obtains JWT token from **Keycloak** (realm: `javatodev-microservices`)
2. API Gateway validates JWT using the JWK Set URI from Keycloak
3. Gateway extracts `Principal.getName()` and forwards as `X-Auth-Id` header
4. Downstream services read `X-Auth-Id` via `AppAuthUserFilter` servlet filter
5. Auth ID is stored in `ApiRequestContextHolder` (ThreadLocal) for auditing

**Test Credentials**: `ib_admin@javatodev.com / 5V7huE3G86uB`

---

## 5. Integration Points

### 5.1 Keycloak (Identity & Access Management)

| Property | Value |
|---|---|
| Version | 23.0.7 |
| Realm | `javatodev-microservices` (imported via `realm-export.json`) |
| Admin Console | `http://localhost:8080` (admin/password) |
| Client Authentication | `client_credentials` grant type |
| Database | PostgreSQL 15 (separate from app MySQL) |
| Integration Point | User Service uses `keycloak-admin-client:24.0.4` |
| Configuration | `app.config.keycloak.*` properties (via Config Server) |

The `KeycloakProperties` class connects using:
- `server-url`, `realm`, `clientId`, `client-secret`
- Implements a **non-thread-safe singleton** pattern for the Keycloak client instance

### 5.2 Zipkin (Distributed Tracing)

| Property | Value |
|---|---|
| Version | 3 |
| Port | 9411 |
| Integration | Micrometer Tracing Bridge Brave (`micrometer-tracing-bridge-brave`) |
| Reporter | `zipkin-reporter-brave` |
| Feign Support | `feign-micrometer` for trace propagation across Feign calls |

All 6 services include tracing dependencies. Trace context is propagated across inter-service Feign calls.

### 5.3 RabbitMQ

**Not implemented**. RabbitMQ is mentioned in the README for a planned Notification Service but no RabbitMQ dependencies, configuration, or message producers/consumers exist in the codebase.

### 5.4 Database Connections

| Service | Schema | Driver | Connection |
|---|---|---|---|
| core-banking-service | `banking_core_service` | `mysql-connector-j:8.4.0` | Configured via Config Server |
| fund-transfer-service | `banking_core_fund_transfer_service` | `mysql-connector-j:8.4.0` | Configured via Config Server |
| user-service | `banking_core_user_service` | `mysql-connector-j:8.4.0` | Configured via Config Server |
| utility-payment-service | `banking_core_utility_payment_service` | `mysql-connector-j:8.4.0` | Configured via Config Server |

All four services connect to the same MySQL 8.4 instance but use separate schemas.

### 5.5 Spring Cloud Config Server

| Property | Value |
|---|---|
| Git Repository | `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` |
| Branch | `main` |
| Search Path | `configuration` |
| Bootstrap | Services use `spring-cloud-starter-bootstrap` for early config fetch |

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build Process

Each service is an **independent Gradle project** (no root `settings.gradle` or multi-module build):

```bash
# Build a single service
cd core-banking-service
./gradlew clean build

# Build Docker image
docker build -t javatodev/core-banking-service .
```

All services use:
- `org.springframework.boot` plugin v3.2.4
- `io.spring.dependency-management` plugin v1.1.4
- `com.gorylenko.gradle-git-properties` plugin v2.4.2 (except service-registry)
- JUnit 5 (`useJUnitPlatform()`)

### 6.2 Docker Configuration

Each service Dockerfile follows the same pattern:

```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh
RUN apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

### 6.3 Docker Compose Orchestration

Two compose files in `docker-compose/`:

**`docker-compose-support-apps.yml`** (infrastructure only):
- MySQL 8.4 (custom Dockerfile with `privileges.sql`)
- Keycloak 23.0.7 + PostgreSQL 15
- Zipkin 3
- Config Server + Service Registry

**`docker-compose.yml`** (full stack):
- All infrastructure services above
- All 6 application microservices
- `wait-for-it.sh` for startup ordering (50s timeout per dependency)

### 6.4 Network Configuration

All containers run on a custom bridge network `javatodev_ib_network` with static IPs:

| Container | Static IP |
|---|---|
| Zipkin | 172.25.0.12 |
| Keycloak | 172.25.0.11 |
| PostgreSQL (Keycloak) | 172.25.0.10 |
| MySQL | 172.25.0.9 |
| Config Server | 172.25.0.8 |
| Service Registry | 172.25.0.7 |
| API Gateway | 172.25.0.6 |
| User Service | 172.25.0.5 |
| Fund Transfer Service | 172.25.0.4 |
| Utility Payment Service | 172.25.0.3 |
| Core Banking Service | 172.25.0.2 |

### 6.5 CI/CD

**No CI/CD pipeline** is configured in the repository. There are no GitHub Actions workflows, Jenkinsfiles, or other pipeline definitions.

### 6.6 Startup Dependency Chain

Services depend on Config Server, Service Registry, and MySQL being available. Docker Compose uses `wait-for-it.sh` to enforce ordering:

```
MySQL (3306) ─┐
               ├── Core Banking Service
               ├── Fund Transfer Service
               ├── User Service
               └── Utility Payment Service

Config Server (8090) ──┐
                       ├── All application services
Service Registry (8081)┘

Keycloak DB (5432) ── Keycloak (8080)
```
