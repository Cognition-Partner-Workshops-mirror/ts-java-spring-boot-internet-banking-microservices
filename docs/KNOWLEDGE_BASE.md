# Application Knowledge Base

## Table of Contents

- [1. Architecture Overview](#1-architecture-overview)
  - [1.1 Services](#11-services)
  - [1.2 Communication Patterns](#12-communication-patterns)
  - [1.3 Infrastructure Components](#13-infrastructure-components)
- [2. Data Model Documentation](#2-data-model-documentation)
  - [2.1 Core Banking Service](#21-core-banking-service)
  - [2.2 User Service](#22-user-service)
  - [2.3 Fund Transfer Service](#23-fund-transfer-service)
  - [2.4 Utility Payment Service](#24-utility-payment-service)
- [3. API Surface Map](#3-api-surface-map)
  - [3.1 Core Banking Service (port 8092)](#31-core-banking-service-port-8092)
  - [3.2 User Service (port 8083)](#32-user-service-port-8083)
  - [3.3 Fund Transfer Service (port 8084)](#33-fund-transfer-service-port-8084)
  - [3.4 Utility Payment Service (port 8085)](#34-utility-payment-service-port-8085)
  - [3.5 API Gateway (port 8082)](#35-api-gateway-port-8082)
- [4. Key Business Logic Inventory](#4-key-business-logic-inventory)
  - [4.1 Fund Transfer Rules](#41-fund-transfer-rules)
  - [4.2 Utility Payment Processing](#42-utility-payment-processing)
  - [4.3 User Management](#43-user-management)
- [5. Build and Deployment Pipeline Summary](#5-build-and-deployment-pipeline-summary)
  - [5.1 Build System (Gradle)](#51-build-system-gradle)
  - [5.2 Docker Compose Deployment](#52-docker-compose-deployment)
  - [5.3 Startup Ordering](#53-startup-ordering)

---

## 1. Architecture Overview

### 1.1 Services

The application consists of **6 microservices**, each built with Java 21 and Spring Boot 3.2.4:

| # | Service | Port | Purpose |
|---|---------|------|---------|
| 1 | **core-banking-service** | 8092 | System of record for users, bank accounts, utility accounts, and transaction ledger. Acts as the internal banking core. |
| 2 | **internet-banking-user-service** | 8083 | Internet banking user registration, approval workflow, and Keycloak identity integration. |
| 3 | **internet-banking-fund-transfer-service** | 8084 | Orchestrates account-to-account fund transfers by delegating to the core banking service. |
| 4 | **internet-banking-utility-payment-service** | 8085 | Orchestrates utility bill payments (e.g., telecom providers) by delegating to the core banking service. |
| 5 | **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway entry point. Routes client traffic, enforces OAuth2/JWT authentication, and injects `X-Auth-Id` header. |
| 6 | **internet-banking-service-registry** | 8081 | Netflix Eureka server for dynamic service discovery. |

A 7th component, **internet-banking-config-server** (port 8090), provides centralized configuration via Spring Cloud Config backed by a [remote Git repository](https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git).

### 1.2 Communication Patterns

```
                         ┌──────────────────────┐
                         │   External Clients    │
                         └──────────┬─────────────┘
                                    │ HTTPS / JWT
                         ┌──────────▼─────────────┐
                         │   API Gateway (:8082)   │
                         │   OAuth2 Resource Server│
                         └──┬────────┬─────────┬───┘
                            │        │         │
               ┌────────────▼──┐ ┌───▼──────┐ ┌▼──────────────┐
               │ User Service  │ │ Fund     │ │ Utility       │
               │ (:8083)       │ │ Transfer │ │ Payment       │
               │               │ │ (:8084)  │ │ (:8085)       │
               └──────┬────────┘ └────┬─────┘ └──────┬────────┘
                      │               │               │
                      │   OpenFeign   │   OpenFeign   │  OpenFeign
                      │               │               │
               ┌──────▼───────────────▼───────────────▼────────┐
               │          Core Banking Service (:8092)          │
               │          (MySQL — banking_core_service)        │
               └───────────────────────────────────────────────┘
```

| Pattern | Technology | Description |
|---------|-----------|-------------|
| **Synchronous REST** | Spring Cloud OpenFeign | All inter-service communication uses declarative Feign clients. User, Fund Transfer, and Utility Payment services call Core Banking endpoints. |
| **Service Discovery** | Netflix Eureka | All services register with Eureka; Feign clients resolve service names (`core-banking-service`) via Eureka. |
| **Centralized Config** | Spring Cloud Config Server | Each service bootstraps configuration from Config Server (Git-backed), using `bootstrap.yml` with profile-specific overrides (`bootstrap-docker.yml`, `bootstrap-dev.yml`). |
| **API Gateway** | Spring Cloud Gateway | Single entry point with route definitions. Injects `X-Auth-Id` header (extracted from JWT principal) into downstream requests via a `GlobalFilter`. |
| **Authentication** | Keycloak + OAuth2 JWT | Gateway acts as OAuth2 Resource Server. The `/register` endpoint is permit-all; all other routes require a valid JWT. |
| **Distributed Tracing** | Micrometer Tracing + Zipkin | All services include Brave bridge and export spans to Zipkin (port 9411). |
| **User Context Propagation** | Custom `X-Auth-Id` header | Gateway extracts the authenticated principal name and sets it as `X-Auth-Id`. Downstream services read this via `AppAuthUserFilter` and store it in a `ThreadLocal` (`ApiRequestContextHolder`). |

### 1.3 Infrastructure Components

| Component | Image / Technology | Purpose |
|-----------|--------------------|---------|
| **MySQL** | Custom image (from `docker-compose/mysql/`) | Single MySQL instance used by Core Banking, User, Fund Transfer, and Utility Payment services. Database: `banking_core_service`. |
| **PostgreSQL** | `postgres:15` | Keycloak's backing database. Database: `keycloak`. |
| **Keycloak** | `quay.io/keycloak/keycloak:23.0.7` | Identity and Access Management. Pre-configured realm imported from `docker-compose/keycloak/`. |
| **Zipkin** | `openzipkin/zipkin:3` | Distributed tracing collector and UI (port 9411). |
| **Docker Network** | `javatodev_ib_network` (bridge, 172.25.0.0/16) | Fixed-IP bridge network for predictable inter-container communication. |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service

The core banking service owns the primary financial data, managed via Flyway migrations against MySQL.

#### `banking_core_user`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal user ID |
| `first_name` | VARCHAR(255) | nullable | User's first name |
| `last_name` | VARCHAR(255) | nullable | User's last name |
| `email` | VARCHAR(255) | nullable | User's email address |
| `identification_number` | VARCHAR(255) | nullable | National ID / NIC number |

**JPA Entity:** `UserEntity` — has a `@OneToMany` relationship to `BankAccountEntity`.

#### `banking_core_account`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal account ID |
| `number` | VARCHAR(255) | nullable | Account number (e.g., `100015003000`) |
| `type` | VARCHAR(255) | nullable | Enum: `SAVINGS_ACCOUNT` |
| `status` | VARCHAR(255) | nullable | Enum: `ACTIVE` |
| `actual_balance` | DECIMAL(19,2) | nullable | Ledger balance |
| `available_balance` | DECIMAL(19,2) | nullable | Available balance |
| `user_id` | BIGINT | FK to `banking_core_user.id` | Account owner |

**JPA Entity:** `BankAccountEntity` — `@ManyToOne` to `UserEntity`.

#### `banking_core_transaction`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal transaction ID |
| `amount` | DECIMAL(19,2) | nullable | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | NOT NULL | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | NOT NULL | Reference (target account number or bill ref) |
| `transaction_id` | VARCHAR(50) | NOT NULL | UUID transaction identifier |
| `account_id` | BIGINT | FK to `banking_core_account.id` | Associated account |

**JPA Entity:** `TransactionEntity` — `@OneToOne(cascade=ALL)` to `BankAccountEntity`. Note: using `@OneToOne` for what is logically a `@ManyToOne` is a modeling concern.

#### `banking_core_utility_account`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | Internal ID |
| `number` | VARCHAR(255) | nullable | Utility provider account number |
| `provider_name` | VARCHAR(255) | nullable | Provider name (e.g., `VODAFONE`, `AIRTEL`) |

**JPA Entity:** `UtilityAccountEntity`

#### Relationships Diagram

```
banking_core_user  1 ──── * banking_core_account  1 ──── * banking_core_transaction
                                                              (logically @ManyToOne,
                                                               coded as @OneToOne)

banking_core_utility_account (standalone, no FK relationships)
```

### 2.2 User Service

The internet banking user service has its own table in MySQL for internet-banking-specific user state.

#### `user`

| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT (PK) | Auto-generated ID |
| `auth_id` | VARCHAR | Keycloak user UUID |
| `identification` | VARCHAR | National ID / NIC (links to core banking user) |
| `status` | ENUM STRING | `PENDING` or `APPROVED` |
| `created_at` | TIMESTAMP | Audit field (from `AuditAware` superclass) |
| `updated_at` | TIMESTAMP | Audit field (from `AuditAware` superclass) |

**JPA Entity:** `UserEntity extends AuditAware` — no direct FK to core banking; linked by `identification` field.

### 2.3 Fund Transfer Service

#### `fund_transfer`

| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT (PK) | Auto-generated ID |
| `transaction_reference` | VARCHAR | UUID from core banking response |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `status` | ENUM STRING | `PENDING`, `SUCCESS` |
| `created_at` | TIMESTAMP | Audit field |
| `updated_at` | TIMESTAMP | Audit field |

**JPA Entity:** `FundTransferEntity extends AuditAware`

### 2.4 Utility Payment Service

#### `utility_payment`

| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT (PK) | Auto-generated ID |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Bill reference number |
| `account` | VARCHAR | Source bank account number |
| `transaction_id` | VARCHAR | UUID from core banking response |
| `status` | ENUM STRING | `PROCESSING`, `SUCCESS` |
| `created_at` | TIMESTAMP | Audit field |
| `updated_at` | TIMESTAMP | Audit field |

**JPA Entity:** `UtilityPaymentEntity extends AuditAware`

---

## 3. API Surface Map

### 3.1 Core Banking Service (port 8092)

| Method | Endpoint | Request Body | Response Body | Description |
|--------|----------|-------------|---------------|-------------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | — | `BankAccount { number, type, status, availableBalance, actualBalance }` | Retrieve bank account by number |
| `GET` | `/api/v1/account/util-account/{account_name}` | — | `UtilityAccount { id, number, providerName }` | Retrieve utility account by provider name |
| `GET` | `/api/v1/user/{identification}` | — | `User { id, firstName, lastName, email, identificationNumber }` | Retrieve user by identification number |
| `GET` | `/api/v1/user` | Query: `page`, `size`, `sort` | `List<User>` | Paginated list of users |
| `POST` | `/api/v1/transaction/fund-transfer` | `FundTransferRequest { fromAccount, toAccount, amount }` | `FundTransferResponse { message, transactionId }` | Execute a fund transfer between accounts |
| `POST` | `/api/v1/transaction/util-payment` | `UtilityPaymentRequest { providerId, amount, referenceNumber, account }` | `UtilityPaymentResponse { message, transactionId }` | Execute a utility payment |

### 3.2 User Service (port 8083)

| Method | Endpoint | Request Body | Response Body | Description |
|--------|----------|-------------|---------------|-------------|
| `POST` | `/api/v1/bank-users/register` | `User { email, identification, password }` | `User { id, email, identification, authId, status }` | Register a new internet banking user (creates Keycloak user + local record) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `UserUpdateRequest { status }` | `User` | Update user status (e.g., approve registration) |
| `GET` | `/api/v1/bank-users` | Query: `page`, `size`, `sort` | `List<User>` | Paginated list of internet banking users |
| `GET` | `/api/v1/bank-users/{id}` | — | `User` | Retrieve user by internal ID |

### 3.3 Fund Transfer Service (port 8084)

| Method | Endpoint | Request Body | Response Body | Description |
|--------|----------|-------------|---------------|-------------|
| `POST` | `/api/v1/transfer` | `FundTransferRequest { fromAccount, toAccount, amount }` | `FundTransferResponse { message, transactionId }` | Initiate a fund transfer (saves local record, delegates to core banking) |
| `GET` | `/api/v1/transfer` | Query: `page`, `size`, `sort` | `List<FundTransfer>` | Paginated list of fund transfers |

### 3.4 Utility Payment Service (port 8085)

| Method | Endpoint | Request Body | Response Body | Description |
|--------|----------|-------------|---------------|-------------|
| `POST` | `/api/v1/utility-payment` | `UtilityPaymentRequest { providerId, amount, referenceNumber, account }` | `UtilityPaymentResponse { message, transactionId }` | Process a utility payment (saves local record, delegates to core banking) |
| `GET` | `/api/v1/utility-payment` | Query: `page`, `size`, `sort` | `List<UtilityPayment>` | Paginated list of utility payments |

### 3.5 API Gateway (port 8082)

The gateway proxies all requests with the following route prefixes (configured via Spring Cloud Config):

| Gateway Path Prefix | Target Service |
|---------------------|---------------|
| `/user/**` | `internet-banking-user-service` |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` |
| `/utility-payment/**` | `internet-banking-utility-payment-service` |
| `/banking-core/**` | `core-banking-service` |

**Security Rules:**
- `POST /user/api/v1/bank-users/register` — **permit all** (public registration)
- `/actuator/**` (all service prefixes) — **permit all** (health checks)
- All other endpoints — **require valid JWT** (OAuth2 Resource Server with Keycloak JWK Set URI)

---

## 4. Key Business Logic Inventory

### 4.1 Fund Transfer Rules

**Location:** `core-banking-service/.../TransactionService.java` and `internet-banking-fund-transfer-service/.../FundTransferService.java`

#### Flow (Orchestration Layer — Fund Transfer Service)
1. Receive `FundTransferRequest { fromAccount, toAccount, amount }`.
2. Create local `FundTransferEntity` with status `PENDING` and persist.
3. Call core banking's `/api/v1/transaction/fund-transfer` via OpenFeign.
4. On success, update local entity with `transactionReference` and status `SUCCESS`.
5. Return response to client.

#### Flow (Execution Layer — Core Banking)
1. Look up source and destination `BankAccount` by account number (throws `EntityNotFoundException` if not found).
2. **Balance validation:** Verify `actualBalance >= 0` AND `actualBalance >= transferAmount`. If not, throw `InsufficientFundsException`.
3. Debit source account: `actualBalance -= amount`, `availableBalance = actualBalance - amount`.
4. Create debit `TransactionEntity` with negative amount.
5. Credit destination account: `actualBalance += amount`, `availableBalance = actualBalance + amount`.
6. Create credit `TransactionEntity` with positive amount.
7. Return `FundTransferResponse` with generated UUID transaction ID.

**Important:** The entire operation runs within a single `@Transactional` boundary in core banking.

#### Known Balance Calculation Issue
The available balance calculation is inconsistent:
- Debit: `availableBalance = actualBalance - amount` (subtracts again from already-debited balance)
- Credit: `availableBalance = actualBalance + amount` (adds again to already-credited balance)

This results in double-counting, causing `availableBalance` to diverge from `actualBalance`.

### 4.2 Utility Payment Processing

**Location:** `core-banking-service/.../TransactionService.java` and `internet-banking-utility-payment-service/.../UtilityPaymentService.java`

#### Flow (Orchestration Layer — Utility Payment Service)
1. Receive `UtilityPaymentRequest { providerId, amount, referenceNumber, account }`.
2. Create local `UtilityPaymentEntity` with status `PROCESSING` and persist.
3. Call core banking's `/api/v1/transaction/util-payment` via OpenFeign.
4. On success, update local entity with `transactionId` and status `SUCCESS`.
5. Return response to client.

#### Flow (Execution Layer — Core Banking)
1. Look up source `BankAccount` by account number.
2. **Balance validation:** Same rules as fund transfer.
3. Look up `UtilityAccount` by provider ID.
4. Debit source account: `actualBalance -= amount`, `availableBalance = actualBalance - amount`.
5. Create `TransactionEntity` with type `UTILITY_PAYMENT` and negative amount.
6. Return `UtilityPaymentResponse` with generated UUID transaction ID.

**Note:** No actual third-party payment provider integration exists; a comment placeholder indicates this: `"//we can call third party API to process UTIL payment from payment provider from here."`

### 4.3 User Management

**Location:** `internet-banking-user-service/.../UserService.java` and `internet-banking-user-service/.../KeycloakUserService.java`

#### Registration Flow
1. Receive `User { email, identification, password }`.
2. Check Keycloak: if email already exists, throw `UserAlreadyRegisteredException`.
3. Call core banking's `/api/v1/user/{identification}` to validate the identification exists in the banking core.
4. Verify email matches the core banking record; throw `InvalidEmailException` if mismatch.
5. Create Keycloak user: `enabled=false`, `emailVerified=false`, with provided credentials.
6. If Keycloak returns 201, read back the Keycloak user to obtain `authId`.
7. Persist local `UserEntity` with `status=PENDING`.
8. Return user DTO.

#### Approval Flow
1. Admin calls `PATCH /update/{id}` with `{ status: "APPROVED" }`.
2. Look up local user entity.
3. If new status is `APPROVED`: read Keycloak user by `authId`, set `enabled=true` and `emailVerified=true`, update in Keycloak.
4. Update local entity status.
5. Return updated user.

#### User Listing
1. Paginated query of local `UserEntity` records.
2. For each user, fetch Keycloak representation by `authId` to enrich with email.

---

## 5. Build and Deployment Pipeline Summary

### 5.1 Build System (Gradle)

Each microservice is an independent Gradle project (no multi-project build). All share:
- **Java 21** (`sourceCompatibility = '21'`)
- **Spring Boot 3.2.4** plugin
- **Spring Cloud 2023.0.0** BOM
- **JUnit 5** via `useJUnitPlatform()`

Common dependencies across business services:
- `spring-boot-starter-web` / `spring-boot-starter-data-jpa`
- `spring-cloud-starter-netflix-eureka-client`
- `spring-cloud-starter-config` + `spring-cloud-starter-bootstrap`
- Micrometer tracing (`micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`)
- Lombok (`compileOnly` + `annotationProcessor`)
- `springdoc-openapi-starter-webflux-ui:2.1.0` (OpenAPI/Swagger)
- `mysql-connector-j:8.4.0` (production) + `h2:2.2.224` (test)

Notable per-service additions:
- **core-banking-service:** `flyway-core:10.12.0`, `flyway-mysql:10.12.0`
- **user-service:** `spring-cloud-starter-openfeign`, `keycloak-admin-client:24.0.4`
- **fund-transfer-service:** `spring-cloud-starter-openfeign`
- **utility-payment-service:** `spring-cloud-starter-openfeign`
- **api-gateway:** `spring-cloud-starter-gateway`, `spring-boot-starter-oauth2-client`, `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-security`
- **config-server:** `spring-cloud-config-server`

The `com.gorylenko.gradle-git-properties:2.4.2` plugin is used in most services to embed Git metadata.

### 5.2 Docker Compose Deployment

Two compose files exist in `docker-compose/`:

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Full stack: all infrastructure + all application services |
| `docker-compose-support-apps.yml` | Infrastructure only: Zipkin, Keycloak, PostgreSQL, MySQL, Config Server, Service Registry |

All containers are placed on a shared bridge network (`172.25.0.0/16`) with fixed IPs for deterministic resolution.

### 5.3 Startup Ordering

Application services use `wait-for-it.sh` scripts in their Docker entrypoints to ensure dependencies are ready:

```
Service Registry (:8081)  ─┐
Config Server (:8090)      ─┤── wait-for-it ──► API Gateway
MySQL (:3306)              ─┤── wait-for-it ──► User Service
                            ├── wait-for-it ──► Fund Transfer Service
                            ├── wait-for-it ──► Utility Payment Service
                            └── wait-for-it ──► Core Banking Service
```

Each Dockerfile follows the same pattern:
1. Base image: `eclipse-temurin:21.0.2_13-jre-alpine`
2. Copy fat JAR (`build/libs/*-0.0.1-SNAPSHOT.jar`) as `app.jar`
3. Copy `wait-for-it.sh` and install `bash`
4. Default entrypoint: `java -jar -Dspring.profiles.active=docker /app.jar`

### 5.4 Seed Data

Core banking is bootstrapped with Flyway migrations that insert test data:
- **4 users** (Sam, Guru, Ragu, Randor) with NIC identification numbers
- **14 savings accounts** with pre-loaded balances (ranging from 12,000 to 889,000.33)
- **6 utility provider accounts** (Vodafone, Verizon, Singtel, Hutch, Airtel, GIO)

Keycloak is bootstrapped via realm import (`docker-compose/keycloak/`) with matching user data.

**Test credentials:** `ib_admin@javatodev.com` / `5V7huE3G86uB`
