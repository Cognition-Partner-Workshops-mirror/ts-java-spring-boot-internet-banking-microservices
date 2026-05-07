# Application Knowledge Base

## 1. Architecture Overview

### 1.1 Services

The system comprises **6 microservices** built with Java 21 and Spring Boot 3.2.4 (Spring Cloud 2023.0.0):

| Service | Port | Role |
|---|---|---|
| **core-banking-service** | 8092 | Core banking engine: accounts, users, transactions, fund transfers, utility payments |
| **internet-banking-user-service** | 8083 | Internet banking user registration/management; integrates with Keycloak for identity |
| **internet-banking-fund-transfer-service** | 8084 | Orchestrates fund transfer requests; delegates processing to core-banking-service |
| **internet-banking-utility-payment-service** | 8085 | Orchestrates utility payment requests; delegates processing to core-banking-service |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway; OAuth2 resource server (Keycloak JWT); routes to downstream services |
| **internet-banking-service-registry** | 8081 | Netflix Eureka Server for service discovery |
| **internet-banking-config-server** | 8090 | Spring Cloud Config Server; reads configuration from a remote Git repository |

### 1.2 Communication Patterns

```
Client (Browser / Postman)
  |
  v
API Gateway (:8082)  <-- OAuth2/JWT via Keycloak
  |  routes via Eureka service names
  +---> internet-banking-user-service (:8083)
  |       |-- OpenFeign --> core-banking-service (:8092)
  |       |-- Keycloak Admin Client --> Keycloak (:8080)
  |
  +---> internet-banking-fund-transfer-service (:8084)
  |       |-- OpenFeign --> core-banking-service (:8092)
  |
  +---> internet-banking-utility-payment-service (:8085)
  |       |-- OpenFeign --> core-banking-service (:8092)
  |
  +---> core-banking-service (:8092)
```

- **Synchronous REST (OpenFeign):** All inter-service communication uses Spring Cloud OpenFeign with Eureka service discovery. Each downstream service calls `core-banking-service` by its Eureka registration name.
- **API Gateway Routing:** Spring Cloud Gateway routes requests by path prefix to downstream services. It injects an `X-Auth-Id` header with the authenticated user's principal name.
- **Service Discovery:** All services register with and discover peers through the centralized Eureka server.
- **Centralized Configuration:** All services (except service-registry) pull configuration from the Config Server, which sources properties from [a GitHub Git repository](https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git).

### 1.3 Infrastructure Components

| Component | Technology | Purpose |
|---|---|---|
| **Database** | MySQL 8.4.0 | Primary persistent store for all business services |
| **Identity Provider** | Keycloak 23.0.7 (PostgreSQL 15 backend) | OAuth2/OIDC authentication; user realm management |
| **Distributed Tracing** | Zipkin 3 + Micrometer Tracing (Brave bridge) | Request tracing across services |
| **Service Registry** | Netflix Eureka | Service discovery |
| **Config Server** | Spring Cloud Config (Git-backed) | Externalized, centralized configuration |
| **Container Runtime** | Docker / Docker Compose | Local development and deployment |

> **Note:** The README mentions RabbitMQ and Prometheus in the tech stack, but **neither is wired** in the current codebase or Docker Compose files. A Notification Service is listed as "PENDING Development."

---

## 2. Data Model Documentation

### 2.1 core-banking-service (MySQL: `banking_core_service`)

#### Tables (Flyway-managed)

**`banking_core_user`**
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `first_name` | VARCHAR(255) | |
| `last_name` | VARCHAR(255) | |
| `email` | VARCHAR(255) | |
| `identification_number` | VARCHAR(255) | National ID / NIC |

**`banking_core_account`**
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `number` | VARCHAR(255) | Account number (e.g., `100015003000`) |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `actual_balance` | DECIMAL(19,2) | |
| `available_balance` | DECIMAL(19,2) | |
| `user_id` | BIGINT (FK -> `banking_core_user.id`) | |

**`banking_core_transaction`**
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `amount` | DECIMAL(19,2) | Negative for debits, positive for credits |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Counterparty account number or payment ref |
| `transaction_id` | VARCHAR(50) | UUID correlation ID |
| `account_id` | BIGINT (FK -> `banking_core_account.id`) | |

**`banking_core_utility_account`**
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `number` | VARCHAR(255) | Utility provider account number |
| `provider_name` | VARCHAR(255) | e.g., VODAFONE, VERIZON |

#### Relationships
```
banking_core_user  1 ---< N  banking_core_account (user_id FK)
banking_core_account 1 ---< 1 banking_core_transaction (account_id FK, OneToOne in JPA)
banking_core_utility_account (standalone lookup table)
```

### 2.2 internet-banking-user-service (MySQL: `banking_core_user_service`)

**`user` (JPA entity: `UserEntity`)**
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `identification` | VARCHAR(255) | National ID reference |
| `email` | VARCHAR(255) | |
| `status` | VARCHAR(255) | Enum: `PENDING`, `ACTIVE`, `DE_ACTIVE` |
| `auth_id` | VARCHAR(255) | Keycloak user ID |
| Audit fields | (from `AuditAware`) | `created_date`, `created_by`, `modified_date`, `modified_by`, `version` |

### 2.3 internet-banking-fund-transfer-service (MySQL: `banking_core_fund_transfer_service`)

**`fund_transfer` (JPA entity: `FundTransferEntity`)**
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `transaction_reference` | VARCHAR(255) | UUID from core-banking-service |
| `from_account` | VARCHAR(255) | Source account number |
| `to_account` | VARCHAR(255) | Destination account number |
| `amount` | DECIMAL(19,2) | |
| `status` | VARCHAR(255) | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| Audit fields | (from `AuditAware`) | `created_date`, `created_by`, `modified_date`, `modified_by`, `version` |

### 2.4 internet-banking-utility-payment-service (MySQL: `banking_core_utility_payment_service`)

**`utility_payment` (JPA entity: `UtilityPaymentEntity`)**
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | |
| `provider_id` | BIGINT | Utility provider reference |
| `amount` | DECIMAL(19,2) | |
| `reference_number` | VARCHAR(255) | Customer reference |
| `account` | VARCHAR(255) | Source bank account number |
| `transaction_id` | VARCHAR(255) | UUID from core-banking-service |
| `status` | VARCHAR(255) | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| Audit fields | (from `AuditAware`) | `created_date`, `created_by`, `modified_date`, `modified_by`, `version` |

---

## 3. API Surface Map

### 3.1 API Gateway Routes (port 8082)

All requests pass through the gateway. The gateway strips the path prefix and forwards to the target service. Authentication is required for all endpoints except user registration and actuator paths.

### 3.2 core-banking-service

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by account number | - | `BankAccount` (id, number, type, status, availableBalance, actualBalance, user) |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | - | `UtilityAccount` (id, number, providerName) |
| `POST` | `/api/v1/transaction/fund-transfer` | Process a fund transfer | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `POST` | `/api/v1/transaction/util-payment` | Process a utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/user/{identification}` | Get user by identification number | - | `User` (id, firstName, lastName, email, identificationNumber, bankAccounts[]) |
| `GET` | `/api/v1/user` | List users (paginated) | Query params: `page`, `size`, `sort` | `List<User>` |

### 3.3 internet-banking-user-service

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Register a new banking user | `{ identification, email }` | `User` |
| `GET` | `/api/v1/bank-users/{id}` | Read user by ID | - | `User` with linked `AccountResponse[]` |
| `PATCH` | `/api/v1/bank-users/{id}` | Update user (e.g., status change) | `{ status }` | `User` |
| `GET` | `/api/v1/bank-users` | List all users (paginated) | Query params: `page`, `size`, `sort` | `List<User>` |

### 3.4 internet-banking-fund-transfer-service

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate a fund transfer | `{ fromAccount, toAccount, amount, authID }` | `{ message, transactionId }` |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | Query params: `page`, `size`, `sort` | `List<FundTransfer>` |

### 3.5 internet-banking-utility-payment-service

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process a utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | Query params: `page`, `size`, `sort` | `List<UtilityPayment>` |

### 3.6 Common Infrastructure Endpoints (all services)

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/actuator/health` | Health check (Spring Boot Actuator) |
| `GET` | `/actuator/info` | Application info |
| Various | `/actuator/*` | Additional actuator endpoints (depends on config) |

---

## 4. Key Business Logic Inventory

### 4.1 Fund Transfer Flow

1. **Client** sends `POST /api/v1/transfer` to the **fund-transfer-service** via the API gateway.
2. **Fund-transfer-service** creates a `FundTransferEntity` with status `PENDING` in its local database.
3. **Fund-transfer-service** calls **core-banking-service** via OpenFeign: `POST /api/v1/transaction/fund-transfer`.
4. **Core-banking-service** (`TransactionService.fundTransfer`):
   - Looks up source and destination `BankAccount` objects.
   - **Validates balance:** source account `actualBalance` must be >= transfer `amount` (throws `InsufficientFundsException` otherwise).
   - Calls `internalFundTransfer`:
     - Deducts `amount` from source account (`actualBalance` and `availableBalance`).
     - Credits `amount` to destination account.
     - Persists two `TransactionEntity` records (debit and credit) with a shared UUID `transactionId`.
   - Returns `{ message, transactionId }`.
5. **Fund-transfer-service** updates local entity with the `transactionReference` and status `SUCCESS`.
6. Returns response to client.

**Key rules:**
- Balance must be non-negative and sufficient for the transfer amount.
- Both debit and credit are executed within a single `@Transactional` boundary in core-banking-service.
- No rollback or compensation logic exists in fund-transfer-service if the core call partially fails.

### 4.2 Utility Payment Flow

1. **Client** sends `POST /api/v1/utility-payment` to the **utility-payment-service** via the API gateway.
2. **Utility-payment-service** creates a `UtilityPaymentEntity` with status `PROCESSING`.
3. Calls **core-banking-service** via OpenFeign: `POST /api/v1/transaction/util-payment`.
4. **Core-banking-service** (`TransactionService.utilPayment`):
   - Looks up source `BankAccount` and target `UtilityAccount`.
   - Validates balance.
   - Deducts amount from source account.
   - Persists a `TransactionEntity` (debit, type `UTILITY_PAYMENT`).
   - Returns `{ message, transactionId }`.
5. **Utility-payment-service** updates local entity with `transactionId` and status `SUCCESS`.

### 4.3 User Registration Flow

1. **Client** sends `POST /api/v1/bank-users/register` (unauthenticated, allowed by gateway).
2. **User-service** (`UserService.register`):
   - Validates email format.
   - Checks if identification number is already registered in core-banking-service (via Feign).
   - Creates user in **Keycloak** (via Keycloak Admin Client).
   - Persists `UserEntity` locally with `authId` = Keycloak user ID, status `ACTIVE`.
3. Returns user response.

### 4.4 User Management

- **Read user by ID:** Fetches local `UserEntity`, then enriches with account data from core-banking-service via Feign.
- **Update user:** Patches status field (`ACTIVE` / `DE_ACTIVE`).
- **List users:** Paginated retrieval from local database.

---

## 5. Integration Points

### 5.1 Keycloak (Identity & Access Management)

- **Version:** 23.0.7
- **Connection:** Keycloak Admin Client SDK (internet-banking-user-service only)
- **Purpose:** User registration, user management within the `javatodev-internet-banking` realm
- **Configuration:** `app.config.keycloak.server-url`, `realm`, `clientId`, `client-secret` (via Spring Cloud Config)
- **Gateway Integration:** OAuth2 Resource Server validates JWTs against Keycloak's JWK Set URI
- **Realm Data:** Pre-loaded via Docker Compose volume mount (`realm-export.json`)

### 5.2 Zipkin (Distributed Tracing)

- **Version:** 3
- **Connection:** HTTP reporter via `io.zipkin.reporter2:zipkin-reporter-brave`
- **Tracing Bridge:** Micrometer Tracing with Brave bridge (`io.micrometer:micrometer-tracing-bridge-brave`)
- **Coverage:** All 4 business services + API gateway include tracing dependencies
- **Feign Integration:** `io.github.openfeign:feign-micrometer` propagates trace context across Feign calls

### 5.3 Database Connections

| Service | Database | Schema | ORM | Migration |
|---|---|---|---|---|
| core-banking-service | MySQL 8.4.0 | `banking_core_service` | Spring Data JPA + Hibernate | Flyway (3 migrations) |
| internet-banking-user-service | MySQL 8.4.0 | `banking_core_user_service` | Spring Data JPA + Hibernate | JPA auto-DDL (no Flyway) |
| internet-banking-fund-transfer-service | MySQL 8.4.0 | `banking_core_fund_transfer_service` | Spring Data JPA + Hibernate | JPA auto-DDL (no Flyway) |
| internet-banking-utility-payment-service | MySQL 8.4.0 | `banking_core_utility_payment_service` | Spring Data JPA + Hibernate | JPA auto-DDL (no Flyway) |

- All services connect via MySQL Connector/J 8.4.0
- Test profiles use H2 in-memory database
- A shared MySQL instance is used in Docker Compose with separate databases per service

### 5.4 Spring Cloud Config Server

- **Backend:** Git repository at `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch:** `main`
- **Search path:** `configuration/`
- **Bootstrap:** Each service uses `bootstrap.yml` (local) or `bootstrap-docker.yml` (Docker profile) to locate the config server

### 5.5 Eureka Service Registry

- **Server:** `internet-banking-service-registry` (port 8081)
- **Clients:** All other services register and discover via Eureka
- **Configuration:** `prefer-ip-address: true` for container networking

### 5.6 RabbitMQ (NOT IMPLEMENTED)

- Listed in README tech stack but **no RabbitMQ dependency, configuration, or usage** exists in the current codebase.
- The planned Notification Service (which would consume messages) is marked as "PENDING Development."

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build Tool:** Gradle (per-service `build.gradle`, no multi-project root build)
- **Each service is an independent Gradle project** with its own `settings.gradle` and `gradle-wrapper.properties` (Gradle 8.6)
- **Plugins:**
  - `org.springframework.boot` (3.2.4)
  - `io.spring.dependency-management` (1.1.4)
  - `com.gorylenko.gradle-git-properties` (2.4.2) — generates `git.properties` for build info

### 6.2 Docker

Each service has an identical Dockerfile pattern:
```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
EXPOSE <port>
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh
RUN apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

### 6.3 Docker Compose

**`docker-compose/docker-compose.yml`** — Full stack deployment:
- Launches all 6 microservices + MySQL + Keycloak (+ PostgreSQL) + Zipkin
- Static IP assignment on a custom bridge network (`172.25.0.0/16`)
- Uses `wait-for-it.sh` to enforce startup ordering (service-registry and config-server must be available before business services)
- Pre-seeds MySQL with user/account/utility data via Flyway migrations and a `privileges.sql` init script

**`docker-compose/docker-compose-support-apps.yml`** — Infrastructure only (for local development):
- Launches Zipkin, Keycloak, PostgreSQL, MySQL, Config Server, and Service Registry
- Business services are expected to run locally against this infrastructure

### 6.4 Profiles

| Profile | Purpose |
|---|---|
| `default` | Local development; config server at `localhost:8090` |
| `docker` | Container deployment; config server at `internet-banking-config-server:8090` |
| `dev` | Development with specific IP; config server at `192.168.1.5:8090` |

### 6.5 Test Setup

- **Test framework:** JUnit 5 (via `spring-boot-starter-test`)
- **Test database:** H2 in-memory (Flyway disabled in test profile)
- **Test coverage:** Only `core-banking-service` has meaningful unit tests (3 test classes: `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`). Other services have only default Spring Boot application context tests.
