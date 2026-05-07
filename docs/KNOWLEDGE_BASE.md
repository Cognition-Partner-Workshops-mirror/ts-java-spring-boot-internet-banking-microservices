# Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Summary

This is a **Java 21 / Spring Boot 3.2.4 / Spring Cloud 2023.0.0** internet banking application composed of **6 microservices** that together implement core banking operations including user management, fund transfers, and utility payments.

### 1.2 Services

| Service | Port | Purpose | Database |
|---------|------|---------|----------|
| **internet-banking-service-registry** | 8081 | Netflix Eureka service discovery server | None |
| **internet-banking-config-server** | 8090 | Spring Cloud Config server (Git-backed) | None |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway + OAuth2/Keycloak security | None |
| **internet-banking-user-service** | 8083 | User registration, management, Keycloak integration | MySQL (`banking_core_user_service`) |
| **internet-banking-fund-transfer-service** | 8084 | Fund transfer orchestration | MySQL (`banking_core_fund_transfer_service`) |
| **internet-banking-utility-payment-service** | 8085 | Utility bill payment orchestration | MySQL (`banking_core_utility_payment_service`) |
| **core-banking-service** | 8092 | Core banking engine: accounts, users, transactions | MySQL (`banking_core_service`) |

### 1.3 Communication Patterns

```
                    ┌──────────────────┐
                    │   Keycloak IAM   │ :8080
                    └────────┬─────────┘
                             │ JWT validation
┌─────────┐        ┌────────▼─────────┐
│  Client  │──────►│   API Gateway    │ :8082
└─────────┘        └────────┬─────────┘
                             │ Routes (via Eureka)
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
     ┌────────────┐  ┌────────────┐  ┌────────────┐
     │User Service│  │Fund Xfer   │  │Utility Pay │
     │   :8083    │  │Service:8084│  │Service:8085│
     └──────┬─────┘  └──────┬─────┘  └──────┬─────┘
            │ Feign          │ Feign          │ Feign
            ▼                ▼                ▼
     ┌─────────────────────────────────────────────┐
     │           Core Banking Service :8092         │
     └──────────────────┬──────────────────────────┘
                        │
                   ┌────▼────┐
                   │  MySQL  │ :3306
                   └─────────┘
```

- **Service Discovery:** All services register with Eureka (service-registry). Feign clients resolve service names via Eureka.
- **Synchronous REST (OpenFeign):** User, Fund Transfer, and Utility Payment services call Core Banking Service via Feign clients.
- **API Gateway Routing:** Spring Cloud Gateway routes requests to downstream services using Eureka-resolved service IDs. It injects an `X-Auth-Id` header with the authenticated principal name.
- **Centralized Configuration:** All services (except service-registry) pull configuration from the Config Server, which reads from a [remote Git repository](https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git).
- **Distributed Tracing:** Micrometer Tracing with Brave bridge exports spans to Zipkin.
- **Authentication Flow:** API Gateway acts as an OAuth2 Resource Server validating JWTs issued by Keycloak. Downstream services receive the user identity via the `X-Auth-Id` header.

### 1.4 Infrastructure Components

| Component | Image/Version | Purpose |
|-----------|--------------|---------|
| MySQL | 8.4.0 | Persistent storage for all business data |
| Keycloak | 23.0.7 | Identity and Access Management (OAuth2/OIDC) |
| PostgreSQL | 15 | Keycloak's backing database |
| Zipkin | 3 | Distributed tracing UI and collector |
| Spring Cloud Config | (embedded) | Externalized configuration management |
| Netflix Eureka | (embedded) | Service discovery and registration |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (`banking_core_service` database)

#### `banking_core_user`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto) | |
| `first_name` | VARCHAR(255) | |
| `last_name` | VARCHAR(255) | |
| `email` | VARCHAR(255) | |
| `identification_number` | VARCHAR(255) | National ID / passport number |

#### `banking_core_account`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto) | |
| `number` | VARCHAR(255) | Account number |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `available_balance` | DECIMAL(19,2) | |
| `actual_balance` | DECIMAL(19,2) | |
| `user_id` | BIGINT (FK) | References `banking_core_user.id` |

**Relationship:** Many accounts belong to one user (`@ManyToOne`).

#### `banking_core_transaction`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto) | |
| `amount` | DECIMAL(19,2) | Negative for debits, positive for credits |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Target account number or reference |
| `transaction_id` | VARCHAR(50) | UUID-based unique transaction identifier |
| `account_id` | BIGINT (FK) | References `banking_core_account.id` |

**Relationship:** One-to-one with `BankAccountEntity` (via `@OneToOne` with `CascadeType.ALL`).

#### `banking_core_utility_account`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto) | |
| `number` | VARCHAR(255) | Utility provider account number |
| `provider_name` | VARCHAR(255) | e.g., VODAFONE, VERIZON, SINGTEL |

**Schema managed by:** Flyway migrations in `core-banking-service/src/main/resources/db/migration/`.

### 2.2 User Service (`banking_core_user_service` database)

#### `user`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto) | |
| `auth_id` | VARCHAR(255) | Keycloak user UUID |
| `identification` | VARCHAR(255) | Links to core banking user |
| `status` | VARCHAR(255) | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | TIMESTAMP | Audit field |
| `created_by` | VARCHAR(255) | Audit field |
| `modified_date` | TIMESTAMP | Audit field |
| `modified_by` | VARCHAR(255) | Audit field |
| `version` | BIGINT | Optimistic locking |

**Schema managed by:** JPA auto-DDL (Hibernate, no Flyway for this service).

### 2.3 Fund Transfer Service (`banking_core_fund_transfer_service` database)

#### `fund_transfer`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto) | |
| `transaction_reference` | VARCHAR(255) | Core banking transaction ID |
| `from_account` | VARCHAR(255) | Source account number |
| `to_account` | VARCHAR(255) | Destination account number |
| `amount` | DECIMAL(19,2) | Transfer amount |
| `status` | VARCHAR(255) | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `modified_date` / `created_by` / `modified_by` / `version` | (audit fields) | |

**Schema managed by:** JPA auto-DDL.

### 2.4 Utility Payment Service (`banking_core_utility_payment_service` database)

#### `utility_payment`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto) | |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL(19,2) | Payment amount |
| `reference_number` | VARCHAR(255) | Bill reference |
| `account` | VARCHAR(255) | Source account number |
| `transaction_id` | VARCHAR(255) | Core banking transaction ID |
| `status` | VARCHAR(255) | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `modified_date` / `created_by` / `modified_by` / `version` | (audit fields) | |

**Schema managed by:** JPA auto-DDL.

---

## 3. API Surface Map

### 3.1 API Gateway Routes

All client-facing traffic enters through the API Gateway (`:8082`). The gateway prefixes route to downstream services:

| Gateway Path Prefix | Target Service |
|---------------------|---------------|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |
| `/banking-core/**` | core-banking-service |

**Security:** All endpoints require a valid JWT except:
- `POST /user/api/v1/bank-users/register` (user registration)
- `/actuator/**` (all actuator endpoints across all services)

### 3.2 Core Banking Service (`:8092`)

#### Account Controller (`/api/v1/account`)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| `GET` | `/bank-account/{account_number}` | Get bank account by number | Path: `account_number` (String) | `BankAccount { id, number, type, status, availableBalance, actualBalance }` |
| `GET` | `/util-account/{account_name}` | Get utility account by provider name | Path: `account_name` (String) | `UtilityAccount { id, number, providerName }` |

#### Transaction Controller (`/api/v1/transaction`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/fund-transfer` | Process fund transfer | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `POST` | `/util-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |

#### User Controller (`/api/v1/user`)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| `GET` | `/{identification}` | Get user by identification number | Path: `identification` (String) | `User { id, firstName, lastName, email, identificationNumber, bankAccounts[] }` |
| `GET` | `/` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |

### 3.3 Internet Banking User Service (`:8083`)

#### User Controller (`/api/v1/bank-users`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/register` | Register new banking user | `{ email, identification, password }` | `User { id, email, identification, authId, status }` |
| `GET` | `/` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |
| `GET` | `/{id}` | Get user by ID | Path: `id` (Long) | `User` |
| `PATCH` | `/{id}` | Update user status | Path: `id` (Long), Body: `{ status }` | `User` |

### 3.4 Internet Banking Fund Transfer Service (`:8084`)

#### Fund Transfer Controller (`/api/v1/transfer`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/` | Initiate fund transfer | `{ fromAccount, toAccount, amount, authID }` | `{ message, transactionId }` |
| `GET` | `/` | List fund transfers (paginated) | Query: `page`, `size`, `sort` | `List<FundTransfer>` |

### 3.5 Internet Banking Utility Payment Service (`:8085`)

#### Utility Payment Controller (`/api/v1/utility-payment`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/` | List utility payments (paginated) | Query: `page`, `size`, `sort` | `List<UtilityPayment>` |

### 3.6 Actuator Endpoints (All Services)

All services expose Spring Boot Actuator at `/actuator/**` for health checks and monitoring.

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow (`UserService.createUser`)

1. Check if email is already registered in Keycloak. If so, throw `UserAlreadyRegisteredException`.
2. Call Core Banking Service via Feign to verify the user exists by identification number.
3. Validate that the email matches the core banking record. If not, throw `InvalidEmailException`.
4. Create user in Keycloak (disabled, email not verified) with provided password.
5. If Keycloak returns HTTP 201, read back the Keycloak user to get the `authId`.
6. Save user entity in local database with `PENDING` status.
7. If core banking user not found, throw `InvalidBankingUserException`.

### 4.2 User Approval Flow (`UserService.updateUser`)

1. Find user entity by ID.
2. If status change is to `APPROVED`:
   - Read Keycloak user representation.
   - Set `enabled=true` and `emailVerified=true` in Keycloak.
   - Update Keycloak user.
3. Update local user entity status.

### 4.3 Fund Transfer Flow (`FundTransferService.fundTransfer`)

1. Create `FundTransferEntity` with `PENDING` status, save to local DB.
2. Call Core Banking Service via Feign (`/api/v1/transaction/fund-transfer`).
3. Core Banking Service (`TransactionService.fundTransfer`):
   a. Look up source and destination `BankAccount` by account number.
   b. **Validate balance**: source account `actualBalance` must be >= transfer amount and > 0.
   c. Debit source account (`actualBalance -= amount`, `availableBalance = actualBalance - amount`).
   d. Record debit transaction.
   e. Credit destination account (`actualBalance += amount`, `availableBalance = actualBalance + amount`).
   f. Record credit transaction.
   g. Return transaction ID.
4. Update local `FundTransferEntity` with transaction reference and `SUCCESS` status.

### 4.4 Utility Payment Flow (`UtilityPaymentService.utilPayment`)

1. Create `UtilityPaymentEntity` with `PROCESSING` status, save to local DB.
2. Call Core Banking Service via Feign (`/api/v1/transaction/util-payment`).
3. Core Banking Service (`TransactionService.utilPayment`):
   a. Look up source `BankAccount` by account number.
   b. **Validate balance**: same as fund transfer.
   c. Look up `UtilityAccount` by provider ID.
   d. Debit source account.
   e. Record transaction with `UTILITY_PAYMENT` type.
   f. Return transaction ID.
4. Update local entity with transaction ID and `SUCCESS` status.

### 4.5 Balance Validation Rule

```java
if (actualBalance < 0 || actualBalance < transferAmount) {
    throw InsufficientFundsException
}
```

### 4.6 Audit Trail

User Service, Fund Transfer Service, and Utility Payment Service entities extend `AuditAware`, which automatically tracks:
- `createdDate`, `createdBy`, `modifiedDate`, `modifiedBy` (via Spring Data JPA auditing)
- `version` (optimistic locking via `@Version`)

The `createdBy` / `modifiedBy` is populated from the `X-Auth-Id` header via `AppAuthUserFilter` -> `ApiRequestContextHolder` -> `AuditorAwareConfig`.

---

## 5. Integration Points

### 5.1 Keycloak (Identity Provider)

- **Version:** 23.0.7
- **Connection:** User Service uses `keycloak-admin-client:24.0.4` to manage users.
- **Realm:** Imported from `docker-compose/keycloak/realm-export.json` at startup.
- **Operations:** Create user, update user (enable/verify), search by email, read by authId.
- **API Gateway:** Validates JWTs using Keycloak's JWK Set URI (`spring.security.oauth2.resourceserver.jwt.jwk-set-uri`).
- **Test Credentials:** `ib_admin@javatodev.com` / `5V7huE3G86uB`

### 5.2 RabbitMQ

- **Status:** Referenced in README as part of the architecture (notification service) but **not implemented** in the current codebase.
- **Planned use:** Fund Transfer and Utility Payment services would push notification messages to RabbitMQ for consumption by a Notification Service.

### 5.3 Zipkin (Distributed Tracing)

- **Version:** 3
- **Port:** 9411
- **Integration:** All services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` dependencies. Traces are automatically exported.

### 5.4 Database Connections

- **MySQL 8.4.0** on port 3306
- Four databases created at init: `banking_core_service`, `banking_core_fund_transfer_service`, `banking_core_user_service`, `banking_core_utility_payment_service`
- **Credentials:** User `javatodev_development` with broad privileges
- **Core Banking Service** uses **Flyway** for schema migrations; other services use **JPA auto-DDL**

### 5.5 Spring Cloud Config Server

- Fetches configuration from: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- Branch: `main`, path: `configuration/`
- All services connect to Config Server via bootstrap configuration
- Profile-specific configs: `bootstrap.yml` (local), `bootstrap-dev.yml` (dev), `bootstrap-docker.yml` (Docker)

### 5.6 Service-to-Service Feign Clients

| Source Service | Target Service | Feign Interface | Key Methods |
|---------------|---------------|-----------------|-------------|
| User Service | Core Banking | `BankingCoreRestClient` | `readUser(identification)` |
| Fund Transfer Service | Core Banking | `BankingCoreFeignClient` | `readAccount(accountNumber)`, `fundTransfer(request)` |
| Utility Payment Service | Core Banking | `BankingCoreRestClient` | `readAccount(accountNumber)`, `utilityPayment(request)` |

---

## 6. Build and Deployment Pipeline

### 6.1 Build System

- **Build Tool:** Gradle (per-service `build.gradle`, no multi-project root build)
- **Java Version:** 21 (Eclipse Temurin 21.0.2)
- **Spring Boot:** 3.2.4 with Spring Cloud 2023.0.0
- **Each service** is an independent Gradle project with its own `settings.gradle`, `build.gradle`, and Gradle wrapper.
- **Git Properties Plugin** (`com.gorylenko.gradle-git-properties:2.4.2`) generates `git.properties` for build info.

### 6.2 Docker

- **Base Image:** `eclipse-temurin:21.0.2_13-jre-alpine`
- **Pattern:** Each service has a `Dockerfile` that copies the built JAR as `app.jar` and includes a `wait-for-it.sh` script for dependency ordering.
- **Entrypoint:** `java -jar -Dspring.profiles.active=docker /app.jar`
- **Supporting containers:** MySQL (custom Dockerfile with init SQL), Keycloak (with realm import), PostgreSQL (for Keycloak), Zipkin.

### 6.3 Docker Compose

Two compose files in `docker-compose/`:

1. **`docker-compose.yml`** - Full stack (all services + infrastructure)
2. **`docker-compose-support-apps.yml`** - Infrastructure only (Zipkin, Keycloak, PostgreSQL, MySQL, Config Server, Service Registry)

All containers share a custom bridge network (`javatodev_ib_network`, subnet `172.25.0.0/16`) with static IP assignments.

**Startup order** enforced via `wait-for-it.sh`:
1. Service Registry + Config Server (no dependencies)
2. MySQL
3. Business services (wait for Registry, Config Server, and MySQL)

### 6.4 Postman Collection

A Postman collection is provided at `postman_collection/JAVA_TO_DEV_MICROSERVICES.postman_collection.json` with an environment file for the `LOCAL_DOCKER_SETUP` configuration.
