# Knowledge Base

> **Repository:** ts-java-spring-boot-internet-banking-microservices  
> **Date:** 2026-05-07  
> **Stack:** Java 21 / Spring Boot 3.2.4 / Spring Cloud 2023.0.0

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Data Model Documentation](#2-data-model-documentation)
3. [API Surface Map](#3-api-surface-map)
4. [Key Business Logic Inventory](#4-key-business-logic-inventory)
5. [Integration Points](#5-integration-points)
6. [Build and Deployment Summary](#6-build-and-deployment-summary)

---

## 1. Architecture Overview

### 1.1 Services

| Service | Port | Type | Key Dependencies |
|---|---|---|---|
| `internet-banking-api-gateway` | 8082 | Infrastructure — API routing + OAuth2/JWT security | Spring Cloud Gateway, Eureka Client, OAuth2 Resource Server, WebFlux |
| `internet-banking-service-registry` | 8081 | Infrastructure — Service discovery | Netflix Eureka Server |
| `internet-banking-config-server` | 8090 | Infrastructure — Centralized configuration | Spring Cloud Config Server (GitHub-backed) |
| `core-banking-service` | 8092 | Business — System of record for accounts, users, transactions | Spring Data JPA, Flyway, MySQL Connector, Eureka Client |
| `internet-banking-fund-transfer-service` | 8084 | Business — Fund transfer orchestration | Spring Data JPA, OpenFeign, Eureka Client, MySQL Connector |
| `internet-banking-utility-payment-service` | 8085 | Business — Utility payment orchestration | Spring Data JPA, OpenFeign, Eureka Client, MySQL Connector |
| `internet-banking-user-service` | 8083 | Business — User registration and lifecycle | Spring Data JPA, OpenFeign, Keycloak Admin Client 24.0.4, Feign OkHttp |

### 1.2 Communication Patterns

```
                          Clients
                             |
                             v
                    +------------------+
                    |   API Gateway    |  OAuth2/JWT validation
                    |     (8082)       |  X-Auth-Id header injection
                    +--------+---------+
                             |  HTTP routing via Eureka discovery
              +--------------+--------------+
              |              |              |
              v              v              v
    +---------+---+  +------+-------+  +---+-----------+
    | User Svc    |  | Fund Xfer    |  | Utility Pay   |
    |   (8083)    |  | Svc (8084)   |  | Svc (8085)    |
    +------+------+  +------+-------+  +-------+-------+
           |                |                   |
           |   Feign (sync) |    Feign (sync)   |
           v                v                   v
    +------+----------------+-------------------+------+
    |              Core Banking Service (8092)          |
    +--------------------------------------------------+
                             |
                        MySQL 8.4 (3306)
```

- **All inter-service communication is synchronous REST via OpenFeign** with service discovery through Eureka
- **No async messaging** — RabbitMQ is mentioned in documentation but not used in any service code
- **Identity propagation:** Gateway extracts JWT principal name and injects `X-Auth-Id` header; downstream services read this via `AppAuthUserFilter`
- **Configuration:** All services fetch config from Config Server at bootstrap via `spring.cloud.config.uri`

### 1.3 Profiles

| Profile | Purpose |
|---|---|
| `dev` (default) | Local development; Config Server at `localhost:8090` |
| `docker` | Docker Compose; Config Server at container hostname; activated via `-Dspring.profiles.active=docker` |

---

## 2. Data Model Documentation

### 2.1 Database Layout

| Schema | Service Owner | Tables |
|---|---|---|
| `banking_core_service` | core-banking-service | `banking_core_user`, `banking_core_account`, `banking_core_utility_account`, `banking_core_transaction` |
| `banking_core_fund_transfer_service` | fund-transfer-service | `fund_transfer` |
| `banking_core_user_service` | user-service | `user` |
| `banking_core_utility_payment_service` | utility-payment-service | `utility_payment` |

All 4 schemas reside on the same MySQL 8.4 instance. DB user: `javatodev_development`.

### 2.2 Core Banking Schema

**`banking_core_user`**

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `email` | VARCHAR(255) | | User email address |
| `first_name` | VARCHAR(255) | | |
| `last_name` | VARCHAR(255) | | |
| `identification_number` | VARCHAR(255) | | National ID / NIC |

**`banking_core_account`**

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `number` | VARCHAR(255) | | Account number (12-digit string) |
| `type` | VARCHAR(255) | | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `available_balance` | DECIMAL(19,2) | | Withdrawable balance |
| `actual_balance` | DECIMAL(19,2) | | Ledger balance |
| `user_id` | BIGINT | FK → `banking_core_user.id` | Account owner |

**`banking_core_utility_account`**

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `number` | VARCHAR(255) | | Utility provider account number |
| `provider_name` | VARCHAR(255) | | e.g., VODAFONE, AIRTEL, GIO |

**`banking_core_transaction`**

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `amount` | DECIMAL(19,2) | | Signed: negative for debits, positive for credits |
| `transaction_type` | VARCHAR(30) | NOT NULL | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | NOT NULL | Counterparty account or bill reference |
| `transaction_id` | VARCHAR(50) | NOT NULL | UUID correlation ID |
| `account_id` | BIGINT | FK → `banking_core_account.id` | Affected account |

**Flyway migrations** (3 files in `db/migration/`):
- `V1.0.20210427174638` — Create `banking_core_user`, `banking_core_account`, `banking_core_utility_account`
- `V1.0.20210427174721` — Seed data: 4 users, 14 accounts, 6 utility providers
- `V1.0.20210429210839` — Create `banking_core_transaction`

### 2.3 Fund Transfer Schema

**`fund_transfer`** (JPA-managed, no Flyway)

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK) | |
| `transaction_reference` | VARCHAR | UUID from core-banking response |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `status` | VARCHAR | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` | INSTANT | AuditAware |
| `created_by` | VARCHAR | AuditAware |
| `modified_date` | INSTANT | AuditAware |
| `modified_by` | VARCHAR | AuditAware |
| `version` | BIGINT | Optimistic locking |

### 2.4 User Service Schema

**`user`** (JPA-managed, no Flyway)

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK) | |
| `auth_id` | VARCHAR | Keycloak user UUID |
| `identification` | VARCHAR | NIC number |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` / `modified_date` | INSTANT | AuditAware |
| `created_by` / `modified_by` | VARCHAR | AuditAware |
| `version` | BIGINT | Optimistic locking |

### 2.5 Utility Payment Schema

**`utility_payment`** (JPA-managed, no Flyway)

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK) | |
| `provider_id` | BIGINT | Utility provider internal ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Bill/customer reference |
| `account` | VARCHAR | Source bank account number |
| `transaction_id` | VARCHAR | UUID from core-banking response |
| `status` | VARCHAR | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `modified_date` | INSTANT | AuditAware |
| `version` | BIGINT | Optimistic locking |

### 2.6 Entity Relationship Diagram

```
banking_core_user (1) ----< (N) banking_core_account (1) ----< (N) banking_core_transaction
                                                                        |
                                                        [transaction_id links to]
                                                                        |
                                    fund_transfer.transaction_reference -+- utility_payment.transaction_id
```

---

## 3. API Surface Map

### 3.1 API Gateway Routes

| Gateway Path Prefix | Target Service | Example |
|---|---|---|
| `/user/**` | internet-banking-user-service | `POST /user/api/v1/bank-users/register` |
| `/fund-transfer/**` | internet-banking-fund-transfer-service | `POST /fund-transfer/api/v1/transfer` |
| `/payment/**` | internet-banking-utility-payment-service | `POST /payment/api/v1/utility-payment` |
| `/core/**` | core-banking-service | `GET /core/api/v1/account/bank-account/{num}` |

Public endpoint (no auth): `POST /user/api/v1/bank-users/register`  
All other endpoints require OAuth2 Bearer token.

### 3.2 Core Banking Service Endpoints

| Method | Path | Request Body | Response | Description |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | — | `BankAccount` (id, number, type, status, availableBalance, actualBalance, user) | Get bank account by number |
| `GET` | `/api/v1/account/util-account/{account_name}` | — | `UtilityAccount` (id, number, providerName) | Get utility account by provider name |
| `POST` | `/api/v1/transaction/fund-transfer` | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` | Execute fund transfer (debit + credit) |
| `POST` | `/api/v1/transaction/util-payment` | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` | Execute utility payment (debit) |
| `GET` | `/api/v1/user/{identification}` | — | `User` (id, firstName, lastName, email, identificationNumber) | Get user by NIC |
| `GET` | `/api/v1/user` | Pageable query params | `List<User>` | List users (paginated) |

### 3.3 Fund Transfer Service Endpoints

| Method | Path | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | `{ fromAccount, toAccount, amount, authID }` | `{ message, transactionId }` | Initiate fund transfer |
| `GET` | `/api/v1/transfer` | Pageable query params | `List<FundTransfer>` | List fund transfers (paginated) |

### 3.4 Utility Payment Service Endpoints

| Method | Path | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` | Process utility payment |
| `GET` | `/api/v1/utility-payment` | Pageable query params | `List<UtilityPayment>` | List utility payments (paginated) |

### 3.5 User Service Endpoints

| Method | Path | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | `{ email, identification, password }` | `User` (id, email, identification, authId, status) | Register new user (PUBLIC) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `{ status }` | `User` | Update user status (admin approval) |
| `GET` | `/api/v1/bank-users` | Pageable query params | `List<User>` | List users (paginated) |
| `GET` | `/api/v1/bank-users/{id}` | — | `User` | Get user by ID |

### 3.6 Infrastructure Endpoints

All services expose Spring Boot Actuator at `/actuator/**` (permitted without auth at the gateway).

---

## 4. Key Business Logic Inventory

### 4.1 Fund Transfer Flow

```
Client                  Fund Transfer Svc           Core Banking Svc
  |                           |                           |
  |-- POST /transfer -------->|                           |
  |                           |-- save entity (PENDING) ->|
  |                           |-- POST /fund-transfer --->|
  |                           |                           |-- lookup fromAccount
  |                           |                           |-- lookup toAccount
  |                           |                           |-- validateBalance(from, amount)
  |                           |                           |-- debit fromAccount
  |                           |                           |-- credit toAccount
  |                           |                           |-- save 2 TransactionEntities
  |                           |                           |-- return {transactionId}
  |                           |<-- {message, txId} -------|
  |                           |-- update entity (SUCCESS) |
  |<-- {message, txId} -------|                           |
```

**Business rules:**
- Balance validation: `actualBalance >= 0 && actualBalance >= amount` (else `InsufficientFundsException`)
- Two ledger entries created: one debit (negative amount) on sender, one credit (positive) on receiver
- Transaction ID: server-generated UUID
- `@Transactional` on core-banking `TransactionService` class (covers debit + credit atomically)
- **Known bug:** Available balance double-deducted — `availableBalance = actualBalance.subtract(amount)` applied after `actualBalance` was already reduced

### 4.2 Utility Payment Flow

```
Client                  Utility Payment Svc         Core Banking Svc
  |                           |                           |
  |-- POST /utility-payment ->|                           |
  |                           |-- save entity (PROCESSING)|
  |                           |-- POST /util-payment ---->|
  |                           |                           |-- lookup bankAccount
  |                           |                           |-- validateBalance(account, amount)
  |                           |                           |-- lookup utilityAccount by providerId
  |                           |                           |-- debit bankAccount
  |                           |                           |-- save TransactionEntity
  |                           |                           |-- return {transactionId}
  |                           |<-- {message, txId} -------|
  |                           |-- update entity (SUCCESS) |
  |<-- {message, txId} -------|                           |
```

**Business rules:**
- Same balance validation as fund transfer
- Only the payer's account is debited; no credit entry on utility provider side
- Comment in code: `"we can call third party API to process UTIL payment from payment provider from here"` — not implemented
- Same available balance double-deduction bug

### 4.3 User Registration Flow

```
Client                  User Service                Keycloak            Core Banking
  |                        |                           |                     |
  |-- POST /register ----->|                           |                     |
  |                        |-- search email in KC ---->|                     |
  |                        |<-- userRepresentations ---|                     |
  |                        |   (reject if non-empty)   |                     |
  |                        |-- GET /user/{NIC} ------->|                     |
  |                        |                           |<--- user lookup ----|
  |                        |   (validate email match)  |                     |
  |                        |-- create KC user -------->|                     |
  |                        |<-- 201 Created ---------- |                     |
  |                        |-- search email in KC ---->|                     |
  |                        |<-- get authId ----------- |                     |
  |                        |-- save UserEntity (PENDING)                     |
  |<-- User (PENDING) -----|                                                 |
```

**Business rules:**
- Email must not already exist in Keycloak
- NIC must exist in core-banking user table
- Email in request must match email in core-banking
- User created in Keycloak with `enabled=false`, `emailVerified=false`
- Local entity saved with `PENDING` status
- Admin approves via `PATCH /update/{id}` with `status=APPROVED`, which enables the Keycloak user and sets `emailVerified=true`

### 4.4 Balance Validation

```java
if (bankAccount.getActualBalance().compareTo(BigDecimal.ZERO) < 0
    || bankAccount.getActualBalance().compareTo(amount) < 0) {
    throw new InsufficientFundsException(...);
}
```

- Checks actual balance only (not available balance)
- No minimum amount check
- No maximum amount / daily limit check
- No account status check (BLOCKED/DORMANT accounts can transact)

---

## 5. Integration Points

### 5.1 Identity Provider — Keycloak 23.0.7

| Aspect | Details |
|---|---|
| **Realm** | Imported from `docker-compose/keycloak/` on startup |
| **Admin Console** | `http://localhost:8080` (admin/password) |
| **Integration** | User-service uses `keycloak-admin-client:24.0.4` for user CRUD |
| **JWT Validation** | API Gateway validates tokens via `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` |
| **Service Account** | Used by Keycloak Admin Client for programmatic user management (`KeycloakManager`) |

### 5.2 Message Broker — RabbitMQ

| Aspect | Details |
|---|---|
| **Status** | **Not implemented** — mentioned in README/docs but no RabbitMQ dependency in any `build.gradle`, no AMQP configuration, no message producers/consumers |
| **Opportunity** | Payment event notifications, async processing, audit event streaming |

### 5.3 Distributed Tracing — Zipkin 3

| Aspect | Details |
|---|---|
| **Dashboard** | `http://localhost:9411` |
| **Integration** | All services include `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` |
| **Feign Tracing** | `feign-micrometer` included for propagating trace context across Feign calls |
| **Sampling** | Default (configured via Config Server — not visible in local `application.yml`) |

### 5.4 Database — MySQL 8.4

| Aspect | Details |
|---|---|
| **Instance** | Single MySQL 8.4 container on port 3306 |
| **Schemas** | 4: `banking_core_service`, `banking_core_fund_transfer_service`, `banking_core_user_service`, `banking_core_utility_payment_service` |
| **User** | `javatodev_development` with broad privileges (CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES on `*.*`) |
| **Migrations** | Flyway in core-banking-service only; other services use JPA auto-DDL (`hibernate.ddl-auto`) |
| **Connection** | Via MySQL Connector/J 8.4.0 |

### 5.5 Service Discovery — Netflix Eureka

| Aspect | Details |
|---|---|
| **Dashboard** | `http://localhost:8081` |
| **All services** register as Eureka clients |
| **Feign clients** use service names (e.g., `@FeignClient(name = "core-banking-service")`) resolved via Eureka |

### 5.6 Configuration — Spring Cloud Config Server

| Aspect | Details |
|---|---|
| **Port** | 8090 |
| **Backend** | GitHub repository (URI configured in config-server's `application.yml`) |
| **Bootstrap** | Services use `bootstrap.yml` with `spring.cloud.config.uri` |
| **Profiles** | `dev` (localhost), `docker` (container hostname) |

---

## 6. Build and Deployment Summary

### 6.1 Build System

- **Build tool:** Gradle (per-service, no multi-module root project)
- **Java:** 21 (source compatibility)
- **Spring Boot:** 3.2.4 via Spring Boot Gradle Plugin
- **Spring Cloud:** 2023.0.0 BOM
- **Lombok:** compile-only + annotation processor
- **Test framework:** JUnit 5 (via `spring-boot-starter-test`), H2 for test DB

### 6.2 Docker

- **Base image:** `eclipse-temurin:21-jre-alpine` (per Dockerfile)
- **Build:** Each service: `./gradlew clean build` → `docker build -t javatodev/{service-name} .`
- **Startup ordering:** `wait-for-it.sh` with 50s timeout chains (registry → config → mysql → app)

### 6.3 Docker Compose

| File | Contents |
|---|---|
| `docker-compose.yml` | All 6 services + MySQL + Keycloak + PostgreSQL + Zipkin |
| `docker-compose-support-apps.yml` | Support apps only (for running services locally) |

**Network:** `javatodev_ib_network` (bridge, `172.25.0.0/16`) with static IPs:

| IP | Service |
|---|---|
| 172.25.0.2 | core-banking-service |
| 172.25.0.3 | utility-payment-service |
| 172.25.0.4 | fund-transfer-service |
| 172.25.0.5 | user-service |
| 172.25.0.6 | api-gateway |
| 172.25.0.7 | service-registry |
| 172.25.0.8 | config-server |
| 172.25.0.9 | MySQL |
| 172.25.0.10 | Keycloak PostgreSQL |
| 172.25.0.11 | Keycloak |
| 172.25.0.12 | Zipkin |

### 6.4 CI/CD

**No CI/CD pipeline** is configured in the repository. No GitHub Actions workflows, no Jenkinsfile, no GitLab CI config.

### 6.5 OpenAPI / Swagger

All 4 business services include `springdoc-openapi-starter-webflux-ui:2.1.0`. **Note:** This is the WebFlux variant, but the services (except gateway) use Spring MVC — should be `springdoc-openapi-starter-webmvc-ui`. Swagger UI may not function correctly.
