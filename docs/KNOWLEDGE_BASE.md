# Application Knowledge Base

## Internet Banking Microservices — Java 21 / Spring Boot 3.2.4

---

## 1. Architecture Overview

### 1.1 Services

The application consists of **6 microservices**, each deployed as an independent Spring Boot application:

| # | Service | Port | Role |
|---|---------|------|------|
| 1 | **internet-banking-config-server** | 8090 | Centralized configuration via Spring Cloud Config (Git-backed) |
| 2 | **internet-banking-service-registry** | 8081 | Netflix Eureka service discovery server |
| 3 | **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway — single entry point, OAuth2/JWT security |
| 4 | **internet-banking-user-service** | 8083 | User registration, approval, Keycloak integration |
| 5 | **internet-banking-fund-transfer-service** | 8084 | Fund transfer orchestration between bank accounts |
| 6 | **internet-banking-utility-payment-service** | 8085 | Utility bill payment processing |

Additionally, **core-banking-service** (port 8092) acts as the backend banking engine — managing accounts, users, and transaction processing.

> **Note:** A Notification Service is referenced in documentation but is listed as *PENDING Development* and does not exist in the codebase.

### 1.2 Communication Patterns

```
┌──────────────┐
│   Client     │
└──────┬───────┘
       │ HTTP (JWT Bearer)
       ▼
┌──────────────────────────┐
│  API Gateway (8082)      │  ◄── OAuth2 Resource Server (Keycloak JWT)
│  Spring Cloud Gateway    │
└──────┬───────┬───────┬───┘
       │       │       │        Eureka service discovery
       ▼       ▼       ▼        (routes by service name)
┌──────┐ ┌──────┐ ┌──────┐
│ User │ │ Fund │ │ Util │
│ Svc  │ │ Xfer │ │ Pay  │
│(8083)│ │(8084)│ │(8085)│
└──┬───┘ └──┬───┘ └──┬───┘
   │        │        │      OpenFeign (sync HTTP)
   ▼        ▼        ▼
┌──────────────────────────┐
│  Core Banking Service    │
│  (8092)                  │
└──────────────────────────┘
```

| Pattern | Technology | Details |
|---------|-----------|---------|
| Service Discovery | Netflix Eureka | All services register with the Eureka server; clients resolve service names at runtime |
| API Gateway | Spring Cloud Gateway | Routes requests to downstream services via Eureka, injects `X-Auth-Id` header from JWT principal |
| Synchronous IPC | Spring Cloud OpenFeign | User, Fund Transfer, and Utility Payment services call Core Banking Service via Feign clients |
| Configuration | Spring Cloud Config Server | Git-backed config repo (`internet-banking-microservices-configurations`), services fetch config on startup via bootstrap.yml |
| Distributed Tracing | Micrometer Tracing + Brave + Zipkin | Trace context propagated across all services; traces exported to Zipkin (port 9411) |
| Authentication | OAuth2 / JWT (Keycloak) | Gateway validates JWTs; downstream services receive user identity via `X-Auth-Id` header |

### 1.3 Infrastructure Components

| Component | Image / Technology | Purpose |
|-----------|--------------------|---------|
| **MySQL 8.4** | `mysql:8.4.0` (custom Dockerfile) | Primary data store; 4 databases provisioned: `banking_core_service`, `banking_core_fund_transfer_service`, `banking_core_user_service`, `banking_core_utility_payment_service` |
| **Keycloak 23.0.7** | `quay.io/keycloak/keycloak:23.0.7` | Identity provider; realm `javatodev-internet-banking` imported on startup |
| **PostgreSQL 15** | `postgres:15` | Keycloak's backing database |
| **Zipkin 3** | `openzipkin/zipkin:3` | Distributed tracing UI and collector |
| **RabbitMQ** | Referenced in README | Intended for async notifications — not yet implemented in code |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (`banking_core_service` database)

Managed via **Flyway** migrations.

#### `banking_core_user`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | User identifier |
| `first_name` | VARCHAR(255) | First name |
| `last_name` | VARCHAR(255) | Last name |
| `email` | VARCHAR(255) | Email address |
| `identification_number` | VARCHAR(255) | Government ID / NIC number |

#### `banking_core_account`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Account identifier |
| `number` | VARCHAR(255) | Account number (e.g., `100015003000`) |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `available_balance` | DECIMAL(19,2) | Available balance |
| `actual_balance` | DECIMAL(19,2) | Actual balance |
| `user_id` | BIGINT (FK → `banking_core_user.id`) | Account owner |

#### `banking_core_utility_account`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Utility account identifier |
| `number` | VARCHAR(255) | Utility account number |
| `provider_name` | VARCHAR(255) | Provider name (e.g., `VODAFONE`, `AIRTEL`) |

#### `banking_core_transaction`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Transaction identifier |
| `amount` | DECIMAL(19,2) | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Reference (account number or provider ref) |
| `transaction_id` | VARCHAR(50) | UUID-based transaction identifier |
| `account_id` | BIGINT (FK → `banking_core_account.id`) | Associated account |

**Relationships:**
- `banking_core_user` 1:N `banking_core_account`
- `banking_core_account` 1:N `banking_core_transaction`

### 2.2 Fund Transfer Service (`banking_core_fund_transfer_service` database)

Schema managed by JPA (`ddl-auto` in test, externalized config in prod).

#### `fund_transfer` (via `FundTransferEntity`)

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Transfer record ID |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `transaction_reference` | VARCHAR | Transaction ID from core banking |
| `status` | VARCHAR (Enum) | `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` | TIMESTAMP | Audit: creation time |
| `created_by` | VARCHAR | Audit: creator |
| `modified_date` | TIMESTAMP | Audit: last modified time |
| `modified_by` | VARCHAR | Audit: last modifier |
| `version` | BIGINT | Optimistic lock version |

### 2.3 User Service (`banking_core_user_service` database)

#### `user` (via `UserEntity`)

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | User record ID |
| `auth_id` | VARCHAR | Keycloak user ID |
| `identification` | VARCHAR | Government ID / NIC |
| `status` | VARCHAR (Enum) | `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | TIMESTAMP | Audit field |
| `created_by` | VARCHAR | Audit field |
| `modified_date` | TIMESTAMP | Audit field |
| `modified_by` | VARCHAR | Audit field |
| `version` | BIGINT | Optimistic lock version |

### 2.4 Utility Payment Service (`banking_core_utility_payment_service` database)

#### `utility_payment` (via `UtilityPaymentEntity`)

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Payment record ID |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Customer reference number |
| `account` | VARCHAR | Source bank account number |
| `transaction_id` | VARCHAR | Transaction ID from core banking |
| `status` | VARCHAR (Enum) | `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | — | Audit fields |

---

## 3. API Surface Map

All downstream services are accessed through the **API Gateway** (port 8082). The gateway routes by path prefix:

| Gateway Path Prefix | Target Service |
|---------------------|----------------|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |
| `/banking-core/**` | core-banking-service |

### 3.1 Core Banking Service (port 8092)

#### Account Controller (`/api/v1/account`)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | Path: `account_number` (String) | `BankAccount { id, number, type, status, availableBalance, actualBalance }` |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | Path: `account_name` (String) | `UtilityAccount { id, number, providerName }` |

#### Transaction Controller (`/api/v1/transaction`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |

#### User Controller (`/api/v1/user`)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| `GET` | `/api/v1/user/{identification}` | Get user by identification number | Path: `identification` (String) | `User { id, firstName, lastName, email, identificationNumber, bankAccounts[] }` |
| `GET` | `/api/v1/user` | List all users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |

### 3.2 User Service (port 8083)

#### User Controller (`/api/v1/bank-users`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| `POST` | `/api/v1/bank-users/register` | Register new user | `{ email, identification, password }` | `User { id, email, identification, authId, status }` |
| `GET` | `/api/v1/bank-users` | List all users (paginated) | Query: `page`, `size`, `sort` | `List<User>` (enriched with Keycloak data) |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | Path: `id` (Long) | `User` |
| `PATCH` | `/api/v1/bank-users/{id}` | Update user status | `{ status: "APPROVED" | "DISABLED" | ... }` | `User` |

**Note:** `POST /register` is the only unauthenticated endpoint (allowed through the gateway without JWT).

### 3.3 Fund Transfer Service (port 8084)

#### Fund Transfer Controller (`/api/v1/transfer`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `{ fromAccount, toAccount, amount, authID }` | `{ message, transactionId }` |
| `GET` | `/api/v1/transfer` | List all transfers (paginated) | Query: `page`, `size`, `sort` | `List<FundTransfer>` |

### 3.4 Utility Payment Service (port 8085)

#### Utility Payment Controller (`/api/v1/utility-payment`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/utility-payment` | List all payments (paginated) | Query: `page`, `size`, `sort` | `List<UtilityPayment>` |

### 3.5 Infrastructure Endpoints

| Service | Endpoint | Description |
|---------|----------|-------------|
| All services | `/actuator/**` | Spring Boot Actuator (health, info, metrics, etc.) |
| Config Server | `/{application}/{profile}` | Configuration retrieval |
| Service Registry | `/eureka/**` | Eureka dashboard and API |
| Zipkin | `:9411` | Trace visualization UI |
| Keycloak | `:8080` | Admin console and OIDC endpoints |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

1. Client calls `POST /user/api/v1/bank-users/register` (unauthenticated)
2. User Service checks if email is already registered in Keycloak
3. User Service calls Core Banking `GET /api/v1/user/{identification}` to verify the user exists in the bank
4. Email in request must match the email on file in Core Banking
5. A Keycloak user is created (initially `enabled=false`, `emailVerified=false`)
6. A local `UserEntity` is saved with `status=PENDING`
7. An admin must later call `PATCH /user/api/v1/bank-users/{id}` with `status=APPROVED` to:
   - Enable the Keycloak user (`enabled=true`, `emailVerified=true`)
   - Update local status to `APPROVED`

### 4.2 Fund Transfer Rules

1. Client calls `POST /fund-transfer/api/v1/transfer`
2. Fund Transfer Service creates a `FundTransferEntity` with `status=PENDING`
3. Delegates to Core Banking `POST /api/v1/transaction/fund-transfer`
4. Core Banking:
   - Reads both source and destination `BankAccount` entities
   - **Balance validation:** `actualBalance >= 0` AND `actualBalance >= amount`; throws `InsufficientFundsException` otherwise
   - Debits source account (`actualBalance -= amount`, `availableBalance = actualBalance - amount`)
   - Creates debit `TransactionEntity` (negative amount)
   - Credits destination account (`actualBalance += amount`, `availableBalance = actualBalance + amount`)
   - Creates credit `TransactionEntity` (positive amount)
   - Returns `transactionId` (UUID)
5. Fund Transfer Service updates its local entity to `status=SUCCESS` with the `transactionReference`

**Potential bug:** `availableBalance` calculation uses `actualBalance` after the subtraction, resulting in double-subtraction for the sender.

### 4.3 Utility Payment Processing

1. Client calls `POST /utility-payment/api/v1/utility-payment`
2. Utility Payment Service creates a `UtilityPaymentEntity` with `status=PROCESSING`
3. Delegates to Core Banking `POST /api/v1/transaction/util-payment`
4. Core Banking:
   - Reads source `BankAccount`
   - **Balance validation** (same as fund transfer)
   - Reads `UtilityAccount` by `providerId`
   - Debits source account
   - Creates `TransactionEntity` for the utility payment
   - Returns `transactionId`
5. Utility Payment Service updates its local entity to `status=SUCCESS`

### 4.4 Authentication & Authorization

- **Keycloak realm:** `javatodev-internet-banking`
- **Client:** `internet-banking-api-client`
- JWT tokens are validated at the API Gateway using the JWK Set URI
- The gateway injects the authenticated principal's name as `X-Auth-Id` header
- Downstream services extract `X-Auth-Id` via `AppAuthUserFilter` and store it in `ApiRequestContextHolder` (ThreadLocal)
- The auditor config uses this context for `@CreatedBy` / `@LastModifiedBy` fields
- Only `/user/api/v1/bank-users/register` and `/actuator/**` endpoints bypass authentication

### 4.5 Audit Trail

- Fund Transfer, User, and Utility Payment services use JPA auditing (`@EnableJpaAuditing`)
- `AuditAware` base class provides `createdDate`, `createdBy`, `modifiedDate`, `modifiedBy`, `version`
- `AuditorAwareConfig` reads the current user from `ApiRequestContextHolder`
- Optimistic locking via `@Version`

---

## 5. Integration Points

### 5.1 Keycloak

- **Admin client library:** `keycloak-admin-client:24.0.4`
- **Connection:** User Service connects to Keycloak Admin API
- **Configuration:** `app.config.keycloak.server-url`, `realm`, `clientId`, `client-secret` (externalized)
- **Operations:** Create user, update user, search user by email, read user by auth ID
- **Gateway integration:** OAuth2 Resource Server validates JWTs using `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`

### 5.2 RabbitMQ

- Referenced in README for notification messaging
- **Not implemented** in the current codebase
- No RabbitMQ dependency in any `build.gradle`
- No message producer/consumer code exists

### 5.3 Zipkin / Distributed Tracing

- All services (except Config Server) include tracing dependencies:
  - `micrometer-tracing-bridge-brave`
  - `zipkin-reporter-brave`
  - `feign-micrometer` (propagates trace context through Feign calls)
- Zipkin server runs on port 9411

### 5.4 Database Connections

| Service | Database | Connection |
|---------|----------|------------|
| Core Banking | `banking_core_service` | MySQL via Spring Data JPA + Flyway migrations |
| Fund Transfer | `banking_core_fund_transfer_service` | MySQL via Spring Data JPA |
| User | `banking_core_user_service` | MySQL via Spring Data JPA |
| Utility Payment | `banking_core_utility_payment_service` | MySQL via Spring Data JPA |
| Keycloak | `keycloak` | PostgreSQL 15 |

All business services share a single MySQL instance with separate databases. The MySQL user `javatodev_development` has broad privileges across all databases.

### 5.5 Spring Cloud Config

- **Config Server** reads from Git repository: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- Search path: `configuration/`, branch: `main`
- All services use `bootstrap.yml` to connect to Config Server at startup
- Profile-specific bootstraps: `bootstrap-dev.yml` (local dev IP), `bootstrap-docker.yml` (Docker service name)

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build tool:** Gradle (each service has its own `build.gradle` and `gradlew` wrapper)
- **No multi-module root project** — each service is an independent Gradle project
- **Java version:** 21 (via `sourceCompatibility = '21'`)
- **Spring Boot:** 3.2.4
- **Spring Cloud:** 2023.0.0
- **Key plugins:** `org.springframework.boot`, `io.spring.dependency-management`, `com.gorylenko.gradle-git-properties`

### 6.2 Docker

Each service has a `Dockerfile` following the same pattern:

```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/{service}-0.0.1-SNAPSHOT.jar app.jar
EXPOSE {port}
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh
RUN apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

- Uses `wait-for-it.sh` for startup ordering
- Activates `docker` Spring profile

### 6.3 Docker Compose

Two compose files in `docker-compose/`:

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Full stack: all 6 services + MySQL + Keycloak + PostgreSQL + Zipkin |
| `docker-compose-support-apps.yml` | Infrastructure only: MySQL + Keycloak + PostgreSQL + Zipkin + Config Server + Service Registry |

**Network:** Custom bridge network `javatodev_ib_network` (subnet `172.25.0.0/16`) with static IPs assigned to each container.

**Startup dependencies:** Managed via `wait-for-it.sh` entrypoint overrides in compose — services wait for Service Registry (8081), Config Server (8090), and MySQL (3306) before starting.

### 6.4 CI/CD

- No GitHub Actions workflows present in the repository
- No automated CI/CD pipeline defined
- Only a `FUNDING.yml` file exists under `.github/`

### 6.5 Test Configuration

- **Test database:** H2 in-memory (each service uses `h2:mem` with Flyway disabled)
- **Core Banking Service:** Has unit tests for `AccountService`, `TransactionService`, `UserService` using Mockito
- **Other services:** Only have `contextLoads()` placeholder tests (which require Spring context and external dependencies)
- **Test framework:** JUnit 5 via `spring-boot-starter-test`
