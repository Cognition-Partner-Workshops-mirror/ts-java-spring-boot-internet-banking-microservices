# Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Summary

This is a Java 21 / Spring Boot 3.2.4 internet banking application composed of **6 microservices** that communicate via synchronous REST (OpenFeign) and are orchestrated through Spring Cloud infrastructure components.

### 1.2 Services

| Service | Port | Role |
|---------|------|------|
| **internet-banking-config-server** | 8090 | Centralized configuration (Spring Cloud Config, backed by Git) |
| **internet-banking-service-registry** | 8081 | Service discovery (Netflix Eureka Server) |
| **internet-banking-api-gateway** | 8082 | API Gateway (Spring Cloud Gateway) with OAuth2/Keycloak security |
| **internet-banking-user-service** | 8083 | User registration, approval, and profile management |
| **internet-banking-fund-transfer-service** | 8084 | Fund transfer processing between bank accounts |
| **internet-banking-utility-payment-service** | 8085 | Utility bill payment processing |
| **core-banking-service** | 8092 | Core banking engine: accounts, users, transactions, balances |

> **Note:** A **Notification Service** is mentioned in the README as planned (consuming RabbitMQ messages) but has **not been implemented**.

### 1.3 Communication Patterns

```
                    ┌──────────────────┐
                    │   Keycloak       │
                    │ (Auth Provider)  │
                    └───────┬──────────┘
                            │ JWT validation
                    ┌───────▼──────────┐
   Clients ────────►│  API Gateway     │
                    │  (port 8082)     │
                    └──┬────┬────┬─────┘
        ┌──────────────┘    │    └──────────────┐
        ▼                   ▼                   ▼
┌───────────────┐  ┌────────────────┐  ┌─────────────────┐
│ User Service  │  │ Fund Transfer  │  │ Utility Payment │
│ (port 8083)   │  │ (port 8084)    │  │ (port 8085)     │
└───────┬───────┘  └───────┬────────┘  └────────┬────────┘
        │                  │                     │
        │     OpenFeign    │      OpenFeign      │
        └──────────┐       │       ┌─────────────┘
                   ▼       ▼       ▼
              ┌──────────────────────┐
              │  Core Banking Service│
              │  (port 8092)         │
              └──────────────────────┘
```

**Synchronous Communication:**
- All inter-service calls use **Spring Cloud OpenFeign** via Eureka service discovery (service names, not hardcoded URLs).
- User Service → Core Banking Service (user lookup by identification)
- Fund Transfer Service → Core Banking Service (fund transfer execution)
- Utility Payment Service → Core Banking Service (utility payment execution)

**No Asynchronous Communication Implemented:**
- RabbitMQ is listed in the tech stack but **no RabbitMQ dependency or messaging code exists** in any service. The planned Notification Service is not yet developed.

### 1.4 Infrastructure Components

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Service Registry | Netflix Eureka Server | Service discovery; all services register here |
| Config Server | Spring Cloud Config | Centralized config from [Git repo](https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git) |
| API Gateway | Spring Cloud Gateway | Single entry point, route proxying, JWT enforcement |
| Identity Provider | Keycloak 23.0.7 (PostgreSQL-backed) | OAuth2/OIDC authentication, user realm management |
| Distributed Tracing | Zipkin 3 + Micrometer Tracing (Brave) | Request tracing across services |
| Database | MySQL 8.4.0 | Primary data store for all business services |
| Database (Keycloak) | PostgreSQL 15 | Keycloak identity store |
| Schema Migration | Flyway 10.12.0 | Database migrations (core-banking-service only) |

### 1.5 Gateway Route Prefixes

Requests are routed through the API Gateway using service-name-based prefixes:

| Gateway Path Prefix | Target Service |
|---------------------|----------------|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |
| `/banking-core/**` | core-banking-service |

The gateway strips the prefix and forwards to the downstream service. The actual routing rules are defined in the external configuration repository.

---

## 2. Data Model Documentation

### 2.1 Core Banking Service Database (`banking_core_service`)

Managed by Flyway migrations. This is the central data store.

#### `banking_core_user`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | User ID |
| `email` | VARCHAR(255) | Email address |
| `first_name` | VARCHAR(255) | First name |
| `last_name` | VARCHAR(255) | Last name |
| `identification_number` | VARCHAR(255) | National ID / identification number |

#### `banking_core_account`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Account ID |
| `number` | VARCHAR(255) | Account number (e.g., `100015003000`) |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `actual_balance` | DECIMAL(19,2) | Ledger balance |
| `available_balance` | DECIMAL(19,2) | Available balance |
| `user_id` | BIGINT (FK → `banking_core_user.id`) | Account owner |

#### `banking_core_transaction`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Transaction ID |
| `amount` | DECIMAL(19,2) | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Reference (target account number) |
| `transaction_id` | VARCHAR(50) | UUID transaction identifier |
| `account_id` | BIGINT (FK → `banking_core_account.id`) | Associated account |

#### `banking_core_utility_account`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Utility account ID |
| `number` | VARCHAR(255) | Provider account number |
| `provider_name` | VARCHAR(255) | Utility provider name (e.g., VODAFONE, AIRTEL) |

**Relationships:**
```
banking_core_user 1──*── banking_core_account 1──1── banking_core_transaction
                                                     (via account_id FK)
banking_core_utility_account (standalone, no FK relationships)
```

### 2.2 User Service Database (`banking_core_user_service`)

JPA/Hibernate auto-DDL (no Flyway).

#### `user`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Internal user ID |
| `auth_id` | VARCHAR(255) | Keycloak user ID (UUID) |
| `identification` | VARCHAR(255) | National ID (links to core banking user) |
| `status` | VARCHAR(255) | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | TIMESTAMP | Audit: creation timestamp |
| `created_by` | VARCHAR(255) | Audit: creator |
| `modified_date` | TIMESTAMP | Audit: last modification timestamp |
| `modified_by` | VARCHAR(255) | Audit: last modifier |
| `version` | BIGINT | Optimistic locking version |

### 2.3 Fund Transfer Service Database (`banking_core_fund_transfer_service`)

JPA/Hibernate auto-DDL (no Flyway).

#### `fund_transfer`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Transfer ID |
| `transaction_reference` | VARCHAR(255) | Core banking transaction ID |
| `from_account` | VARCHAR(255) | Source account number |
| `to_account` | VARCHAR(255) | Target account number |
| `amount` | DECIMAL(19,2) | Transfer amount |
| `status` | VARCHAR(255) | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `modified_date` | TIMESTAMP | Audit timestamps |
| `version` | BIGINT | Optimistic locking version |

### 2.4 Utility Payment Service Database (`banking_core_utility_payment_service`)

JPA/Hibernate auto-DDL (no Flyway).

#### `utility_payment`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Payment ID |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL(19,2) | Payment amount |
| `reference_number` | VARCHAR(255) | Customer reference number |
| `account` | VARCHAR(255) | Paying bank account number |
| `transaction_id` | VARCHAR(255) | Core banking transaction ID |
| `status` | VARCHAR(255) | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `modified_date` | TIMESTAMP | Audit timestamps |
| `version` | BIGINT | Optimistic locking version |

---

## 3. API Surface Map

### 3.1 Core Banking Service (port 8092)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | - | `BankAccount` (id, number, type, status, availableBalance, actualBalance) |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | - | `UtilityAccount` (id, number, providerName) |
| `POST` | `/api/v1/transaction/fund-transfer` | Execute fund transfer | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `POST` | `/api/v1/transaction/util-payment` | Execute utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/user/{identification}` | Get user by identification number | - | `User` (id, firstName, lastName, email, identificationNumber, bankAccounts[]) |
| `GET` | `/api/v1/user` | List users (paginated) | Pageable query params | `List<User>` |

### 3.2 User Service (port 8083)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/bank-users/register` | Register new internet banking user | `{ email, identification, password }` | `User` (id, email, identification, authId, status) |
| `GET` | `/api/v1/bank-users` | List all users (paginated) | Pageable query params | `List<User>` |
| `GET` | `/api/v1/bank-users/{userId}` | Get user by ID | - | `User` |
| `PUT` | `/api/v1/bank-users/{userId}` | Update user (status change / approval) | `{ status }` | `User` |

### 3.3 Fund Transfer Service (port 8084)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `{ fromAccount, toAccount, amount, authID }` | `{ message, transactionId }` |
| `GET` | `/api/v1/transfer` | List all fund transfers (paginated) | Pageable query params | `List<FundTransfer>` |

### 3.4 Utility Payment Service (port 8085)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/utility-payment` | List all utility payments (paginated) | Pageable query params | `List<UtilityPayment>` |

### 3.5 Infrastructure Endpoints

| Service | Endpoint | Description |
|---------|----------|-------------|
| All services | `/actuator/**` | Spring Boot Actuator (health, info, metrics) |
| Service Registry | `http://localhost:8081/eureka` | Eureka dashboard |
| Config Server | `http://localhost:8090/{service}/{profile}` | Configuration retrieval |
| Core Banking, Fund Transfer, Utility Payment | `/swagger-ui.html` | OpenAPI/Swagger UI (springdoc) |
| Zipkin | `http://localhost:9411` | Distributed tracing UI |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

**Location:** `internet-banking-user-service` → `UserService.createUser()`

1. Check if email already exists in Keycloak → throw `UserAlreadyRegisteredException` if duplicate
2. Look up user in core banking service by identification number (Feign call)
3. Validate email matches core banking record → throw `InvalidEmailException` if mismatch
4. Create user in Keycloak with provided password (disabled, email unverified)
5. Retrieve Keycloak-assigned `authId`
6. Save user to local database with `PENDING` status
7. If core banking user not found → throw `InvalidBankingUserException`

### 4.2 User Approval Flow

**Location:** `internet-banking-user-service` → `UserService.updateUser()`

1. Look up user by ID
2. If new status is `APPROVED`:
   - Read user from Keycloak by `authId`
   - Set `enabled=true` and `emailVerified=true` in Keycloak
3. Update status in local database

### 4.3 Fund Transfer Flow

**Location:** `internet-banking-fund-transfer-service` → `FundTransferService.fundTransfer()`

1. Save transfer record locally with `PENDING` status
2. Call Core Banking Service via Feign to execute the transfer
3. Update local record with transaction reference and `SUCCESS` status
4. Return success response

**Core Banking Execution** (`core-banking-service` → `TransactionService.fundTransfer()`):

1. Read both source and destination bank accounts
2. **Balance validation:** Check `fromAccount.actualBalance >= amount` → throw `InsufficientFundsException` if insufficient
3. Call `internalFundTransfer()`:
   - Debit source account (subtract from `actualBalance` and `availableBalance`)
   - Record debit transaction
   - Credit destination account (add to `actualBalance` and `availableBalance`)
   - Record credit transaction
4. Return transaction ID and success message

### 4.4 Utility Payment Flow

**Location:** `internet-banking-utility-payment-service` → `UtilityPaymentService.utilPayment()`

1. Save payment record locally with `PROCESSING` status
2. Call Core Banking Service via Feign to execute the payment
3. Update local record with transaction ID and `SUCCESS` status
4. Return success response

**Core Banking Execution** (`core-banking-service` → `TransactionService.utilPayment()`):

1. Read paying bank account and utility account
2. **Balance validation:** Check `account.actualBalance >= amount` → throw `InsufficientFundsException`
3. Debit the paying account
4. Record the transaction with type `UTILITY_PAYMENT`
5. Return transaction ID and success message

### 4.5 Business Rules Summary

| Rule | Location | Description |
|------|----------|-------------|
| Insufficient funds check | `TransactionService` | Prevents transfers/payments when `actualBalance < amount` |
| Duplicate email prevention | `UserService.createUser()` | Checks Keycloak before registration |
| Email-identification match | `UserService.createUser()` | Email must match core banking records |
| User approval activates Keycloak | `UserService.updateUser()` | APPROVED status enables Keycloak login |
| Optimistic locking | All entity `AuditAware` bases | `@Version` field prevents concurrent modification |

---

## 5. Integration Points

### 5.1 Keycloak (Identity Provider)

- **Version:** 23.0.7
- **Connection:** `internet-banking-user-service` uses `keycloak-admin-client:24.0.4` to manage users
- **Configuration:** `KeycloakProperties` bean loaded from external config (server URL, realm, client-id, client-secret)
- **Manager:** `KeycloakManager` creates Keycloak admin client instances
- **Operations:** Create user, update user, search by email, read by auth ID
- **API Gateway:** Validates JWTs via `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` (Keycloak JWKS endpoint)
- **Realm:** Imported via `realm-export.json` volume mount at startup

### 5.2 RabbitMQ

- **Status:** Listed in tech stack but **NOT implemented**
- No RabbitMQ dependencies in any `build.gradle`
- No messaging configuration or listener code exists
- The planned Notification Service (consumer) is not developed

### 5.3 Zipkin (Distributed Tracing)

- **Version:** Zipkin 3 (Docker image `openzipkin/zipkin:3`)
- **Integration:** All services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave`
- **Port:** 9411
- **Trace propagation:** Automatic via Brave/Micrometer instrumentation, includes Feign calls

### 5.4 Database Connections

| Service | Database | Schema | DDL Strategy |
|---------|----------|--------|-------------|
| core-banking-service | MySQL 8.4.0 | `banking_core_service` | Flyway migrations |
| internet-banking-user-service | MySQL 8.4.0 | `banking_core_user_service` | Hibernate auto-DDL |
| internet-banking-fund-transfer-service | MySQL 8.4.0 | `banking_core_fund_transfer_service` | Hibernate auto-DDL |
| internet-banking-utility-payment-service | MySQL 8.4.0 | `banking_core_utility_payment_service` | Hibernate auto-DDL |

- **Credentials:** `javatodev_development` / `oPItyPticIAt` (created via `privileges.sql` init script)
- **Root password:** Hardcoded in `docker-compose.yml`
- **Connection details** are stored in the external Git config repository

### 5.5 Spring Cloud Config Server

- **Git repository:** `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch:** `main`
- **Search path:** `configuration`
- All services bootstrap from config server at `http://localhost:8090` (overridden to Docker hostname in Docker profile)

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build tool:** Gradle (each service is an independent Gradle project with its own `build.gradle` and `settings.gradle`)
- **No multi-project build:** Services are not linked via a root `settings.gradle`
- **Plugin:** `com.gorylenko.gradle-git-properties:2.4.2` generates git info for actuator `/info` endpoint
- **Test framework:** JUnit 5 (JUnit Platform)

### 6.2 Docker

Each service has a `Dockerfile` following the same pattern:

```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/{service}-0.0.1-SNAPSHOT.jar app.jar
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh && apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

- Base image: Eclipse Temurin JRE 21 on Alpine
- `wait-for-it.sh` used for service startup ordering
- Docker profile activates Docker-specific configuration (hostnames, ports)

### 6.3 Docker Compose

Two compose files in `docker-compose/`:

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Full stack: all 6 services + MySQL + Keycloak + PostgreSQL + Zipkin |
| `docker-compose-support-apps.yml` | Support infrastructure only: MySQL, Keycloak, PostgreSQL, Zipkin, Config Server, Service Registry |

**Startup ordering** enforced via `wait-for-it.sh` entrypoints:
1. Service Registry + Config Server (no dependencies)
2. MySQL (no dependencies)
3. All business services wait for: Service Registry → Config Server → MySQL

**Network:** Custom bridge network `javatodev_ib_network` with subnet `172.25.0.0/16` and static IPs assigned per container.

### 6.4 CI/CD

- **GitHub Actions:** Previously configured but workflows have been removed (most recent commit: "Remove GH Actions workflows (PAT scope compatibility)")
- **No current CI/CD pipeline** is active in the repository
- No Kubernetes manifests are present despite Kubernetes being listed in the tech stack

### 6.5 Test Configuration

| Service | Test Database | Flyway in Tests |
|---------|---------------|-----------------|
| core-banking-service | H2 in-memory | Disabled (`flyway.enabled: false`) |
| Other services | H2 in-memory (via test dependency) | N/A |

Test data is managed through seed SQL migrations (V1.0.20210427174721) that populate 4 users, 14 bank accounts, and 6 utility accounts.
