# Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Topology

The application implements a **microservices architecture** for an internet banking platform, built with **Java 21** and **Spring Boot 3.2.4** using **Spring Cloud 2023.0.0**.

```
                          ┌─────────────────────┐
                          │     Keycloak         │
                          │  (OAuth2 / OIDC)     │
                          │   Port: 8080         │
                          └──────────┬───────────┘
                                     │ JWT validation
                                     ▼
┌──────────┐    ┌─────────────────────────────────────┐    ┌──────────────────┐
│  Client   │───▶│       API Gateway (8082)             │───▶│  Config Server   │
│           │    │  Spring Cloud Gateway + OAuth2       │    │    (8090)        │
└──────────┘    └──────────┬──────┬──────┬─────────────┘    └──────────────────┘
                           │      │      │                          │
               ┌───────────┘      │      └───────────┐             │ config fetch
               ▼                  ▼                  ▼             ▼
     ┌──────────────┐   ┌──────────────┐   ┌──────────────┐  ┌──────────────┐
     │ User Service  │   │ Fund Transfer│   │  Utility     │  │   Service    │
     │   (8083)      │   │  Service     │   │  Payment     │  │  Registry    │
     │               │   │  (8084)      │   │  Service     │  │  (Eureka)    │
     └──────┬────────┘   └──────┬───────┘   │  (8085)      │  │  (8081)      │
            │                   │            └──────┬───────┘  └──────────────┘
            │ Feign             │ Feign             │ Feign
            ▼                   ▼                   ▼
     ┌──────────────────────────────────────────────────────┐
     │              Core Banking Service (8092)              │
     │         Accounts, Users, Transactions                 │
     └──────────────────────┬───────────────────────────────┘
                            │
                            ▼
                     ┌──────────────┐     ┌──────────────┐
                     │   MySQL 8.4  │     │   Zipkin 3   │
                     │  Port: 3306  │     │  Port: 9411  │
                     └──────────────┘     └──────────────┘
```

### 1.2 Microservices Inventory

| # | Service | Port | Purpose | Has DB | Key Dependencies |
|---|---------|------|---------|--------|-----------------|
| 1 | `internet-banking-service-registry` | 8081 | Netflix Eureka service discovery | No | `spring-cloud-starter-netflix-eureka-server` |
| 2 | `internet-banking-config-server` | 8090 | Centralized configuration (Git-backed) | No | `spring-cloud-config-server` |
| 3 | `internet-banking-api-gateway` | 8082 | API Gateway, OAuth2 security, request routing | No | `spring-cloud-starter-gateway`, `spring-boot-starter-oauth2-resource-server` |
| 4 | `internet-banking-user-service` | 8083 | User registration, management, Keycloak integration | Yes (MySQL) | OpenFeign, Keycloak Admin Client |
| 5 | `internet-banking-fund-transfer-service` | 8084 | Fund transfer orchestration | Yes (MySQL) | OpenFeign |
| 6 | `internet-banking-utility-payment-service` | 8085 | Utility bill payment orchestration | Yes (MySQL) | OpenFeign |
| 7 | `core-banking-service` | 8092 | Core banking engine: accounts, users, transactions | Yes (MySQL) | Flyway, JPA |

> **Note:** A **Notification Service** is mentioned in the README as consuming RabbitMQ messages but is listed as "PENDING Development" and does not exist in the codebase.

### 1.3 Communication Patterns

| Pattern | Technology | Usage |
|---------|-----------|-------|
| **Synchronous REST** | Spring Cloud OpenFeign | All inter-service calls (User→Core, FundTransfer→Core, UtilityPayment→Core) |
| **Service Discovery** | Netflix Eureka | All services register with Eureka; Feign clients resolve service names |
| **API Gateway** | Spring Cloud Gateway | Single entry point; routes requests by path prefix to downstream services |
| **Centralized Config** | Spring Cloud Config Server | All services fetch configuration from a Git repository at startup |
| **Distributed Tracing** | Micrometer Tracing + Zipkin | Brave bridge propagates trace/span IDs across Feign calls |
| **Auth Token Propagation** | Custom GlobalFilter | API Gateway extracts JWT principal name and forwards it as `X-Auth-Id` header |

### 1.4 Infrastructure Components

| Component | Image/Technology | Purpose | Persistence |
|-----------|-----------------|---------|-------------|
| **MySQL 8.4** | Custom Dockerfile (mysql:8.4.0) | Primary database for all data services | Docker volume `mysqldata` |
| **Keycloak 23.0.7** | quay.io/keycloak/keycloak:23.0.7 | OAuth2/OIDC identity provider | Backed by PostgreSQL 15 |
| **PostgreSQL 15** | postgres:15 | Keycloak metadata store | Docker volume `postgres_data` |
| **Zipkin 3** | openzipkin/zipkin:3 | Distributed trace collection and UI | In-memory (no persistence) |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service Database (`banking_core_service`)

#### Tables (managed by Flyway migrations)

**`banking_core_user`**
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `first_name` | VARCHAR(255) | | User's first name |
| `last_name` | VARCHAR(255) | | User's last name |
| `email` | VARCHAR(255) | | User's email address |
| `identification_number` | VARCHAR(255) | | National ID / identification number |

**`banking_core_account`**
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `number` | VARCHAR(255) | | Account number (e.g., 12-digit string) |
| `type` | VARCHAR(255) | | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `actual_balance` | DECIMAL(19,2) | | Ledger balance |
| `available_balance` | DECIMAL(19,2) | | Available balance |
| `user_id` | BIGINT | FK → `banking_core_user.id` | Account owner |

**`banking_core_transaction`**
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `amount` | DECIMAL(19,2) | | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | NOT NULL | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | NOT NULL | Destination account number or reference |
| `transaction_id` | VARCHAR(50) | NOT NULL | UUID-based unique transaction identifier |
| `account_id` | BIGINT | FK → `banking_core_account.id` | Associated account |

**`banking_core_utility_account`**
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `number` | VARCHAR(255) | | Provider account number |
| `provider_name` | VARCHAR(255) | | Utility provider name (e.g., VODAFONE, VERIZON) |

#### Entity Relationships
```
banking_core_user 1──────N banking_core_account
banking_core_account 1──────N banking_core_transaction
banking_core_utility_account (standalone, no FK relationships)
```

### 2.2 User Service Database (`banking_core_user_service`)

**`user`** (JPA auto-generated via `ddl-auto`)
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK) | Surrogate key |
| `auth_id` | VARCHAR | Keycloak user ID |
| `identification` | VARCHAR | National ID reference |
| `status` | VARCHAR (Enum) | `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | TIMESTAMP | Audit field |
| `created_by` | VARCHAR | Audit field |
| `modified_date` | TIMESTAMP | Audit field |
| `modified_by` | VARCHAR | Audit field |
| `version` | BIGINT | Optimistic locking |

### 2.3 Fund Transfer Service Database (`banking_core_fund_transfer_service`)

**`fund_transfer`** (JPA auto-generated)
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK) | Surrogate key |
| `transaction_reference` | VARCHAR | Core banking transaction ID |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `status` | VARCHAR (Enum) | `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `modified_date` | TIMESTAMP | Audit fields |
| `version` | BIGINT | Optimistic locking |

### 2.4 Utility Payment Service Database (`banking_core_utility_payment_service`)

**`utility_payment`** (JPA auto-generated)
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK) | Surrogate key |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Bill reference number |
| `account` | VARCHAR | Source account number |
| `transaction_id` | VARCHAR | Core banking transaction ID |
| `status` | VARCHAR (Enum) | `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `modified_date` | TIMESTAMP | Audit fields |
| `version` | BIGINT | Optimistic locking |

---

## 3. API Surface Map

### 3.1 API Gateway Routes

All requests enter through the API Gateway on port **8082**. The gateway routes by path prefix (configured via Spring Cloud Config Git repo):

| Path Prefix | Target Service |
|-------------|---------------|
| `/user/**` | `internet-banking-user-service` |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` |
| `/utility-payment/**` | `internet-banking-utility-payment-service` |
| `/banking-core/**` | `core-banking-service` |

### 3.2 Core Banking Service Endpoints (`/api/v1`)

| Method | Path | Controller | Description | Request Body | Response |
|--------|------|-----------|-------------|-------------|----------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | `AccountController` | Get bank account by number | - | `BankAccount` (id, number, type, status, availableBalance, actualBalance) |
| `GET` | `/api/v1/account/util-account/{account_name}` | `AccountController` | Get utility account by provider name | - | `UtilityAccount` (id, number, providerName) |
| `POST` | `/api/v1/transaction/fund-transfer` | `TransactionController` | Process fund transfer | `FundTransferRequest` (fromAccount, toAccount, amount) | `FundTransferResponse` (message, transactionId) |
| `POST` | `/api/v1/transaction/util-payment` | `TransactionController` | Process utility payment | `UtilityPaymentRequest` (providerId, amount, referenceNumber, account) | `UtilityPaymentResponse` (message, transactionId) |
| `GET` | `/api/v1/user/{identification}` | `UserController` | Get user by identification number | - | `User` (id, firstName, lastName, email, identificationNumber, bankAccounts[]) |
| `GET` | `/api/v1/user` | `UserController` | Get paginated user list | Query: `page`, `size`, `sort` | `List<User>` |

### 3.3 User Service Endpoints (`/api/v1/bank-users`)

| Method | Path | Controller | Description | Request Body | Response |
|--------|------|-----------|-------------|-------------|----------|
| `POST` | `/api/v1/bank-users/register` | `UserController` | Register new user (public) | `User` (email, identification, password) | `User` (id, authId, identification, status) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `UserController` | Update user status | `UserUpdateRequest` (status) | `User` |
| `GET` | `/api/v1/bank-users` | `UserController` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | `UserController` | Get user by ID | - | `User` |

### 3.4 Fund Transfer Service Endpoints (`/api/v1/transfer`)

| Method | Path | Controller | Description | Request Body | Response |
|--------|------|-----------|-------------|-------------|----------|
| `POST` | `/api/v1/transfer` | `FundTransferController` | Initiate fund transfer | `FundTransferRequest` (fromAccount, toAccount, amount, authID) | `FundTransferResponse` (message, transactionId) |
| `GET` | `/api/v1/transfer` | `FundTransferController` | List fund transfers (paginated) | Query: `page`, `size`, `sort` | `List<FundTransfer>` |

### 3.5 Utility Payment Service Endpoints (`/api/v1/utility-payment`)

| Method | Path | Controller | Description | Request Body | Response |
|--------|------|-----------|-------------|-------------|----------|
| `POST` | `/api/v1/utility-payment` | `UtilityPaymentController` | Process utility payment | `UtilityPaymentRequest` (providerId, amount, referenceNumber, account) | `UtilityPaymentResponse` (message, transactionId) |
| `GET` | `/api/v1/utility-payment` | `UtilityPaymentController` | List utility payments (paginated) | Query: `page`, `size`, `sort` | `List<UtilityPayment>` |

### 3.6 Infrastructure Endpoints

| Method | Path | Service | Description |
|--------|------|---------|-------------|
| `GET` | `/actuator/**` | All services | Spring Boot Actuator health/info/metrics |
| `GET` | `/swagger-ui.html` | Core, User, FundTransfer, UtilityPayment | OpenAPI/Swagger UI (springdoc) |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

```
Client → API Gateway → User Service → Core Banking Service (validate user exists)
                                     → Keycloak (create user)
                                     → User Service DB (save record)
```

1. Check if email is already registered in Keycloak (reject if duplicate)
2. Call Core Banking Service to verify the user's identification number exists
3. Validate that the email matches the Core Banking user record
4. Create user in Keycloak with credentials (disabled + email unverified)
5. Save user entity locally with `PENDING` status and Keycloak `authId`

### 4.2 User Approval Flow

1. Admin calls `PATCH /api/v1/bank-users/update/{id}` with `status: APPROVED`
2. If status is `APPROVED`, the Keycloak user is enabled and email marked as verified
3. User entity status is updated in the local database

### 4.3 Fund Transfer Flow

```
Client → API Gateway → Fund Transfer Service → Core Banking Service
```

1. **Fund Transfer Service** receives the request and saves a `PENDING` record
2. Calls **Core Banking Service** `/api/v1/transaction/fund-transfer` via Feign
3. **Core Banking Service** validates:
   - Both source and destination accounts exist
   - Source account has sufficient balance (`actualBalance >= amount`)
4. Debits the source account: `actualBalance -= amount`, `availableBalance = actualBalance - amount`
5. Credits the destination account: `actualBalance += amount`, `availableBalance = actualBalance + amount`
6. Creates two transaction records (debit + credit) with the same `transactionId`
7. **Fund Transfer Service** updates its record to `SUCCESS` with the transaction reference

### 4.4 Utility Payment Flow

```
Client → API Gateway → Utility Payment Service → Core Banking Service
```

1. **Utility Payment Service** receives the request and saves a `PROCESSING` record
2. Calls **Core Banking Service** `/api/v1/transaction/util-payment` via Feign
3. **Core Banking Service** validates:
   - Source account exists and has sufficient balance
   - Utility provider account exists (by `providerId`)
4. Debits the source account
5. Creates a transaction record with type `UTILITY_PAYMENT`
6. **Utility Payment Service** updates its record to `SUCCESS` with the transaction ID

### 4.5 Balance Validation Rules

- Transfer is rejected if `actualBalance < 0` OR `actualBalance < requestedAmount`
- Throws `InsufficientFundsException` with error code `BANKING-CORE-SERVICE-1001`

### 4.6 Auth Token Propagation

1. API Gateway validates the JWT token against Keycloak's JWK endpoint
2. Extracts the `Principal.name` from the JWT
3. A `GlobalFilter` adds it as the `X-Auth-Id` header to downstream requests
4. Each downstream service has an `AppAuthUserFilter` that reads `X-Auth-Id` and stores it in a thread-local `ApiRequestContext`

---

## 5. Integration Points

### 5.1 Keycloak (Identity Provider)

| Aspect | Details |
|--------|---------|
| **Version** | 23.0.7 |
| **Realm** | `javatodev-internet-banking` (imported from `realm-export.json`) |
| **Client** | `internet-banking-api-client` (client_credentials grant) |
| **Integration** | `keycloak-admin-client:24.0.4` in User Service |
| **Operations** | Create user, update user, search by email, read user by ID |
| **Gateway Auth** | OAuth2 Resource Server validating JWTs via JWK Set URI |

### 5.2 Zipkin (Distributed Tracing)

| Aspect | Details |
|--------|---------|
| **Version** | Zipkin 3 |
| **Library** | `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` |
| **Coverage** | All 6 services + API Gateway include tracing dependencies |
| **Feign Integration** | `feign-micrometer` for automatic span propagation in Feign calls |

### 5.3 RabbitMQ (Message Queue)

| Aspect | Details |
|--------|---------|
| **Status** | **Referenced in README but NOT implemented in codebase** |
| **Intended Use** | Fund Transfer and Utility Payment services would push notification messages |
| **Intended Consumer** | Notification Service (not yet developed) |

### 5.4 Database Connections

| Service | Database | Schema | Migration Strategy |
|---------|----------|--------|-------------------|
| Core Banking Service | MySQL 8.4 | `banking_core_service` | **Flyway** (`flyway-core:10.12.0`, `flyway-mysql:10.12.0`) |
| User Service | MySQL 8.4 | `banking_core_user_service` | **JPA ddl-auto** (implied, no Flyway) |
| Fund Transfer Service | MySQL 8.4 | `banking_core_fund_transfer_service` | **JPA ddl-auto** (implied, no Flyway) |
| Utility Payment Service | MySQL 8.4 | `banking_core_utility_payment_service` | **JPA ddl-auto** (implied, no Flyway) |

**Connection credentials** are managed through the centralized config server (Git-backed configuration).

### 5.5 Spring Cloud Config (Centralized Configuration)

| Aspect | Details |
|--------|---------|
| **Git Repository** | `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` |
| **Branch** | `main` |
| **Search Path** | `configuration/` |
| **Bootstrap Profiles** | `default` (localhost), `dev` (192.168.1.5), `docker` (container hostname) |

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build Tool:** Gradle 8.6 (each service is an independent Gradle project with its own `build.gradle`)
- **No multi-project build:** There is no root `settings.gradle` or `build.gradle` — each service is built independently
- **Java Version:** 21 (Eclipse Temurin 21.0.2_13 JRE Alpine for Docker)
- **Spring Boot Plugin:** `org.springframework.boot:3.2.4`
- **Git Properties Plugin:** `com.gorylenko.gradle-git-properties:2.4.2` (5 of 6 services)

### 6.2 Docker Build

Each service has a `Dockerfile` following a consistent pattern:

```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
EXPOSE <port>
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh
RUN apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

Key observations:
- Uses JRE-only Alpine image for minimal footprint
- `wait-for-it.sh` script for startup ordering
- Docker profile activates `bootstrap-docker.yml` for container-aware config

### 6.3 Docker Compose Deployment

Two compose files available:

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Full stack: all 7 services + MySQL + Keycloak + PostgreSQL + Zipkin |
| `docker-compose-support-apps.yml` | Infrastructure only: MySQL, Keycloak, PostgreSQL, Zipkin, Config Server, Service Registry |

**Network:** Custom bridge network `javatodev_ib_network` with subnet `172.25.0.0/16` and static IP assignments.

**Startup ordering:** Managed via `wait-for-it.sh` in entrypoints:
1. Service Registry (8081) must be available
2. Config Server (8090) must be available
3. MySQL (3306) must be available (for data services)

### 6.4 Database Initialization

- MySQL container uses a custom Dockerfile that executes `privileges.sql` on first boot
- Creates 4 databases and a `javatodev_development` user with broad privileges
- Core Banking Service uses Flyway to create tables and insert seed data

### 6.5 Test Infrastructure

- All services include `spring-boot-starter-test`
- Data services include `h2:2.2.224` for in-memory test databases
- Core Banking Service has unit tests for `AccountService`, `TransactionService`, and `UserService`
- Other services only have empty `contextLoads()` placeholder tests
- User Service has a dedicated `src/test/resources/application.yml` with H2 config and Keycloak mocks
