# Application Knowledge Base

## 1. Architecture Overview

### System Summary

This is a Java 21 / Spring Boot 3.2.4 internet banking application built on a microservices architecture using Spring Cloud 2023.0.0. The system models a simplified banking domain with user management, fund transfers, and utility payments.

### Services

| Service | Port | Role |
|---|---|---|
| **core-banking-service** | 8092 | Core banking engine — accounts, users, transactions, balance management |
| **internet-banking-user-service** | 8083 | Internet banking user registration, approval, and Keycloak integration |
| **internet-banking-fund-transfer-service** | 8084 | Orchestrates fund transfers between accounts via the core banking service |
| **internet-banking-utility-payment-service** | 8085 | Orchestrates utility bill payments via the core banking service |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway — routing, OAuth2 security, header propagation |
| **internet-banking-service-registry** | 8081 | Netflix Eureka service registry |
| **internet-banking-config-server** | 8090 | Spring Cloud Config Server backed by a Git repository |

### Communication Patterns

| Pattern | Implementation | Usage |
|---|---|---|
| Synchronous REST | Spring Cloud OpenFeign | Service-to-service calls (user-service → core-banking, fund-transfer → core-banking, utility-payment → core-banking) |
| Service Discovery | Netflix Eureka | All business services register with Eureka; Feign clients resolve service names to instances |
| API Gateway | Spring Cloud Gateway | Single entry point; routes to downstream services by path prefix |
| Centralized Config | Spring Cloud Config Server | All services bootstrap configuration from a remote Git repo via Config Server |
| Distributed Tracing | Micrometer Tracing + Zipkin | Brave bridge propagates trace context across Feign calls; traces exported to Zipkin |
| Authentication Header Propagation | Custom `GlobalFilter` (gateway) + `AppAuthUserFilter` (services) | Gateway extracts OAuth2 principal and sets `X-Auth-Id` header; downstream services read it via a servlet filter and store it in a `ThreadLocal` |

### Infrastructure Components

| Component | Technology | Purpose |
|---|---|---|
| Database | MySQL 8.4 | Persistent store for core-banking, user-service, fund-transfer, and utility-payment data |
| Identity Provider | Keycloak 23.0.7 (PostgreSQL 15 backend) | OAuth2 / OpenID Connect authentication; realm `javatodev-internet-banking` |
| Distributed Tracing | Zipkin 3 | Collects and visualizes distributed traces |
| Message Broker | RabbitMQ (referenced in README) | Intended for notification service — **not yet implemented** |
| Schema Migration | Flyway 10.12 | Manages DDL and seed data for `core-banking-service` |
| Containerization | Docker / Docker Compose | Full-stack deployment with fixed IP addressing on a custom bridge network (`172.25.0.0/16`) |

---

## 2. Data Model Documentation

### core-banking-service

Managed via Flyway migrations. Database: `banking_core_service`.

#### `banking_core_user`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `email` | VARCHAR(255) | |
| `first_name` | VARCHAR(255) | |
| `last_name` | VARCHAR(255) | |
| `identification_number` | VARCHAR(255) | National ID or equivalent |

#### `banking_core_account`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `actual_balance` | DECIMAL(19,2) | Ledger balance |
| `available_balance` | DECIMAL(19,2) | Spendable balance |
| `number` | VARCHAR(255) | Account number |
| `status` | VARCHAR(255) | e.g. `ACTIVE` |
| `type` | VARCHAR(255) | e.g. `SAVINGS_ACCOUNT` |
| `user_id` | BIGINT (FK → `banking_core_user.id`) | Account owner |

#### `banking_core_transaction`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `amount` | DECIMAL(19,2) | Signed amount (negative = debit) |
| `transaction_type` | VARCHAR(30) | `FUND_TRANSFER` or `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Destination account or payment ref |
| `transaction_id` | VARCHAR(50) | UUID grouping both legs of a transfer |
| `account_id` | BIGINT (FK → `banking_core_account.id`) | |

#### `banking_core_utility_account`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `number` | VARCHAR(255) | Utility provider account number |
| `provider_name` | VARCHAR(255) | e.g. `VODAFONE`, `VERIZON` |

### internet-banking-user-service

Database: `banking_core_user_service`. Schema managed by JPA auto-DDL (no Flyway).

#### `user`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `auth_id` | VARCHAR | Keycloak user UUID |
| `identification` | VARCHAR | Links to core banking user's identification_number |
| `status` | ENUM(`PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST`) | |
| `created_date` | TIMESTAMP | Audit field |
| `created_by` | VARCHAR | Audit field — populated from `X-Auth-Id` header |
| `modified_date` | TIMESTAMP | Audit field |
| `modified_by` | VARCHAR | Audit field |
| `version` | BIGINT | Optimistic locking |

### internet-banking-fund-transfer-service

Database: `banking_core_fund_transfer_service`. Schema managed by JPA auto-DDL (no Flyway).

#### `fund_transfer`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `transaction_reference` | VARCHAR | UUID returned by core-banking |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `status` | ENUM(`PENDING`, `PROCESSING`, `SUCCESS`, `FAILED`) | |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | — | Audit + optimistic locking fields |

### internet-banking-utility-payment-service

Database: `banking_core_utility_payment_service`. Schema managed by JPA auto-DDL (no Flyway).

#### `utility_payment`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `provider_id` | BIGINT | References utility account in core-banking |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Customer reference (e.g. bill number) |
| `account` | VARCHAR | Source bank account number |
| `transaction_id` | VARCHAR | UUID returned by core-banking |
| `status` | ENUM(`PENDING`, `PROCESSING`, `SUCCESS`, `FAILED`) | |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | — | Audit + optimistic locking fields |

### Entity Relationships (Cross-Service)

```
core-banking-service                  internet-banking-user-service
┌──────────────────┐                  ┌──────────────┐
│ banking_core_user│◄─── (Feign) ────│     user      │
│  .identification │                  │ .identification│
│  _number         │                  │ .auth_id ──────┼──► Keycloak User
└──────┬───────────┘                  └──────────────┘
       │ 1:N
┌──────▼───────────┐
│banking_core_     │
│  account         │
│  .number ◄───────┼─── referenced by fund_transfer.from_account / .to_account
│                  │                       and utility_payment.account
└──────┬───────────┘
       │ 1:N
┌──────▼───────────┐
│banking_core_     │
│  transaction     │
└──────────────────┘
```

---

## 3. API Surface Map

All endpoints are exposed externally through the API Gateway (port `8082`) which prefixes routes. Internally, each service exposes the paths listed below.

### core-banking-service (port 8092)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/user/{identification}` | Look up user by identification number | — | `UserResponse` (firstName, lastName, email, identificationNumber, bankAccounts[]) |
| `GET` | `/api/v1/user` | Paginated list of users | Query: `page`, `size`, `sort` | `List<User>` |
| `GET` | `/api/v1/account/bank-account/{account_number}` | Look up bank account by number | — | `BankAccount` (number, actualBalance, availableBalance, status, type) |
| `GET` | `/api/v1/account/util-account/{account_name}` | Look up utility account by provider name | — | `UtilityAccount` (id, number, providerName) |
| `POST` | `/api/v1/transaction/fund-transfer` | Execute a fund transfer in the core ledger | `FundTransferRequest` (fromAccount, toAccount, amount) | `FundTransferResponse` (message, transactionId) |
| `POST` | `/api/v1/transaction/util-payment` | Execute a utility payment in the core ledger | `UtilityPaymentRequest` (account, providerId, amount, referenceNumber) | `UtilityPaymentResponse` (message, transactionId) |

### internet-banking-user-service (port 8083)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Register new internet banking user (creates Keycloak user + local record) | `User` (email, identification, password) | `User` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user status (e.g. approve) | `UserUpdateRequest` (status) | `User` |
| `GET` | `/api/v1/bank-users` | Paginated list of users (enriched from Keycloak) | Query: `page`, `size`, `sort` | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Read user by database ID | — | `User` |

### internet-banking-fund-transfer-service (port 8084)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate a fund transfer (persists locally, then calls core-banking) | `FundTransferRequest` (fromAccount, toAccount, amount, authID) | `FundTransferResponse` (message, transactionId) |
| `GET` | `/api/v1/transfer` | Paginated list of fund transfers | Query: `page`, `size`, `sort` | `List<FundTransfer>` |

### internet-banking-utility-payment-service (port 8085)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process a utility payment (persists locally, then calls core-banking) | `UtilityPaymentRequest` (providerId, amount, referenceNumber, account) | `UtilityPaymentResponse` (message, transactionId) |
| `GET` | `/api/v1/utility-payment` | Paginated list of utility payments | Query: `page`, `size`, `sort` | `List<UtilityPayment>` |

### API Gateway Routes (port 8082)

| Route Prefix | Target Service |
|---|---|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |
| `/banking-core/**` | core-banking-service |

Security: All routes require a valid OAuth2 JWT except `/user/api/v1/bank-users/register` and `/actuator/**` paths.

---

## 4. Key Business Logic Inventory

### Fund Transfer Flow

1. Client sends `POST /api/v1/transfer` to fund-transfer-service.
2. Service creates a `FundTransferEntity` with status `PENDING`.
3. Calls core-banking `POST /api/v1/transaction/fund-transfer` via Feign.
4. Core-banking validates both accounts exist, checks `actualBalance >= amount` (throws `InsufficientFundsException` otherwise).
5. Core-banking debits the source account (`actualBalance -= amount`, `availableBalance = actualBalance - amount`).
6. Core-banking credits the destination account (`actualBalance += amount`, `availableBalance = actualBalance + amount`).
7. Two `TransactionEntity` records are created (debit leg with negative amount, credit leg with positive amount), both sharing the same `transactionId`.
8. Fund-transfer-service updates its record to `SUCCESS` with the `transactionReference`.

**Known bug**: Available balance calculation is incorrect — after subtracting the transfer amount from `actualBalance`, it subtracts the amount _again_ when setting `availableBalance` (double-deduction on the source, double-addition on the destination).

### Utility Payment Flow

1. Client sends `POST /api/v1/utility-payment` to utility-payment-service.
2. Service creates a `UtilityPaymentEntity` with status `PROCESSING`.
3. Calls core-banking `POST /api/v1/transaction/util-payment` via Feign.
4. Core-banking validates the source account balance and looks up the utility provider.
5. Debits the source account and records a `UTILITY_PAYMENT` transaction.
6. Utility-payment-service updates its record to `SUCCESS`.

**Same available balance bug** as fund transfer exists here.

### User Registration Flow

1. Client sends `POST /api/v1/bank-users/register` (permitted without auth).
2. User-service checks Keycloak for existing email (rejects duplicates).
3. Calls core-banking `GET /api/v1/user/{identification}` to verify the user exists in the core system.
4. Validates email matches core banking records.
5. Creates a Keycloak user (`enabled=false`, `emailVerified=false`) with the provided password.
6. Persists a local `UserEntity` with status `PENDING`.
7. An admin later calls `PATCH /api/v1/bank-users/update/{id}` with `status=APPROVED`, which enables the Keycloak user and marks email as verified.

### Audit Trail

- User-service, fund-transfer-service, and utility-payment-service use JPA auditing (`@CreatedDate`, `@CreatedBy`, `@LastModifiedDate`, `@LastModifiedBy`).
- `createdBy`/`modifiedBy` populated from the `X-Auth-Id` header via `AppAuthUserFilter` → `ApiRequestContextHolder` (ThreadLocal) → `AuditorAwareConfig`.
- The API Gateway's `GlobalFilter` extracts the OAuth2 principal name and sets `X-Auth-Id`.

---

## 5. Integration Points

### Keycloak

- **Version**: 23.0.7
- **Realm**: `javatodev-internet-banking`
- **Client**: `internet-banking-api-client` (confidential, `client_credentials` grant)
- **User-service integration**: Uses `keycloak-admin-client` 24.0.4 to create/update/search users programmatically.
- **Gateway integration**: Validates JWTs using the Keycloak JWKS endpoint (`spring.security.oauth2.resourceserver.jwt.jwk-set-uri`).
- **Configuration**: Keycloak URL, realm, client ID, and client secret are externalized via Spring Cloud Config (properties: `app.config.keycloak.*`).
- **Realm import**: The Docker Compose setup imports a `realm-export.json` with pre-configured realm, clients, roles, and test users.

### RabbitMQ

- **Status**: Referenced in the README as intended for the Notification Service, but **no RabbitMQ dependency exists** in any `build.gradle` and **no notification service is implemented**.

### Zipkin

- **Version**: 3 (Docker image `openzipkin/zipkin:3`)
- **Port**: 9411
- **Integration**: All business services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave`. Feign calls include `feign-micrometer` for trace context propagation.
- **Configuration**: Zipkin endpoint is configured via Spring Cloud Config (externalized).

### Database Connections

| Service | Database | Connection |
|---|---|---|
| core-banking-service | `banking_core_service` (MySQL) | Flyway-managed schema |
| internet-banking-user-service | `banking_core_user_service` (MySQL) | JPA auto-DDL |
| internet-banking-fund-transfer-service | `banking_core_fund_transfer_service` (MySQL) | JPA auto-DDL |
| internet-banking-utility-payment-service | `banking_core_utility_payment_service` (MySQL) | JPA auto-DDL |
| Keycloak | `keycloak` (PostgreSQL 15) | Internal to Keycloak |

MySQL credentials are provisioned via the custom MySQL Docker image (`privileges.sql`): user `javatodev_development` / password `oPItyPticIAt`.

### Feign Client Topology

```
internet-banking-user-service ──► core-banking-service
  └─ GET /api/v1/user/{identification}

internet-banking-fund-transfer-service ──► core-banking-service
  ├─ GET  /api/v1/account/bank-account/{account_number}
  └─ POST /api/v1/transaction/fund-transfer

internet-banking-utility-payment-service ──► core-banking-service
  ├─ GET  /api/v1/account/bank-account/{account_number}
  └─ POST /api/v1/transaction/util-payment
```

---

## 6. Build and Deployment Pipeline Summary

### Build System

- **Build tool**: Gradle (per-service, no multi-project build)
- **Java**: 21 (source compatibility)
- **Spring Boot**: 3.2.4 (via `org.springframework.boot` Gradle plugin)
- **Spring Cloud**: 2023.0.0 (BOM)
- Each service is an independent Gradle project with its own `settings.gradle`, `build.gradle`, and Gradle wrapper.
- The `com.gorylenko.gradle-git-properties` plugin generates `git.properties` for build info actuator endpoints.

### Docker

- **Base image**: `eclipse-temurin:21.0.2_13-jre-alpine`
- Each service has a `Dockerfile` that copies the fat JAR and a `wait-for-it.sh` script.
- `wait-for-it.sh` ensures startup ordering (waits for service-registry, config-server, and MySQL before launching).
- Docker images are tagged under the `javatodev/` namespace.

### Docker Compose

Two compose files:

1. **`docker-compose.yml`** — Full stack (all services + infra).
2. **`docker-compose-support-apps.yml`** — Infrastructure only (Zipkin, Keycloak, MySQL, Config Server, Service Registry).

Key features:
- Custom bridge network `javatodev_ib_network` with subnet `172.25.0.0/16`.
- Fixed IP addresses for all containers.
- Named volumes for MySQL and PostgreSQL data persistence.
- MySQL initialized via a custom Dockerfile that runs `privileges.sql` to create databases and the app user.

### Configuration Management

- Spring Cloud Config Server reads from: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` (branch `main`, path `configuration/`).
- Each service has three bootstrap profiles:
  - `bootstrap.yml` — localhost (default)
  - `bootstrap-dev.yml` — development IP (`192.168.1.5`)
  - `bootstrap-docker.yml` — Docker service name (`internet-banking-config-server`)
- The Docker entrypoint activates the `docker` profile: `-Dspring.profiles.active=docker`.

### Testing

- **Unit tests**: Core-banking-service has meaningful unit tests for `AccountService`, `TransactionService`, and `UserService` (Mockito-based, ~15 test methods).
- **Other services**: Only have auto-generated `contextLoads()` placeholder tests.
- **Test database**: H2 in-memory database with Flyway disabled for test profiles.
- **Test framework**: JUnit 5 via `spring-boot-starter-test`.

### OpenAPI / Swagger

- All business services include `springdoc-openapi-starter-webflux-ui:2.1.0` for Swagger UI.
- Controllers are annotated with `@Tag` and `@Operation` for API documentation.
