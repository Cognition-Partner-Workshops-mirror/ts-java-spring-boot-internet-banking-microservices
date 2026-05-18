# Internet Banking Microservices — Application Knowledge Base

<!-- Comprehensive technical reference for the internet banking microservices platform. -->
<!-- Generated from source code analysis of all 6 services, infrastructure configs, and tests. -->

## Table of Contents

- [1. Architecture Overview](#1-architecture-overview)
- [2. Data Model Documentation](#2-data-model-documentation)
- [3. API Surface Map](#3-api-surface-map)
- [4. Key Business Logic Inventory](#4-key-business-logic-inventory)
- [5. Integration Points](#5-integration-points)
- [6. Build and Deployment Pipeline Summary](#6-build-and-deployment-pipeline-summary)

---

## 1. Architecture Overview

### 1.1 System Summary

<!-- High-level description of the platform and its technology stack. -->

A containerized internet banking platform built with **Java 21**, **Spring Boot 3.2.4**, and **Spring Cloud 2023.0.0**. The system comprises 6 microservices implementing user management, fund transfers, utility bill payments, and core banking ledger operations.

### 1.2 Service Inventory

| Service | Port | Framework | Purpose |
|---|---|---|---|
| `internet-banking-api-gateway` | 8082 | Spring Cloud Gateway (WebFlux) | Edge router, OAuth2/JWT enforcement, request proxying |
| `internet-banking-service-registry` | 8081 | Spring Cloud Netflix Eureka Server | Dynamic service discovery |
| `internet-banking-config-server` | 8090 | Spring Cloud Config Server | Centralized externalized configuration (Git-backed) |
| `core-banking-service` | 8092 | Spring Boot Web (MVC) | System of record: users, accounts, transactions, ledger |
| `internet-banking-fund-transfer-service` | 8084 | Spring Boot Web (MVC) | Orchestrates account-to-account money movement |
| `internet-banking-utility-payment-service` | 8085 | Spring Boot Web (MVC) | Orchestrates bill payments to utility providers |
| `internet-banking-user-service` | 8083 | Spring Boot Web (MVC) | User registration, status management, Keycloak integration |

### 1.3 Communication Patterns

<!-- All inter-service communication is synchronous REST via OpenFeign through Eureka discovery. -->

```
┌──────────────┐      ┌───────────────────────┐
│   Client     │─────>│   API Gateway (8082)  │
│   (Browser)  │      │   OAuth2 + JWT        │
└──────────────┘      └──────┬────────────────┘
                             │  Routes via path prefix
               ┌─────────────┼─────────────────────┐
               │             │                     │
               v             v                     v
  ┌────────────────┐ ┌──────────────┐  ┌─────────────────────┐
  │ User Service   │ │ Fund Transfer│  │ Utility Payment     │
  │ (8083)         │ │ Service(8084)│  │ Service (8085)      │
  └──────┬─────────┘ └──────┬───────┘  └──────┬──────────────┘
         │  Feign            │  Feign          │  Feign
         v                   v                 v
  ┌──────────────────────────────────────────────────┐
  │            Core Banking Service (8092)            │
  │    Users, Accounts, Transactions, Utility Accts   │
  └──────────────────────────────────────────────────┘
```

- **Synchronous REST** via OpenFeign — all inter-service calls use Feign clients resolved through Eureka.
- **No asynchronous messaging** — RabbitMQ is referenced in some documentation but is **not implemented** in the codebase.
- **Gateway routing** — path-prefix based: `/user/**` -> user-service, `/fund-transfer/**` -> fund-transfer-service, `/payment/**` -> utility-payment-service, `/core/**` -> core-banking-service.
- **Auth propagation** — Gateway extracts JWT principal and forwards it as `X-Auth-Id` header to downstream services via a `GlobalFilter`.

### 1.4 Infrastructure Components

| Component | Technology | Version | Purpose |
|---|---|---|---|
| Database | MySQL | 8.4.0 | Primary data store (4 schemas on single instance) |
| Identity Provider | Keycloak | 23.0.7 | OAuth2/OIDC, user management, realm configuration |
| Keycloak DB | PostgreSQL | 15 | Backing store for Keycloak |
| Distributed Tracing | Zipkin | 3 | Trace collection and visualization |
| Tracing Bridge | Micrometer Tracing (Brave) | — | Instrumentation bridge for Zipkin |
| Schema Migrations | Flyway | 10.12.0 | Versioned DB migrations (core-banking-service only) |
| Container Orchestration | Docker Compose | 3.6 | Local development deployment |

---

## 2. Data Model Documentation

### 2.1 Database Schema Overview

<!-- Four separate MySQL schemas on a single MySQL 8.4 instance. User: javatodev_development. -->

All four schemas reside on a single MySQL 8.4 instance. The database user is `javatodev_development`.

### 2.2 Schema: `banking_core_service`

<!-- Managed by Flyway migrations. Core system of record for users, accounts, and transactions. -->

#### `banking_core_user`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT` | PK, AUTO_INCREMENT | Surrogate key |
| `first_name` | `VARCHAR(255)` | — | User's first name |
| `last_name` | `VARCHAR(255)` | — | User's last name |
| `email` | `VARCHAR(255)` | — | Email address |
| `identification_number` | `VARCHAR(255)` | — | National Identity Card (NIC) number |

#### `banking_core_account`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT` | PK, AUTO_INCREMENT | Surrogate key |
| `number` | `VARCHAR(255)` | — | Account number (e.g., `100015003000`) |
| `type` | `VARCHAR(255)` | ENUM string | `SAVINGS_ACCOUNT` (only type seeded) |
| `status` | `VARCHAR(255)` | ENUM string | `ACTIVE` / `INACTIVE` |
| `available_balance` | `DECIMAL(19,2)` | — | Balance available for transactions |
| `actual_balance` | `DECIMAL(19,2)` | — | Ledger balance |
| `user_id` | `BIGINT` | FK -> `banking_core_user.id` | Account owner |

#### `banking_core_transaction`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT` | PK, AUTO_INCREMENT | Surrogate key |
| `amount` | `DECIMAL(19,2)` | — | Transaction amount (negative for debits) |
| `transaction_type` | `VARCHAR(30)` | NOT NULL | `FUND_TRANSFER` or `UTILITY_PAYMENT` |
| `reference_number` | `VARCHAR(50)` | NOT NULL | Target account number or reference |
| `transaction_id` | `VARCHAR(50)` | NOT NULL | UUID grouping related debit/credit entries |
| `account_id` | `BIGINT` | FK -> `banking_core_account.id` | Associated account |

#### `banking_core_utility_account`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT` | PK, AUTO_INCREMENT | Surrogate key |
| `number` | `VARCHAR(255)` | — | Provider account number |
| `provider_name` | `VARCHAR(255)` | — | Utility provider (e.g., VODAFONE, AIRTEL) |

### 2.3 Schema: `banking_core_fund_transfer_service`

<!-- JPA-managed (auto-DDL). Tracks fund transfer orchestration state. -->

#### `fund_transfer`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT` | PK, AUTO_INCREMENT | Surrogate key |
| `transaction_reference` | `VARCHAR(255)` | — | UUID from core-banking transaction |
| `from_account` | `VARCHAR(255)` | — | Source account number |
| `to_account` | `VARCHAR(255)` | — | Destination account number |
| `amount` | `DECIMAL(19,2)` | — | Transfer amount |
| `status` | `VARCHAR(255)` | ENUM string | `PENDING` -> `SUCCESS` |
| `created_date` | `TIMESTAMP` | Audit | Record creation timestamp |
| `created_by` | `VARCHAR(255)` | Audit | Creator identifier |
| `modified_date` | `TIMESTAMP` | Audit | Last modification timestamp |
| `modified_by` | `VARCHAR(255)` | Audit | Last modifier identifier |
| `version` | `BIGINT` | Optimistic lock | JPA `@Version` field |

### 2.4 Schema: `banking_core_user_service`

<!-- JPA-managed. Links local user records to Keycloak identities. -->

#### `user`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT` | PK, AUTO_INCREMENT | Surrogate key |
| `auth_id` | `VARCHAR(255)` | — | Keycloak user UUID |
| `identification` | `VARCHAR(255)` | — | NIC number (links to core-banking user) |
| `status` | `VARCHAR(255)` | ENUM string | `PENDING` -> `APPROVED` |
| `created_date` | `TIMESTAMP` | Audit | Record creation timestamp |
| `created_by` | `VARCHAR(255)` | Audit | Creator identifier |
| `modified_date` | `TIMESTAMP` | Audit | Last modification timestamp |
| `modified_by` | `VARCHAR(255)` | Audit | Last modifier identifier |
| `version` | `BIGINT` | Optimistic lock | JPA `@Version` field |

### 2.5 Schema: `banking_core_utility_payment_service`

<!-- JPA-managed. Tracks utility payment orchestration state. -->

#### `utility_payment`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT` | PK, AUTO_INCREMENT | Surrogate key |
| `provider_id` | `BIGINT` | — | Utility provider ID |
| `amount` | `DECIMAL(19,2)` | — | Payment amount |
| `reference_number` | `VARCHAR(255)` | — | Bill reference number |
| `account` | `VARCHAR(255)` | — | Paying account number |
| `transaction_id` | `VARCHAR(255)` | — | UUID from core-banking transaction |
| `status` | `VARCHAR(255)` | ENUM string | `PROCESSING` -> `SUCCESS` |
| `created_date` | `TIMESTAMP` | Audit | Record creation timestamp |
| `created_by` | `VARCHAR(255)` | Audit | Creator identifier |
| `modified_date` | `TIMESTAMP` | Audit | Last modification timestamp |
| `modified_by` | `VARCHAR(255)` | Audit | Last modifier identifier |
| `version` | `BIGINT` | Optimistic lock | JPA `@Version` field |

### 2.6 Entity Relationship Diagram

```
banking_core_service:
  banking_core_user 1──* banking_core_account 1──1 banking_core_transaction
  banking_core_utility_account (standalone)

banking_core_fund_transfer_service:
  fund_transfer (standalone, references account numbers as strings)

banking_core_user_service:
  user (standalone, references core user by NIC, Keycloak by auth_id)

banking_core_utility_payment_service:
  utility_payment (standalone, references account number and provider ID as values)
```

<!-- Cross-service references are by value (account numbers, NIC) rather than foreign keys. -->

---

## 3. API Surface Map

### 3.1 API Gateway Routes

<!-- Gateway strips the prefix and forwards to the target service. -->

| Gateway Path Prefix | Target Service | Eureka Service Name |
|---|---|---|
| `/user/**` | internet-banking-user-service | `internet-banking-user-service` |
| `/fund-transfer/**` | internet-banking-fund-transfer-service | `internet-banking-fund-transfer-service` |
| `/payment/**` | internet-banking-utility-payment-service | `internet-banking-utility-payment-service` |
| `/core/**` | core-banking-service | `core-banking-service` |

### 3.2 Core Banking Service (port 8092)

| Method | Endpoint | Auth | Request Body | Response | Description |
|---|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Internal | — | `BankAccount` | Get bank account by account number |
| `GET` | `/api/v1/account/util-account/{account_name}` | Internal | — | `UtilityAccount` | Get utility account by provider name |
| `POST` | `/api/v1/transaction/fund-transfer` | Internal | `FundTransferRequest` | `FundTransferResponse` | Process fund transfer (debit/credit) |
| `POST` | `/api/v1/transaction/util-payment` | Internal | `UtilityPaymentRequest` | `UtilityPaymentResponse` | Process utility payment (debit) |
| `GET` | `/api/v1/user/{identification}` | Internal | — | `User` | Get user by NIC identification |
| `GET` | `/api/v1/user` | Internal | `Pageable` (query params) | `List<User>` | List users (paginated) |

**Request/Response DTOs:**

```java
// FundTransferRequest
{ "fromAccount": "string", "toAccount": "string", "amount": BigDecimal }

// FundTransferResponse
{ "message": "string", "transactionId": "string (UUID)" }

// UtilityPaymentRequest
{ "providerId": "string", "amount": BigDecimal, "referenceNumber": "string", "account": "string" }

// UtilityPaymentResponse
{ "message": "string", "transactionId": "string (UUID)" }
```

### 3.3 Internet Banking User Service (port 8083)

| Method | Endpoint | Auth | Request Body | Response | Description |
|---|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | **Public** | `User` | `User` | Register new user (creates Keycloak account) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | JWT | `UserUpdateRequest` | `User` | Update user status (e.g., approve) |
| `GET` | `/api/v1/bank-users` | JWT | `Pageable` (query params) | `List<User>` | List registered users (paginated) |
| `GET` | `/api/v1/bank-users/{id}` | JWT | — | `User` | Get user by internal ID |

**Request/Response DTOs:**

```java
// User (request for register)
{ "email": "string", "identification": "string (NIC)", "password": "string" }

// User (response)
{ "id": Long, "email": "string", "identification": "string", "authId": "string (UUID)",
  "status": "PENDING|APPROVED", "version": Long }

// UserUpdateRequest
{ "status": "PENDING|APPROVED" }
```

### 3.4 Internet Banking Fund Transfer Service (port 8084)

| Method | Endpoint | Auth | Request Body | Response | Description |
|---|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | JWT | `FundTransferRequest` | `FundTransferResponse` | Initiate fund transfer |
| `GET` | `/api/v1/transfer` | JWT | `Pageable` (query params) | `List<FundTransfer>` | List all transfers (paginated) |

**Request/Response DTOs:**

```java
// FundTransferRequest
{ "fromAccount": "string", "toAccount": "string", "amount": BigDecimal, "authID": "string" }

// FundTransferResponse
{ "message": "string", "transactionId": "string (UUID)" }
```

### 3.5 Internet Banking Utility Payment Service (port 8085)

| Method | Endpoint | Auth | Request Body | Response | Description |
|---|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | JWT | `UtilityPaymentRequest` | `UtilityPaymentResponse` | Process utility payment |
| `GET` | `/api/v1/utility-payment` | JWT | `Pageable` (query params) | `List<UtilityPayment>` | List all payments (paginated) |

**Request/Response DTOs:**

```java
// UtilityPaymentRequest
{ "providerId": Long, "amount": BigDecimal, "referenceNumber": "string", "account": "string" }

// UtilityPaymentResponse
{ "message": "string", "transactionId": "string (UUID)" }
```

### 3.6 Infrastructure Endpoints

| Service | Endpoint | Purpose |
|---|---|---|
| All services | `/actuator/health` | Health check (Spring Boot Actuator) |
| All services | `/actuator/info` | Application info (git properties) |
| Service Registry | `http://localhost:8081/` | Eureka Dashboard |
| Config Server | `http://localhost:8090/{app}/{profile}` | Configuration retrieval |
| Zipkin | `http://localhost:9411/` | Trace visualization UI |

---

## 4. Key Business Logic Inventory

### 4.1 Fund Transfer Flow

<!-- Orchestrated by fund-transfer-service, executed by core-banking-service. -->

```
1. Client -> Gateway -> Fund Transfer Service: POST /api/v1/transfer
2. Fund Transfer Service:
   a. Save FundTransferEntity with status=PENDING
   b. Feign call -> Core Banking: POST /api/v1/transaction/fund-transfer
3. Core Banking Service (TransactionService.fundTransfer):
   a. Look up source account (fromAccount) — throws EntityNotFoundException if missing
   b. Look up destination account (toAccount) — throws EntityNotFoundException if missing
   c. Validate source account balance >= transfer amount — throws InsufficientFundsException
   d. Call internalFundTransfer():
      - Debit source: actualBalance -= amount, availableBalance = actualBalance - amount
      - Save debit TransactionEntity (amount negative)
      - Credit destination: actualBalance += amount, availableBalance = actualBalance + amount
      - Save credit TransactionEntity (amount positive)
      - All within @Transactional
   e. Return transactionId (UUID)
4. Fund Transfer Service:
   a. Update entity: transactionReference = transactionId, status = SUCCESS
   b. Return response to client
```

**Known Bug:** Double-deduction in `internalFundTransfer()` — `availableBalance` is computed from the already-debited `actualBalance`, causing a double subtraction on the source account. The same pattern affects the credit side (double addition on destination).

### 4.2 Utility Payment Flow

<!-- Orchestrated by utility-payment-service, executed by core-banking-service. -->

```
1. Client -> Gateway -> Utility Payment Service: POST /api/v1/utility-payment
2. Utility Payment Service:
   a. Save UtilityPaymentEntity with status=PROCESSING
   b. Feign call -> Core Banking: POST /api/v1/transaction/util-payment
3. Core Banking Service (TransactionService.utilPayment):
   a. Look up paying account — throws EntityNotFoundException if missing
   b. Validate account balance >= payment amount — throws InsufficientFundsException
   c. Look up utility provider account — throws EntityNotFoundException if missing
   d. Debit paying account: actualBalance -= amount, availableBalance = actualBalance - amount
   e. Save debit TransactionEntity
   f. Return transactionId (UUID)
4. Utility Payment Service:
   a. Update entity: transactionId = response.transactionId, status = SUCCESS
   b. Return response to client
```

**Known Bug:** Same double-deduction pattern as fund transfer — `availableBalance` is set to `actualBalance - amount` after `actualBalance` was already reduced by `amount`.

### 4.3 User Registration Flow

<!-- Orchestrated by user-service, involves core-banking-service and Keycloak. -->

```
1. Client -> Gateway -> User Service: POST /api/v1/bank-users/register (PUBLIC endpoint)
2. User Service:
   a. Check Keycloak for existing user by email — throw UserAlreadyRegisteredException if found
   b. Feign call -> Core Banking: GET /api/v1/user/{identification}
      - Validates user exists in core banking by NIC
   c. Verify email matches core-banking record — throw InvalidEmailException if mismatch
   d. Create Keycloak user:
      - Set email, username (=email), firstName, lastName
      - Set temporary password from request
      - emailVerified=false, enabled=false
   e. If Keycloak returns 201:
      - Read back Keycloak user to get authId (UUID)
      - Save UserEntity with status=PENDING
   f. Return created user
3. Admin approves user: PATCH /api/v1/bank-users/update/{id} with status=APPROVED
   a. Enable Keycloak user (enabled=true, emailVerified=true)
   b. Update local entity status to APPROVED
```

### 4.4 Business Rules Summary

| Rule | Location | Description |
|---|---|---|
| Balance validation | `TransactionService.validateBalance()` | `actualBalance >= 0` AND `actualBalance >= amount` |
| Email uniqueness | `UserService.createUser()` | Checks Keycloak for existing email before registration |
| NIC verification | `UserService.createUser()` | User must exist in core-banking by NIC before registration |
| Email match | `UserService.createUser()` | Registration email must match core-banking user's email |
| Admin approval | `UserService.updateUser()` | Users start as PENDING; admin PATCH sets APPROVED and enables Keycloak account |
| Transfer status tracking | `FundTransferService` | PENDING -> SUCCESS (no FAILED state) |
| Payment status tracking | `UtilityPaymentService` | PROCESSING -> SUCCESS (no FAILED state) |

---

## 5. Integration Points

### 5.1 Keycloak (Identity & Access Management)

<!-- Only used by user-service and api-gateway. -->

| Aspect | Detail |
|---|---|
| Version | 23.0.7 |
| Protocol | OIDC / OAuth2 |
| Admin Client | `keycloak-admin-client:24.0.4` (user-service) |
| Auth Mode | `client_credentials` grant type |
| Configuration | `app.config.keycloak.*` properties (server-url, realm, clientId, client-secret) |
| Realm | Imported from `docker-compose/keycloak/realm-export.json` at startup |
| Gateway Integration | OAuth2 Resource Server with JWK Set URI for JWT validation |
| User Operations | Create user, update user (enable/disable), read user by email/ID, set credentials |

### 5.2 MySQL (Data Store)

| Aspect | Detail |
|---|---|
| Version | 8.4.0 |
| Schemas | `banking_core_service`, `banking_core_fund_transfer_service`, `banking_core_user_service`, `banking_core_utility_payment_service` |
| Connector | `mysql-connector-j:8.4.0` |
| User | `javatodev_development` with broad privileges (CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES) |
| Migrations | Flyway 10.12.0 for `banking_core_service` only; other schemas use JPA auto-DDL |
| Network | Static IP `172.25.0.9` on `javatodev_ib_network` |

### 5.3 Zipkin (Distributed Tracing)

| Aspect | Detail |
|---|---|
| Version | 3 |
| Bridge | Micrometer Tracing with Brave (`micrometer-tracing-bridge-brave`) |
| Reporter | `zipkin-reporter-brave` |
| Feign Tracing | `feign-micrometer` for propagating trace context across Feign calls |
| UI | `http://localhost:9411` |
| Network | Static IP `172.25.0.12` on `javatodev_ib_network` |

### 5.4 Spring Cloud Config Server

| Aspect | Detail |
|---|---|
| Git Repository | `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` |
| Search Path | `configuration` |
| Default Branch | `main` |
| Profiles | `dev` (local), `docker` (containerized) |
| Bootstrap | All services use `bootstrap.yml` / `bootstrap-{profile}.yml` to connect to config server |

### 5.5 Eureka (Service Discovery)

| Aspect | Detail |
|---|---|
| Server Port | 8081 |
| Self-Registration | Disabled (`register-with-eureka: false`) |
| All Services | Register as Eureka clients |
| Feign Resolution | Feign clients use Eureka service names (e.g., `core-banking-service`) for URL resolution |

### 5.6 RabbitMQ

<!-- Referenced in documentation but NOT implemented in the codebase. -->

**Not implemented.** RabbitMQ is mentioned in some external documentation but there are no RabbitMQ dependencies, configurations, or message producers/consumers anywhere in the codebase. All communication is synchronous REST.

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

<!-- Each service is an independent Gradle project. No multi-module root build. -->

- **Build Tool:** Gradle (each service has its own independent `build.gradle`)
- **No multi-module root project** — each service must be built separately
- **Java Version:** 21 (source compatibility)
- **Spring Boot:** 3.2.4 via Spring Boot Gradle Plugin
- **Spring Cloud:** 2023.0.0 BOM

**Build command per service:**
```bash
cd <service-directory> && ./gradlew clean build
```

### 6.2 Docker

<!-- Each service has its own Dockerfile based on eclipse-temurin:21-jre-alpine. -->

Each service includes:
- `Dockerfile` — based on `eclipse-temurin:21-jre-alpine`
- `wait-for-it.sh` — startup ordering script (50s timeout)

**Docker build per service:**
```bash
cd <service-directory> && docker build -t javatodev/<service-name> .
```

### 6.3 Docker Compose

Two compose files in `docker-compose/`:

| File | Contents |
|---|---|
| `docker-compose-support-apps.yml` | MySQL, Keycloak, PostgreSQL, Zipkin, Config Server, Service Registry |
| `docker-compose.yml` | All 6 services + all support infrastructure |

**Network:** `javatodev_ib_network` — bridge network with subnet `172.25.0.0/16`. All containers assigned static IPs.

**Startup ordering:** Services use chained `wait-for-it.sh` calls to wait for Service Registry (8081), Config Server (8090), and MySQL (3306) before starting.

**Volumes:**
- `postgres_data` — Keycloak PostgreSQL data
- `mysqldata` — MySQL data

### 6.4 CI/CD

<!-- No CI/CD pipeline is configured in the repository. -->

**No CI/CD pipeline** is configured in the repository. There are no GitHub Actions workflows, Jenkinsfiles, or other pipeline definitions. Builds and deployments are manual.

### 6.5 Dependency Summary (per service)

| Dependency | Core Banking | Fund Transfer | Utility Payment | User Service | API Gateway | Config Server | Service Registry |
|---|---|---|---|---|---|---|---|
| spring-boot-starter-web | x | x | x | x | — | — | x |
| spring-boot-starter-data-jpa | x | x | x | x | — | — | — |
| spring-cloud-gateway | — | — | — | — | x | — | — |
| spring-cloud-eureka-client | x | x | x | x | x | — | — |
| spring-cloud-eureka-server | — | — | — | — | — | — | x |
| spring-cloud-config-server | — | — | — | — | — | x | — |
| spring-cloud-config-client | x | x | x | x | x | — | — |
| spring-cloud-openfeign | — | x | x | x | — | — | — |
| spring-boot-actuator | x | x | x | x | x | x | x |
| micrometer-tracing-brave | x | x | x | x | x | — | — |
| zipkin-reporter-brave | x | x | x | x | x | — | — |
| springdoc-openapi (webflux-ui) | x | x | x | x | — | — | — |
| keycloak-admin-client | — | — | — | x | — | — | — |
| oauth2-resource-server | — | — | — | — | x | — | — |
| flyway-core | x | — | — | — | — | — | — |
| mysql-connector-j | x | x | x | x | — | — | — |
| lombok | x | x | x | x | — | — | — |
| h2 (test) | x | x | x | x | — | — | — |
