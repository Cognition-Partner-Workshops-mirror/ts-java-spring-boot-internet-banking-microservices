# Application Knowledge Base

## 1. Architecture Overview

### 1.1 Services

This application consists of **6 microservices** built with Java 21 and Spring Boot 3.2.4 (Spring Cloud 2023.0.0):

| Service | Port | Description |
|---------|------|-------------|
| **internet-banking-config-server** | 8090 | Centralized configuration server backed by a Git repository |
| **internet-banking-service-registry** | 8081 | Netflix Eureka service registry for service discovery |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway with OAuth2/Keycloak security |
| **internet-banking-user-service** | 8083 | User registration, approval, and management via Keycloak |
| **internet-banking-fund-transfer-service** | 8084 | Fund transfer orchestration between bank accounts |
| **internet-banking-utility-payment-service** | 8085 | Utility bill payment processing |
| **core-banking-service** | 8092 | Core banking engine — accounts, users, transactions, balance management |

### 1.2 Communication Patterns

| Pattern | Technology | Usage |
|---------|-----------|-------|
| **Synchronous REST** | Spring Cloud OpenFeign | Service-to-service calls (fund-transfer → core-banking, utility-payment → core-banking, user-service → core-banking) |
| **Service Discovery** | Netflix Eureka | All business services register with Eureka; Feign clients resolve service names via Eureka |
| **API Gateway** | Spring Cloud Gateway | Single entry point; routes requests by path prefix to downstream services |
| **Centralized Config** | Spring Cloud Config Server | All services fetch configuration from a remote Git repo at startup |
| **Async Messaging** | RabbitMQ (planned) | Referenced in README for notification service; not yet implemented in code |

**Feign Client Mappings:**

- `internet-banking-fund-transfer-service` → `core-banking-service`
  - `GET /api/v1/account/bank-account/{account_number}` (read account)
  - `POST /api/v1/transaction/fund-transfer` (execute fund transfer)
- `internet-banking-utility-payment-service` → `core-banking-service`
  - `GET /api/v1/account/bank-account/{account_number}` (read account)
  - `POST /api/v1/transaction/util-payment` (execute utility payment)
- `internet-banking-user-service` → `core-banking-service`
  - `GET /api/v1/user/{identification}` (look up user by NIC)

### 1.3 Infrastructure Components

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Database** | MySQL 8.4.0 | Persistent storage for all business services (4 separate databases) |
| **Identity Provider** | Keycloak 23.0.7 (PostgreSQL 15 backend) | OAuth2/OIDC authentication and user management |
| **Distributed Tracing** | Zipkin 3 + Micrometer Tracing (Brave) | Request tracing across services |
| **API Documentation** | SpringDoc OpenAPI (springdoc-openapi-starter-webflux-ui 2.1.0) | Swagger UI for core-banking, fund-transfer, utility-payment, and user services |
| **Database Migration** | Flyway 10.12.0 | Schema versioning for core-banking-service |
| **Container Runtime** | Docker / Docker Compose | Full-stack local deployment |

---

## 2. Data Model Documentation

### 2.1 core-banking-service (MySQL: `banking_core_service`)

**Managed via Flyway migrations.**

#### `banking_core_user`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | bigint(20) | PK, AUTO_INCREMENT |
| `email` | varchar(255) | |
| `first_name` | varchar(255) | |
| `last_name` | varchar(255) | |
| `identification_number` | varchar(255) | Unique identifier (NIC) |

**JPA Entity:** `UserEntity` — maps to `banking_core_user`. Fields: `id`, `firstName`, `lastName`, `email`, `identificationNumber`, plus a `List<BankAccountEntity> bankAccounts` (OneToMany).

#### `banking_core_account`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | bigint(20) | PK, AUTO_INCREMENT |
| `number` | varchar(255) | Account number |
| `type` | varchar(255) | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | varchar(255) | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `actual_balance` | decimal(19,2) | |
| `available_balance` | decimal(19,2) | |
| `user_id` | bigint(20) | FK → `banking_core_user(id)` |

**JPA Entity:** `BankAccountEntity` — maps to `banking_core_account`. Has `@ManyToOne` relationship to `UserEntity`.

#### `banking_core_utility_account`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | bigint(20) | PK, AUTO_INCREMENT |
| `number` | varchar(255) | Provider account number |
| `provider_name` | varchar(255) | Provider name (e.g., VODAFONE, VERIZON) |

**JPA Entity:** `UtilityAccountEntity`.

#### `banking_core_transaction`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | bigint(20) | PK, AUTO_INCREMENT |
| `amount` | decimal(19,2) | Signed amount (negative for debits) |
| `transaction_type` | varchar(30) | NOT NULL. Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | varchar(50) | NOT NULL |
| `transaction_id` | varchar(50) | NOT NULL. UUID-based |
| `account_id` | bigint(20) | FK → `banking_core_account(id)` |

**JPA Entity:** `TransactionEntity` — maps to `banking_core_transaction`. Has `@ManyToOne` to `BankAccountEntity`.

### 2.2 internet-banking-user-service (MySQL: `banking_core_user_service`)

#### `user`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | bigint | PK, AUTO_INCREMENT |
| `auth_id` | varchar | Keycloak user ID |
| `identification` | varchar | NIC number |
| `status` | varchar | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | timestamp | Audit field |
| `created_by` | varchar | Audit field |
| `modified_date` | timestamp | Audit field |
| `modified_by` | varchar | Audit field |
| `version` | bigint | Optimistic locking |

**JPA Entity:** `UserEntity` extends `AuditAware`. Schema managed by JPA auto-DDL (no Flyway).

### 2.3 internet-banking-fund-transfer-service (MySQL: `banking_core_fund_transfer_service`)

#### `fund_transfer`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | bigint | PK, AUTO_INCREMENT |
| `transaction_reference` | varchar | Core banking transaction ID |
| `from_account` | varchar | Source account number |
| `to_account` | varchar | Destination account number |
| `amount` | decimal | Transfer amount |
| `status` | varchar | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | — | Audit fields (AuditAware) |

**JPA Entity:** `FundTransferEntity` extends `AuditAware`.

### 2.4 internet-banking-utility-payment-service (MySQL: `banking_core_utility_payment_service`)

#### `utility_payment`

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | bigint | PK, AUTO_INCREMENT |
| `provider_id` | bigint | Utility provider ID |
| `amount` | decimal | Payment amount |
| `reference_number` | varchar | Customer reference |
| `account` | varchar | Source bank account number |
| `transaction_id` | varchar | Core banking transaction ID |
| `status` | varchar | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| Audit fields | — | Via `AuditAware` |

**JPA Entity:** `UtilityPaymentEntity` extends `AuditAware`.

### 2.5 Entity Relationships Diagram (Logical)

```
banking_core_user 1───* banking_core_account 1───* banking_core_transaction
                                                         ↑
banking_core_utility_account                    (via account_id FK)

internet-banking-user-service.user ──(auth_id)──→ Keycloak User
                                   ──(identification)──→ banking_core_user.identification_number

fund_transfer ──(transaction_reference)──→ banking_core_transaction.transaction_id
utility_payment ──(transaction_id)──→ banking_core_transaction.transaction_id
```

---

## 3. API Surface Map

### 3.1 API Gateway Routes

All external requests enter through the API Gateway (port 8082) and are routed by path prefix:

| Path Prefix | Target Service |
|------------|----------------|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/utility-payment/**` or `/payment/**` | internet-banking-utility-payment-service |
| `/core/**` or `/banking-core/**` | core-banking-service |

### 3.2 core-banking-service Endpoints

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|--------------|----------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | — | `BankAccount { id, number, type, status, availableBalance, actualBalance, user }` |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider | — | `UtilityAccount { id, number, providerName }` |
| `GET` | `/api/v1/user/{identification}` | Get user by NIC | — | `User { id, firstName, lastName, email, identificationNumber, bankAccounts[] }` |
| `GET` | `/api/v1/user` | List users (paginated) | — | `List<User>` |
| `POST` | `/api/v1/transaction/fund-transfer` | Execute fund transfer | `FundTransferRequest { fromAccount, toAccount, amount }` | `FundTransferResponse { message, transactionId }` |
| `POST` | `/api/v1/transaction/util-payment` | Execute utility payment | `UtilityPaymentRequest { providerId, amount, referenceNumber, account }` | `UtilityPaymentResponse { message, transactionId }` |

### 3.3 internet-banking-user-service Endpoints

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|--------------|----------|
| `POST` | `/api/v1/bank-user/register` | Register new user (public) | `User { email, identification, password }` | `User { id, email, identification, authId, status }` |
| `GET` | `/api/v1/bank-user` | List users (paginated) | — | `List<User>` |
| `GET` | `/api/v1/bank-user/{id}` | Get user by ID | — | `User` |
| `PATCH` | `/api/v1/bank-user/update/{id}` | Update user status | `UserUpdateRequest { status }` | `User` |

### 3.4 internet-banking-fund-transfer-service Endpoints

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|--------------|----------|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `FundTransferRequest { fromAccount, toAccount, amount, authID }` | `FundTransferResponse { message, transactionId }` |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | — | `List<FundTransfer>` |

### 3.5 internet-banking-utility-payment-service Endpoints

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|--------------|----------|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `UtilityPaymentRequest { providerId, amount, referenceNumber, account }` | `UtilityPaymentResponse { message, transactionId }` |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | — | `List<UtilityPayment>` |

### 3.6 Infrastructure Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/actuator/health` | Health check (all services via Actuator) |
| `GET` | `/actuator/**` | Actuator endpoints (exposed per service) |

---

## 4. Key Business Logic Inventory

### 4.1 Fund Transfer Flow

**Entry:** `FundTransferController.sendFundTransfer()` in fund-transfer-service.

1. Receive `FundTransferRequest { fromAccount, toAccount, amount, authID }`.
2. Create `FundTransferEntity` with status `PENDING` and persist to local DB.
3. Call `core-banking-service` via Feign: `POST /api/v1/transaction/fund-transfer`.
4. In core-banking-service `TransactionService.fundTransfer()`:
   a. Look up both accounts via `AccountService.readBankAccount()`.
   b. **Validate balance:** `fromAccount.actualBalance >= amount` and `actualBalance >= 0`. Throws `InsufficientFundsException` if not.
   c. Call `internalFundTransfer()`:
      - Generate UUID `transactionId`.
      - Debit from-account: `actualBalance -= amount`, `availableBalance = actualBalance - amount`.
      - Save debit transaction record (negative amount).
      - Credit to-account: `actualBalance += amount`, `availableBalance = actualBalance + amount`.
      - Save credit transaction record (positive amount).
   d. Return `FundTransferResponse { message, transactionId }`.
5. Update local `FundTransferEntity` with `transactionReference` and status `SUCCESS`.
6. Return response to caller.

**Key Rules:**
- Both accounts must exist.
- Source account must have sufficient actual balance.
- Transaction is `@Transactional` in core-banking-service.
- No rollback handling in fund-transfer-service if core-banking fails mid-transaction.

### 4.2 Utility Payment Flow

**Entry:** `UtilityPaymentController.processPayment()` in utility-payment-service.

1. Receive `UtilityPaymentRequest { providerId, amount, referenceNumber, account }`.
2. Create `UtilityPaymentEntity` with status `PROCESSING` and persist.
3. Call `core-banking-service` via Feign: `POST /api/v1/transaction/util-payment`.
4. In core-banking-service `TransactionService.utilPayment()`:
   a. Generate UUID `transactionId`.
   b. Look up source bank account.
   c. **Validate balance:** Same as fund transfer.
   d. Look up utility account by `providerId`.
   e. Debit source account (subtract amount from both `actualBalance` and `availableBalance`).
   f. Save transaction record with type `UTILITY_PAYMENT`.
   g. Return `UtilityPaymentResponse { message, transactionId }`.
5. Update local entity with `transactionId` and status `SUCCESS`.
6. Return response.

**Known Bug:** In `utilPayment()`, `availableBalance` is set to `actualBalance - amount` after `actualBalance` was already reduced, resulting in a double deduction of `availableBalance`.

### 4.3 User Registration Flow

**Entry:** `UserController.registerUser()` in user-service.

1. Receive `User { email, identification, password }`.
2. Check Keycloak: if email already registered → throw `UserAlreadyRegisteredException`.
3. Call core-banking-service via Feign: `GET /api/v1/user/{identification}` to look up user by NIC.
4. Validate email matches core banking record → throw `InvalidEmailException` if mismatch.
5. Create Keycloak user with `UserRepresentation` (email, name, credentials). Enabled = false, email verified = false.
6. If Keycloak returns 201:
   a. Look up the newly created Keycloak user by email to get `authId`.
   b. Create local `UserEntity` with status `PENDING`.
   c. Persist and return.
7. Otherwise throw `InvalidBankingUserException`.

### 4.4 User Approval Flow

**Entry:** `UserController.updateUser()` in user-service.

1. Receive `UserUpdateRequest { status }` for user ID.
2. If status = `APPROVED`:
   a. Read Keycloak user by `authId`.
   b. Set `enabled = true` and `emailVerified = true` in Keycloak.
   c. Update Keycloak.
3. Update local entity status.

---

## 5. Integration Points

### 5.1 Keycloak (Identity Provider)

- **Version:** 23.0.7 (Quay.io image)
- **Realm:** `javatodev-internet-banking` (imported via realm-export.json)
- **Client:** `internet-banking-core-client` (configured in realm export)
- **Integration Point:** `internet-banking-user-service` uses `keycloak-admin-client:24.0.4` to:
  - Create users (`KeycloakUserService.createUser()`)
  - Update users (`KeycloakUserService.updateUser()`)
  - Search users by email (`KeycloakUserService.readUserByEmail()`)
  - Read user details (`KeycloakUserService.readUser()`)
- **API Gateway:** Validates JWT tokens via `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`.
- **Configuration:** Managed via `KeycloakProperties` and `KeycloakManager` classes.
- **Backend DB:** PostgreSQL 15 (container `keycloak_postgre_db`, IP `172.25.0.10`).

### 5.2 RabbitMQ (Message Broker)

- **Status:** Referenced in README as planned for the Notification service.
- **Current state:** Not implemented in code. No RabbitMQ dependencies in any `build.gradle`. No message producers or consumers exist.

### 5.3 Zipkin (Distributed Tracing)

- **Version:** Zipkin 3 (Docker image `openzipkin/zipkin:3`)
- **Port:** 9411
- **Integration:** All business services include:
  - `io.micrometer:micrometer-tracing-bridge-brave`
  - `io.zipkin.reporter2:zipkin-reporter-brave`
  - `io.github.openfeign:feign-micrometer`
- **Configuration:** Trace export URL configured via Spring Cloud Config Server (external Git repo).

### 5.4 Database Connections

| Service | Database Name | Technology | Migration |
|---------|--------------|------------|-----------|
| core-banking-service | `banking_core_service` | MySQL 8.4 | Flyway (3 migration scripts) |
| internet-banking-user-service | `banking_core_user_service` | MySQL 8.4 | JPA auto-DDL |
| internet-banking-fund-transfer-service | `banking_core_fund_transfer_service` | MySQL 8.4 | JPA auto-DDL |
| internet-banking-utility-payment-service | `banking_core_utility_payment_service` | MySQL 8.4 | JPA auto-DDL |

- All databases run on a single MySQL 8.4.0 instance (container `mysql_javatodev_app`, IP `172.25.0.9`).
- Test databases use H2 in-memory with Flyway disabled.
- MySQL user: `javatodev_development` / `oPItyPticIAt` (created via `privileges.sql` init script).
- MySQL root password: `woVERANKliGharym`.

### 5.5 Spring Cloud Config Server

- **Git Repository:** `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch:** `main`
- **Search Path:** `configuration/`
- All services except config-server and service-registry bootstrap from config server.
- Docker profile (`-Dspring.profiles.active=docker`) is activated in containers.

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build tool:** Gradle (per-service `build.gradle`, no multi-project root build)
- **Java version:** 21 (Eclipse Temurin 21.0.2_13-jre-alpine in Docker)
- **Spring Boot:** 3.2.4
- **Spring Cloud:** 2023.0.0
- **Plugins:**
  - `org.springframework.boot` 3.2.4
  - `io.spring.dependency-management` 1.1.4
  - `com.gorylenko.gradle-git-properties` 2.4.2 (5 of 6 services; not service-registry)
- **Test framework:** JUnit 5 (`useJUnitPlatform()`)

### 6.2 Docker Build

Each service has its own `Dockerfile`:

```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
EXPOSE <port>
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh
RUN apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

Build process: `./gradlew build` per service → `docker build` → `docker-compose up`.

### 6.3 Docker Compose

Two compose files in `docker-compose/`:

1. **`docker-compose.yml`** — Full stack (all 10 containers)
2. **`docker-compose-support-apps.yml`** — Infrastructure only (Zipkin, Keycloak, PostgreSQL, MySQL, Config Server, Service Registry)

**Startup Dependencies** (via `wait-for-it.sh` in entrypoints):
- API Gateway, User Service, Fund Transfer, Utility Payment, Core Banking all wait for:
  - Service Registry (8081)
  - Config Server (8090)
  - MySQL (3306) — business services only

**Network:** Custom bridge network `javatodev_ib_network` (subnet `172.25.0.0/16`) with static IP assignments.

### 6.4 CI/CD

- No CI/CD pipeline configuration found in the repository (no `.github/workflows`, `Jenkinsfile`, or similar).
- The `.github` directory exists but contains no workflow files.

### 6.5 Seed Data

Core banking service includes Flyway seed data:
- 4 users with NIC numbers
- 14 bank accounts across 4 users (all `SAVINGS_ACCOUNT`, `ACTIVE`)
- 6 utility provider accounts (VODAFONE, VERIZON, SINGTEL, HUTCH, AIRTEL, GIO)
- Test credentials: `ib_admin@javatodev.com` / `5V7huE3G86uB` (Keycloak)
