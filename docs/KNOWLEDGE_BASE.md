# Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Summary

This is a **Java 21 / Spring Boot 3.2.4 / Spring Cloud 2023.0.0** internet banking application composed of **6 microservices** following a gateway-routing architecture with centralized configuration and service discovery.

### 1.2 Services

| Service | Port | Role |
|---|---|---|
| **internet-banking-api-gateway** | 8082 | Single entry point; routes requests, enforces OAuth2/JWT authentication via Keycloak |
| **internet-banking-config-server** | 8090 | Spring Cloud Config Server; serves configuration from a remote Git repository |
| **internet-banking-service-registry** | 8081 | Netflix Eureka Server; service discovery for all microservices |
| **core-banking-service** | 8092 | Core banking engine; manages users, accounts, transactions (fund transfers & utility payments) |
| **internet-banking-fund-transfer-service** | 8084 | Orchestrates fund transfers; persists transfer records and delegates to core-banking-service |
| **internet-banking-utility-payment-service** | 8085 | Orchestrates utility payments; persists payment records and delegates to core-banking-service |

> **Note:** A Notification Service is mentioned in the README as "PENDING Development" and is not implemented.

### 1.3 Communication Patterns

| Pattern | Technology | Usage |
|---|---|---|
| **Synchronous REST** | Spring Cloud OpenFeign | Fund-transfer-service -> core-banking-service; utility-payment-service -> core-banking-service; user-service -> core-banking-service |
| **Service Discovery** | Netflix Eureka | All business services register with Eureka; Feign clients resolve service names through Eureka |
| **API Gateway Routing** | Spring Cloud Gateway | Routes external requests to downstream services via path-prefix-based routing |
| **Centralized Config** | Spring Cloud Config | All services (except registry) fetch configuration from a Git-backed config server at bootstrap |
| **Message Queue** | RabbitMQ | Referenced in documentation for notification service but **not yet implemented** in code |

### 1.4 Infrastructure Components

| Component | Technology | Purpose |
|---|---|---|
| **Identity Provider** | Keycloak 23.0.7 (on PostgreSQL 15) | User authentication, JWT token issuance, realm/client management |
| **Application Database** | MySQL 8.4.0 | Persistent storage for all business services (4 schemas) |
| **Distributed Tracing** | Zipkin 3 + Micrometer Tracing (Brave) | End-to-end request tracing across microservices |
| **Service Registry** | Netflix Eureka | Service discovery and health monitoring |
| **Config Store** | Git repository (GitHub) | Externalized configuration for all services |
| **Container Orchestration** | Docker Compose | Local development and deployment |

### 1.5 Network Topology (Docker Compose)

All containers run on a custom bridge network `javatodev_ib_network` (subnet `172.25.0.0/16`) with static IP assignments:

```
172.25.0.12  - Zipkin
172.25.0.11  - Keycloak
172.25.0.10  - Keycloak PostgreSQL DB
172.25.0.9   - MySQL (application DB)
172.25.0.8   - Config Server
172.25.0.7   - Service Registry (Eureka)
172.25.0.6   - API Gateway
172.25.0.5   - User Service
172.25.0.4   - Fund Transfer Service
172.25.0.3   - Utility Payment Service
172.25.0.2   - Core Banking Service
```

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (Database: `banking_core_service`)

#### `banking_core_user`
| Field | Type | Notes |
|---|---|---|
| `id` | Long (PK, auto-increment) | |
| `firstName` | String | |
| `lastName` | String | |
| `email` | String | |
| `identificationNumber` | String | National ID / NIC |

#### `banking_core_account`
| Field | Type | Notes |
|---|---|---|
| `id` | Long (PK, auto-increment) | |
| `number` | String | Account number (e.g., `100015003000`) |
| `type` | Enum (`AccountType`) | Account type classification |
| `status` | Enum (`AccountStatus`) | Active/Inactive/etc. |
| `availableBalance` | BigDecimal | |
| `actualBalance` | BigDecimal | |
| `user_id` | Long (FK -> `banking_core_user.id`) | ManyToOne relationship |

#### `banking_core_transaction`
| Field | Type | Notes |
|---|---|---|
| `id` | Long (PK, auto-increment) | |
| `amount` | BigDecimal | Negative for debits, positive for credits |
| `transactionType` | Enum (`FUND_TRANSFER`, `UTILITY_PAYMENT`) | |
| `referenceNumber` | String | Target account number or reference |
| `transactionId` | String | UUID-based transaction identifier |
| `account_id` | Long (FK -> `banking_core_account.id`) | OneToOne (CascadeType.ALL) |

#### `banking_core_utility_account`
| Field | Type | Notes |
|---|---|---|
| `id` | Long (PK, auto-increment) | |
| `number` | String | Provider account number |
| `providerName` | String | e.g., "HUTCH" |

### 2.2 Fund Transfer Service (Database: `banking_core_fund_transfer_service`)

#### `fund_transfer`
| Field | Type | Notes |
|---|---|---|
| `id` | Long (PK, auto-increment) | |
| `transactionReference` | String | Core banking transaction ID |
| `fromAccount` | String | Source account number |
| `toAccount` | String | Destination account number |
| `amount` | BigDecimal | |
| `status` | Enum (`PENDING`, `SUCCESS`, `PROCESSING`) | |
| `createdAt` | LocalDateTime | Inherited from `AuditAware` |
| `updatedAt` | LocalDateTime | Inherited from `AuditAware` |

### 2.3 User Service (Database: `banking_core_user_service`)

#### `user`
| Field | Type | Notes |
|---|---|---|
| `id` | Long (PK, auto-increment) | |
| `authId` | String | Keycloak user ID (UUID) |
| `identification` | String | National ID / NIC |
| `status` | Enum (`PENDING`, `APPROVED`) | |
| `createdAt` | LocalDateTime | Inherited from `AuditAware` |
| `updatedAt` | LocalDateTime | Inherited from `AuditAware` |

### 2.4 Utility Payment Service (Database: `banking_core_utility_payment_service`)

#### `utility_payment`
| Field | Type | Notes |
|---|---|---|
| `id` | Long (PK, auto-increment) | |
| `providerId` | Long | Utility provider ID |
| `amount` | BigDecimal | |
| `referenceNumber` | String | Customer reference (e.g., phone number) |
| `account` | String | Source bank account number |
| `transactionId` | String | Core banking transaction ID |
| `status` | Enum (`PROCESSING`, `SUCCESS`, `PENDING`) | |
| `createdAt` | LocalDateTime | Inherited from `AuditAware` |
| `updatedAt` | LocalDateTime | Inherited from `AuditAware` |

### 2.5 Entity Relationships

```
banking_core_user  1 --- * banking_core_account
banking_core_account 1 --- 1 banking_core_transaction
banking_core_utility_account (standalone, referenced by providerId)
```

---

## 3. API Surface Map

### 3.1 API Gateway Routes

All external requests flow through the API Gateway (`port 8082`) with path-prefix-based routing:

| Gateway Path Prefix | Target Service |
|---|---|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/payment/**` | internet-banking-utility-payment-service |
| `/core/**` | core-banking-service |

### 3.2 Core Banking Service Endpoints (`/api/v1/...`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | - | `BankAccount` |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | - | `UtilityAccount` |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer | `FundTransferRequest` | `FundTransferResponse` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `UtilityPaymentRequest` | `UtilityPaymentResponse` |
| `GET` | `/api/v1/user/{identification}` | Get user by NIC | - | `User` |
| `GET` | `/api/v1/user` | List users (paginated) | Pageable params | `List<User>` |

### 3.3 Fund Transfer Service Endpoints

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `FundTransferRequest` | `FundTransferResponse` |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | Pageable params | `List<FundTransfer>` |

**Request: `FundTransferRequest`**
```json
{
  "fromAccount": "100015003001",
  "toAccount": "100015003000",
  "amount": 1250.34,
  "authID": "string"
}
```

**Response: `FundTransferResponse`**
```json
{
  "message": "Fund Transfer Successfully Completed",
  "transactionId": "uuid-string"
}
```

### 3.4 User Service Endpoints

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Register new user | `User` | `User` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user status | `UserUpdateRequest` | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | Pageable params | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | - | `User` |

**Request: `User` (registration)**
```json
{
  "email": "user@example.com",
  "identification": "901830556V",
  "password": "123"
}
```

**Request: `UserUpdateRequest`**
```json
{
  "status": "APPROVED"
}
```

### 3.5 Utility Payment Service Endpoints

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `UtilityPaymentRequest` | `UtilityPaymentResponse` |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | Pageable params | `List<UtilityPayment>` |

**Request: `UtilityPaymentRequest`**
```json
{
  "providerId": 2,
  "amount": 2000,
  "referenceNumber": "0712402547",
  "account": "100015003000"
}
```

**Response: `UtilityPaymentResponse`**
```json
{
  "message": "Utility Payment Successfully Processed",
  "transactionId": "uuid-string"
}
```

### 3.6 Actuator Endpoints (All Services)

All services expose Spring Boot Actuator at `/actuator/**` including:
- `/actuator/health` - Health check
- Tracing data sent to Zipkin

---

## 4. Key Business Logic Inventory

### 4.1 Fund Transfer Flow

1. **Fund Transfer Service** receives `POST /api/v1/transfer`
2. Creates a `FundTransferEntity` with `status=PENDING` and persists it
3. Calls **Core Banking Service** via Feign: `POST /api/v1/transaction/fund-transfer`
4. Core Banking Service:
   - Reads both source and destination `BankAccount`s
   - Validates source account has sufficient balance (`actualBalance >= amount`)
   - Debits source account: `actualBalance -= amount`, `availableBalance = actualBalance - amount`
   - Credits destination account: `actualBalance += amount`, `availableBalance = actualBalance + amount`
   - Creates two `TransactionEntity` records (debit + credit) with `transactionType=FUND_TRANSFER`
   - Returns transaction ID
5. Fund Transfer Service updates entity with `transactionReference` and `status=SUCCESS`

### 4.2 Utility Payment Flow

1. **Utility Payment Service** receives `POST /api/v1/utility-payment`
2. Creates a `UtilityPaymentEntity` with `status=PROCESSING` and persists it
3. Calls **Core Banking Service** via Feign: `POST /api/v1/transaction/util-payment`
4. Core Banking Service:
   - Reads the source `BankAccount`
   - Validates sufficient balance
   - Reads the `UtilityAccount` by provider ID
   - Debits the source account
   - Creates a `TransactionEntity` with `transactionType=UTILITY_PAYMENT`
   - Returns transaction ID
5. Utility Payment Service updates entity with `transactionId` and `status=SUCCESS`

### 4.3 User Registration Flow

1. **User Service** receives `POST /api/v1/bank-users/register`
2. Checks if email is already registered in Keycloak
3. Calls **Core Banking Service** via Feign: `GET /api/v1/user/{identification}` to verify user exists in core banking
4. Validates that the provided email matches the core banking record
5. Creates a Keycloak user (disabled, email unverified) with the provided password
6. Persists a local `UserEntity` with `status=PENDING` and the Keycloak `authId`
7. Returns the created user

### 4.4 User Approval Flow

1. Admin calls `PATCH /api/v1/bank-users/update/{id}` with `{"status": "APPROVED"}`
2. If new status is `APPROVED`:
   - Reads the Keycloak user representation
   - Sets `enabled=true` and `emailVerified=true`
   - Updates Keycloak
3. Updates local entity status

### 4.5 Balance Validation Rules

- Source account `actualBalance` must be >= 0
- Source account `actualBalance` must be >= transfer/payment `amount`
- Violation throws `InsufficientFundsException` (HTTP 400)

---

## 5. Integration Points

### 5.1 Keycloak Integration

- **Version:** 23.0.7
- **Usage:** User authentication and authorization
- **Realm:** Configured via external config; imported at startup via volume mount (`docker-compose/keycloak/`)
- **API Gateway:** Validates JWT tokens using `spring-boot-starter-oauth2-resource-server` with JWK set URI from Keycloak
- **User Service:** Uses `keycloak-admin-client:24.0.4` to:
  - Create users (`client_credentials` grant)
  - Search users by email
  - Read/update user representations (enable, verify email)
- **Configuration:** Server URL, realm, client ID, and client secret injected via Spring Cloud Config (`app.config.keycloak.*`)
- **Security Rules:**
  - `/user/api/v1/bank-users/register` is publicly accessible (permitAll)
  - All `/actuator/**` endpoints are publicly accessible
  - All other endpoints require a valid JWT

### 5.2 RabbitMQ Integration

- **Status:** Mentioned in documentation but **not implemented** in code
- **Intended Use:** Push notification messages from fund-transfer and utility-payment services for the (unbuilt) notification service

### 5.3 Zipkin / Distributed Tracing

- **Version:** Zipkin 3
- **Libraries:** `micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`, `feign-micrometer`
- **Coverage:** All 6 services include tracing dependencies
- **Configuration:** Trace data sent to Zipkin server at `172.25.0.12:9411`

### 5.4 Database Connections

| Service | Database | Schema |
|---|---|---|
| core-banking-service | MySQL 8.4 | `banking_core_service` |
| internet-banking-fund-transfer-service | MySQL 8.4 | `banking_core_fund_transfer_service` |
| internet-banking-user-service | MySQL 8.4 | `banking_core_user_service` |
| internet-banking-utility-payment-service | MySQL 8.4 | `banking_core_utility_payment_service` |

- **Database User:** `javatodev_development` (created via `privileges.sql` init script)
- **ORM:** Spring Data JPA / Hibernate
- **Migrations:** Flyway (core-banking-service only; `flyway-core:10.12.0`, `flyway-mysql:10.12.0`)
- **Test DB:** H2 in-memory (all services include `com.h2database:h2` as test dependency)

### 5.5 Spring Cloud Config

- **Config Server:** Reads from `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Path:** `configuration/` directory, `main` branch
- **Bootstrap:** Services use `bootstrap.yml` / `bootstrap-docker.yml` to point to config server URL
- **Profile Activation:** Docker profile activated via `-Dspring.profiles.active=docker` in container entrypoints

### 5.6 Service-to-Service Communication (Feign Clients)

| Caller | Target | Feign Client | Endpoints Called |
|---|---|---|---|
| fund-transfer-service | core-banking-service | `BankingCoreFeignClient` | `GET /api/v1/account/bank-account/{num}`, `POST /api/v1/transaction/fund-transfer` |
| utility-payment-service | core-banking-service | `BankingCoreRestClient` | `GET /api/v1/account/bank-account/{num}`, `POST /api/v1/transaction/util-payment` |
| user-service | core-banking-service | `BankingCoreRestClient` | `GET /api/v1/user/{identification}` |

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build Tool:** Gradle (per-service `build.gradle`, no multi-project build)
- **Java Version:** 21 (Eclipse Temurin 21.0.2)
- **Spring Boot:** 3.2.4
- **Spring Cloud:** 2023.0.0
- **Plugins:**
  - `org.springframework.boot` (3.2.4)
  - `io.spring.dependency-management` (1.1.4)
  - `com.gorylenko.gradle-git-properties` (2.4.2) - Generates `git.properties` for build info

### 6.2 Docker Build

Each service has its own `Dockerfile`:
```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh && apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

**Build steps (manual):**
1. `./gradlew build` in each service directory
2. `docker build -t javatodev/<service-name> .` for each service

### 6.3 Docker Compose Deployment

Two compose files:
- `docker-compose.yml` - Full stack (all 10 containers)
- `docker-compose-support-apps.yml` - Infrastructure only (Zipkin, Keycloak, MySQL, Config Server, Service Registry)

**Startup Order (enforced by `wait-for-it.sh`):**
1. Infrastructure: MySQL, Keycloak/PostgreSQL, Zipkin
2. Config Server (port 8090)
3. Service Registry / Eureka (port 8081)
4. Business services wait for both Config Server and Service Registry before starting

### 6.4 Configuration Management

- **Local development:** `bootstrap.yml` → Config Server at `localhost:8090`
- **Docker:** `bootstrap-docker.yml` → Config Server at `internet-banking-config-server:8090`
- **Externalized configs:** Stored in separate Git repository, fetched by Config Server at runtime

### 6.5 Test Infrastructure

- **Framework:** JUnit 5 (JUnit Platform)
- **Mocking:** Mockito
- **Test DB:** H2 in-memory
- **Test execution:** `./gradlew test` per service
- **Coverage:** Unit tests exist only for `core-banking-service` (3 test classes: `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`)
