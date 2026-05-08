# Internet Banking Microservices — Application Knowledge Base

## Table of Contents

- [1. Architecture Overview](#1-architecture-overview)
- [2. Data Model Documentation](#2-data-model-documentation)
- [3. API Surface Map](#3-api-surface-map)
- [4. Key Business Logic Inventory](#4-key-business-logic-inventory)
- [5. Integration Points](#5-integration-points)
- [6. Build and Deployment Pipeline Summary](#6-build-and-deployment-pipeline-summary)

---

## 1. Architecture Overview

### Technology Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 21 |
| Framework | Spring Boot | 3.2.4 |
| Cloud | Spring Cloud | 2023.0.0 |
| Service Discovery | Netflix Eureka | via Spring Cloud |
| API Gateway | Spring Cloud Gateway | via Spring Cloud |
| Configuration | Spring Cloud Config | via Spring Cloud |
| Inter-service Communication | Spring Cloud OpenFeign | via Spring Cloud |
| Identity & Access Management | Keycloak | 23.0.7 |
| Distributed Tracing | Zipkin + Micrometer Brave | 3 |
| Databases | MySQL 8.4 (app data), PostgreSQL 15 (Keycloak) | — |
| Build Tool | Gradle | 8.6 |
| Containerization | Docker + Docker Compose | v3.6 |
| ORM | Spring Data JPA / Hibernate | via Spring Boot |
| Migration | Flyway | 10.12.0 (core-banking only) |
| API Docs | springdoc-openapi | 2.1.0 |

### Services

The application comprises **6 microservices** organized into three tiers:

#### Infrastructure Services

| Service | Port | Purpose | Key Dependencies |
|---|---|---|---|
| `internet-banking-config-server` | 8090 | Centralized configuration management; reads properties from a GitHub-hosted Git repository | Spring Cloud Config Server |
| `internet-banking-service-registry` | 8081 | Service discovery; all other services register here | Netflix Eureka Server |
| `internet-banking-api-gateway` | 8082 | Single entry point for all client requests; route-based proxying, OAuth2/JWT enforcement via Keycloak | Spring Cloud Gateway, Spring Security OAuth2 |

#### Business Services

| Service | Port | Purpose | Key Dependencies |
|---|---|---|---|
| `core-banking-service` | 8092 | System of record: users, bank accounts, utility accounts, transaction processing | Spring Data JPA, Flyway, MySQL |
| `internet-banking-user-service` | 8083 | User lifecycle management: registration, status updates, Keycloak integration | OpenFeign (→ core-banking), Keycloak Admin Client, MySQL |
| `internet-banking-fund-transfer-service` | 8084 | Orchestrates peer-to-peer fund transfers between bank accounts | OpenFeign (→ core-banking), MySQL |
| `internet-banking-utility-payment-service` | 8085 | Orchestrates utility bill payments from bank accounts to utility providers | OpenFeign (→ core-banking), MySQL |

### Communication Patterns

```
┌──────────┐     HTTP/JWT      ┌──────────────────────┐
│  Client  │ ───────────────►  │   API Gateway (:8082)│
└──────────┘                   └──────────┬───────────┘
                                          │  routes via Eureka
                    ┌─────────────────────┼─────────────────────┐
                    │                     │                     │
                    ▼                     ▼                     ▼
          ┌─────────────────┐  ┌──────────────────┐  ┌───────────────────┐
          │  User Service   │  │ Fund Transfer    │  │ Utility Payment   │
          │  (:8083)        │  │ Service (:8084)  │  │ Service (:8085)   │
          └────────┬────────┘  └────────┬─────────┘  └────────┬──────────┘
                   │  Feign             │  Feign               │  Feign
                   ▼                    ▼                      ▼
          ┌────────────────────────────────────────────────────────────┐
          │                Core Banking Service (:8092)                │
          │   (Users, Accounts, Transactions — System of Record)      │
          └────────────────────────────────────────────────────────────┘
```

- **Synchronous REST via OpenFeign**: All inter-service calls are synchronous HTTP requests routed through Eureka service discovery.
- **No asynchronous messaging**: RabbitMQ is mentioned in documentation but is **not implemented** in the codebase. There is no message broker integration.
- **Gateway-to-downstream auth**: The Gateway extracts the authenticated principal name from the JWT and forwards it as an `X-Auth-Id` HTTP header to downstream services. Downstream services read this header via `AppAuthUserFilter` but perform **no independent JWT validation**.

### Configuration Management

- **Spring Cloud Config Server** at port 8090 reads configuration from a remote Git repository:
  `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- Each service has `bootstrap.yml` (localhost), `bootstrap-dev.yml` (local network IP), and `bootstrap-docker.yml` (Docker network hostname) profiles for config server URI.
- Runtime configuration (database connections, Keycloak settings, Eureka URLs, Zipkin URLs) is externalized in the remote Git config repo — not in this repository.

---

## 2. Data Model Documentation

### Database Architecture

All application data resides in a single **MySQL 8.4** instance with **4 separate schemas**, each owned by one service. Keycloak uses a separate **PostgreSQL 15** instance.

| Schema | Owner Service | Migration Tool |
|---|---|---|
| `banking_core_service` | core-banking-service | Flyway |
| `banking_core_fund_transfer_service` | internet-banking-fund-transfer-service | JPA auto-DDL |
| `banking_core_user_service` | internet-banking-user-service | JPA auto-DDL |
| `banking_core_utility_payment_service` | internet-banking-utility-payment-service | JPA auto-DDL |

Database user: `javatodev_development` (created via `privileges.sql` init script).

### Entity Relationship Diagrams

#### Schema: `banking_core_service`

```
┌───────────────────────┐       ┌──────────────────────────┐
│   banking_core_user   │       │  banking_core_account    │
├───────────────────────┤       ├──────────────────────────┤
│ id (PK, BIGINT)       │◄──┐  │ id (PK, BIGINT)          │
│ first_name (VARCHAR)  │   │  │ number (VARCHAR)          │
│ last_name (VARCHAR)   │   │  │ type (ENUM: SAVINGS_      │
│ email (VARCHAR)       │   │  │   ACCOUNT)                │
│ identification_number │   │  │ status (ENUM: ACTIVE)     │
│   (VARCHAR)           │   └──│ user_id (FK → user.id)    │
└───────────────────────┘      │ actual_balance (DECIMAL)  │
                               │ available_balance(DECIMAL)│
                               └──────────┬───────────────┘
                                          │
┌──────────────────────────────┐          │
│  banking_core_transaction    │          │
├──────────────────────────────┤          │
│ id (PK, BIGINT)              │          │
│ amount (DECIMAL)             │          │
│ transaction_type (ENUM:      │          │
│   FUND_TRANSFER,             │          │
│   UTILITY_PAYMENT)           │          │
│ reference_number (VARCHAR)   │          │
│ transaction_id (VARCHAR)     │          │
│ account_id (FK → account.id) │◄─────────┘
└──────────────────────────────┘

┌──────────────────────────────┐
│ banking_core_utility_account │
├──────────────────────────────┤
│ id (PK, BIGINT)              │
│ number (VARCHAR)             │
│ provider_name (VARCHAR)      │
└──────────────────────────────┘
```

#### Schema: `banking_core_fund_transfer_service`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK) | Auto-increment |
| `transaction_reference` | VARCHAR | UUID from core-banking |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `status` | ENUM | `PENDING`, `SUCCESS` |
| `created_date` | INSTANT | Audit: creation timestamp |
| `created_by` | VARCHAR | Audit: creator |
| `modified_date` | INSTANT | Audit: last modified |
| `modified_by` | VARCHAR | Audit: modifier |
| `version` | LONG | Optimistic locking |

#### Schema: `banking_core_user_service`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK) | Auto-increment |
| `auth_id` | VARCHAR | Keycloak user UUID |
| `identification` | VARCHAR | National ID (NIC) — links to core-banking user |
| `status` | ENUM | `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | INSTANT | Audit: creation timestamp |
| `created_by` | VARCHAR | Audit: creator |
| `modified_date` | INSTANT | Audit: last modified |
| `modified_by` | VARCHAR | Audit: modifier |
| `version` | LONG | Optimistic locking |

#### Schema: `banking_core_utility_payment_service`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK) | Auto-increment |
| `provider_id` | LONG | Utility provider reference |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | External reference |
| `account` | VARCHAR | Source bank account number |
| `transaction_id` | VARCHAR | UUID from core-banking |
| `status` | ENUM | `PROCESSING`, `SUCCESS` |
| `created_date` | INSTANT | Audit: creation timestamp |
| `created_by` | VARCHAR | Audit: creator |
| `modified_date` | INSTANT | Audit: last modified |
| `modified_by` | VARCHAR | Audit: modifier |
| `version` | LONG | Optimistic locking |

### Shared Base Classes

- **`AuditAware`** (`@MappedSuperclass`): Provides `createdDate`, `createdBy`, `modifiedDate`, `modifiedBy`, and `version` fields. Used by `FundTransferEntity`, `UserEntity` (user-service), and `UtilityPaymentEntity`. **Duplicated** across 3 services (fund-transfer, user-service, utility-payment).
- **`BaseMapper<E, D>`**: Abstract generic mapper with `convertToEntity`/`convertToDto` plus collection variants. **Duplicated** across 4 services (core-banking, fund-transfer, user-service, utility-payment).

---

## 3. API Surface Map

### Gateway Routes

All client traffic flows through the API Gateway (`:8082`). Route prefixes:

| Gateway Path Prefix | Target Service | Service Port |
|---|---|---|
| `/user/**` | internet-banking-user-service | 8083 |
| `/fund-transfer/**` | internet-banking-fund-transfer-service | 8084 |
| `/payment/**` | internet-banking-utility-payment-service | 8085 |
| `/core/**` | core-banking-service | 8092 |

Public endpoint (no JWT required): `POST /user/api/v1/bank-users/register`

### Core Banking Service (`:8092`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by account number | — | `BankAccount { number, type, status, availableBalance, actualBalance }` |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | — | `UtilityAccount { id, number, providerName }` |
| `POST` | `/api/v1/transaction/fund-transfer` | Process internal fund transfer | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/user/{identification}` | Get user by NIC identification | — | `User { id, firstName, lastName, email, identificationNumber, accounts[] }` |
| `GET` | `/api/v1/user` | List users (paginated) | `?page=&size=&sort=` | `List<User>` |

### Internet Banking User Service (`:8083`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Register new user (PUBLIC) | `{ email, identification, password }` | `User { id, authId, identification, status }` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user status (admin) | `{ status: "APPROVED"\|"DISABLED"\|... }` | `User { id, authId, identification, status }` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | `?page=&size=&sort=` | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | — | `User { id, authId, identification, status }` |

### Internet Banking Fund Transfer Service (`:8084`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | `?page=&size=&sort=` | `List<FundTransfer>` |

### Internet Banking Utility Payment Service (`:8085`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | `?page=&size=&sort=` | `List<UtilityPayment>` |

### Actuator Endpoints (all services)

All services expose Spring Boot Actuator endpoints (permitted without auth at the gateway):
- `/actuator/health`, `/actuator/info`, `/actuator/prometheus` (where configured)

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

**Service:** `internet-banking-user-service` → `core-banking-service` + Keycloak

```
Client POST /api/v1/bank-users/register { email, identification, password }
  │
  ├─ 1. Check Keycloak: email already registered? → throw UserAlreadyRegisteredException
  │
  ├─ 2. Feign call → core-banking GET /api/v1/user/{identification}
  │     └─ Validate user exists in core banking system
  │
  ├─ 3. Validate email matches core banking record → throw InvalidEmailException if mismatch
  │
  ├─ 4. Create Keycloak user:
  │     ├─ Set email, username, firstName, lastName
  │     ├─ Set temporary password from request
  │     ├─ emailVerified=false, enabled=false
  │     └─ Expect HTTP 201 response
  │
  ├─ 5. Look up created Keycloak user to get authId (UUID)
  │
  └─ 6. Save UserEntity { authId, identification, status=PENDING }
```

**Admin Approval:** `PATCH /api/v1/bank-users/update/{id}` with `{ status: "APPROVED" }`:
- Updates Keycloak user: `enabled=true`, `emailVerified=true`
- Updates local entity status to `APPROVED`

### 4.2 Fund Transfer Flow

**Service:** `internet-banking-fund-transfer-service` → `core-banking-service`

```
Client POST /api/v1/transfer { fromAccount, toAccount, amount }
  │
  ├─ 1. Save FundTransferEntity { status=PENDING }
  │
  ├─ 2. Feign call → core-banking POST /api/v1/transaction/fund-transfer
  │     │
  │     ├─ a. Look up fromAccount and toAccount via AccountService
  │     ├─ b. Validate fromAccount has sufficient balance
  │     ├─ c. Debit fromAccount: actualBalance -= amount, availableBalance = actualBalance - amount
  │     ├─ d. Save debit TransactionEntity (FUND_TRANSFER, negative amount)
  │     ├─ e. Credit toAccount: actualBalance += amount, availableBalance = actualBalance + amount
  │     ├─ f. Save credit TransactionEntity (FUND_TRANSFER, positive amount)
  │     └─ g. Return { transactionId }
  │
  └─ 3. Update FundTransferEntity { transactionReference, status=SUCCESS }
```

**⚠ Known Bug — Double Deduction:** In `TransactionService.internalFundTransfer()`:
- Line 90-91: `fromAccount.actualBalance -= amount` then `fromAccount.availableBalance = fromAccount.actualBalance - amount`
- The `availableBalance` is computed from the **already-debited** `actualBalance`, causing a double subtraction.
- Same bug exists in `utilPayment()` (lines 63-64) and in the credit side for `toAccount` (lines 99-100) where `availableBalance` gets double-added.

### 4.3 Utility Payment Flow

**Service:** `internet-banking-utility-payment-service` → `core-banking-service`

```
Client POST /api/v1/utility-payment { providerId, amount, referenceNumber, account }
  │
  ├─ 1. Save UtilityPaymentEntity { status=PROCESSING }
  │
  ├─ 2. Feign call → core-banking POST /api/v1/transaction/util-payment
  │     │
  │     ├─ a. Look up bank account
  │     ├─ b. Validate sufficient balance
  │     ├─ c. Look up utility provider by providerId
  │     ├─ d. Debit bank account (⚠ same double-deduction bug)
  │     ├─ e. Save TransactionEntity (UTILITY_PAYMENT, negative amount)
  │     └─ f. Return { transactionId }
  │
  └─ 3. Update UtilityPaymentEntity { transactionId, status=SUCCESS }
```

### 4.4 Balance Validation Rule

```java
if (actualBalance < 0 || actualBalance < requestedAmount) {
    throw InsufficientFundsException
}
```

- Only checks `actualBalance`, not `availableBalance`.
- No minimum balance enforcement.
- No daily/per-transaction limits.

### 4.5 Failure Handling Gaps

- **No rollback on Feign failure:** If the core-banking Feign call fails after the orchestrating service has saved a `PENDING`/`PROCESSING` entity, that entity stays in that state forever. No compensation, retry, or dead-letter mechanism exists.
- **No idempotency keys:** Fund-transfer and utility-payment POST endpoints can process duplicate transactions if the same request is sent twice.

---

## 5. Integration Points

### 5.1 Keycloak (Identity & Access Management)

| Aspect | Detail |
|---|---|
| **Version** | 23.0.7 |
| **Backing Store** | PostgreSQL 15 (container: `keycloak_postgre_db`, IP: 172.25.0.10) |
| **Admin Console** | `http://localhost:8080` (admin/password) |
| **Realm** | Imported from `docker-compose/keycloak/realm-export.json` |
| **Gateway Integration** | OAuth2 Resource Server with JWK Set URI validation |
| **User Service Integration** | Keycloak Admin Client (`keycloak-admin-client:24.0.4`) using `client_credentials` grant |
| **Configuration** | `app.config.keycloak.server-url`, `.realm`, `.clientId`, `.client-secret` (externalized) |
| **Singleton Pattern** | `KeycloakProperties` maintains a static singleton `Keycloak` instance (not thread-safe) |

### 5.2 RabbitMQ (Message Broker)

- **Status:** **Not implemented**. Listed in the README technology stack and architecture diagram, but no RabbitMQ dependency exists in any `build.gradle`, and no messaging code is present.
- **Intended Use:** Notification service (marked "PENDING Development" in README).

### 5.3 Zipkin (Distributed Tracing)

| Aspect | Detail |
|---|---|
| **Version** | Zipkin 3 (Docker image: `openzipkin/zipkin:3`) |
| **Container** | `openzipkin_server`, IP: 172.25.0.12, Port: 9411 |
| **Client Libraries** | `micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`, `feign-micrometer` |
| **Coverage** | All 6 services include tracing dependencies |
| **Configuration** | Zipkin URL externalized in remote config (expected: `management.zipkin.tracing.endpoint`) |

### 5.4 Database Connections

| Database | Engine | Container | IP | Port | Schemas |
|---|---|---|---|---|---|
| MySQL | 8.4.0 | `mysql_javatodev_app` | 172.25.0.9 | 3306 | `banking_core_service`, `banking_core_fund_transfer_service`, `banking_core_user_service`, `banking_core_utility_payment_service` |
| PostgreSQL | 15 | `keycloak_postgre_db` | 172.25.0.10 | 5432 (closed) | `keycloak` |

- MySQL root password: hardcoded in `docker-compose.yml` and `Dockerfile`
- Application user `javatodev_development` created via `privileges.sql` with broad privileges on `*.*`

### 5.5 Spring Cloud Config Server (External Configuration)

| Aspect | Detail |
|---|---|
| **Git Repository** | `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` |
| **Branch** | `main` |
| **Search Path** | `configuration` |
| **Bootstrap Profiles** | `default` (localhost:8090), `dev` (192.168.1.5:8090), `docker` (internet-banking-config-server:8090) |

### 5.6 Service Discovery (Eureka)

- Eureka Server at `:8081` (does not register itself)
- All other services register as Eureka clients
- Feign clients resolve service names (`core-banking-service`) through Eureka

---

## 6. Build and Deployment Pipeline Summary

### Build System

- **Each service has its own independent Gradle build** — there is no multi-module root `build.gradle` or `settings.gradle`.
- Gradle Wrapper version: **8.6** (checked in per service).
- All services use `com.gorylenko.gradle-git-properties` plugin (except `internet-banking-service-registry`).

### Build Commands

```bash
# Build a single service (from its directory)
./gradlew clean build

# Build Docker image (from service directory, after build)
docker build -t javatodev/<service-name> .
```

### Docker Compose Deployment

Two compose files in `docker-compose/`:

| File | Contents |
|---|---|
| `docker-compose-support-apps.yml` | Infrastructure only: MySQL, Keycloak, PostgreSQL, Zipkin, Config Server, Service Registry |
| `docker-compose.yml` | Full stack: all infrastructure + all 6 application services |

### Container Networking

| Container | IP Address | Port |
|---|---|---|
| `core-banking-service` | 172.25.0.2 | 8092 |
| `internet-banking-utility-payment-service` | 172.25.0.3 | 8085 |
| `internet-banking-fund-transfer-service` | 172.25.0.4 | 8084 |
| `internet-banking-user-service` | 172.25.0.5 | 8083 |
| `internet-banking-api-gateway` | 172.25.0.6 | 8082 |
| `internet-banking-service-registry` | 172.25.0.7 | 8081 |
| `internet-banking-config-server` | 172.25.0.8 | 8090 |
| `mysql_javatodev_app` | 172.25.0.9 | 3306 |
| `keycloak_postgre_db` | 172.25.0.10 | 5432 |
| `keycloak_web` | 172.25.0.11 | 8080 |
| `openzipkin_server` | 172.25.0.12 | 9411 |

Network: `javatodev_ib_network` — bridge driver, subnet `172.25.0.0/16`.

### Startup Ordering

Application services use `wait-for-it.sh` scripts in their Docker entrypoints to wait for dependencies:
1. Service Registry (`:8081`) — must be available first
2. Config Server (`:8090`) — must be available second
3. MySQL (`:3306`) — must be available for data-layer services
4. Then the service JARs start with `-Dspring.profiles.active=docker`

Timeout: 50 seconds per dependency.

### Dockerfiles

All application services use the same Dockerfile pattern:
```dockerfile
FROM eclipse-temurin:21-jre-alpine
# Copy wait-for-it.sh, app.jar
# ENTRYPOINT configured in docker-compose.yml
```

### CI/CD

- **No CI/CD pipeline** is configured in this repository.
- No GitHub Actions workflows, Jenkinsfiles, or similar automation files.
- The `.github/` directory contains only a `FUNDING.yml`.

### Test Data

Flyway seeds `banking_core_service` with:
- 4 users (Sam, Guru, Ragu, Randor)
- 14 savings accounts with balances ranging from 12,000 to 889,000.33
- 6 utility providers (VODAFONE, VERIZON, SINGTEL, HUTCH, AIRTEL, GIO)

Keycloak realm is pre-imported with matching user/client data. Test credentials: `ib_admin@javatodev.com / 5V7huE3G86uB`.
