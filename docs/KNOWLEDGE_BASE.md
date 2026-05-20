# Application Knowledge Base

> **Repository:** `ts-java-spring-boot-internet-banking-microservices`
> **Stack:** Java 21 · Spring Boot 3.2.4 · Spring Cloud 2023.0.0 · Gradle 8.6
> **Last updated:** 2026-05-20

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Data Model Documentation](#2-data-model-documentation)
3. [API Surface Map](#3-api-surface-map)
4. [Key Business Logic Inventory](#4-key-business-logic-inventory)
5. [Integration Points](#5-integration-points)
6. [Build and Deployment Pipeline Summary](#6-build-and-deployment-pipeline-summary)

---

## 1. Architecture Overview

### 1.1 Services

The application consists of **6 microservices** organized in a layered architecture:

| Service | Port | Role | Key Dependencies |
|---|---|---|---|
| **internet-banking-service-registry** | 8081 | Netflix Eureka server – central service discovery | `spring-cloud-starter-netflix-eureka-server` |
| **internet-banking-config-server** | 8090 | Spring Cloud Config – externalised configuration from a Git repo | `spring-cloud-config-server` |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway – single entry point, OAuth 2.0 / JWT enforcement, header injection (`X-Auth-Id`) | `spring-cloud-starter-gateway`, `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-security` |
| **internet-banking-user-service** | 8083 | User registration, approval, CRUD; integrates with Keycloak Admin API and core-banking-service via Feign | `keycloak-admin-client`, `spring-cloud-starter-openfeign` |
| **internet-banking-fund-transfer-service** | 8084 | Fund transfer orchestration; persists transfer records and delegates to core-banking-service via Feign | `spring-cloud-starter-openfeign`, `spring-boot-starter-data-jpa` |
| **internet-banking-utility-payment-service** | 8085 | Utility payment orchestration; persists payment records and delegates to core-banking-service via Feign | `spring-cloud-starter-openfeign`, `spring-boot-starter-data-jpa` |
| **core-banking-service** | 8092 | Dummy banking core – accounts, users, transactions, balance management; MySQL + Flyway migrations | `spring-boot-starter-data-jpa`, `flyway-core`, `flyway-mysql` |

### 1.2 Communication Patterns

```
                            ┌──────────────────────┐
                            │     API Gateway       │
                            │ (JWT/OAuth2 + Eureka) │
                            └──────┬───────────────┘
                                   │ HTTP / REST
              ┌────────────────────┼────────────────────┐
              ▼                    ▼                     ▼
   ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────────┐
   │  User Service     │ │ Fund Transfer    │ │ Utility Payment      │
   │  (Feign → Core)   │ │ Service          │ │ Service              │
   │  (Feign → Keyclk) │ │ (Feign → Core)   │ │ (Feign → Core)      │
   └────────┬─────────┘ └────────┬─────────┘ └──────────┬───────────┘
            │                    │                       │
            └────────────────────┼───────────────────────┘
                                 ▼
                      ┌──────────────────────┐
                      │  Core Banking Service │
                      │  (MySQL + Flyway)     │
                      └──────────────────────┘
```

- **Synchronous REST (OpenFeign):** All inter-service calls use OpenFeign clients resolved via Eureka service names. No direct URLs are hard-coded.
- **Service Discovery:** Netflix Eureka (`internet-banking-service-registry`). All services register as Eureka clients.
- **Centralised Configuration:** Spring Cloud Config Server reads from a Git-hosted configuration repository (`internet-banking-microservices-configurations`). All downstream services import config via `spring-cloud-starter-bootstrap`.
- **Authentication / Authorisation:** API Gateway enforces JWT validation against a Keycloak OIDC provider. The gateway extracts the principal name and injects it as `X-Auth-Id` header into proxied requests. Downstream services read this header via a custom servlet filter (`AppAuthUserFilter`).
- **RabbitMQ (planned):** Referenced in README for notification service but **not yet implemented** in the codebase.

### 1.3 Infrastructure Components

| Component | Image / Version | Purpose |
|---|---|---|
| MySQL 8.4.0 | `mysql:8.4.0` (custom Dockerfile) | Persistent store for core-banking-service, user-service, fund-transfer-service, utility-payment-service |
| Keycloak 23.0.7 | `quay.io/keycloak/keycloak:23.0.7` | Identity provider (OIDC / OAuth 2.0). Realm imported on startup |
| PostgreSQL 15 | `postgres:15` | Keycloak's backing database |
| Zipkin 3 | `openzipkin/zipkin:3` | Distributed tracing collector |

---

## 2. Data Model Documentation

### 2.1 core-banking-service (MySQL: `banking_core_service`)

Database schema is managed by **Flyway** with 3 migration scripts.

#### `banking_core_user`

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` PK AUTO_INCREMENT | |
| `first_name` | `VARCHAR(255)` | |
| `last_name` | `VARCHAR(255)` | |
| `email` | `VARCHAR(255)` | |
| `identification_number` | `VARCHAR(255)` | National ID |

#### `banking_core_account`

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` PK AUTO_INCREMENT | |
| `number` | `VARCHAR(255)` | Account number |
| `type` | `VARCHAR(255)` | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | `VARCHAR(255)` | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `actual_balance` | `DECIMAL(19,2)` | |
| `available_balance` | `DECIMAL(19,2)` | |
| `user_id` | `BIGINT` FK → `banking_core_user.id` | Many-to-one |

#### `banking_core_utility_account`

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` PK AUTO_INCREMENT | |
| `number` | `VARCHAR(255)` | |
| `provider_name` | `VARCHAR(255)` | e.g. VODAFONE, VERIZON |

#### `banking_core_transaction`

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` PK AUTO_INCREMENT | |
| `amount` | `DECIMAL(19,2)` | Negative for debits |
| `transaction_type` | `VARCHAR(30)` | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | `VARCHAR(50)` | |
| `transaction_id` | `VARCHAR(50)` | UUID-based |
| `account_id` | `BIGINT` FK → `banking_core_account.id` | |

### 2.2 internet-banking-fund-transfer-service (MySQL)

#### `fund_transfer`

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` PK AUTO_INCREMENT | |
| `transaction_reference` | `VARCHAR(255)` | Maps to core banking `transactionId` |
| `from_account` | `VARCHAR(255)` | Source account number |
| `to_account` | `VARCHAR(255)` | Destination account number |
| `amount` | `DECIMAL(19,2)` | |
| `status` | `VARCHAR(255)` | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` | `TIMESTAMP` | Audit field |
| `created_by` | `VARCHAR(255)` | Audit field (from `X-Auth-Id`) |
| `modified_date` | `TIMESTAMP` | Audit field |
| `modified_by` | `VARCHAR(255)` | Audit field |
| `version` | `BIGINT` | Optimistic locking |

### 2.3 internet-banking-utility-payment-service (MySQL)

#### `utility_payment`

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` PK AUTO_INCREMENT | |
| `provider_id` | `BIGINT` | Utility provider reference |
| `amount` | `DECIMAL(19,2)` | |
| `reference_number` | `VARCHAR(255)` | Customer reference |
| `account` | `VARCHAR(255)` | Source bank account |
| `transaction_id` | `VARCHAR(255)` | Core banking transaction ID |
| `status` | `VARCHAR(255)` | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | | Audit fields (same as fund_transfer) |

### 2.4 internet-banking-user-service (MySQL)

#### `user`

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` PK AUTO_INCREMENT | |
| `auth_id` | `VARCHAR(255)` | Keycloak user UUID |
| `identification` | `VARCHAR(255)` | National ID (must match core banking) |
| `status` | `VARCHAR(255)` | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | | Audit fields |

### 2.5 Entity Relationships Diagram (Textual)

```
banking_core_user  1──*  banking_core_account  1──*  banking_core_transaction
                                                        │
                                                        └── transaction_type: FUND_TRANSFER | UTILITY_PAYMENT

internet_banking_user (user-service) ── auth_id ──► Keycloak User
                                      ── identification ──► banking_core_user.identification_number

fund_transfer (fund-transfer-service) ── from_account / to_account ──► banking_core_account.number
                                       ── transaction_reference ──► banking_core_transaction.transaction_id

utility_payment (utility-payment-service) ── account ──► banking_core_account.number
                                            ── provider_id ──► banking_core_utility_account.id
                                            ── transaction_id ──► banking_core_transaction.transaction_id
```

---

## 3. API Surface Map

### 3.1 API Gateway Routes

All requests are proxied through the gateway at port `8082`. Route prefixes:

| Prefix | Target Service |
|---|---|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |
| `/banking-core/**` or `/core/**` | core-banking-service |

Public (unauthenticated): `/user/api/v1/bank-users/register`, `/actuator/**`, `/{service}/actuator/**`
All other routes require a valid JWT Bearer token.

### 3.2 core-banking-service Endpoints

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Read bank account by number | – | `BankAccount { id, number, type, status, availableBalance, actualBalance, user }` |
| `GET` | `/api/v1/account/util-account/{account_name}` | Read utility account by provider name | – | `UtilityAccount { id, number, providerName }` |
| `POST` | `/api/v1/transaction/fund-transfer` | Process internal fund transfer | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/user/{identification}` | Read user by national ID | – | `User { id, firstName, lastName, email, identificationNumber, bankAccounts[] }` |
| `GET` | `/api/v1/user` | Read all users (paginated) | – | `Page<User>` |

### 3.3 internet-banking-user-service Endpoints

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Register new internet banking user | `{ email, identification, password }` | `User { id, email, identification, authId, status }` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user status (e.g. approve) | `{ status }` | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | – | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Read single user | – | `User` |

### 3.4 internet-banking-fund-transfer-service Endpoints

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `{ fromAccount, toAccount, amount, authID }` | `{ message, transactionId }` |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | – | `List<FundTransfer>` |

### 3.5 internet-banking-utility-payment-service Endpoints

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | – | `List<UtilityPayment>` |

### 3.6 Infrastructure Endpoints (all services with actuator)

| Method | Path | Description |
|---|---|---|
| `GET` | `/actuator/health` | Health check |
| `GET` | `/actuator/info` | Application info (git properties) |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

1. **Duplicate check:** Checks Keycloak for existing user with the same email.
2. **Core banking lookup:** Calls `core-banking-service` to verify the identification number exists and the email matches.
3. **Keycloak provisioning:** Creates a Keycloak user (disabled, email unverified) with the provided password.
4. **Local persistence:** Stores user record with `PENDING` status and the Keycloak `authId`.

### 4.2 User Approval Flow

1. Admin calls `PATCH /api/v1/bank-users/update/{id}` with `{ status: "APPROVED" }`.
2. Service reads the Keycloak user by `authId`, sets `enabled=true` and `emailVerified=true`.
3. Updates local user entity status.

### 4.3 Fund Transfer Flow

1. **internet-banking-fund-transfer-service** receives the transfer request.
2. Creates a `FundTransferEntity` with status `PENDING` and saves to local DB.
3. Calls `core-banking-service` `/api/v1/transaction/fund-transfer` via Feign.
4. **core-banking-service** `TransactionService.fundTransfer()`:
   - Reads both source and destination `BankAccount` DTOs.
   - **Validates balance:** `actualBalance >= 0` AND `actualBalance >= transferAmount`. Throws `InsufficientFundsException` if not.
   - Debits source account (`actualBalance -= amount`, `availableBalance = actualBalance - amount`).
   - Credits destination account (`actualBalance += amount`, `availableBalance = actualBalance + amount`).
   - Creates two `TransactionEntity` records (debit + credit) with a shared UUID `transactionId`.
   - Runs inside `@Transactional`.
5. On success, fund-transfer-service updates local entity to `SUCCESS` with the `transactionReference`.

### 4.4 Utility Payment Flow

1. **internet-banking-utility-payment-service** receives the payment request.
2. Creates a `UtilityPaymentEntity` with status `PROCESSING` and saves to local DB.
3. Calls `core-banking-service` `/api/v1/transaction/util-payment` via Feign.
4. **core-banking-service** `TransactionService.utilPayment()`:
   - Reads the source bank account and validates balance.
   - Looks up the utility provider by `providerId`.
   - Debits the source account.
   - Creates a `TransactionEntity` record.
5. On success, utility-payment-service updates local entity to `SUCCESS` with the `transactionId`.

### 4.5 Authentication / Authorization

- API Gateway uses Spring Security with OAuth 2.0 Resource Server (JWT).
- JWT is validated against Keycloak's JWK Set URI.
- The principal name is extracted from the JWT and injected as `X-Auth-Id` header via a `GlobalFilter`.
- Downstream services read `X-Auth-Id` via `AppAuthUserFilter` (servlet filter) and store it in a `ThreadLocal` (`ApiRequestContextHolder`) for JPA auditing.
- User registration endpoint is explicitly permitted without authentication.
- Actuator endpoints are permitted without authentication.

---

## 5. Integration Points

### 5.1 Keycloak (Identity Provider)

| Property | Value |
|---|---|
| Used by | `internet-banking-user-service`, `internet-banking-api-gateway` |
| Admin API | `keycloak-admin-client:24.0.4` via `KeycloakManager` / `KeycloakProperties` |
| Configuration | `app.config.keycloak.server-url`, `realm`, `clientId`, `client-secret` (from Spring Cloud Config) |
| Auth flow | `client_credentials` grant for admin operations; JWT validation at gateway |
| Realm | `javatodev-internet-banking` (imported from `realm-export.json`) |
| Test credentials | `ib_admin@javatodev.com / 5V7huE3G86uB` |

### 5.2 RabbitMQ (Message Broker)

- **Status:** Referenced in README but **not implemented** in any service.
- Intended for notification service to consume messages from fund-transfer and utility-payment services.

### 5.3 Zipkin (Distributed Tracing)

| Property | Value |
|---|---|
| Used by | All services (via `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave`) |
| Collector URL | `http://172.25.0.12:9411` (Docker) |
| Integration | Automatic via Spring Boot Actuator tracing auto-configuration |

### 5.4 Database Connections

| Service | Database | Driver | Migration |
|---|---|---|---|
| core-banking-service | MySQL 8.4 (`banking_core_service`) | `mysql-connector-j:8.4.0` | Flyway (3 scripts) |
| internet-banking-user-service | MySQL 8.4 | `mysql-connector-j:8.4.0` | Hibernate `ddl-auto` (via Cloud Config) |
| internet-banking-fund-transfer-service | MySQL 8.4 | `mysql-connector-j:8.4.0` | Hibernate `ddl-auto` (via Cloud Config) |
| internet-banking-utility-payment-service | MySQL 8.4 | `mysql-connector-j:8.4.0` | Hibernate `ddl-auto` (via Cloud Config) |
| Tests (all) | H2 in-memory | `h2:2.2.224` | Flyway disabled, JPA `ddl-auto=none` |

### 5.5 Spring Cloud Config (Externalised Configuration)

| Property | Value |
|---|---|
| Git Repository | `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` |
| Branch | `main` |
| Search Path | `configuration` |
| Config Server Port | 8090 |
| Client Bootstrap | All downstream services use `spring-cloud-starter-bootstrap` + `spring-cloud-starter-config` |

### 5.6 Netflix Eureka (Service Discovery)

| Property | Value |
|---|---|
| Server | `internet-banking-service-registry` on port 8081 |
| Clients | All other 5 services register as Eureka clients |
| Feign resolution | Service names (e.g. `core-banking-service`) are resolved via Eureka |

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Gradle 8.6** – each service has its own `gradlew` wrapper (no root build file).
- All services share the same plugin versions: Spring Boot `3.2.4`, Spring Dependency Management `1.1.4`.
- Lombok is used across all business services (`compileOnly` + `annotationProcessor`).
- Springdoc OpenAPI (`2.1.0`) is included in core-banking, user, fund-transfer, and utility-payment services.
- Git properties plugin (`com.gorylenko.gradle-git-properties:2.4.2`) generates build metadata (used on all services except service-registry).

### 6.2 Docker

Each service has a `Dockerfile`:
- Base image: `eclipse-temurin:21.0.2_13-jre-alpine`
- Copies the built JAR as `app.jar`
- Includes `wait-for-it.sh` for startup ordering
- Activates `docker` Spring profile

### 6.3 Docker Compose

Two compose files under `docker-compose/`:

| File | Purpose |
|---|---|
| `docker-compose.yml` | Full stack: all 6 services + MySQL + Keycloak + PostgreSQL + Zipkin |
| `docker-compose-support-apps.yml` | Infrastructure only: MySQL + Keycloak + PostgreSQL + Zipkin + Config Server + Service Registry |

- Custom Docker network: `javatodev_ib_network` (`172.25.0.0/16`)
- Static IP assignments for all containers
- `wait-for-it.sh` entrypoints ensure startup order: service-registry → config-server → MySQL → application services
- MySQL custom image seeds databases via `privileges.sql`
- Keycloak imports realm configuration from `realm-export.json`

### 6.4 CI/CD

- No GitHub Actions workflows are defined (`.github/` contains only `FUNDING.yml`).
- No automated build/test/deploy pipeline exists in the repository.
- Deployment is manual via `docker-compose up -d`.

### 6.5 Postman Collection

- Collection: `JAVA_TO_DEV_MICROSERVICES.postman_collection.json`
- Environment: `BANKING_CORE_MICROSERVICES_PROJECT.postman_environment.json`
- Covers: Health checks, fund transfer, user read, bank account read, utility account read, utility payment, user registration, user update, user listing.
