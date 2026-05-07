# Application Knowledge Base

## Table of Contents

- [1. Architecture Overview](#1-architecture-overview)
  - [1.1 Service Inventory](#11-service-inventory)
  - [1.2 Communication Patterns](#12-communication-patterns)
  - [1.3 Infrastructure Components](#13-infrastructure-components)
  - [1.4 Network Topology](#14-network-topology)
- [2. Data Model Documentation](#2-data-model-documentation)
  - [2.1 Core Banking Service](#21-core-banking-service)
  - [2.2 Internet Banking User Service](#22-internet-banking-user-service)
  - [2.3 Fund Transfer Service](#23-fund-transfer-service)
  - [2.4 Utility Payment Service](#24-utility-payment-service)
- [3. API Surface Map](#3-api-surface-map)
  - [3.1 API Gateway Routes](#31-api-gateway-routes)
  - [3.2 Core Banking Service Endpoints](#32-core-banking-service-endpoints)
  - [3.3 User Service Endpoints](#33-user-service-endpoints)
  - [3.4 Fund Transfer Service Endpoints](#34-fund-transfer-service-endpoints)
  - [3.5 Utility Payment Service Endpoints](#35-utility-payment-service-endpoints)
  - [3.6 Service Registry Endpoints](#36-service-registry-endpoints)
  - [3.7 Config Server Endpoints](#37-config-server-endpoints)
- [4. Key Business Logic Inventory](#4-key-business-logic-inventory)
  - [4.1 Fund Transfer Rules](#41-fund-transfer-rules)
  - [4.2 Utility Payment Processing](#42-utility-payment-processing)
  - [4.3 User Management](#43-user-management)
- [5. Integration Points](#5-integration-points)
  - [5.1 Keycloak (IAM)](#51-keycloak-iam)
  - [5.2 RabbitMQ (Messaging)](#52-rabbitmq-messaging)
  - [5.3 Zipkin (Distributed Tracing)](#53-zipkin-distributed-tracing)
  - [5.4 Database Connections](#54-database-connections)
  - [5.5 Spring Cloud Config](#55-spring-cloud-config)
  - [5.6 Netflix Eureka (Service Discovery)](#56-netflix-eureka-service-discovery)
- [6. Build and Deployment Pipeline](#6-build-and-deployment-pipeline)
  - [6.1 Build System](#61-build-system)
  - [6.2 Docker Compose Orchestration](#62-docker-compose-orchestration)
  - [6.3 Startup Order](#63-startup-order)

---

## 1. Architecture Overview

### 1.1 Service Inventory

The application consists of **6 microservices** organized in a layered architecture:

| Service | Port | Role | Spring Boot Profile |
|---|---|---|---|
| **internet-banking-api-gateway** | 8082 | Edge gateway, OAuth2 enforcement, request routing | Reactive (WebFlux) |
| **internet-banking-service-registry** | 8081 | Netflix Eureka server for service discovery | Standard |
| **internet-banking-config-server** | 8090 | Centralized configuration from Git | Standard |
| **core-banking-service** | 8092 | Authoritative ledger: accounts, users, transactions | Standard (Web + JPA) |
| **internet-banking-user-service** | 8083 | User lifecycle management with Keycloak integration | Standard (Web + JPA + Feign) |
| **internet-banking-fund-transfer-service** | 8084 | Orchestrates peer-to-peer fund transfers | Standard (Web + JPA + Feign) |
| **internet-banking-utility-payment-service** | 8085 | Processes third-party utility bill payments | Standard (Web + JPA + Feign) |

### 1.2 Communication Patterns

```
                          +-----------+
                          | Keycloak  |
                          | (AuthN)   |
                          +-----+-----+
                                |
                                | JWT validation
                                v
Client --> [API Gateway (8082)] --+--> [User Service (8083)] --Feign--> [Core Banking (8092)]
           (OAuth2 Resource     |
            Server + Routing)   +--> [Fund Transfer (8084)] --Feign--> [Core Banking (8092)]
                                |
                                +--> [Utility Payment (8085)] --Feign--> [Core Banking (8092)]

           All services register with --> [Eureka Registry (8081)]
           All services fetch config from --> [Config Server (8090)] --> GitHub Git repo
```

**Synchronous Communication:**
- **Client to Services**: HTTP/REST via API Gateway
- **Inter-Service**: Spring Cloud OpenFeign (service-to-service REST calls via Eureka discovery)
  - User Service -> Core Banking Service (`/api/v1/user/{identification}`)
  - Fund Transfer Service -> Core Banking Service (`/api/v1/account/bank-account/{account_number}`, `/api/v1/transaction/fund-transfer`)
  - Utility Payment Service -> Core Banking Service (`/api/v1/account/bank-account/{account_number}`, `/api/v1/transaction/util-payment`)

**Asynchronous Communication:**
- RabbitMQ is listed as a dependency in the architecture diagram but is **not yet implemented** in the current codebase. Intended for notification service integration.

**Gateway Routing:**
- The API Gateway forwards an `X-Auth-Id` header containing the authenticated principal name to downstream services via a `GlobalFilter`.

### 1.3 Infrastructure Components

| Component | Image/Version | Purpose |
|---|---|---|
| MySQL | 8.4.0 | Primary RDBMS for all business services |
| PostgreSQL | 15 | Keycloak identity store |
| Keycloak | 23.0.7 | Identity and Access Management (OAuth2/OIDC) |
| Zipkin | 3.x | Distributed tracing collection and UI |
| Spring Cloud Config Server | - | Externalized configuration from Git |
| Netflix Eureka | - | Service discovery and registration |

### 1.4 Network Topology

All containers run on a custom Docker bridge network `javatodev_ib_network` (`172.25.0.0/16`) with static IP assignments:

| Container | Static IP | Port |
|---|---|---|
| Zipkin | 172.25.0.12 | 9411 |
| Keycloak | 172.25.0.11 | 8080 |
| Keycloak PostgreSQL | 172.25.0.10 | 5432 (closed) |
| MySQL | 172.25.0.9 | 3306 |
| Config Server | 172.25.0.8 | 8090 |
| Service Registry | 172.25.0.7 | 8081 |
| API Gateway | 172.25.0.6 | 8082 |
| User Service | 172.25.0.5 | 8083 |
| Fund Transfer Service | 172.25.0.4 | 8084 |
| Utility Payment Service | 172.25.0.3 | 8085 |
| Core Banking Service | 172.25.0.2 | 8092 |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service

**Database:** `banking_core_service` (MySQL)

Schema managed by **Flyway** migrations (`src/main/resources/db/migration/`).

#### `banking_core_user`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `first_name` | VARCHAR(255) | | User's first name |
| `last_name` | VARCHAR(255) | | User's last name |
| `email` | VARCHAR(255) | | Email address |
| `identification_number` | VARCHAR(255) | | National ID (NIC), used for cross-service lookup |

#### `banking_core_account`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `number` | VARCHAR(255) | | Account number (e.g., `100015003000`) |
| `type` | VARCHAR(255) | | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `available_balance` | DECIMAL(19,2) | | Available balance |
| `actual_balance` | DECIMAL(19,2) | | Actual (ledger) balance |
| `user_id` | BIGINT | FK -> `banking_core_user.id` | Account owner |

**Relationships:** Many accounts to one user (`@ManyToOne` on `user_id`).

#### `banking_core_utility_account`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `number` | VARCHAR(255) | | Utility account number |
| `provider_name` | VARCHAR(255) | | Provider name (e.g., `VODAFONE`, `VERIZON`) |

#### `banking_core_transaction`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `amount` | DECIMAL(19,2) | | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | NOT NULL | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | NOT NULL | Counterparty account number or reference |
| `transaction_id` | VARCHAR(50) | NOT NULL | UUID transaction identifier |
| `account_id` | BIGINT | FK -> `banking_core_account.id` | Associated bank account |

**Entity Relationship Diagram:**

```
banking_core_user (1) ---< (N) banking_core_account (1) ---< (N) banking_core_transaction
                                                                    
banking_core_utility_account (standalone)
```

### 2.2 Internet Banking User Service

**Database:** `banking_core_user_service` (MySQL)

Schema managed by **JPA auto-DDL** (Hibernate).

#### `user`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `auth_id` | VARCHAR(255) | | Keycloak user ID |
| `identification` | VARCHAR(255) | | NIC linking to core banking |
| `status` | VARCHAR(255) | | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | TIMESTAMP | Audit | Creation timestamp |
| `created_by` | VARCHAR(255) | Audit | Creator |
| `modified_date` | TIMESTAMP | Audit | Last modification timestamp |
| `modified_by` | VARCHAR(255) | Audit | Last modifier |
| `version` | BIGINT | | Optimistic locking version |

Extends `AuditAware` (JPA `@MappedSuperclass` with `@EntityListeners(AuditingEntityListener.class)`).

### 2.3 Fund Transfer Service

**Database:** `banking_core_fund_transfer_service` (MySQL)

Schema managed by **JPA auto-DDL** (Hibernate).

#### `fund_transfer`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `from_account` | VARCHAR(255) | | Source account number |
| `to_account` | VARCHAR(255) | | Destination account number |
| `amount` | DECIMAL(19,2) | | Transfer amount |
| `transaction_reference` | VARCHAR(255) | | Transaction ID from core banking |
| `status` | VARCHAR(255) | | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` | TIMESTAMP | Audit | |
| `created_by` | VARCHAR(255) | Audit | |
| `modified_date` | TIMESTAMP | Audit | |
| `modified_by` | VARCHAR(255) | Audit | |
| `version` | BIGINT | | Optimistic locking |

### 2.4 Utility Payment Service

**Database:** `banking_core_utility_payment_service` (MySQL)

Schema managed by **JPA auto-DDL** (Hibernate).

#### `utility_payment`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `provider_id` | BIGINT | | Utility provider ID |
| `amount` | DECIMAL(19,2) | | Payment amount |
| `reference_number` | VARCHAR(255) | | Bill reference number |
| `account` | VARCHAR(255) | | Source bank account number |
| `transaction_id` | VARCHAR(255) | | Transaction ID from core banking |
| `status` | VARCHAR(255) | | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` | TIMESTAMP | Audit | |
| `created_by` | VARCHAR(255) | Audit | |
| `modified_date` | TIMESTAMP | Audit | |
| `modified_by` | VARCHAR(255) | Audit | |
| `version` | BIGINT | | Optimistic locking |

---

## 3. API Surface Map

### 3.1 API Gateway Routes

The API Gateway routes requests to downstream services via Eureka service names. Route configuration is externalized in the Spring Cloud Config Git repository. The gateway:
- **Permits unauthenticated access** to: `/user/api/v1/bank-users/register`, `/actuator/**`, and all service actuator paths.
- **Requires JWT authentication** for all other endpoints.
- Injects `X-Auth-Id` header (authenticated principal name) into all proxied requests.

Typical route prefix mapping (based on convention):

| Path Prefix | Target Service |
|---|---|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |
| `/banking-core/**` | core-banking-service |

### 3.2 Core Banking Service Endpoints

**Base URL:** `http://localhost:8092`

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | - | `BankAccount` |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | - | `UtilityAccount` |
| `GET` | `/api/v1/user/{identification}` | Get user by NIC | - | `User` |
| `GET` | `/api/v1/user` | List users (paginated) | - | `List<User>` |
| `POST` | `/api/v1/transaction/fund-transfer` | Process internal fund transfer | `FundTransferRequest` | `FundTransferResponse` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `UtilityPaymentRequest` | `UtilityPaymentResponse` |

**Request/Response Shapes:**

```json
// FundTransferRequest
{ "fromAccount": "string", "toAccount": "string", "amount": 0.00 }

// FundTransferResponse
{ "message": "string", "transactionId": "string (UUID)" }

// UtilityPaymentRequest
{ "providerId": 0, "amount": 0.00, "referenceNumber": "string", "account": "string" }

// UtilityPaymentResponse
{ "message": "string", "transactionId": "string (UUID)" }

// BankAccount
{ "id": 0, "number": "string", "type": "SAVINGS_ACCOUNT|FIXED_DEPOSIT|LOAN_ACCOUNT",
  "status": "PENDING|ACTIVE|DORMANT|BLOCKED",
  "availableBalance": 0.00, "actualBalance": 0.00,
  "user": { "id": 0, "firstName": "string", "lastName": "string", "email": "string",
            "identificationNumber": "string", "bankAccounts": [...] } }

// User (core)
{ "id": 0, "firstName": "string", "lastName": "string", "email": "string",
  "identificationNumber": "string", "bankAccounts": [...] }
```

### 3.3 User Service Endpoints

**Base URL:** `http://localhost:8083`

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Register new user (creates Keycloak + local record) | `User` | `User` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user status (approve/disable) | `UserUpdateRequest` | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated, enriched from Keycloak) | - | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | - | `User` |

**Request/Response Shapes:**

```json
// User (registration request)
{ "email": "string", "identification": "string", "password": "string" }

// User (response)
{ "id": 0, "email": "string", "identification": "string", "authId": "string",
  "status": "PENDING|APPROVED|DISABLED|BLACKLIST", "version": 0 }

// UserUpdateRequest
{ "status": "APPROVED|DISABLED|BLACKLIST" }
```

### 3.4 Fund Transfer Service Endpoints

**Base URL:** `http://localhost:8084`

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `FundTransferRequest` | `FundTransferResponse` |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | - | `List<FundTransfer>` |

**Request/Response Shapes:**

```json
// FundTransferRequest
{ "fromAccount": "string", "toAccount": "string", "amount": 0.00 }

// FundTransferResponse
{ "message": "string", "transactionId": "string (UUID)" }

// FundTransfer (list item)
{ "id": 0, "fromAccount": "string", "toAccount": "string", "amount": 0.00,
  "transactionReference": "string", "status": "PENDING|PROCESSING|SUCCESS|FAILED",
  "version": 0 }
```

### 3.5 Utility Payment Service Endpoints

**Base URL:** `http://localhost:8085`

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `UtilityPaymentRequest` | `UtilityPaymentResponse` |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | - | `List<UtilityPayment>` |

**Request/Response Shapes:**

```json
// UtilityPaymentRequest
{ "providerId": 0, "amount": 0.00, "referenceNumber": "string", "account": "string" }

// UtilityPaymentResponse
{ "message": "string", "transactionId": "string (UUID)" }
```

### 3.6 Service Registry Endpoints

**Base URL:** `http://localhost:8081`

| Method | Path | Description |
|---|---|---|
| `GET` | `/eureka/apps` | List registered services |
| `GET` | `/` | Eureka dashboard (web UI) |

### 3.7 Config Server Endpoints

**Base URL:** `http://localhost:8090`

| Method | Path | Description |
|---|---|---|
| `GET` | `/{application}/{profile}` | Fetch configuration for a service |

Configuration is sourced from: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` (branch: `main`, path: `configuration/`).

---

## 4. Key Business Logic Inventory

### 4.1 Fund Transfer Rules

**Location:** `core-banking-service` > `TransactionService.fundTransfer()` and `internalFundTransfer()`

**Flow (orchestrated by Fund Transfer Service):**

1. **Fund Transfer Service** receives request, creates a `FundTransferEntity` with status `PENDING`, and persists it.
2. **Fund Transfer Service** calls **Core Banking Service** via Feign (`/api/v1/transaction/fund-transfer`).
3. **Core Banking Service** validates both source and destination accounts exist.
4. **Balance Validation:** Source account's `actualBalance` must be >= 0 AND >= transfer `amount`. Throws `InsufficientFundsException` otherwise.
5. **Debit:** Source account's `actualBalance` decremented by `amount`. `availableBalance` recalculated (note: current implementation has a double-subtraction bug - see Gap Analysis).
6. **Transaction Record (debit):** Saved with type `FUND_TRANSFER`, negative amount, reference = destination account number.
7. **Credit:** Destination account's `actualBalance` incremented by `amount`. `availableBalance` recalculated.
8. **Transaction Record (credit):** Saved with type `FUND_TRANSFER`, positive amount, reference = destination account number.
9. **Fund Transfer Service** updates local entity to `SUCCESS` with the `transactionId` from core banking.

**Key Business Rules:**
- Both accounts must exist (throws `EntityNotFoundException`)
- Source account must have sufficient actual balance
- Transfer is **atomic** within core banking (`@Transactional` on `TransactionService`)
- Transfer amount must be positive (not validated in code - gap)
- No same-account transfer check (gap)

### 4.2 Utility Payment Processing

**Location:** `core-banking-service` > `TransactionService.utilPayment()` and `internet-banking-utility-payment-service` > `UtilityPaymentService.utilPayment()`

**Flow:**

1. **Utility Payment Service** receives request, creates `UtilityPaymentEntity` with status `PROCESSING`.
2. **Utility Payment Service** calls **Core Banking Service** via Feign (`/api/v1/transaction/util-payment`).
3. **Core Banking Service** validates the source bank account exists and has sufficient funds.
4. **Core Banking Service** validates the utility provider exists (by `providerId`).
5. **Debit:** Source account's balances decremented.
6. **Transaction Record:** Saved with type `UTILITY_PAYMENT`, negative amount, reference = bill reference number.
7. **Utility Payment Service** updates local entity to `SUCCESS`.

**Key Business Rules:**
- Source account must exist and have sufficient balance
- Utility provider must exist (by ID)
- No actual third-party API integration (placeholder comment in code)
- Payment is one-directional (debit only, no credit to utility provider's bank account)

### 4.3 User Management

**Location:** `internet-banking-user-service` > `UserService`

**Registration Flow:**

1. Check if email already exists in Keycloak. If so, throw `UserAlreadyRegisteredException`.
2. Validate user's NIC against Core Banking Service via Feign (`/api/v1/user/{identification}`).
3. Validate that the email matches the core banking record. If not, throw `InvalidEmailException`.
4. Create user in Keycloak (disabled, email unverified) with provided password.
5. Re-read user from Keycloak to get the `authId`.
6. Save user locally with status `PENDING`.
7. If NIC not found in core banking, throw `InvalidBankingUserException`.

**User Approval Flow:**

1. Admin calls `PATCH /api/v1/bank-users/update/{id}` with status `APPROVED`.
2. Service reads user, retrieves Keycloak representation.
3. Enables user in Keycloak and marks email as verified.
4. Updates local status to `APPROVED`.

**User Statuses:** `PENDING` -> `APPROVED` -> `DISABLED` / `BLACKLIST`

---

## 5. Integration Points

### 5.1 Keycloak (IAM)

- **Version:** 23.0.7
- **Realm:** Imported from `docker-compose/keycloak/realm-export.json`
- **Client Integration:** `keycloak-admin-client:24.0.4` in User Service
- **Authentication:** `client_credentials` grant type
- **Configuration Properties** (externalized via Config Server):
  - `app.config.keycloak.server-url`
  - `app.config.keycloak.realm`
  - `app.config.keycloak.clientId`
  - `app.config.keycloak.client-secret`
- **API Gateway:** Validates JWT tokens using `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`
- **Singleton Pattern:** `KeycloakProperties` uses a static singleton for the Keycloak client instance (not thread-safe - gap)

### 5.2 RabbitMQ (Messaging)

- **Status:** Referenced in architecture documentation but **not implemented** in the current codebase.
- **Intended Use:** Notification service would consume messages from RabbitMQ for sending notifications after fund transfers and utility payments.
- No RabbitMQ dependency in any `build.gradle` file.

### 5.3 Zipkin (Distributed Tracing)

- **Version:** 3.x (Docker image `openzipkin/zipkin:3`)
- **Port:** 9411
- **Integration:** All services include:
  - `micrometer-tracing-bridge-brave`
  - `zipkin-reporter-brave`
  - `feign-micrometer` (for Feign client span propagation)
- **Configuration:** Tracing endpoints configured via centralized Config Server.

### 5.4 Database Connections

| Service | Database | Schema Management |
|---|---|---|
| Core Banking Service | `banking_core_service` (MySQL) | Flyway migrations |
| User Service | `banking_core_user_service` (MySQL) | JPA auto-DDL (Hibernate) |
| Fund Transfer Service | `banking_core_fund_transfer_service` (MySQL) | JPA auto-DDL (Hibernate) |
| Utility Payment Service | `banking_core_utility_payment_service` (MySQL) | JPA auto-DDL (Hibernate) |
| Keycloak | `keycloak` (PostgreSQL 15) | Keycloak managed |

All MySQL databases are created by `docker-compose/mysql/privileges.sql` on first startup. MySQL credentials: user `javatodev_development` / password `oPItyPticIAt`.

### 5.5 Spring Cloud Config

- **Config Server** pulls configuration from a public Git repository:
  `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Search path:** `configuration/`
- **Default label:** `main`
- Each service uses `bootstrap.yml` to point to the Config Server URL.
- Profile-specific bootstrap files: `bootstrap-docker.yml` (Docker Compose), `bootstrap-dev.yml` (developer workstation).

### 5.6 Netflix Eureka (Service Discovery)

- **Eureka Server** at port 8081, self-registration disabled.
- All business services and the API Gateway register as Eureka clients.
- Feign clients resolve service names (e.g., `core-banking-service`) through Eureka.

---

## 6. Build and Deployment Pipeline

### 6.1 Build System

- **Build tool:** Gradle (wrapper included per service, no root build file)
- **Java version:** 21 (Eclipse Temurin 21.0.2)
- **Spring Boot:** 3.2.4
- **Spring Cloud:** 2023.0.0
- **Plugin:** `com.gorylenko.gradle-git-properties` for embedding Git info at build time

Each service is an independent Gradle project. There is **no multi-project Gradle build** - each must be built individually:

```bash
cd <service-dir> && ./gradlew build
```

### 6.2 Docker Compose Orchestration

**Two compose files:**

1. `docker-compose.yml` - Full stack (infrastructure + all services)
2. `docker-compose-support-apps.yml` - Infrastructure only (Zipkin, Keycloak, MySQL, Config Server, Service Registry)

**Dockerfile Pattern (all services):**
```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh
RUN apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

### 6.3 Startup Order

Services use `wait-for-it.sh` scripts in their Docker entrypoints to manage dependencies:

```
MySQL + Keycloak DB + Zipkin  (start independently)
         |
         v
  Config Server (port 8090)
         |
         v
  Service Registry (port 8081)
         |
         v
  API Gateway + User Service + Fund Transfer Service + Utility Payment Service + Core Banking Service
  (all wait for both Config Server and Service Registry; business services also wait for MySQL)
```

### 6.4 Test Credentials

```
Test Email:    ib_admin@javatodev.com
Test Password: 5V7huE3G86uB
```

### 6.5 Technology Stack Summary

| Category | Technology | Version |
|---|---|---|
| Language | Java | 21 |
| Framework | Spring Boot | 3.2.4 |
| Cloud | Spring Cloud | 2023.0.0 |
| Service Discovery | Netflix Eureka | - |
| API Gateway | Spring Cloud Gateway | - |
| Config Management | Spring Cloud Config | - |
| Inter-Service Comm | Spring Cloud OpenFeign | - |
| Security | Spring Security + OAuth2 | - |
| IAM | Keycloak | 23.0.7 |
| Tracing | Micrometer + Brave + Zipkin | - |
| ORM | Spring Data JPA + Hibernate | - |
| DB Migration | Flyway (core-banking only) | 10.12.0 |
| Database | MySQL | 8.4.0 |
| Keycloak DB | PostgreSQL | 15 |
| Code Generation | Lombok | - |
| API Docs | SpringDoc OpenAPI | 2.1.0 |
| Containerization | Docker + Docker Compose | - |
| Build | Gradle | - |
