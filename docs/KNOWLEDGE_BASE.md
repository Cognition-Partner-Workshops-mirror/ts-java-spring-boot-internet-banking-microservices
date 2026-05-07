# Application Knowledge Base

> Comprehensive technical reference for the Internet Banking Microservices platform.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Data Model Documentation](#2-data-model-documentation)
3. [API Surface Map](#3-api-surface-map)
4. [Key Business Logic Inventory](#4-key-business-logic-inventory)
5. [Integration Points](#5-integration-points)
6. [Build and Deployment Pipeline](#6-build-and-deployment-pipeline)

---

## 1. Architecture Overview

### 1.1 System Summary

A microservices-based internet banking platform built with **Java 21** and **Spring Boot 3.2.4**. The system handles user registration, bank account management, fund transfers between accounts, and utility bill payments. It consists of **7 independently deployable services** orchestrated via Docker Compose on a fixed-IP bridge network.

### 1.2 Services

| Service | Port | Purpose | Key Annotations |
|---------|------|---------|-----------------|
| **Service Registry** | 8081 | Netflix Eureka server for service discovery | `@EnableEurekaServer` |
| **Config Server** | 8090 | Centralized Git-backed configuration | `@EnableConfigServer` |
| **API Gateway** | 8082 | Routing, OAuth2/JWT security, identity propagation | `@SpringBootApplication`, `@EnableWebFluxSecurity` |
| **Core Banking Service** | 8092 | System of record: accounts, users, transactions, ledger | `@SpringBootApplication` |
| **User Service** | 8083 | Internet banking registration, Keycloak integration | `@EnableFeignClients` |
| **Fund Transfer Service** | 8084 | Account-to-account fund transfers | `@EnableFeignClients` |
| **Utility Payment Service** | 8085 | Third-party bill payments | `@EnableFeignClients` |

### 1.3 Communication Patterns

```
┌──────────┐    HTTPS/JWT    ┌────────────────────────────────────┐
│  Client   │───────────────>│       API Gateway (:8082)          │
└──────────┘                 │  OAuth2 Resource Server + Routing  │
                             └───┬──────────┬──────────┬─────────┘
                                 │          │          │
                    ┌────────────┘          │          └────────────┐
                    ▼                       ▼                       ▼
           ┌────────────────┐   ┌───────────────────┐   ┌──────────────────┐
           │  User Service   │   │  Fund Transfer     │   │  Utility Payment  │
           │  :8083          │   │  Service :8084      │   │  Service :8085    │
           └───────┬─────────┘   └──────────┬──────────┘   └────────┬──────────┘
                   │  OpenFeign              │  OpenFeign            │  OpenFeign
                   └─────────────┬───────────┘──────────────────────┘
                                 ▼
                      ┌──────────────────────┐
                      │  Core Banking Service │
                      │  :8092 (Ledger)       │
                      └──────────┬────────────┘
                                 │ JPA/Flyway
                      ┌──────────▼────────────┐
                      │  MySQL 8.4             │
                      │  4 databases           │
                      └────────────────────────┘
```

**Communication Summary:**

| Pattern | Technology | Usage |
|---------|-----------|-------|
| Client → Gateway | HTTP + JWT Bearer | All external API traffic |
| Gateway → Services | HTTP (Eureka-resolved) | Route proxying with `X-Auth-Id` header injection |
| Service → Core Banking | Spring Cloud OpenFeign | User validation, fund transfers, utility payments |
| Services → Config Server | HTTP (bootstrap phase) | Fetch externalized configuration on startup |
| Services → Eureka | HTTP | Registration and discovery |
| Services → Zipkin | HTTP | Distributed trace span reporting |

### 1.4 Infrastructure Components

| Component | Technology | Version | Role |
|-----------|-----------|---------|------|
| Service Discovery | Netflix Eureka | Spring Cloud 2023.0.0 | Dynamic service location |
| Configuration | Spring Cloud Config | Spring Cloud 2023.0.0 | Git-backed centralized config |
| API Gateway | Spring Cloud Gateway | Spring Cloud 2023.0.0 | Reactive routing + security |
| Identity | Keycloak | 23.0.7 | OAuth2/OIDC provider |
| App Database | MySQL | 8.4.0 | Account, transaction, user data |
| IAM Database | PostgreSQL | 15 | Keycloak persistence |
| Tracing | Zipkin | 3 | Distributed request tracking |
| Messaging | RabbitMQ | Referenced in README | Not yet integrated in codebase |

### 1.5 Network Topology (Docker Compose)

All services run on bridge network `javatodev_ib_network` (`172.25.0.0/16`):

| Service | Container Name | IP | Port |
|---------|---------------|-----|------|
| Core Banking | `core-banking-service` | 172.25.0.2 | 8092 |
| Utility Payment | `internet-banking-utility-payment-service` | 172.25.0.3 | 8085 |
| Fund Transfer | `internet-banking-fund-transfer-service` | 172.25.0.4 | 8084 |
| User Service | `internet-banking-user-service` | 172.25.0.5 | 8083 |
| API Gateway | `internet-banking-api-gateway` | 172.25.0.6 | 8082 |
| Service Registry | `internet-banking-service-registry` | 172.25.0.7 | 8081 |
| Config Server | `internet-banking-config-server` | 172.25.0.8 | 8090 |
| MySQL | `mysql_javatodev_app` | 172.25.0.9 | 3306 |
| PostgreSQL | `keycloak_postgre_db` | 172.25.0.10 | 5432 |
| Keycloak | `keycloak_web` | 172.25.0.11 | 8080 |
| Zipkin | `openzipkin_server` | 172.25.0.12 | 9411 |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (`banking_core_service` database)

Schema managed by **Flyway** migrations.

#### `banking_core_user`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal user ID |
| `first_name` | VARCHAR(255) | | User's first name |
| `last_name` | VARCHAR(255) | | User's last name |
| `email` | VARCHAR(255) | | Email address |
| `identification_number` | VARCHAR(255) | | National ID (NIC) |

**JPA Entity:** `UserEntity` with `@OneToMany` → `BankAccountEntity`

#### `banking_core_account`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Account ID |
| `number` | VARCHAR(255) | | Account number (e.g., `100015003000`) |
| `type` | VARCHAR(255) | | `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | | `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `actual_balance` | DECIMAL(19,2) | | Ledger balance |
| `available_balance` | DECIMAL(19,2) | | Withdrawable balance |
| `user_id` | BIGINT | FK → `banking_core_user.id` | Account owner |

**JPA Entity:** `BankAccountEntity` with `@ManyToOne` → `UserEntity`

#### `banking_core_transaction`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Transaction ID |
| `amount` | DECIMAL(19,2) | | Signed amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | NOT NULL | `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | NOT NULL | Destination account or provider reference |
| `transaction_id` | VARCHAR(50) | NOT NULL | UUID grouping related entries |
| `account_id` | BIGINT | FK → `banking_core_account.id` | Affected account |

**JPA Entity:** `TransactionEntity` with `@OneToOne` → `BankAccountEntity`

#### `banking_core_utility_account`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Provider ID |
| `number` | VARCHAR(255) | | Provider account number |
| `provider_name` | VARCHAR(255) | | Provider name (e.g., VODAFONE) |

**JPA Entity:** `UtilityAccountEntity`

#### Relationships

```
banking_core_user (1) ──────< (N) banking_core_account
banking_core_account (1) ────── (1) banking_core_transaction
banking_core_utility_account (standalone)
```

### 2.2 User Service (`banking_core_user_service` database)

Schema managed by **Hibernate auto-DDL** (no Flyway).

#### `user`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK) | Internal ID |
| `auth_id` | VARCHAR | Keycloak user UUID |
| `identification` | VARCHAR | National ID (NIC) |
| `status` | ENUM | `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_by` | VARCHAR | Audit: who created |
| `created_on` | TIMESTAMP | Audit: when created |
| `updated_by` | VARCHAR | Audit: who last modified |
| `updated_on` | TIMESTAMP | Audit: when last modified |

**JPA Entity:** `UserEntity extends AuditAware`

### 2.3 Fund Transfer Service (`banking_core_fund_transfer_service` database)

Schema managed by **Hibernate auto-DDL**.

#### `fund_transfer`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK) | Internal ID |
| `transaction_reference` | VARCHAR | Core Banking transaction UUID |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `status` | ENUM | `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_by` | VARCHAR | Audit field |
| `created_on` | TIMESTAMP | Audit field |
| `updated_by` | VARCHAR | Audit field |
| `updated_on` | TIMESTAMP | Audit field |

**JPA Entity:** `FundTransferEntity extends AuditAware`

### 2.4 Utility Payment Service (`banking_core_utility_payment_service` database)

Schema managed by **Hibernate auto-DDL**.

#### `utility_payment`

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK) | Internal ID |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Customer reference (e.g., phone number) |
| `account` | VARCHAR | Source account number |
| `transaction_id` | VARCHAR | Core Banking transaction UUID |
| `status` | ENUM | `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_by` | VARCHAR | Audit field |
| `created_on` | TIMESTAMP | Audit field |
| `updated_by` | VARCHAR | Audit field |
| `updated_on` | TIMESTAMP | Audit field |

**JPA Entity:** `UtilityPaymentEntity extends AuditAware`

---

## 3. API Surface Map

All external traffic flows through the **API Gateway** (`http://localhost:8082`).

### 3.1 Core Banking Service

**Gateway prefix:** `/core`

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `GET` | `/api/v1/user/{identification}` | Read user by NIC | - | `User { firstName, lastName, email, identificationNumber, bankAccounts[] }` |
| `GET` | `/api/v1/user` | List all users (paginated) | Query: `page`, `size`, `sort` | `User[]` |
| `GET` | `/api/v1/account/bank-account/{account_number}` | Read bank account | - | `BankAccount { number, type, status, actualBalance, availableBalance }` |
| `GET` | `/api/v1/account/util-account/{provider_name}` | Read utility account | - | `UtilityAccount { id, number, providerName }` |
| `POST` | `/api/v1/transaction/fund-transfer` | Execute fund transfer | `{ fromAccount, toAccount, amount }` | `{ transactionId, message }` |
| `POST` | `/api/v1/transaction/util-payment` | Execute utility payment | `{ providerId, amount, referenceNumber, account }` | `{ transactionId, message }` |

### 3.2 User Service

**Gateway prefix:** `/user`

| Method | Endpoint | Auth | Description | Request Body | Response |
|--------|----------|------|-------------|-------------|----------|
| `POST` | `/api/v1/bank-users/register` | **No** | Register new user | `{ email, password, identification }` | `User { id, email, identification, status, authId }` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Yes | Update user status | `{ status }` | `User` |
| `GET` | `/api/v1/bank-users` | Yes | List users (paginated) | Query: `page`, `size` | `User[]` |
| `GET` | `/api/v1/bank-users/{id}` | Yes | Read user by DB ID | - | `User` |

### 3.3 Fund Transfer Service

**Gateway prefix:** `/fund-transfer`

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `{ fromAccount, toAccount, amount }` | `{ transactionId, message }` |
| `GET` | `/api/v1/transfer` | List all transfers (paginated) | Query: `page`, `size` | `FundTransfer[]` |

### 3.4 Utility Payment Service

**Gateway prefix:** `/payment`

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ transactionId, message }` |
| `GET` | `/api/v1/utility-payment` | List all payments (paginated) | Query: `page`, `size` | `UtilityPayment[]` |

### 3.5 Health Check Endpoints

All actuator health endpoints are **public** (no auth required):

| Service | Endpoint (via Gateway) |
|---------|------------------------|
| User Service | `GET /user/actuator/health` |
| Fund Transfer | `GET /fund-transfer/actuator/health` |
| Core Banking | `GET /banking-core/actuator/health` |
| Utility Payment | `GET /utility-payment/actuator/health` |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration (`UserService.createUser`)

**Location:** `internet-banking-user-service/.../service/UserService.java`

```
1. Duplicate check: Query Keycloak for existing user by email
   → If found → throw UserAlreadyRegisteredException

2. Identity validation: Call Core Banking via Feign → GET /api/v1/user/{identification}
   → If not found → throw InvalidBankingUserException

3. Email verification: Compare request email with Core Banking record
   → If mismatch → throw InvalidEmailException

4. Keycloak provisioning:
   - Create UserRepresentation (email, username, firstName, lastName)
   - Set emailVerified=false, enabled=false
   - Set password as credential
   - Call Keycloak Admin API to create user

5. Local persistence:
   - Fetch Keycloak user to get authId (UUID)
   - Save to local DB with status=PENDING
   - Return User DTO
```

### 4.2 User Approval (`UserService.updateUser`)

**Location:** `internet-banking-user-service/.../service/UserService.java`

```
1. Find user by ID in local database
2. If new status is APPROVED:
   - Fetch user from Keycloak by authId
   - Set enabled=true, emailVerified=true
   - Update in Keycloak
3. Update status in local database
```

### 4.3 Fund Transfer (`TransactionService.fundTransfer`)

**Location:** `core-banking-service/.../service/TransactionService.java`

```
1. Read source account (fromAccount) → EntityNotFoundException if missing
2. Read destination account (toAccount) → EntityNotFoundException if missing
3. Validate source account balance:
   - actualBalance must be >= 0
   - actualBalance must be >= transfer amount
   → InsufficientFundsException if fails
4. Execute internalFundTransfer:
   a. Generate UUID transactionId
   b. Debit source: actualBalance -= amount, availableBalance = actualBalance - amount
   c. Save source account
   d. Create debit TransactionEntity (amount.negate())
   e. Credit destination: actualBalance += amount, availableBalance = actualBalance + amount
   f. Save destination account
   g. Create credit TransactionEntity (amount)
5. Return { transactionId, "Transaction successfully completed" }
```

**Important:** The entire operation is `@Transactional`.

### 4.4 Fund Transfer (Orchestration Layer)

**Location:** `internet-banking-fund-transfer-service/.../service/FundTransferService.java`

```
1. Create FundTransferEntity with status=PENDING, save to local DB
2. Call Core Banking via Feign → POST /api/v1/transaction/fund-transfer
3. Update entity: transactionReference = response.transactionId, status=SUCCESS
4. Save updated entity
5. Return { transactionId, "Fund Transfer Successfully Completed" }
```

### 4.5 Utility Payment (`TransactionService.utilPayment`)

**Location:** `core-banking-service/.../service/TransactionService.java`

```
1. Generate UUID transactionId
2. Read source bank account → EntityNotFoundException if missing
3. Validate balance (same rules as fund transfer)
4. Read utility provider account by ID
5. Debit source account: actualBalance -= amount, availableBalance = actualBalance - amount
6. Create TransactionEntity with type=UTILITY_PAYMENT, amount.negate()
7. Return { transactionId, "Utility payment successfully completed" }
```

### 4.6 Utility Payment (Orchestration Layer)

**Location:** `internet-banking-utility-payment-service/.../service/UtilityPaymentService.java`

```
1. Create UtilityPaymentEntity with status=PROCESSING, save to local DB
2. Call Core Banking via Feign → POST /api/v1/transaction/util-payment
3. Update entity: transactionId = response.transactionId, status=SUCCESS
4. Save updated entity
5. Return { transactionId, "Utility Payment Successfully Processed" }
```

### 4.7 Balance Validation Rules

```java
private void validateBalance(BankAccount bankAccount, BigDecimal amount) {
    if (bankAccount.getActualBalance().compareTo(BigDecimal.ZERO) < 0
        || bankAccount.getActualBalance().compareTo(amount) < 0) {
        throw new InsufficientFundsException(
            "Insufficient funds in the account " + bankAccount.getNumber(),
            GlobalErrorCode.INSUFFICIENT_FUNDS);
    }
}
```

Rules:
- Account actual balance must not be negative
- Account actual balance must be >= requested amount
- No minimum balance enforcement
- No daily/transaction limits
- No duplicate transfer detection

---

## 5. Integration Points

### 5.1 Keycloak (Identity & Access Management)

**Version:** 23.0.7
**Realm:** `javatodev-internet-banking`
**Client:** `internet-banking-core-client`

| Integration Point | Service | Method | Purpose |
|-------------------|---------|--------|---------|
| JWT validation | API Gateway | JWK Set URI | Validates Bearer tokens on all secured endpoints |
| User creation | User Service | Keycloak Admin Client | Creates users during registration |
| User update | User Service | Keycloak Admin Client | Enables users during approval |
| User lookup | User Service | Keycloak Admin Client | Searches by email, reads by ID |

**Configuration Properties:**
```yaml
app.config.keycloak:
  server-url: <keycloak-base-url>
  realm: javatodev-internet-banking
  clientId: internet-banking-core-client
  client-secret: <secret>
```

**Auth Flow:**
```
Client → Keycloak (password grant) → JWT → API Gateway (validate) → X-Auth-Id → Services
```

### 5.2 MySQL Database Connections

**Single MySQL 8.4 instance** with 4 separate databases:

| Database | Service | Connection |
|----------|---------|-----------|
| `banking_core_service` | Core Banking | JPA + Flyway |
| `banking_core_user_service` | User Service | JPA + Hibernate auto-DDL |
| `banking_core_fund_transfer_service` | Fund Transfer | JPA + Hibernate auto-DDL |
| `banking_core_utility_payment_service` | Utility Payment | JPA + Hibernate auto-DDL |

**Credentials:** `javatodev_development` / `oPItyPticIAt` (created by `privileges.sql`)

### 5.3 Zipkin (Distributed Tracing)

**Version:** 3
**URL:** `http://localhost:9411`

All application services include:
- `micrometer-tracing-bridge-brave` - Trace context creation and propagation
- `zipkin-reporter-brave` - Span reporting to Zipkin collector
- `feign-micrometer` - Automatic Feign call tracing

Trace context is propagated across HTTP headers between services.

### 5.4 RabbitMQ (Messaging)

**Status:** Referenced in README as part of the tech stack, but **not currently integrated** in the codebase. No RabbitMQ dependencies exist in any `build.gradle` file, and no messaging configuration is present.

### 5.5 OpenFeign Inter-Service Clients

| Service | Feign Client | Target | Endpoints Called |
|---------|-------------|--------|-----------------|
| User Service | `BankingCoreRestClient` | `core-banking-service` | `GET /api/v1/user/{identification}` |
| Fund Transfer | `BankingCoreFeignClient` | `core-banking-service` | `GET /api/v1/account/bank-account/{account_number}`<br>`POST /api/v1/transaction/fund-transfer` |
| Utility Payment | `BankingCoreRestClient` | `core-banking-service` | `GET /api/v1/account/bank-account/{account_number}`<br>`POST /api/v1/transaction/util-payment` |

All Feign clients use **Eureka service discovery** (no hard-coded URLs). Fund Transfer and Utility Payment services use `CustomFeignClientConfiguration` with a `CustomFeignErrorDecoder`.

---

## 6. Build and Deployment Pipeline

### 6.1 Build System

**Tool:** Gradle 8.6 with Spring dependency management

**Multi-project structure** (each service is an independent Gradle project, not a Gradle multi-project build):

```
Each service has its own:
├── build.gradle          # Independent build with Spring Boot plugin 3.2.4
├── gradlew / gradlew.bat # Local Gradle wrapper
├── settings.gradle       # Single-project settings
└── gradle/wrapper/       # Gradle wrapper JARs
```

**Common plugins across services:**
- `java`
- `org.springframework.boot` (3.2.4)
- `io.spring.dependency-management` (1.1.4)
- `com.gorylenko.gradle-git-properties` (2.4.2) - except Service Registry

**Build command:**
```bash
./gradlew clean build    # Per-service (from each service directory)
```

### 6.2 Docker Deployment

**Dockerfiles** (all application services follow the same pattern):

```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
EXPOSE <port>
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh
RUN apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

### 6.3 Docker Compose Files

| File | Purpose | Services |
|------|---------|----------|
| `docker-compose.yml` | Full stack deployment | All 7 services + MySQL + Keycloak + PostgreSQL + Zipkin |
| `docker-compose-support-apps.yml` | Infrastructure only | MySQL, Keycloak, PostgreSQL, Zipkin, Config Server, Service Registry |

### 6.4 Startup Order

Enforced by `wait-for-it.sh` scripts in Docker entrypoints:

```
Phase 1 (no dependencies):     MySQL, PostgreSQL, Zipkin
Phase 2 (depends on Phase 1):  Keycloak (→ PostgreSQL)
Phase 3 (no service deps):     Config Server, Service Registry
Phase 4 (→ Registry + Config): API Gateway
Phase 5 (→ Registry + Config + MySQL): User, Fund Transfer, Utility Payment, Core Banking
```

### 6.5 Configuration Pipeline

```
Git Repository (GitHub)
    │
    ▼
Config Server (clones repo on startup)
    │
    ▼
Service bootstrap.yml → fetches from Config Server
    │
    ▼
Application starts with merged configuration
```

**Config Repository:** `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- Branch: `main`
- Search path: `configuration/`

### 6.6 CI/CD

No CI/CD pipeline (GitHub Actions, Jenkins, etc.) is configured in the repository. Deployment is manual via Docker Compose.
