# Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Topology

The application is a Spring Boot 3.2.4 / Java 21 microservices system implementing an internet banking platform. It comprises **6 microservices** orchestrated via Docker Compose on a fixed-IP bridge network (`172.25.0.0/16`).

```
                         ┌─────────────────────────┐
                         │     Keycloak (IAM)       │
                         │   172.25.0.11 :8080      │
                         └────────────┬────────────┘
                                      │ OAuth2 JWT
                         ┌────────────▼────────────┐
          Clients ──────►│   API Gateway            │
                         │   172.25.0.6  :8082      │
                         └──┬────────┬─────────┬───┘
                            │        │         │
               ┌────────────▼──┐ ┌───▼──────┐ ┌▼──────────────────┐
               │ User Service  │ │ Fund     │ │ Utility Payment   │
               │ 172.25.0.5    │ │ Transfer │ │ Service            │
               │ :8083         │ │ Service  │ │ 172.25.0.3 :8085  │
               └───────┬───┘  │ │172.25.0.4│ └─────────┬─────────┘
                       │      │ │ :8084    │           │
                       │      │ └────┬─────┘           │
                       │      │      │                 │
                       └──────┴──────┼─────────────────┘
                                     │  OpenFeign
                         ┌───────────▼───────────┐
                         │  Core Banking Service  │
                         │  172.25.0.2  :8092     │
                         └───────────┬───────────┘
                                     │
                         ┌───────────▼───────────┐
                         │      MySQL 3306       │
                         │    172.25.0.9          │
                         └───────────────────────┘

  Supporting Infrastructure:
  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
  │ Config Server    │  │ Service Registry │  │ Zipkin           │
  │ 172.25.0.8 :8090 │  │ 172.25.0.7 :8081│  │ 172.25.0.12:9411 │
  └──────────────────┘  └──────────────────┘  └──────────────────┘
```

### 1.2 Service Inventory

| Service | Port | Database | Role |
|---------|------|----------|------|
| `core-banking-service` | 8092 | MySQL `banking_core_service` | System of record for accounts, users, and ledger transactions |
| `internet-banking-user-service` | 8083 | MySQL `banking_core_user_service` | User registration, profile management, Keycloak integration |
| `internet-banking-fund-transfer-service` | 8084 | MySQL `banking_core_fund_transfer_service` | Account-to-account fund transfers |
| `internet-banking-utility-payment-service` | 8085 | MySQL `banking_core_utility_payment_service` | Utility bill payments |
| `internet-banking-api-gateway` | 8082 | None | Spring Cloud Gateway + OAuth2 security |
| `internet-banking-service-registry` | 8081 | None | Netflix Eureka discovery server |
| `internet-banking-config-server` | 8090 | None | Spring Cloud Config (Git-backed) |

### 1.3 Communication Patterns

| Pattern | Technology | Usage |
|---------|-----------|-------|
| **Service Discovery** | Netflix Eureka | All services register with Eureka; Feign clients resolve service names dynamically |
| **Synchronous REST** | Spring Cloud OpenFeign | User Service -> Core Banking, Fund Transfer -> Core Banking, Utility Payment -> Core Banking |
| **API Gateway Routing** | Spring Cloud Gateway | Routes prefixed by service name to downstream services |
| **Centralized Config** | Spring Cloud Config Server | Git-backed configuration repository at `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` |
| **Distributed Tracing** | Micrometer Tracing + Zipkin | Brave bridge propagates trace/span IDs across services |
| **Authentication** | Keycloak + OAuth2 JWT | Gateway validates JWT tokens; downstream services receive `X-Auth-Id` header |
| **Async Messaging** | RabbitMQ (planned) | Intended for notification service (not yet implemented) |

### 1.4 Infrastructure Components

| Component | Image | Purpose |
|-----------|-------|---------|
| MySQL | Custom build (from `docker-compose/mysql/`) | Application databases with Flyway migrations |
| Keycloak | `quay.io/keycloak/keycloak:23.0.7` | Identity and access management |
| PostgreSQL | `postgres:15` | Keycloak's backend database |
| Zipkin | `openzipkin/zipkin:3` | Distributed trace collection and UI |
| RabbitMQ | Referenced in README (not in compose) | Planned for async notifications |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL: `banking_core_service`)

#### `banking_core_user`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGINT(20)` | PK, AUTO_INCREMENT | Internal user ID |
| `email` | `VARCHAR(255)` | | User email address |
| `first_name` | `VARCHAR(255)` | | First name |
| `last_name` | `VARCHAR(255)` | | Last name |
| `identification_number` | `VARCHAR(255)` | | National ID / NIC number |

#### `banking_core_account`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGINT(20)` | PK, AUTO_INCREMENT | Account ID |
| `number` | `VARCHAR(255)` | | Account number (e.g., `100015003000`) |
| `type` | `VARCHAR(255)` | | Enum: `SAVINGS_ACCOUNT` |
| `status` | `VARCHAR(255)` | | Enum: `ACTIVE` |
| `actual_balance` | `DECIMAL(19,2)` | | Actual balance |
| `available_balance` | `DECIMAL(19,2)` | | Available balance |
| `user_id` | `BIGINT(20)` | FK -> `banking_core_user.id` | Owning user |

#### `banking_core_transaction`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGINT(20)` | PK, AUTO_INCREMENT | Transaction ID |
| `amount` | `DECIMAL(19,2)` | | Signed amount (negative for debits) |
| `transaction_type` | `VARCHAR(30)` | NOT NULL | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | `VARCHAR(50)` | NOT NULL | Target account number or reference |
| `transaction_id` | `VARCHAR(50)` | NOT NULL | UUID-based transaction identifier |
| `account_id` | `BIGINT(20)` | FK -> `banking_core_account.id` | Related account |

#### `banking_core_utility_account`
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGINT(20)` | PK, AUTO_INCREMENT | Utility account ID |
| `number` | `VARCHAR(255)` | | Provider account number |
| `provider_name` | `VARCHAR(255)` | | Provider name (e.g., `VODAFONE`, `AIRTEL`) |

#### Relationships
```
banking_core_user 1──────* banking_core_account
banking_core_account 1──────* banking_core_transaction
banking_core_utility_account (standalone - no FK relationships)
```

### 2.2 User Service (MySQL: `banking_core_user_service`)

#### `user` (JPA-managed, auto-generated via Hibernate)
| Field | Type | Description |
|-------|------|-------------|
| `id` | `BIGINT` | PK, AUTO_INCREMENT |
| `auth_id` | `VARCHAR` | Keycloak user ID (UUID) |
| `identification` | `VARCHAR` | National ID / NIC |
| `status` | `VARCHAR` | Enum: `PENDING`, `APPROVED` |
| `created_date` | `INSTANT` | Audit: creation timestamp |
| `created_by` | `VARCHAR` | Audit: creator |
| `modified_date` | `INSTANT` | Audit: last modification |
| `modified_by` | `VARCHAR` | Audit: last modifier |
| `version` | `BIGINT` | Optimistic locking version |

### 2.3 Fund Transfer Service (MySQL: `banking_core_fund_transfer_service`)

#### `fund_transfer` (JPA-managed)
| Field | Type | Description |
|-------|------|-------------|
| `id` | `BIGINT` | PK, AUTO_INCREMENT |
| `from_account` | `VARCHAR` | Source account number |
| `to_account` | `VARCHAR` | Destination account number |
| `amount` | `DECIMAL(19,2)` | Transfer amount |
| `transaction_reference` | `VARCHAR` | Core banking transaction ID |
| `status` | `VARCHAR` | Enum: `PENDING`, `SUCCESS` |
| `created_date` / `modified_date` | `INSTANT` | Audit timestamps |
| `version` | `BIGINT` | Optimistic locking |

### 2.4 Utility Payment Service (MySQL: `banking_core_utility_payment_service`)

#### `utility_payment` (JPA-managed)
| Field | Type | Description |
|-------|------|-------------|
| `id` | `BIGINT` | PK, AUTO_INCREMENT |
| `provider_id` | `BIGINT` | Utility provider ID |
| `amount` | `DECIMAL(19,2)` | Payment amount |
| `reference_number` | `VARCHAR` | Reference / bill number |
| `account` | `VARCHAR` | Source account number |
| `transaction_id` | `VARCHAR` | Core banking transaction ID |
| `status` | `VARCHAR` | Enum: `PROCESSING`, `SUCCESS` |
| `created_date` / `modified_date` | `INSTANT` | Audit timestamps |
| `version` | `BIGINT` | Optimistic locking |

---

## 3. API Surface Map

### 3.1 API Gateway Routes

All client requests enter through the gateway at port `8082`. The gateway strips the service prefix and forwards to the downstream service resolved via Eureka.

| Gateway Path Prefix | Target Service |
|---------------------|---------------|
| `/user/**` | `internet-banking-user-service` |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` |
| `/utility-payment/**` | `internet-banking-utility-payment-service` |
| `/banking-core/**` | `core-banking-service` |

### 3.2 Core Banking Service (port 8092)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | - | `BankAccount` | Lookup bank account by number |
| `GET` | `/api/v1/account/util-account/{account_name}` | - | `UtilityAccount` | Lookup utility account by provider name |
| `POST` | `/api/v1/transaction/fund-transfer` | `FundTransferRequest` | `FundTransferResponse` | Execute a fund transfer between two accounts |
| `POST` | `/api/v1/transaction/util-payment` | `UtilityPaymentRequest` | `UtilityPaymentResponse` | Execute a utility bill payment |
| `GET` | `/api/v1/user/{identification}` | - | `User` | Get user by identification number |
| `GET` | `/api/v1/user` | `Pageable` (query params) | `List<User>` | List users with pagination |

**Request/Response Shapes:**

```json
// FundTransferRequest
{ "fromAccount": "string", "toAccount": "string", "amount": 0.00 }

// FundTransferResponse
{ "message": "string", "transactionId": "uuid-string" }

// UtilityPaymentRequest
{ "providerId": 0, "amount": 0.00, "referenceNumber": "string", "account": "string" }

// UtilityPaymentResponse
{ "message": "string", "transactionId": "uuid-string" }
```

### 3.3 User Service (port 8083)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `POST` | `/api/v1/bank-users/register` | `User` | `User` | Register a new banking user (public endpoint) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `UserUpdateRequest` | `User` | Update user status (e.g., approve) |
| `GET` | `/api/v1/bank-users` | `Pageable` (query params) | `List<User>` | List all users with pagination |
| `GET` | `/api/v1/bank-users/{id}` | - | `User` | Get user by internal ID |

**Request/Response Shapes:**

```json
// User (request for registration)
{ "email": "string", "identification": "string", "password": "string" }

// User (response)
{ "id": 0, "email": "string", "identification": "string", "authId": "uuid", "status": "PENDING|APPROVED", "version": 0 }

// UserUpdateRequest
{ "status": "APPROVED" }
```

### 3.4 Fund Transfer Service (port 8084)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `POST` | `/api/v1/transfer` | `FundTransferRequest` | `FundTransferResponse` | Initiate a fund transfer |
| `GET` | `/api/v1/transfer` | `Pageable` (query params) | `List<FundTransfer>` | List all fund transfers with pagination |

**Request/Response Shapes:**

```json
// FundTransferRequest
{ "fromAccount": "string", "toAccount": "string", "amount": 0.00, "authID": "string" }

// FundTransferResponse
{ "message": "string", "transactionId": "uuid-string" }
```

### 3.5 Utility Payment Service (port 8085)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `POST` | `/api/v1/utility-payment` | `UtilityPaymentRequest` | `UtilityPaymentResponse` | Process a utility payment |
| `GET` | `/api/v1/utility-payment` | `Pageable` (query params) | `List<UtilityPayment>` | List utility payments with pagination |

**Request/Response Shapes:**

```json
// UtilityPaymentRequest
{ "providerId": 0, "amount": 0.00, "referenceNumber": "string", "account": "string" }

// UtilityPaymentResponse
{ "message": "string", "transactionId": "uuid-string" }
```

### 3.6 Service-to-Service Feign Calls

| Source Service | Target Service | Feign Method | Target Endpoint |
|---------------|---------------|-------------|----------------|
| User Service | Core Banking | `readUser(identification)` | `GET /api/v1/user/{identification}` |
| Fund Transfer Service | Core Banking | `readAccount(accountNumber)` | `GET /api/v1/account/bank-account/{account_number}` |
| Fund Transfer Service | Core Banking | `fundTransfer(request)` | `POST /api/v1/transaction/fund-transfer` |
| Utility Payment Service | Core Banking | `readAccount(accountNumber)` | `GET /api/v1/account/bank-account/{account_number}` |
| Utility Payment Service | Core Banking | `utilityPayment(request)` | `POST /api/v1/transaction/util-payment` |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

1. Client sends `POST /user/api/v1/bank-users/register` with email, identification, and password
2. User Service checks Keycloak for existing email registration
3. User Service calls Core Banking to validate the identification number exists
4. If core user exists and email matches, a Keycloak user is created (disabled, email unverified)
5. Local `UserEntity` record is saved with status `PENDING` and the Keycloak `authId`
6. Admin later calls `PATCH /update/{id}` with `{ "status": "APPROVED" }` to enable the Keycloak account

### 4.2 Fund Transfer Flow

1. Client sends `POST /fund-transfer/api/v1/transfer` with fromAccount, toAccount, amount
2. Fund Transfer Service creates a `FundTransferEntity` with status `PENDING`
3. Service delegates to Core Banking via Feign: `POST /api/v1/transaction/fund-transfer`
4. Core Banking validates both accounts exist and source has sufficient funds
5. Core Banking debits source account, credits destination account, creates two `TransactionEntity` records
6. Fund Transfer Service updates local entity to `SUCCESS` with the transaction reference

### 4.3 Utility Payment Flow

1. Client sends `POST /utility-payment/api/v1/utility-payment` with providerId, amount, referenceNumber, account
2. Utility Payment Service creates `UtilityPaymentEntity` with status `PROCESSING`
3. Service delegates to Core Banking via Feign: `POST /api/v1/transaction/util-payment`
4. Core Banking validates account balance, debits the account, creates a `TransactionEntity`
5. Utility Payment Service updates local entity to `SUCCESS` with the transaction ID

### 4.4 Balance Validation Rules

- Both `actualBalance` and comparison against transfer `amount` are checked
- If `actualBalance < 0` OR `actualBalance < amount`, an `InsufficientFundsException` is thrown
- Balance updates modify both `actualBalance` and `availableBalance` fields

### 4.5 Authentication / Authorization Header Propagation

1. Gateway intercepts all requests and extracts the OAuth2 principal name
2. The principal is injected as the `X-Auth-Id` HTTP header
3. Downstream services extract this header via `AppAuthUserFilter` servlet filter
4. The auth ID is stored in a thread-local `ApiRequestContextHolder` for audit purposes
5. `AuditorAwareConfig` reads from this context to populate `createdBy` / `modifiedBy` fields

---

## 5. Integration Points

### 5.1 Keycloak

- **Version:** 23.0.7
- **Connection:** User Service connects via `keycloak-admin-client:24.0.4`
- **Configuration:** Server URL, realm, client ID, and client secret are injected via `@Value` from Spring Cloud Config
- **Grant Type:** `client_credentials`
- **Operations:** Create user, update user (enable/verify), search by email, read by auth ID
- **Realm Data:** Pre-imported from `docker-compose/keycloak/` volume mount
- **Gateway Integration:** JWT validation via `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`

### 5.2 RabbitMQ

- **Status:** Planned but not implemented
- **Intended Use:** Fund Transfer and Utility Payment services would push notification messages to RabbitMQ for a Notification Service to consume
- **Current State:** No RabbitMQ dependency in any `build.gradle`, no RabbitMQ container in Docker Compose

### 5.3 Zipkin

- **Version:** 3 (Docker image `openzipkin/zipkin:3`)
- **Port:** 9411
- **Integration:** Via `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` in all business services
- **Feign Tracing:** `feign-micrometer` dependency included for trace propagation across Feign calls

### 5.4 Database Connections

| Service | Database | Schema | Migration |
|---------|----------|--------|-----------|
| Core Banking | MySQL | `banking_core_service` | Flyway (3 versioned migrations) |
| User Service | MySQL | `banking_core_user_service` | Hibernate auto-DDL (no Flyway) |
| Fund Transfer | MySQL | `banking_core_fund_transfer_service` | Hibernate auto-DDL (no Flyway) |
| Utility Payment | MySQL | `banking_core_utility_payment_service` | Hibernate auto-DDL (no Flyway) |

- MySQL user: `javatodev_development` / password: `oPItyPticIAt` (created via `privileges.sql`)
- Keycloak DB: PostgreSQL 15 with user `keycloak` / password `password`

### 5.5 Spring Cloud Config

- **Config Server** pulls from: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch:** `main`
- **Search path:** `configuration/`
- All services connect to Config Server at bootstrap time via `bootstrap.yml` / `bootstrap-docker.yml`
- Profile-specific configs loaded via `-Dspring.profiles.active=docker`

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build Tool:** Gradle (wrapper version in each service)
- **Java Version:** 21 (Eclipse Temurin 21.0.2)
- **Spring Boot:** 3.2.4
- **Spring Cloud:** 2023.0.0
- **Plugin:** `com.gorylenko.gradle-git-properties` for Git metadata in actuator `/info`

### 6.2 Docker Build

Each service has its own `Dockerfile`:
```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh
RUN apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

### 6.3 Docker Compose Orchestration

- **Main file:** `docker-compose/docker-compose.yml` - Full stack with all services
- **Support file:** `docker-compose/docker-compose-support-apps.yml` - Infrastructure only (MySQL, Keycloak, Zipkin, Config Server, Service Registry)
- **Network:** Custom bridge `javatodev_ib_network` with static IPs
- **Startup ordering:** `wait-for-it.sh` scripts ensure Config Server, Service Registry, and MySQL are ready before app services start
- **Volumes:** `postgres_data` and `mysqldata` for database persistence

### 6.4 Deployment Workflow

1. Build each service JAR: `./gradlew build` (in each service directory)
2. Build Docker images: `docker build -t javatodev/<service-name> .`
3. Start infrastructure: `docker-compose -f docker-compose-support-apps.yml up -d`
4. Start application services: `docker-compose up -d`

### 6.5 Test Data

- 4 pre-seeded users in Core Banking (Sam, Guru, Ragu, Randor)
- 14 savings accounts with balances ranging from 12,000 to 889,000.33
- 6 utility providers (VODAFONE, VERIZON, SINGTEL, HUTCH, AIRTEL, GIO)
- Keycloak realm with test credentials: `ib_admin@javatodev.com` / `5V7huE3G86uB`
