# Application Knowledge Base

## 1. Architecture Overview

### System Summary

This is a **Java 21 / Spring Boot 3.2.4** internet banking application built on a microservices architecture using **Spring Cloud 2023.0.0**. The system models a simplified banking platform with user management, fund transfers, and utility payments.

### Services

| # | Service | Port | Purpose |
|---|---------|------|---------|
| 1 | **internet-banking-service-registry** | 8081 | Netflix Eureka service discovery server. All other services register here. |
| 2 | **internet-banking-config-server** | 8090 | Spring Cloud Config Server. Serves externalized configuration from a [Git repository](https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git). |
| 3 | **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway. Single entry point for all client requests. Handles OAuth2/JWT validation via Keycloak and routes traffic to downstream services. |
| 4 | **internet-banking-user-service** | 8083 | Manages internet banking user registration, approval workflows, and profile retrieval. Integrates with Keycloak for identity management. |
| 5 | **internet-banking-fund-transfer-service** | 8084 | Orchestrates fund transfer requests between accounts. Persists transfer records and delegates actual balance operations to the core banking service. |
| 6 | **internet-banking-utility-payment-service** | 8085 | Orchestrates utility bill payments (telecom, etc.). Persists payment records and delegates actual balance operations to the core banking service. |
| 7 | **core-banking-service** | 8092 | The "core bank" — owns accounts, users, transactions, and balance management. All financial mutations happen here. |

> **Note:** A Notification Service is referenced in documentation as a planned service (consuming RabbitMQ messages) but has **not been implemented**.

### Communication Patterns

```
┌─────────┐       ┌──────────────┐       ┌───────────────────────┐
│  Client  │──────>│  API Gateway │──────>│  User Service         │
│          │       │  (8082)      │       │  Fund Transfer Service│
│          │       │              │       │  Utility Payment Svc  │
│          │       │              │       │  Core Banking Service │
└─────────┘       └──────────────┘       └───────────┬───────────┘
                                                      │
                                          OpenFeign (sync REST)
                                                      │
                                                      v
                                          ┌───────────────────────┐
                                          │  Core Banking Service │
                                          │  (source of truth)    │
                                          └───────────────────────┘
```

- **Client → API Gateway**: HTTP/REST over JWT-secured endpoints. Gateway validates OAuth2 tokens via Keycloak's JWK Set URI.
- **Gateway → Downstream Services**: Route-based proxying. Gateway injects `X-Auth-Id` header with the authenticated principal name.
- **Service → Service (OpenFeign)**: Synchronous REST calls via Spring Cloud OpenFeign, resolved through Eureka service discovery.
  - `internet-banking-user-service` → `core-banking-service` (user lookup by identification)
  - `internet-banking-fund-transfer-service` → `core-banking-service` (fund transfer execution, account lookup)
  - `internet-banking-utility-payment-service` → `core-banking-service` (utility payment execution, account lookup)
- **Service Discovery**: All services register with Netflix Eureka (`internet-banking-service-registry`). Feign clients use logical service names (e.g., `core-banking-service`) resolved via Eureka.
- **Configuration**: All services (except config server and registry) pull configuration from `internet-banking-config-server` at startup via Spring Cloud Config bootstrap.

### Infrastructure Components

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Service Discovery | Netflix Eureka | Service registration and discovery |
| API Gateway | Spring Cloud Gateway | Request routing, security enforcement |
| Config Server | Spring Cloud Config | Centralized externalized configuration |
| Identity Provider | Keycloak 23.0.7 | OAuth2/OIDC authentication & user management |
| Primary Database | MySQL 8.4.0 | Persistent storage for all business services |
| Keycloak Database | PostgreSQL 15 | Persistent storage for Keycloak |
| Distributed Tracing | Zipkin 3 + Micrometer Brave | Request tracing across services |
| Message Broker | RabbitMQ | Referenced in docs but **not wired** in code |

---

## 2. Data Model Documentation

### Core Banking Service (`banking_core_service` database)

#### `banking_core_user`

| Column | Type | Description |
|--------|------|-------------|
| `id` | `BIGINT` PK AUTO_INCREMENT | Internal user ID |
| `first_name` | `VARCHAR(255)` | User first name |
| `last_name` | `VARCHAR(255)` | User last name |
| `email` | `VARCHAR(255)` | User email address |
| `identification_number` | `VARCHAR(255)` | National ID / NIC number |

#### `banking_core_account`

| Column | Type | Description |
|--------|------|-------------|
| `id` | `BIGINT` PK AUTO_INCREMENT | Internal account ID |
| `number` | `VARCHAR(255)` | Account number (e.g., `100015003000`) |
| `type` | `VARCHAR(255)` ENUM | `SAVINGS_ACCOUNT` (only type seeded) |
| `status` | `VARCHAR(255)` ENUM | `ACTIVE` / `INACTIVE` |
| `actual_balance` | `DECIMAL(19,2)` | Actual ledger balance |
| `available_balance` | `DECIMAL(19,2)` | Available balance for transactions |
| `user_id` | `BIGINT` FK → `banking_core_user.id` | Owning user |

**Relationship:** `banking_core_user` 1:N `banking_core_account`

#### `banking_core_utility_account`

| Column | Type | Description |
|--------|------|-------------|
| `id` | `BIGINT` PK AUTO_INCREMENT | Internal utility account ID |
| `number` | `VARCHAR(255)` | Utility provider account number |
| `provider_name` | `VARCHAR(255)` | Provider name (e.g., `VODAFONE`, `AIRTEL`) |

#### `banking_core_transaction`

| Column | Type | Description |
|--------|------|-------------|
| `id` | `BIGINT` PK AUTO_INCREMENT | Internal transaction ID |
| `amount` | `DECIMAL(19,2)` | Transaction amount (negative for debits) |
| `transaction_type` | `VARCHAR(30)` ENUM | `FUND_TRANSFER` or `UTILITY_PAYMENT` |
| `reference_number` | `VARCHAR(50)` | Destination account number or reference |
| `transaction_id` | `VARCHAR(50)` | UUID transaction identifier |
| `account_id` | `BIGINT` FK → `banking_core_account.id` | Associated account |

### Internet Banking User Service (`banking_core_user_service` database)

#### `user` (JPA auto-generated)

| Column | Type | Description |
|--------|------|-------------|
| `id` | `BIGINT` PK AUTO_INCREMENT | Internal user ID |
| `auth_id` | `VARCHAR(255)` | Keycloak user UUID |
| `identification` | `VARCHAR(255)` | NIC / identification number |
| `status` | `VARCHAR(255)` ENUM | `PENDING` / `APPROVED` |
| `created_by` | `VARCHAR(255)` | Audit: creator (from `AuditAware`) |
| `created_date` | `DATETIME` | Audit: creation timestamp |
| `last_modified_by` | `VARCHAR(255)` | Audit: last modifier |
| `last_modified_date` | `DATETIME` | Audit: last modification timestamp |

### Internet Banking Fund Transfer Service (`banking_core_fund_transfer_service` database)

#### `fund_transfer` (JPA auto-generated)

| Column | Type | Description |
|--------|------|-------------|
| `id` | `BIGINT` PK AUTO_INCREMENT | Internal transfer ID |
| `from_account` | `VARCHAR(255)` | Source account number |
| `to_account` | `VARCHAR(255)` | Destination account number |
| `amount` | `DECIMAL(19,2)` | Transfer amount |
| `transaction_reference` | `VARCHAR(255)` | Core banking transaction UUID |
| `status` | `VARCHAR(255)` ENUM | `PENDING` / `SUCCESS` |
| `created_by` / `created_date` / `last_modified_by` / `last_modified_date` | — | Audit fields (from `AuditAware`) |

### Internet Banking Utility Payment Service (`banking_core_utility_payment_service` database)

#### `utility_payment` (JPA auto-generated)

| Column | Type | Description |
|--------|------|-------------|
| `id` | `BIGINT` PK AUTO_INCREMENT | Internal payment ID |
| `provider_id` | `BIGINT` | Utility provider ID |
| `amount` | `DECIMAL(19,2)` | Payment amount |
| `reference_number` | `VARCHAR(255)` | Customer reference (e.g., phone number) |
| `account` | `VARCHAR(255)` | Source bank account number |
| `transaction_id` | `VARCHAR(255)` | Core banking transaction UUID |
| `status` | `VARCHAR(255)` ENUM | `PROCESSING` / `SUCCESS` |
| `created_by` / `created_date` / `last_modified_by` / `last_modified_date` | — | Audit fields (from `AuditAware`) |

---

## 3. API Surface Map

### Core Banking Service (port 8092, gateway prefix: `/core`)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | — | `BankAccount` (number, type, status, availableBalance, actualBalance) | Lookup bank account by number |
| `GET` | `/api/v1/account/util-account/{account_name}` | — | `UtilityAccount` (id, number, providerName) | Lookup utility account by provider name |
| `GET` | `/api/v1/user/{identification}` | — | `User` (id, firstName, lastName, email, identificationNumber, accounts[]) | Lookup user by NIC |
| `GET` | `/api/v1/user` | Query: `page`, `size`, `sort` | `List<User>` | Paginated user list |
| `POST` | `/api/v1/transaction/fund-transfer` | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` | Execute fund transfer |
| `POST` | `/api/v1/transaction/util-payment` | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` | Execute utility payment |

### Internet Banking User Service (port 8083, gateway prefix: `/user`)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `POST` | `/api/v1/bank-users/register` | `{ password, email, identification }` | `User` (id, authId, identification, status) | Register new internet banking user |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `{ status }` | `User` | Update user status (e.g., approve) |
| `GET` | `/api/v1/bank-users` | Query: `page`, `size`, `sort` | `List<User>` | Paginated user list |
| `GET` | `/api/v1/bank-users/{id}` | — | `User` | Read user by internal ID |

### Internet Banking Fund Transfer Service (port 8084, gateway prefix: `/fund-transfer`)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `POST` | `/api/v1/transfer` | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` | Initiate fund transfer |
| `GET` | `/api/v1/transfer` | Query: `page`, `size`, `sort` | `List<FundTransfer>` | Paginated transfer history |

### Internet Banking Utility Payment Service (port 8085, gateway prefix: `/payment`)

| Method | Endpoint | Request Body | Response | Description |
|--------|----------|-------------|----------|-------------|
| `POST` | `/api/v1/utility-payment` | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` | Process utility payment |
| `GET` | `/api/v1/utility-payment` | Query: `page`, `size`, `sort` | `List<UtilityPayment>` | Paginated payment history |

### Infrastructure Endpoints

| Method | Endpoint | Service |
|--------|----------|---------|
| `GET` | `/actuator/health` | All services (via Spring Boot Actuator) |
| `GET` | `/actuator/info` | All services (Git properties included) |
| `GET` | `<eureka-host>:8081/` | Eureka Dashboard |

---

## 4. Key Business Logic Inventory

### User Registration Flow (`internet-banking-user-service`)

1. Check if email already exists in Keycloak → throw `UserAlreadyRegisteredException` if duplicate.
2. Call `core-banking-service` to verify user exists by NIC identification number.
3. Validate that the provided email matches the core banking user's email → throw `InvalidEmailException` on mismatch.
4. Create user in Keycloak with `enabled=false`, `emailVerified=false`.
5. On successful Keycloak creation (HTTP 201), persist user record locally with `status=PENDING`.
6. If core banking user not found → throw `InvalidBankingUserException`.

### User Approval Flow (`internet-banking-user-service`)

1. Admin calls `PATCH /api/v1/bank-users/update/{id}` with `{ "status": "APPROVED" }`.
2. Service fetches user from local DB.
3. If new status is `APPROVED`, calls Keycloak to set `enabled=true` and `emailVerified=true`.
4. Persists updated status to local DB.

### Fund Transfer Flow (`internet-banking-fund-transfer-service` → `core-banking-service`)

1. **Fund Transfer Service** receives request, creates local `FundTransferEntity` with `status=PENDING`.
2. Calls `core-banking-service` via Feign: `POST /api/v1/transaction/fund-transfer`.
3. **Core Banking Service** validates both accounts exist, checks sender has sufficient funds (`actualBalance >= amount`).
4. Deducts amount from sender's `actualBalance` and `availableBalance`.
5. Creates debit `TransactionEntity` for sender.
6. Adds amount to receiver's `actualBalance` and `availableBalance`.
7. Creates credit `TransactionEntity` for receiver.
8. Returns `transactionId` (UUID).
9. **Fund Transfer Service** updates local record with `transactionReference` and `status=SUCCESS`.

### Utility Payment Flow (`internet-banking-utility-payment-service` → `core-banking-service`)

1. **Utility Payment Service** receives request, creates local `UtilityPaymentEntity` with `status=PROCESSING`.
2. Calls `core-banking-service` via Feign: `POST /api/v1/transaction/util-payment`.
3. **Core Banking Service** validates source account exists, checks sufficient funds.
4. Looks up utility provider by ID.
5. Deducts amount from sender's balances.
6. Creates debit `TransactionEntity`.
7. Returns `transactionId` (UUID).
8. **Utility Payment Service** updates local record with `transactionId` and `status=SUCCESS`.

### Balance Validation Rule

```java
if (actualBalance < 0 || actualBalance < transferAmount) {
    throw InsufficientFundsException
}
```

---

## 5. Integration Points

### Keycloak (Identity & Access Management)

- **Version:** 23.0.7
- **Realm:** `javatodev-internet-banking` (imported at container startup)
- **Client:** `internet-banking-core-client`
- **Integration Points:**
  - **API Gateway** validates JWT tokens against Keycloak's JWK Set URI (`spring.security.oauth2.resourceserver.jwt.jwk-set-uri`).
  - **User Service** uses `keycloak-admin-client:24.0.4` to manage users (create, update, search) via Keycloak Admin REST API with `client_credentials` grant type.
- **Configuration Properties:**
  - `app.config.keycloak.server-url`
  - `app.config.keycloak.realm`
  - `app.config.keycloak.clientId`
  - `app.config.keycloak.client-secret`
- **Security Note:** Keycloak singleton instance is lazily initialized (not thread-safe).

### RabbitMQ (Message Broker)

- **Status:** Referenced in README and architecture diagrams but **not implemented** in any service code.
- **Intended Use:** Notification service was planned to consume messages from RabbitMQ for push notifications.
- No RabbitMQ dependencies exist in any `build.gradle`.

### Zipkin (Distributed Tracing)

- **Version:** Zipkin 3
- **Port:** 9411
- **Integration:** All business services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` dependencies.
- **Feign Tracing:** `feign-micrometer` dependency enables trace propagation across Feign calls.
- Configuration is externalized via Spring Cloud Config.

### Database Connections

| Service | Database | Schema |
|---------|----------|--------|
| core-banking-service | MySQL 8.4.0 | `banking_core_service` |
| internet-banking-user-service | MySQL 8.4.0 | `banking_core_user_service` |
| internet-banking-fund-transfer-service | MySQL 8.4.0 | `banking_core_fund_transfer_service` |
| internet-banking-utility-payment-service | MySQL 8.4.0 | `banking_core_utility_payment_service` |
| Keycloak | PostgreSQL 15 | `keycloak` |

- MySQL credentials: `javatodev_development` / `oPItyPticIAt` (created via `privileges.sql`).
- Core Banking Service uses **Flyway** for schema migrations (`flyway-core:10.12.0`, `flyway-mysql:10.12.0`).
- Other services rely on **JPA/Hibernate auto-DDL** (no Flyway).

---

## 6. Build and Deployment Pipeline Summary

### Build System

- **Build Tool:** Gradle (per-service `build.gradle`, no multi-project root build)
- **Java Version:** 21 (Eclipse Temurin)
- **Spring Boot:** 3.2.4
- **Spring Cloud:** 2023.0.0
- Each service is an independent Gradle project with its own `gradlew` wrapper (Gradle 8.6).
- `com.gorylenko.gradle-git-properties` plugin generates `git.properties` in each service.

### Docker

- Each service has its own `Dockerfile` based on `eclipse-temurin:21.0.2_13-jre-alpine`.
- Dockerfiles copy the pre-built JAR (`build/libs/*-0.0.1-SNAPSHOT.jar`) and a `wait-for-it.sh` script.
- Services use `wait-for-it.sh` to wait for dependent infrastructure (config server, service registry, MySQL) before starting.
- Spring profile: `-Dspring.profiles.active=docker` is used in container entrypoints.

### Docker Compose

Two compose files in `docker-compose/`:

1. **`docker-compose.yml`** — Full stack: all 7 services + Zipkin + Keycloak + PostgreSQL + MySQL.
2. **`docker-compose-support-apps.yml`** — Infrastructure only: Zipkin + Keycloak + PostgreSQL + MySQL + Config Server + Service Registry.

All containers are on a custom bridge network (`172.25.0.0/16`) with static IP assignments.

### Startup Order

1. MySQL, PostgreSQL, Zipkin, Keycloak (no dependencies)
2. `internet-banking-config-server` (no dependencies)
3. `internet-banking-service-registry` (no dependencies)
4. `internet-banking-api-gateway` (waits for registry + config server)
5. Business services (wait for registry + config server + MySQL)

### CI/CD

- No CI/CD pipeline exists in the repository (`.github/` only contains `FUNDING.yml`).
- GitHub Actions workflows were explicitly removed (per commit history: "Remove GH Actions workflows").
- Build artifacts are Docker images pushed to `javatodev/*` Docker Hub repositories.

### Testing

- Test framework: JUnit 5 (`useJUnitPlatform()`)
- Test database: H2 in-memory (`com.h2database:h2:2.2.224`)
- Only `core-banking-service` has meaningful unit tests (3 test classes: `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`).
- Other services have only default Spring Boot application context tests (empty test classes).
