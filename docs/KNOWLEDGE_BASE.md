# Application Knowledge Base

> **Repository:** `ts-java-spring-boot-internet-banking-microservices`
> **Stack:** Java 21 / Spring Boot 3.2.4 / Spring Cloud 2023.0.0
> **Last updated:** 2026-05-07

---

## 1. Architecture Overview

### 1.1 Service Inventory

| # | Service | Port | Role |
|---|---------|------|------|
| 1 | `internet-banking-service-registry` | 8081 | Netflix Eureka server for service discovery |
| 2 | `internet-banking-config-server` | 8090 | Spring Cloud Config server (Git-backed) |
| 3 | `internet-banking-api-gateway` | 8082 | Spring Cloud Gateway + OAuth2 resource server |
| 4 | `internet-banking-user-service` | 8083 | User registration, profile management, Keycloak integration |
| 5 | `internet-banking-fund-transfer-service` | 8084 | Account-to-account fund transfers |
| 6 | `internet-banking-utility-payment-service` | 8085 | Third-party utility bill payments |
| 7 | `core-banking-service` | 8092 | System-of-record for accounts, users, and ledger transactions |

### 1.2 Communication Patterns

```
                          +-----------------+
                          |   Keycloak      |
                          |  (OAuth2/OIDC)  |
                          +--------+--------+
                                   |
                                   | JWT validation
                                   v
Client --> [API Gateway :8082] --+--> [User Service :8083]
                                 +--> [Fund Transfer Service :8084]
                                 +--> [Utility Payment Service :8085]
                                 +--> [Core Banking Service :8092]
                                         ^       ^        ^
                                         |       |        |
                        OpenFeign -------+-------+--------+
                    (user-svc, fund-xfer-svc, util-pay-svc)
```

- **Client -> Gateway:** HTTP/REST. The gateway validates JWTs against Keycloak and forwards an `X-Auth-Id` header (extracted from the principal) to downstream services.
- **Service -> Service:** Synchronous REST via **Spring Cloud OpenFeign**, routed through **Eureka** service discovery. No direct host/port references in code; services resolve each other by Eureka service name.
- **Gateway routing:** Path-prefix based, configured in application properties fetched from the config server. Prefixes: `/user/**`, `/fund-transfer/**`, `/utility-payment/**`, `/banking-core/**`.
- **Async messaging (planned):** RabbitMQ is referenced in the README for notification messages from fund-transfer and utility-payment services, but the notification service is listed as **PENDING development** and no RabbitMQ client code exists in the current codebase.

### 1.3 Infrastructure Components

| Component | Image / Version | Purpose | Docker IP |
|-----------|----------------|---------|-----------|
| MySQL | `mysql:8.4.0` | Primary datastore for all business services | 172.25.0.9 |
| PostgreSQL | `postgres:15` | Keycloak identity store | 172.25.0.10 |
| Keycloak | `keycloak:23.0.7` | OAuth2/OIDC identity provider | 172.25.0.11 |
| Zipkin | `openzipkin/zipkin:3` | Distributed tracing collector/UI | 172.25.0.12 |
| Spring Cloud Config Server | Custom build | Centralized configuration (Git-backed) | 172.25.0.8 |
| Netflix Eureka | Custom build | Service registry / discovery | 172.25.0.7 |

### 1.4 Networking

All containers run on a custom Docker bridge network (`javatodev_ib_network`) with static IPs in the `172.25.0.0/16` subnet. Services use `wait-for-it.sh` scripts to block startup until their dependencies (config server, service registry, MySQL) are healthy.

### 1.5 Configuration Management

- **Spring Cloud Config Server** fetches configuration from a remote Git repository: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` (branch: `main`, path: `configuration`).
- Each service has a `bootstrap.yml` that points to the config server URL. Profile-specific bootstrap files exist for `dev` and `docker` environments.
- Docker containers start with `-Dspring.profiles.active=docker` to use the Docker bootstrap config.

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL: `banking_core_service`)

Managed via **Flyway** migrations.

#### `banking_core_user`
| Column | Type | Description |
|--------|------|-------------|
| `id` | `BIGINT` (PK, auto-increment) | Internal user ID |
| `first_name` | `VARCHAR(255)` | First name |
| `last_name` | `VARCHAR(255)` | Last name |
| `email` | `VARCHAR(255)` | Email address |
| `identification_number` | `VARCHAR(255)` | National ID (NIC) |

#### `banking_core_account`
| Column | Type | Description |
|--------|------|-------------|
| `id` | `BIGINT` (PK, auto-increment) | Internal account ID |
| `number` | `VARCHAR(255)` | Account number |
| `type` | `VARCHAR(255)` | Enum: `SAVINGS_ACCOUNT` |
| `status` | `VARCHAR(255)` | Enum: `ACTIVE`, `CLOSED` |
| `actual_balance` | `DECIMAL(19,2)` | Ledger balance |
| `available_balance` | `DECIMAL(19,2)` | Available for withdrawal |
| `user_id` | `BIGINT` (FK -> `banking_core_user.id`) | Account owner |

#### `banking_core_utility_account`
| Column | Type | Description |
|--------|------|-------------|
| `id` | `BIGINT` (PK, auto-increment) | Internal ID |
| `number` | `VARCHAR(255)` | Provider account number |
| `provider_name` | `VARCHAR(255)` | e.g., VODAFONE, AIRTEL |

#### `banking_core_transaction`
| Column | Type | Description |
|--------|------|-------------|
| `id` | `BIGINT` (PK, auto-increment) | Internal ID |
| `amount` | `DECIMAL(19,2)` | Signed amount (negative for debit) |
| `transaction_type` | `VARCHAR(30)` | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | `VARCHAR(50)` | Target account number or reference |
| `transaction_id` | `VARCHAR(50)` | UUID for the transaction |
| `account_id` | `BIGINT` (FK -> `banking_core_account.id`) | Source/target account |

#### Relationships
```
banking_core_user 1──*  banking_core_account
banking_core_account 1──*  banking_core_transaction
banking_core_utility_account (standalone, no FK relationships)
```

### 2.2 User Service (MySQL: `banking_core_user_service`)

Schema managed by JPA `hibernate.ddl-auto` (no Flyway).

#### `user`
| Column | Type | Description |
|--------|------|-------------|
| `id` | `BIGINT` (PK, auto-increment) | Internal ID |
| `auth_id` | `VARCHAR` | Keycloak user UUID |
| `identification` | `VARCHAR` | National ID (maps to core banking user) |
| `status` | `VARCHAR` | Enum: `PENDING`, `APPROVED` |
| `created_date` | `INSTANT` | Audit: creation timestamp |
| `created_by` | `VARCHAR` | Audit: creator |
| `modified_date` | `INSTANT` | Audit: last modification |
| `modified_by` | `VARCHAR` | Audit: modifier |
| `version` | `BIGINT` | Optimistic locking version |

### 2.3 Fund Transfer Service (MySQL: `banking_core_fund_transfer_service`)

Schema managed by JPA `hibernate.ddl-auto` (no Flyway).

#### `fund_transfer`
| Column | Type | Description |
|--------|------|-------------|
| `id` | `BIGINT` (PK, auto-increment) | Internal ID |
| `from_account` | `VARCHAR` | Source account number |
| `to_account` | `VARCHAR` | Destination account number |
| `amount` | `DECIMAL` | Transfer amount |
| `transaction_reference` | `VARCHAR` | UUID from core banking |
| `status` | `VARCHAR` | Enum: `PENDING`, `SUCCESS`, `FAILED` |
| `created_date` / `modified_date` / `created_by` / `modified_by` / `version` | Audit fields (inherited from `AuditAware`) |

### 2.4 Utility Payment Service (MySQL: `banking_core_utility_payment_service`)

Schema managed by JPA `hibernate.ddl-auto` (no Flyway).

#### `utility_payment`
| Column | Type | Description |
|--------|------|-------------|
| `id` | `BIGINT` (PK, auto-increment) | Internal ID |
| `provider_id` | `BIGINT` | Core banking utility account ID |
| `amount` | `DECIMAL` | Payment amount |
| `reference_number` | `VARCHAR` | Bill reference |
| `account` | `VARCHAR` | Payer's bank account number |
| `transaction_id` | `VARCHAR` | UUID from core banking |
| `status` | `VARCHAR` | Enum: `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `modified_date` / `created_by` / `modified_by` / `version` | Audit fields (inherited from `AuditAware`) |

---

## 3. API Surface Map

### 3.1 Core Banking Service (`:8092`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | - | `BankAccount` |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | - | `UtilityAccount` |
| `GET` | `/api/v1/user/{identification}` | Get user by national ID | - | `User` |
| `GET` | `/api/v1/user` | List users (paginated) | - | `List<User>` |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer | `FundTransferRequest` | `FundTransferResponse` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `UtilityPaymentRequest` | `UtilityPaymentResponse` |

**Request/Response Shapes:**

```json
// FundTransferRequest
{ "fromAccount": "string", "toAccount": "string", "amount": 0.00 }

// FundTransferResponse
{ "message": "string", "transactionId": "uuid-string" }

// UtilityPaymentRequest
{ "providerId": 0, "amount": 0.00, "referenceNumber": "string", "account": "string" }

// UtilityPaymentResponse
{ "message": "string", "transactionId": "uuid-string" }

// BankAccount
{ "id": 0, "number": "string", "type": "SAVINGS_ACCOUNT", "status": "ACTIVE",
  "availableBalance": 0.00, "actualBalance": 0.00, "user": {...} }

// User (core banking)
{ "id": 0, "firstName": "string", "lastName": "string",
  "email": "string", "identificationNumber": "string", "accounts": [...] }
```

### 3.2 User Service (`:8083`)

Gateway prefix: `/user/**`

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/bank-users/register` | Register new user (public) | `User` | `User` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user (approve/reject) | `UserUpdateRequest` | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | - | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | - | `User` |

**Request/Response Shapes:**

```json
// User (registration request)
{ "email": "string", "identification": "string", "password": "string" }

// User (response)
{ "id": 0, "email": "string", "identification": "string",
  "authId": "uuid-string", "status": "PENDING|APPROVED", "version": 0 }

// UserUpdateRequest
{ "status": "APPROVED|PENDING" }
```

### 3.3 Fund Transfer Service (`:8084`)

Gateway prefix: `/fund-transfer/**`

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `FundTransferRequest` | `FundTransferResponse` |
| `GET` | `/api/v1/transfer` | List transfers (paginated) | - | `List<FundTransfer>` |

**Request/Response Shapes:**

```json
// FundTransferRequest
{ "fromAccount": "string", "toAccount": "string", "amount": 0.00, "authID": "string" }

// FundTransferResponse
{ "message": "string", "transactionId": "uuid-string" }
```

### 3.4 Utility Payment Service (`:8085`)

Gateway prefix: `/utility-payment/**`

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `UtilityPaymentRequest` | `UtilityPaymentResponse` |
| `GET` | `/api/v1/utility-payment` | List payments (paginated) | - | `List<UtilityPayment>` |

**Request/Response Shapes:**

```json
// UtilityPaymentRequest
{ "providerId": 0, "amount": 0.00, "referenceNumber": "string", "account": "string" }

// UtilityPaymentResponse
{ "message": "string", "transactionId": "uuid-string" }
```

### 3.5 Service Registry (`:8081`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/` | Eureka dashboard (HTML) |
| `GET` | `/eureka/apps` | Registered services (XML/JSON) |

### 3.6 Config Server (`:8090`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/{application}/{profile}` | Fetch config for service + profile |
| `GET` | `/{application}/{profile}/{label}` | Fetch config for service + profile + Git branch |

---

## 4. Key Business Logic Inventory

### 4.1 Fund Transfer Rules (`TransactionService.fundTransfer`)

1. Look up both source (`fromAccount`) and destination (`toAccount`) bank accounts via `AccountService`.
2. **Balance validation:** `fromAccount.actualBalance` must be >= 0 AND >= transfer `amount`. Throws `InsufficientFundsException` otherwise.
3. Debit source account: subtract `amount` from both `actualBalance` and `availableBalance`.
4. Credit destination account: add `amount` to both `actualBalance` and `availableBalance`.
5. Create two `TransactionEntity` records (one debit, one credit) with the same UUID `transactionId`.
6. Return `FundTransferResponse` with the transaction ID.
7. **Note:** The entire operation is `@Transactional` (JTA).

**Orchestration (Fund Transfer Service):**
1. Save a `FundTransferEntity` with status `PENDING`.
2. Call core banking via Feign to execute the transfer.
3. Update entity with `transactionReference` and status `SUCCESS`.
4. No rollback/compensation logic if the Feign call fails mid-way.

### 4.2 Utility Payment Processing (`TransactionService.utilPayment`)

1. Look up the payer's bank account.
2. **Balance validation:** Same rules as fund transfer.
3. Look up the utility provider account by `providerId`.
4. Debit the payer's account (subtract from both balances).
5. Record a `TransactionEntity` with type `UTILITY_PAYMENT`.
6. Return `UtilityPaymentResponse`.
7. **Note:** No actual third-party API call is made (placeholder comment in code).

**Orchestration (Utility Payment Service):**
1. Save a `UtilityPaymentEntity` with status `PROCESSING`.
2. Call core banking via Feign.
3. Update entity with `transactionId` and status `SUCCESS`.

### 4.3 User Registration Flow (`UserService.createUser`)

1. Check Keycloak for existing user with the same email. Throw `UserAlreadyRegisteredException` if found.
2. Call core banking service via Feign to verify the user exists by `identification` (national ID).
3. Validate that the email matches the core banking record. Throw `InvalidEmailException` if mismatch.
4. Create a Keycloak user representation with `enabled=false`, `emailVerified=false`.
5. Set user credentials (password) from the request.
6. Call Keycloak Admin API to create the user. Expect HTTP 201.
7. Re-query Keycloak to get the assigned `authId` (UUID).
8. Save a local `UserEntity` with status `PENDING`.
9. Return the created user.

### 4.4 User Approval (`UserService.updateUser`)

1. Find the user entity by ID.
2. If new status is `APPROVED`, update Keycloak: set `enabled=true`, `emailVerified=true`.
3. Update the local entity's status.

### 4.5 Balance Calculation Bug

In `TransactionService.utilPayment()` and `internalFundTransfer()`, the `availableBalance` is set to `actualBalance.subtract(amount)` AFTER `actualBalance` was already reduced. This effectively **double-subtracts** the amount from `availableBalance`, resulting in incorrect balance tracking.

---

## 5. Integration Points

### 5.1 Keycloak Integration

- **Service:** `internet-banking-user-service`
- **Library:** `keycloak-admin-client:24.0.4`
- **Connection:** Configured via `app.config.keycloak.*` properties (server-url, realm, clientId, client-secret) in the remote config repo.
- **Authentication:** Client credentials grant (`grant_type=client_credentials`).
- **Operations:** Create user, read user by ID, read user by email, update user (enable/verify).
- **Singleton pattern:** `KeycloakProperties` uses a static `Keycloak` instance (not thread-safe; see Gap Analysis).
- **Realm data:** Pre-imported via `realm-export.json` mounted into the Keycloak container.

### 5.2 RabbitMQ (Planned, Not Implemented)

- Referenced in README as the messaging backbone for notifications.
- **No RabbitMQ client dependency** exists in any `build.gradle`.
- **No notification service** exists in the codebase.

### 5.3 Zipkin Distributed Tracing

- **All business services** include Micrometer Tracing + Brave + Zipkin Reporter dependencies.
- Trace data is exported to `openzipkin/zipkin:3` at `172.25.0.12:9411`.
- Correlation IDs propagate across Feign calls via the `feign-micrometer` bridge.
- The API Gateway also includes tracing dependencies.

### 5.4 Inter-Service REST (OpenFeign)

| Caller | Target | Feign Client | Endpoints Called |
|--------|--------|-------------|-----------------|
| `user-service` | `core-banking-service` | `BankingCoreRestClient` | `GET /api/v1/user/{identification}` |
| `fund-transfer-service` | `core-banking-service` | `BankingCoreFeignClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/fund-transfer` |
| `utility-payment-service` | `core-banking-service` | `BankingCoreRestClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/util-payment` |

All Feign clients resolve `core-banking-service` by Eureka name. Fund transfer and utility payment services include a `CustomFeignClientConfiguration` with `Logger.Level.FULL` for request/response logging.

### 5.5 Database Connections

| Service | Database | Schema | Migration |
|---------|----------|--------|-----------|
| `core-banking-service` | MySQL | `banking_core_service` | **Flyway** (3 migration scripts) |
| `internet-banking-user-service` | MySQL | `banking_core_user_service` | JPA auto (no Flyway) |
| `internet-banking-fund-transfer-service` | MySQL | `banking_core_fund_transfer_service` | JPA auto (no Flyway) |
| `internet-banking-utility-payment-service` | MySQL | `banking_core_utility_payment_service` | JPA auto (no Flyway) |

All services connect to the same MySQL instance with credentials managed via the remote config server.

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Gradle** with the Spring Boot plugin (`3.2.4`) and Spring Dependency Management plugin (`1.1.4`).
- Java source compatibility: **21**.
- All services use `com.gorylenko.gradle-git-properties` plugin to embed Git metadata (except service-registry).
- Tests use **JUnit 5** (`useJUnitPlatform()`).
- Test database: **H2 in-memory** (core-banking-service has H2 test config; other services also include H2 test dependency).

### 6.2 Docker Build

Each service has a `Dockerfile` (details not inspected but follow standard Spring Boot JAR packaging). The `wait-for-it.sh` script is included in each service for startup ordering.

### 6.3 Docker Compose Deployment

**`docker-compose.yml`** (full stack):
1. Starts infrastructure: Zipkin, Keycloak + PostgreSQL, MySQL.
2. Starts platform services: Config Server, Service Registry.
3. Starts business services: API Gateway, User Service, Fund Transfer Service, Utility Payment Service, Core Banking Service.
4. All on a single bridge network with fixed IPs.

**`docker-compose-support-apps.yml`** (infrastructure only):
- Only starts Zipkin, Keycloak, PostgreSQL, MySQL, Config Server, and Service Registry.
- Useful for local development where business services run on the host.

### 6.4 Startup Ordering

Services use `wait-for-it.sh` in their Docker entrypoints to wait for:
1. Service Registry (`:8081`)
2. Config Server (`:8090`)
3. MySQL (`:3306`) (for services that need a database)

### 6.5 Seed Data

- **MySQL:** `privileges.sql` creates the app user and 4 databases.
- **Flyway (core-banking):** Creates tables and inserts 4 users, 14 bank accounts, and 6 utility provider accounts.
- **Keycloak:** `realm-export.json` pre-configures the realm, client, and user data.

### 6.6 OpenAPI/Swagger

All business services include `springdoc-openapi-starter-webflux-ui:2.1.0` for auto-generated API documentation. Swagger annotations (`@Tag`, `@Operation`) are present on all controller methods. The Swagger UI is accessible at `/swagger-ui.html` on each service.

### 6.7 Postman Collection

A Postman collection and environment file are provided in `postman_collection/` for manual API testing. The environment `LOCAL_DOCKER_SETUP` is pre-configured for the Docker Compose deployment.
