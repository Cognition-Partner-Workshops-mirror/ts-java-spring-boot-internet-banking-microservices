# Application Knowledge Base

## Table of Contents

- [1. Architecture Overview](#1-architecture-overview)
- [2. Data Model Documentation](#2-data-model-documentation)
- [3. API Surface Map](#3-api-surface-map)
- [4. Key Business Logic Inventory](#4-key-business-logic-inventory)
- [5. Integration Points](#5-integration-points)
- [6. Build and Deployment Pipeline](#6-build-and-deployment-pipeline)

---

## 1. Architecture Overview

### 1.1 System Summary

This is a Java 21 / Spring Boot 3.2.4 internet banking application built with a microservices architecture using Spring Cloud 2023.0.0. It implements common banking operations (fund transfers, utility payments, user management) across six independently deployable services.

### 1.2 Services

| Service | Port | Role |
|---------|------|------|
| **internet-banking-service-registry** | 8081 | Netflix Eureka Server for service discovery |
| **internet-banking-config-server** | 8090 | Spring Cloud Config Server; serves configuration from a remote Git repo |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway; single entry point, JWT-based security via Keycloak |
| **internet-banking-user-service** | 8083 | User registration, approval, and retrieval; integrates with Keycloak Admin API |
| **internet-banking-fund-transfer-service** | 8084 | Initiates and records fund transfers; delegates to core banking via Feign |
| **internet-banking-utility-payment-service** | 8085 | Initiates and records utility payments; delegates to core banking via Feign |
| **core-banking-service** | 8092 | Core banking engine; manages accounts, users, transactions, and balances |

### 1.3 Communication Patterns

| Pattern | Implementation | Usage |
|---------|---------------|-------|
| **Synchronous REST (Feign)** | Spring Cloud OpenFeign | User Service → Core Banking (user lookup); Fund Transfer Service → Core Banking (account read, fund transfer execution); Utility Payment Service → Core Banking (account read, payment execution) |
| **Service Discovery** | Netflix Eureka | All business services register with the Eureka server and discover each other by service name |
| **Centralized Configuration** | Spring Cloud Config Server | Config served from a remote Git repository (`internet-banking-microservices-configurations`); services use `spring-cloud-starter-bootstrap` to fetch config at startup |
| **API Gateway** | Spring Cloud Gateway | Routes external requests to downstream services; injects `X-Auth-Id` header via a global filter |
| **Distributed Tracing** | Micrometer Tracing + Zipkin | All services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave`; traces export to Zipkin at port 9411 |

> **Note:** The README mentions RabbitMQ for notifications, but the Notification Service is marked as "PENDING Development." No RabbitMQ producer or consumer code exists in the current codebase, and no RabbitMQ container is defined in `docker-compose.yml`.

### 1.4 Infrastructure Components

| Component | Technology | Container Name | Docker Network IP |
|-----------|-----------|----------------|-------------------|
| Service Registry | Eureka Server | internet-banking-service-registry | 172.25.0.7 |
| Config Server | Spring Cloud Config | internet-banking-config-server | 172.25.0.8 |
| API Gateway | Spring Cloud Gateway | internet-banking-api-gateway | 172.25.0.6 |
| Identity Provider | Keycloak 23.0.7 | keycloak_web | 172.25.0.11 |
| Keycloak Database | PostgreSQL 15 | keycloak_postgre_db | 172.25.0.10 |
| Application Database | MySQL 8.4.0 | mysql_javatodev_app | 172.25.0.9 |
| Distributed Tracing | Zipkin 3 | openzipkin_server | 172.25.0.12 |

### 1.5 High-Level Architecture Diagram (Text)

```
                        ┌──────────────┐
                        │   Keycloak   │
                        │  (Auth/IdP)  │
                        └──────┬───────┘
                               │ JWT validation
┌─────────┐    HTTP    ┌───────▼────────┐
│  Client  │──────────►│  API Gateway   │
└─────────┘            │   (port 8082)  │
                       └───────┬────────┘
              ┌────────────────┼────────────────┐
              ▼                ▼                 ▼
     ┌────────────────┐ ┌──────────────┐ ┌──────────────────┐
     │  User Service   │ │ Fund Transfer│ │ Utility Payment  │
     │  (port 8083)    │ │  (port 8084) │ │   (port 8085)    │
     └───────┬────────┘ └──────┬───────┘ └────────┬─────────┘
             │   Feign          │  Feign            │  Feign
             ▼                  ▼                   ▼
          ┌──────────────────────────────────────────┐
          │          Core Banking Service             │
          │             (port 8092)                   │
          └──────────────────┬───────────────────────┘
                             │
                     ┌───────▼───────┐     ┌─────────┐
                     │   MySQL 8.4   │     │  Zipkin  │
                     │  (port 3306)  │     │  (9411)  │
                     └───────────────┘     └─────────┘
              ┌──────────────────────────────┐
              │  Eureka Service Registry     │
              │       (port 8081)            │
              └──────────────────────────────┘
              ┌──────────────────────────────┐
              │  Config Server (port 8090)   │
              │  Git-backed configuration    │
              └──────────────────────────────┘
```

---

## 2. Data Model Documentation

### 2.1 Core Banking Service

The core banking service owns the primary banking domain model, backed by MySQL with Flyway migrations.

#### Entities

**`banking_core_user`**

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `first_name` | VARCHAR(255) | User's first name |
| `last_name` | VARCHAR(255) | User's last name |
| `email` | VARCHAR(255) | User's email |
| `identification_number` | VARCHAR(255) | National ID or equivalent |

**`banking_core_account`**

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `number` | VARCHAR(255) | Account number |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `available_balance` | DECIMAL(19,2) | Available balance |
| `actual_balance` | DECIMAL(19,2) | Actual balance |
| `user_id` | BIGINT (FK → `banking_core_user.id`) | Owning user |

**`banking_core_transaction`**

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `amount` | DECIMAL(19,2) | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Reference (destination account or utility ref) |
| `transaction_id` | VARCHAR(50) | UUID-based transaction identifier |
| `account_id` | BIGINT (FK → `banking_core_account.id`) | Associated account |

**`banking_core_utility_account`**

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `number` | VARCHAR(255) | Utility provider account number |
| `provider_name` | VARCHAR(255) | Provider name (e.g., VODAFONE, AIRTEL) |

#### Relationships

```
banking_core_user  1 ──── * banking_core_account
banking_core_account 1 ──── 1 banking_core_transaction
```

- A user has many bank accounts (`@OneToMany`, lazy fetch).
- A transaction references one bank account (`@OneToOne` with `CascadeType.ALL`).

### 2.2 Internet Banking User Service

Maintains a separate `user` table for internet banking registration state.

**`user`** (JPA-managed, `ddl-auto` via Hibernate)

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `auth_id` | VARCHAR | Keycloak user UUID |
| `identification` | VARCHAR | Maps to core banking `identification_number` |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | TIMESTAMP | Audit: creation timestamp |
| `created_by` | VARCHAR | Audit: creator ID |
| `modified_date` | TIMESTAMP | Audit: last modification timestamp |
| `modified_by` | VARCHAR | Audit: last modifier ID |
| `version` | BIGINT | Optimistic locking version |

### 2.3 Internet Banking Fund Transfer Service

**`fund_transfer`** (JPA-managed)

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `transaction_reference` | VARCHAR | Transaction ID from core banking |
| `status` | VARCHAR | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | (audit fields) | From `AuditAware` superclass |

### 2.4 Internet Banking Utility Payment Service

**`utility_payment`** (JPA-managed)

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Utility reference number |
| `account` | VARCHAR | Source bank account number |
| `transaction_id` | VARCHAR | Transaction ID from core banking |
| `status` | VARCHAR | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | (audit fields) | From `AuditAware` superclass |

### 2.5 Database Topology

| Service | Database Name | Schema Management |
|---------|---------------|-------------------|
| Core Banking | `banking_core_service` | Flyway migrations (3 scripts) |
| User Service | `banking_core_user_service` | Hibernate `ddl-auto` (inferred) |
| Fund Transfer | `banking_core_fund_transfer_service` | Hibernate `ddl-auto` (inferred) |
| Utility Payment | `banking_core_utility_payment_service` | Hibernate `ddl-auto` (inferred) |

All four databases are hosted on a single MySQL 8.4 instance. Test profiles use H2 in-memory databases.

---

## 3. API Surface Map

### 3.1 API Gateway Routes

The API Gateway routes requests based on path prefixes (configured via Spring Cloud Config):

| Path Prefix | Target Service |
|-------------|---------------|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |
| `/banking-core/**` | core-banking-service |

### 3.2 Core Banking Service (port 8092)

#### Account Controller (`/api/v1/account`)

| Method | Path | Description | Request | Response |
|--------|------|-------------|---------|----------|
| `GET` | `/bank-account/{account_number}` | Get bank account by account number | Path: `account_number` (String) | `BankAccount` { id, number, type, status, availableBalance, actualBalance, user } |
| `GET` | `/util-account/{account_name}` | Get utility account by provider name | Path: `account_name` (String) | `UtilityAccount` { id, number, providerName } |

#### Transaction Controller (`/api/v1/transaction`)

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|-------------|----------|
| `POST` | `/fund-transfer` | Process fund transfer | `FundTransferRequest` { fromAccount, toAccount, amount } | `FundTransferResponse` { message, transactionId } |
| `POST` | `/util-payment` | Process utility payment | `UtilityPaymentRequest` { providerId, amount, referenceNumber, account } | `UtilityPaymentResponse` { message, transactionId } |

#### User Controller (`/api/v1/user`)

| Method | Path | Description | Request | Response |
|--------|------|-------------|---------|----------|
| `GET` | `/{identification}` | Get user by identification number | Path: `identification` (String) | `User` { id, firstName, lastName, email, identificationNumber, bankAccounts[] } |
| `GET` | `/` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |

### 3.3 Internet Banking User Service (port 8083)

#### User Controller (`/api/v1/bank-users`)

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|-------------|----------|
| `POST` | `/register` | Register new user | `User` { email, identification, password } | `User` { id, email, identification, authId, status } |
| `PATCH` | `/update/{id}` | Update user (e.g., approve) | `UserUpdateRequest` { status } | `User` |
| `GET` | `/` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |
| `GET` | `/{id}` | Get user by ID | Path: `id` (Long) | `User` |

### 3.4 Internet Banking Fund Transfer Service (port 8084)

#### Fund Transfer Controller (`/api/v1/transfer`)

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|-------------|----------|
| `POST` | `/` | Initiate fund transfer | `FundTransferRequest` { fromAccount, toAccount, amount, authID } | `FundTransferResponse` { message, transactionId } |
| `GET` | `/` | List fund transfers (paginated) | Query: `page`, `size`, `sort` | `List<FundTransfer>` |

### 3.5 Internet Banking Utility Payment Service (port 8085)

#### Utility Payment Controller (`/api/v1/utility-payment`)

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|-------------|----------|
| `POST` | `/` | Process utility payment | `UtilityPaymentRequest` { providerId, amount, referenceNumber, account } | `UtilityPaymentResponse` { message, transactionId } |
| `GET` | `/` | List utility payments (paginated) | Query: `page`, `size`, `sort` | `List<UtilityPayment>` |

### 3.6 Infrastructure Endpoints

| Service | Endpoint | Description |
|---------|----------|-------------|
| Service Registry | `GET /eureka/apps` | Eureka service listing |
| All services | `GET /actuator/**` | Spring Boot Actuator (health, info, etc.) |
| Zipkin | `GET :9411` | Distributed tracing UI |

### 3.7 Security

- The API Gateway enforces OAuth2/JWT authentication on all routes **except**:
  - `POST /user/api/v1/bank-users/register` (public registration)
  - `/actuator/**` endpoints on all services
- JWT tokens are issued by Keycloak and validated against the JWK Set URI.
- The gateway injects the authenticated principal's name into the `X-Auth-Id` header.
- Downstream services extract `X-Auth-Id` via `AppAuthUserFilter` for auditing.

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

**Location:** `internet-banking-user-service` → `UserService.createUser()`

1. Check if email is already registered in Keycloak → throw `UserAlreadyRegisteredException` if exists.
2. Look up user in core banking by identification number via Feign client.
3. Validate that the email provided matches the core banking record → throw `InvalidEmailException` if mismatch.
4. Create a Keycloak user with email as username, credentials set, `enabled=false`, `emailVerified=false`.
5. On successful Keycloak creation (HTTP 201), persist the user locally with status `PENDING`.
6. If no matching core banking user found → throw `InvalidBankingUserException`.

### 4.2 User Approval Flow

**Location:** `internet-banking-user-service` → `UserService.updateUser()`

1. Admin sends `PATCH /update/{id}` with `{ status: "APPROVED" }`.
2. If new status is `APPROVED`:
   - Fetch the Keycloak user representation.
   - Set `enabled=true` and `emailVerified=true`.
   - Update the Keycloak user.
3. Persist the new status in the local database.

### 4.3 Fund Transfer Flow

**Orchestration:** `internet-banking-fund-transfer-service` → `FundTransferService.fundTransfer()`
**Execution:** `core-banking-service` → `TransactionService.fundTransfer()`

#### Orchestration Layer (Fund Transfer Service):
1. Persist a `FundTransferEntity` with status `PENDING`.
2. Call core banking's `/api/v1/transaction/fund-transfer` via Feign.
3. Update the local record with `transactionReference` and status `SUCCESS`.
4. Return success response.

#### Execution Layer (Core Banking Service):
1. Read both source and destination `BankAccount` objects.
2. **Balance Validation:** Check `actualBalance >= 0` AND `actualBalance >= transfer amount`. Throw `InsufficientFundsException` if validation fails.
3. **Debit source account:** Subtract amount from `actualBalance`; recalculate `availableBalance` as `actualBalance - amount` (note: this double-subtracts — see Gap Analysis).
4. Save a debit `TransactionEntity` (negative amount) for the source account.
5. **Credit destination account:** Add amount to `actualBalance`; recalculate `availableBalance` as `actualBalance + amount` (note: this double-adds — see Gap Analysis).
6. Save a credit `TransactionEntity` (positive amount) for the destination account.
7. Return a UUID-based `transactionId`.

### 4.4 Utility Payment Flow

**Orchestration:** `internet-banking-utility-payment-service` → `UtilityPaymentService.utilPayment()`
**Execution:** `core-banking-service` → `TransactionService.utilPayment()`

#### Orchestration Layer (Utility Payment Service):
1. Persist a `UtilityPaymentEntity` with status `PROCESSING`.
2. Call core banking's `/api/v1/transaction/util-payment` via Feign.
3. Update the local record with `transactionId` and status `SUCCESS`.
4. Return success response.

#### Execution Layer (Core Banking Service):
1. Read the source `BankAccount`.
2. **Balance Validation:** Same check as fund transfer.
3. Read the `UtilityAccount` by provider ID.
4. Debit the source account (same double-subtraction bug as fund transfer).
5. Save a `UTILITY_PAYMENT` transaction record.
6. Return success with a UUID `transactionId`.

### 4.5 Business Rules Summary

| Rule | Enforcement | Location |
|------|-------------|----------|
| Minimum balance check | `actualBalance >= 0 && actualBalance >= amount` | `TransactionService.validateBalance()` |
| Email uniqueness | Keycloak email search before registration | `UserService.createUser()` |
| Email-identification match | Core banking email must match registration email | `UserService.createUser()` |
| User approval enables Keycloak login | Sets `enabled=true` and `emailVerified=true` | `UserService.updateUser()` |
| Audit trail | `AuditAware` base class with `@CreatedDate`, `@CreatedBy`, etc. | Fund Transfer, Utility Payment, User entities |
| Optimistic locking | `@Version` field on entities with `AuditAware` | Fund Transfer, Utility Payment, User entities |

---

## 5. Integration Points

### 5.1 Keycloak

| Aspect | Detail |
|--------|--------|
| **Version** | 23.0.7 |
| **Protocol** | Admin REST API via `keycloak-admin-client:24.0.4` |
| **Auth Method** | Client credentials grant (`client_credentials`) |
| **Configuration** | `app.config.keycloak.server-url`, `realm`, `clientId`, `client-secret` (externalized via Spring Cloud Config) |
| **Realm** | `javatodev-internet-banking` (imported from `realm-export.json`) |
| **Operations** | Create user, update user, search by email, read user by ID |
| **Consuming Service** | `internet-banking-user-service` only |
| **Backing DB** | PostgreSQL 15 (container: `keycloak_postgre_db`) |

### 5.2 Zipkin (Distributed Tracing)

| Aspect | Detail |
|--------|--------|
| **Version** | Zipkin 3 (Docker image: `openzipkin/zipkin:3`) |
| **Client Libraries** | `micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`, `feign-micrometer` |
| **Coverage** | All 4 business services + API Gateway |
| **Port** | 9411 |
| **Trace Propagation** | Via Brave (B3 headers) |

### 5.3 RabbitMQ

| Aspect | Detail |
|--------|--------|
| **Status** | **NOT IMPLEMENTED** |
| **README mention** | Fund Transfer and Utility Payment services are supposed to push notification messages to RabbitMQ |
| **Notification Service** | Marked as "PENDING Development" in README |
| **Code evidence** | No RabbitMQ dependencies in any `build.gradle`; no `docker-compose` definition for RabbitMQ |

### 5.4 Database Connections

| Service | Database | Connection |
|---------|----------|------------|
| Core Banking | `banking_core_service` (MySQL) | JPA + Flyway |
| User Service | `banking_core_user_service` (MySQL) | JPA (Hibernate auto-DDL) |
| Fund Transfer | `banking_core_fund_transfer_service` (MySQL) | JPA (Hibernate auto-DDL) |
| Utility Payment | `banking_core_utility_payment_service` (MySQL) | JPA (Hibernate auto-DDL) |
| Keycloak | `keycloak` (PostgreSQL) | Internal to Keycloak |

All application databases share a single MySQL 8.4 instance. The MySQL container runs a custom `privileges.sql` init script that creates a shared `javatodev_development` user with broad privileges across all databases.

### 5.5 Spring Cloud Config Server

| Aspect | Detail |
|--------|--------|
| **Git URI** | `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` |
| **Search Path** | `configuration` |
| **Branch** | `main` |
| **Consumer Services** | All services except config server itself use `spring-cloud-starter-config` + `spring-cloud-starter-bootstrap` |

### 5.6 Eureka Service Registry

| Aspect | Detail |
|--------|--------|
| **Port** | 8081 |
| **Self-registration** | Disabled (`register-with-eureka: false`) |
| **Consumer Services** | All business services + API Gateway register as Eureka clients |
| **Discovery** | Feign clients resolve service names (e.g., `core-banking-service`) through Eureka |

### 5.7 Inter-Service Feign Clients

| Source Service | Target Service | Feign Client Interface | Endpoints Called |
|---------------|----------------|----------------------|-----------------|
| User Service | Core Banking | `BankingCoreRestClient` | `GET /api/v1/user/{identification}` |
| Fund Transfer Service | Core Banking | `BankingCoreFeignClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/fund-transfer` |
| Utility Payment Service | Core Banking | `BankingCoreRestClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/util-payment` |

---

## 6. Build and Deployment Pipeline

### 6.1 Build System

| Aspect | Detail |
|--------|--------|
| **Build Tool** | Gradle (per-service `build.gradle`, no root multi-project build) |
| **Java Version** | 21 (source compatibility) |
| **Spring Boot** | 3.2.4 |
| **Spring Cloud** | 2023.0.0 |
| **Plugins** | `org.springframework.boot`, `io.spring.dependency-management`, `com.gorylenko.gradle-git-properties` (on most services) |

Each service is an independent Gradle project with its own `build.gradle`, `settings.gradle`, and `gradlew` wrapper. There is **no root-level Gradle build** — each service must be built individually.

### 6.2 Docker

Each service has a `Dockerfile` following the same pattern:

```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
EXPOSE <port>
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh
RUN apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

- Base image: Eclipse Temurin 21 JRE Alpine
- All services include `wait-for-it.sh` for startup dependency management
- Docker profile (`-Dspring.profiles.active=docker`) activates Docker-specific configuration

### 6.3 Docker Compose

**`docker-compose.yml`** — Full stack deployment:

- Defines all 7 application containers + MySQL + PostgreSQL (Keycloak) + Keycloak + Zipkin
- Uses a custom bridge network (`javatodev_ib_network`, subnet `172.25.0.0/16`) with static IP assignments
- Startup ordering managed via `wait-for-it.sh` entrypoint overrides (waits for service registry, config server, and MySQL)
- Named volumes for MySQL (`mysqldata`) and PostgreSQL (`postgres_data`) data persistence

**`docker-compose-support-apps.yml`** — Infrastructure only:

- Defines Zipkin, Keycloak, PostgreSQL, MySQL, Config Server, and Service Registry
- Intended for local development where business services run outside Docker

### 6.4 CI/CD

- **GitHub Actions:** The `.github/` directory contains only a `FUNDING.yml` file. All CI/CD workflows have been removed (commit message: "Remove GH Actions workflows (PAT scope compatibility)").
- **No automated build/test/deploy pipeline** exists in the repository.

### 6.5 Build Instructions

To build a single service:

```bash
cd <service-directory>
./gradlew clean build
```

To deploy via Docker Compose:

```bash
# Build all services first (each service individually)
for svc in core-banking-service internet-banking-config-server internet-banking-service-registry internet-banking-api-gateway internet-banking-user-service internet-banking-fund-transfer-service internet-banking-utility-payment-service; do
  (cd $svc && ./gradlew clean build)
done

# Start infrastructure + services
cd docker-compose
docker-compose up -d
```

### 6.6 Test Data

Pre-seeded via Flyway migration `V1.0.20210427174721__temp_data.sql`:

- **4 users** (Sam, Guru, Ragu, Randor) with identification numbers
- **14 savings accounts** distributed across users with various balances
- **6 utility provider accounts** (Vodafone, Verizon, Singtel, Hutch, Airtel, GIO)
- **Keycloak realm** imported from `realm-export.json` with matching test credentials: `ib_admin@javatodev.com / 5V7huE3G86uB`
