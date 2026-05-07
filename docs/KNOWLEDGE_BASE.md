# Application Knowledge Base

## Internet Banking Microservices - Java 21 / Spring Boot 3.2.4

---

## 1. Architecture Overview

### 1.1 System Architecture

The application follows a **microservices architecture** with 6 independently deployable services communicating via synchronous REST (OpenFeign) and routed through a central API Gateway. Infrastructure services (Config Server, Service Registry) support service discovery and centralized configuration.

```
                    +-----------------------+
                    |   Keycloak (AuthN)    |
                    |   (OAuth2/OIDC)       |
                    +-----------+-----------+
                                |
                    +-----------v-----------+
   Clients -------->  API Gateway (:8082)   |
                    |  (Spring Cloud GW)    |
                    +-----------+-----------+
                                |
              +-----------------+-----------------+
              |                 |                 |
   +----------v---+  +---------v-----+  +--------v----------+
   | User Service  |  | Fund Transfer |  | Utility Payment   |
   |   (:8083)     |  | Service(:8084)|  | Service (:8085)   |
   +-------+------+  +-------+-------+  +--------+----------+
           |                  |                   |
           +------------------+-------------------+
                              |
                    +---------v---------+
                    | Core Banking Svc  |
                    |    (:8092)        |
                    +---------+---------+
                              |
                    +---------v---------+
                    |   MySQL 8.4.0     |
                    +-------------------+

   Supporting Infrastructure:
   +---------------------+  +---------------------+  +------------------+
   | Config Server(:8090)|  | Service Registry    |  | Zipkin (:9411)   |
   | (Spring Cloud Cfg)  |  | (:8081) (Eureka)    |  | (Dist. Tracing)  |
   +---------------------+  +---------------------+  +------------------+
```

### 1.2 Service Inventory

| Service | Port | Type | Purpose |
|---------|------|------|---------|
| `internet-banking-api-gateway` | 8082 | Infrastructure | Routes requests, enforces OAuth2/JWT auth |
| `internet-banking-config-server` | 8090 | Infrastructure | Centralized configuration via Git repo |
| `internet-banking-service-registry` | 8081 | Infrastructure | Eureka service discovery |
| `internet-banking-user-service` | 8083 | Business | User registration, management, Keycloak integration |
| `internet-banking-fund-transfer-service` | 8084 | Business | Fund transfers between bank accounts |
| `internet-banking-utility-payment-service` | 8085 | Business | Utility bill payments |
| `core-banking-service` | 8092 | Business | Core banking operations: accounts, users, transactions |

### 1.3 Communication Patterns

| Pattern | Technology | Usage |
|---------|-----------|-------|
| Synchronous REST | Spring Cloud OpenFeign | Service-to-service calls (User/FundTransfer/UtilityPayment -> Core Banking) |
| Service Discovery | Netflix Eureka | All services register and discover each other |
| API Gateway | Spring Cloud Gateway | Single entry point, JWT validation, request routing |
| Centralized Config | Spring Cloud Config Server | Git-backed configuration for all services |
| Distributed Tracing | Micrometer Tracing + Zipkin | Trace propagation across services via Brave |
| Auth Header Propagation | Custom `X-Auth-Id` header | Gateway extracts JWT principal and forwards as header |

### 1.4 Infrastructure Components

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Database | MySQL 8.4.0 | Primary data store (4 databases) |
| Identity Provider | Keycloak 23.0.7 | OAuth2/OIDC authentication, user realm management |
| Keycloak DB | PostgreSQL 15 | Keycloak persistence |
| Tracing | Zipkin 3 | Distributed trace collection and visualization |
| Messaging | RabbitMQ | Planned for notification service (not yet implemented) |
| Containerization | Docker / Docker Compose | Deployment orchestration |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service

**Database:** `banking_core_service`

#### `banking_core_user` (UserEntity)
| Field | Type | Description |
|-------|------|-------------|
| `id` | Long (PK, auto) | Primary key |
| `firstName` | String | User's first name |
| `lastName` | String | User's last name |
| `email` | String | User's email address |
| `identificationNumber` | String | National ID / identification number |

**Relationships:** One-to-Many -> `BankAccountEntity` (mappedBy `user`, LAZY, CASCADE ALL)

#### `banking_core_account` (BankAccountEntity)
| Field | Type | Description |
|-------|------|-------------|
| `id` | Long (PK, auto) | Primary key |
| `number` | String | Account number |
| `type` | AccountType (enum) | Account type (e.g., SAVINGS, CURRENT) |
| `status` | AccountStatus (enum) | Account status |
| `availableBalance` | BigDecimal | Available balance |
| `actualBalance` | BigDecimal | Actual/ledger balance |
| `user_id` | FK -> UserEntity | Owning user |

#### `banking_core_transaction` (TransactionEntity)
| Field | Type | Description |
|-------|------|-------------|
| `id` | Long (PK, auto) | Primary key |
| `amount` | BigDecimal | Transaction amount (negative for debits) |
| `transactionType` | TransactionType (enum) | FUND_TRANSFER or UTILITY_PAYMENT |
| `referenceNumber` | String | Reference (destination account or utility ref) |
| `transactionId` | String | UUID-based transaction identifier |
| `account_id` | FK -> BankAccountEntity | Associated account (OneToOne, CASCADE ALL) |

#### `banking_core_utility_account` (UtilityAccountEntity)
| Field | Type | Description |
|-------|------|-------------|
| `id` | Long (PK, auto) | Primary key |
| `number` | String | Utility account number |
| `providerName` | String | Provider name (e.g., electricity, water) |

### 2.2 User Service

**Database:** `banking_core_user_service`

#### `user` (UserEntity extends AuditAware)
| Field | Type | Description |
|-------|------|-------------|
| `id` | Long (PK, auto) | Primary key |
| `authId` | String | Keycloak user ID |
| `identification` | String | Maps to core banking identification number |
| `status` | Status (enum) | PENDING / APPROVED |
| `createdAt` | LocalDateTime | Audit: creation timestamp |
| `updatedAt` | LocalDateTime | Audit: update timestamp |
| `createdBy` | String | Audit: creator |
| `updatedBy` | String | Audit: last modifier |

### 2.3 Fund Transfer Service

**Database:** `banking_core_fund_transfer_service`

#### `fund_transfer` (FundTransferEntity extends AuditAware)
| Field | Type | Description |
|-------|------|-------------|
| `id` | Long (PK, auto) | Primary key |
| `transactionReference` | String | Core banking transaction ID |
| `fromAccount` | String | Source account number |
| `toAccount` | String | Destination account number |
| `amount` | BigDecimal | Transfer amount |
| `status` | TransactionStatus (enum) | PENDING / SUCCESS / FAILED |
| `createdAt` / `updatedAt` / `createdBy` / `updatedBy` | Audit fields | From AuditAware base class |

### 2.4 Utility Payment Service

**Database:** `banking_core_utility_payment_service`

#### `utility_payment` (UtilityPaymentEntity extends AuditAware)
| Field | Type | Description |
|-------|------|-------------|
| `id` | Long (PK, auto) | Primary key |
| `providerId` | Long | Utility provider ID |
| `amount` | BigDecimal | Payment amount |
| `referenceNumber` | String | Utility reference/bill number |
| `account` | String | Source bank account number |
| `transactionId` | String | Core banking transaction ID |
| `status` | TransactionStatus (enum) | PROCESSING / SUCCESS / FAILED |
| `createdAt` / `updatedAt` / `createdBy` / `updatedBy` | Audit fields | From AuditAware base class |

---

## 3. API Surface Map

### 3.1 Core Banking Service (`:8092`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | - | `BankAccount` |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | - | `UtilityAccount` |
| `GET` | `/api/v1/user/{identification}` | Get user by identification number | - | `User` |
| `GET` | `/api/v1/user` | List users (paginated) | - | `List<User>` |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer | `FundTransferRequest` | `FundTransferResponse` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `UtilityPaymentRequest` | `UtilityPaymentResponse` |

### 3.2 User Service (`:8083`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/bank-users/register` | Register new user | `User` | `User` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user (e.g., approve) | `UserUpdateRequest` | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | - | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | - | `User` |

### 3.3 Fund Transfer Service (`:8084`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `FundTransferRequest` | `FundTransferResponse` |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | - | `List<FundTransfer>` |

### 3.4 Utility Payment Service (`:8085`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `UtilityPaymentRequest` | `UtilityPaymentResponse` |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | - | `List<UtilityPayment>` |

### 3.5 API Gateway Route Prefixes (`:8082`)

All requests are routed through the gateway with these prefixes:
- `/user/**` -> User Service
- `/fund-transfer/**` -> Fund Transfer Service
- `/banking-core/**` -> Core Banking Service
- `/utility-payment/**` -> Utility Payment Service

### 3.6 Key Request/Response Shapes

**FundTransferRequest:**
```json
{ "fromAccount": "string", "toAccount": "string", "amount": 0.00 }
```

**FundTransferResponse:**
```json
{ "message": "string", "transactionId": "uuid-string" }
```

**UtilityPaymentRequest:**
```json
{ "providerId": 0, "amount": 0.00, "referenceNumber": "string", "account": "string" }
```

**UtilityPaymentResponse:**
```json
{ "message": "string", "transactionId": "uuid-string" }
```

**User (Registration Request):**
```json
{
  "firstName": "string", "lastName": "string", "email": "string",
  "identification": "string", "password": "string"
}
```

**UserUpdateRequest:**
```json
{ "status": "APPROVED" }
```

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

1. Client sends `POST /user/api/v1/bank-users/register` (public endpoint, no JWT required)
2. User Service checks Keycloak for existing email registration
3. User Service calls Core Banking Service via Feign (`GET /api/v1/user/{identification}`) to validate the user exists in the banking core
4. Validates email matches between request and core banking record
5. Creates user in Keycloak (disabled, email unverified, with provided password)
6. Stores user in local DB with `PENDING` status and Keycloak auth ID
7. Admin later approves via `PATCH /update/{id}` which enables the Keycloak user and sets email as verified

### 4.2 Fund Transfer Flow

1. Client sends `POST /fund-transfer/api/v1/transfer` (JWT required)
2. Fund Transfer Service saves a `PENDING` transfer record locally
3. Calls Core Banking Service via Feign (`POST /api/v1/transaction/fund-transfer`)
4. Core Banking Service:
   - Reads both source and destination accounts
   - Validates source account has sufficient funds (`actualBalance >= amount`)
   - Debits source account (`actualBalance - amount`, `availableBalance - amount`)
   - Credits destination account (`actualBalance + amount`, `availableBalance + amount`)
   - Creates transaction records for both accounts
   - Returns transaction ID
5. Fund Transfer Service updates local record to `SUCCESS` with transaction reference

### 4.3 Utility Payment Flow

1. Client sends `POST /utility-payment/api/v1/utility-payment` (JWT required)
2. Utility Payment Service saves a `PROCESSING` record locally
3. Calls Core Banking Service via Feign (`POST /api/v1/transaction/util-payment`)
4. Core Banking Service:
   - Validates source account balance
   - Looks up utility provider account
   - Debits source account
   - Creates transaction record
   - Returns transaction ID
5. Utility Payment Service updates local record to `SUCCESS`

### 4.4 Business Rules

| Rule | Location | Description |
|------|----------|-------------|
| Insufficient Funds Check | `TransactionService.validateBalance()` | Rejects if `actualBalance < 0` or `actualBalance < amount` |
| Duplicate Email Prevention | `UserService.createUser()` | Checks Keycloak for existing email before registration |
| Email Validation | `UserService.createUser()` | Validates request email matches core banking record |
| User Approval Workflow | `UserService.updateUser()` | Status change to APPROVED enables Keycloak user and verifies email |
| Transaction Atomicity | `TransactionService` (`@Transactional`) | Fund transfers and utility payments run in a single DB transaction |

---

## 5. Integration Points

### 5.1 Keycloak Integration

- **Service:** User Service
- **Library:** `keycloak-admin-client:24.0.4`
- **Configuration:** `KeycloakProperties` reads server URL, realm, client ID, client secret from Spring Cloud Config
- **Operations:** Create user, update user (enable/verify email), search by email, read by auth ID
- **Auth Flow:** Client credentials grant (`client_credentials`)
- **Realm:** Imported at Keycloak startup from `docker-compose/keycloak/` volume mount
- **Gateway Integration:** JWT validation via `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`

### 5.2 RabbitMQ (Planned)

- **Status:** Referenced in README but **not implemented**
- **Intended Use:** Push notification messages from Fund Transfer and Utility Payment services
- **Missing:** Notification service not yet developed

### 5.3 Zipkin / Distributed Tracing

- **Technology:** Micrometer Tracing with Brave bridge + Zipkin Reporter
- **Configured In:** All 4 business services and API Gateway (via `build.gradle` dependencies)
- **Zipkin Server:** Port 9411 in Docker Compose
- **Note:** Replaced deprecated Spring Cloud Sleuth with Micrometer Tracing (Spring Boot 3.x migration)

### 5.4 Database Connections

| Service | Database | Technology |
|---------|----------|-----------|
| Core Banking Service | `banking_core_service` | MySQL 8.4.0, Spring Data JPA, Flyway migrations |
| User Service | `banking_core_user_service` | MySQL 8.4.0, Spring Data JPA |
| Fund Transfer Service | `banking_core_fund_transfer_service` | MySQL 8.4.0, Spring Data JPA |
| Utility Payment Service | `banking_core_utility_payment_service` | MySQL 8.4.0, Spring Data JPA |
| Keycloak | `keycloak` | PostgreSQL 15 |

- **DB User:** `javatodev_development` (created by `privileges.sql` init script)
- **Test Databases:** H2 in-memory (core banking service only)

### 5.5 Spring Cloud Config Server

- **Git Repository:** `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Search Path:** `configuration/`
- **Branch:** `main`
- **Bootstrap:** Each service has `bootstrap.yml` (localhost) and `bootstrap-docker.yml` (Docker hostname) profiles

---

## 6. Build and Deployment Pipeline

### 6.1 Build System

- **Build Tool:** Gradle (per-service `build.gradle`, no multi-project root build)
- **Java Version:** 21 (source compatibility)
- **Spring Boot:** 3.2.4
- **Spring Cloud:** 2023.0.0
- **Git Properties Plugin:** `com.gorylenko.gradle-git-properties:2.4.2` (all services except Service Registry)

### 6.2 Docker Deployment

Each service has:
- `Dockerfile` (builds JAR-based image)
- `wait-for-it.sh` (startup dependency script)

**Docker Compose orchestration:**
- `docker-compose.yml` — Full stack (all services + infrastructure)
- `docker-compose-support-apps.yml` — Infrastructure only (Zipkin, Keycloak, MySQL, Config Server, Service Registry)

**Startup Order (enforced by `wait-for-it.sh`):**
1. MySQL, Keycloak, Zipkin (infrastructure)
2. Config Server, Service Registry
3. API Gateway (waits for Registry + Config)
4. Business services (wait for Registry + Config + MySQL)

### 6.3 Docker Network

- **Network:** `javatodev_ib_network` (bridge, subnet `172.25.0.0/16`)
- **Static IPs:** Each container assigned a fixed IP (172.25.0.2 - 172.25.0.12)

### 6.4 Profiles

| Profile | Purpose | Config Source |
|---------|---------|--------------|
| `default` | Local development | `bootstrap.yml` (localhost Config Server) |
| `docker` | Docker Compose deployment | `bootstrap-docker.yml` (Docker hostname Config Server) |
| `dev` | Development variant | `bootstrap-dev.yml` |

### 6.5 Test Configuration

- **Framework:** JUnit 5 (JUnit Platform)
- **Test DB:** H2 in-memory (core-banking-service only)
- **Flyway:** Disabled in tests
- **Coverage:** Unit tests exist only in `core-banking-service` (AccountServiceTest, TransactionServiceTest, UserServiceTest)
