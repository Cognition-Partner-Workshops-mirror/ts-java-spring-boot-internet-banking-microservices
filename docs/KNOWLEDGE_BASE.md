# Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Summary

This is a **Java 21 / Spring Boot 3.2.4** internet banking application built on a microservices architecture using **Spring Cloud 2023.0.0**. The system implements a banking concept with user management, fund transfers, and utility payments.

### 1.2 Microservices Inventory

| Service | Port | Purpose | Database |
|---|---|---|---|
| **core-banking-service** | 8092 | Core banking engine — accounts, users, transactions, balance management | MySQL (via Flyway) |
| **internet-banking-user-service** | 8083 | User registration, approval workflows, Keycloak integration | MySQL (JPA/Hibernate DDL) |
| **internet-banking-fund-transfer-service** | 8084 | Orchestrates fund transfers between accounts via core banking | MySQL (JPA/Hibernate DDL) |
| **internet-banking-utility-payment-service** | 8085 | Orchestrates utility bill payments via core banking | MySQL (JPA/Hibernate DDL) |
| **internet-banking-api-gateway** | 8082 | Single entry point — routing, OAuth2/JWT security, request enrichment | None |
| **internet-banking-service-registry** | 8081 | Netflix Eureka service discovery | None |
| **internet-banking-config-server** | 8090 | Spring Cloud Config — centralized configuration from Git | None |

### 1.3 Communication Patterns

```
┌──────────────┐
│   Client     │
└──────┬───────┘
       │ HTTPS (JWT Bearer)
       ▼
┌──────────────────────────────┐
│   API Gateway (:8082)        │
│   OAuth2 Resource Server     │
│   + X-Auth-Id header inject  │
└──────┬───────┬───────┬───────┘
       │       │       │         (Eureka-resolved routes)
       ▼       ▼       ▼
   ┌───────┐ ┌─────┐ ┌──────┐
   │ User  │ │Fund │ │Util  │
   │Service│ │Xfer │ │Pay   │
   │:8083  │ │:8084│ │:8085 │
   └───┬───┘ └──┬──┘ └──┬───┘
       │        │       │       (OpenFeign, Eureka-resolved)
       ▼        ▼       ▼
   ┌──────────────────────────┐
   │  Core Banking Service    │
   │        (:8092)           │
   └──────────────────────────┘
```

- **Client → API Gateway**: HTTP/REST with JWT bearer tokens (Keycloak-issued).
- **API Gateway → Downstream Services**: Spring Cloud Gateway routes with `X-Auth-Id` header injection (extracted from JWT principal).
- **Downstream Services → Core Banking**: Synchronous REST via **Spring Cloud OpenFeign** with Eureka service discovery. No circuit breakers, retries, or fallbacks configured.
- **Service Discovery**: All services register with **Netflix Eureka** (service-registry). Feign clients resolve service names via Eureka.
- **Configuration**: All services (except service-registry) pull config from **Spring Cloud Config Server**, which reads from a Git repository (`internet-banking-microservices-configurations`).
- **Tracing**: All business services include **Micrometer Tracing (Brave)** with **Zipkin** reporter.
- **Notification Service**: Referenced in README as planned ("PENDING Development") — intended to consume messages from **RabbitMQ**. Not yet implemented; RabbitMQ is not in any dependency or Docker Compose file.

### 1.4 Infrastructure Components

| Component | Technology | Purpose |
|---|---|---|
| Service Discovery | Netflix Eureka | Service registration and lookup |
| API Gateway | Spring Cloud Gateway (WebFlux) | Routing, security, header injection |
| Config Server | Spring Cloud Config (Git-backed) | Externalized configuration |
| Identity Provider | Keycloak 23.0.7 (PostgreSQL-backed) | OAuth2/OIDC, user management |
| Database | MySQL 8.4.0 | Persistent storage for all business services |
| Distributed Tracing | Zipkin 3 + Micrometer Brave | Request tracing across services |
| Schema Migration | Flyway 10.12.0 | Database schema versioning (core-banking-service only) |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL — `banking_core_service` schema, Flyway-managed)

#### `banking_core_user`
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `first_name` | VARCHAR(255) | | User first name |
| `last_name` | VARCHAR(255) | | User last name |
| `email` | VARCHAR(255) | | User email address |
| `identification_number` | VARCHAR(255) | | National ID / government-issued ID |

#### `banking_core_account`
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `number` | VARCHAR(255) | | Account number (e.g., `100015003000`) |
| `type` | VARCHAR(255) | | Enum: `SAVINGS_ACCOUNT` |
| `status` | VARCHAR(255) | | Enum: `ACTIVE` |
| `actual_balance` | DECIMAL(19,2) | | Ledger balance |
| `available_balance` | DECIMAL(19,2) | | Usable balance |
| `user_id` | BIGINT | FK → `banking_core_user.id` | Account owner |

#### `banking_core_transaction`
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `amount` | DECIMAL(19,2) | | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | NOT NULL | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | NOT NULL | Destination account or reference |
| `transaction_id` | VARCHAR(50) | NOT NULL | UUID grouping debit+credit legs |
| `account_id` | BIGINT | FK → `banking_core_account.id` | Associated account |

#### `banking_core_utility_account`
| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | Surrogate key |
| `number` | VARCHAR(255) | | Provider account number |
| `provider_name` | VARCHAR(255) | | e.g., `VODAFONE`, `VERIZON`, `SINGTEL` |

**Relationships:**
- `banking_core_user` 1 ──→ N `banking_core_account` (via `user_id` FK)
- `banking_core_account` 1 ──→ 1 `banking_core_transaction` (via `account_id` FK, mapped as `@OneToOne` in JPA — see Gap Analysis)

### 2.2 User Service (MySQL — JPA/Hibernate auto-DDL)

#### `user` (entity: `UserEntity extends AuditAware`)
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT PK | Surrogate key |
| `auth_id` | VARCHAR | Keycloak user ID |
| `identification` | VARCHAR | Links to core banking user's `identification_number` |
| `status` | VARCHAR (Enum) | `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | TIMESTAMP | Audit field |
| `created_by` | VARCHAR | Audit field |
| `modified_date` | TIMESTAMP | Audit field |
| `modified_by` | VARCHAR | Audit field |
| `version` | BIGINT | Optimistic locking |

### 2.3 Fund Transfer Service (MySQL — JPA/Hibernate auto-DDL)

#### `fund_transfer` (entity: `FundTransferEntity extends AuditAware`)
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT PK | Surrogate key |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `transaction_reference` | VARCHAR | UUID from core banking |
| `status` | VARCHAR (Enum) | `PENDING`, `SUCCESS`, `FAILED`, `PROCESSING` |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | — | Audit fields |

### 2.4 Utility Payment Service (MySQL — JPA/Hibernate auto-DDL)

#### `utility_payment` (entity: `UtilityPaymentEntity extends AuditAware`)
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT PK | Surrogate key |
| `provider_id` | BIGINT | Utility provider ID in core banking |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Customer reference |
| `account` | VARCHAR | Source bank account number |
| `transaction_id` | VARCHAR | UUID from core banking |
| `status` | VARCHAR (Enum) | `PROCESSING`, `SUCCESS`, `FAILED`, `PENDING` |
| Audit fields | — | Same as above |

---

## 3. API Surface Map

### 3.1 Core Banking Service (`/api/v1/...` on port 8092)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | — | `BankAccount` | Look up bank account by number |
| `GET` | `/api/v1/account/util-account/{account_name}` | — | `UtilityAccount` | Look up utility account by provider name |
| `POST` | `/api/v1/transaction/fund-transfer` | `FundTransferRequest { fromAccount, toAccount, amount }` | `FundTransferResponse { message, transactionId }` | Execute fund transfer (debit + credit) |
| `POST` | `/api/v1/transaction/util-payment` | `UtilityPaymentRequest { providerId, amount, referenceNumber, account }` | `UtilityPaymentResponse { message, transactionId }` | Execute utility payment |
| `GET` | `/api/v1/user/{identification}` | — | `User` | Look up user by identification number |
| `GET` | `/api/v1/user` | Query: `page`, `size`, `sort` | `List<User>` (paginated) | List all users |

### 3.2 User Service (`/api/v1/bank-users/...` on port 8083)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | `User { email, identification, password }` | `User` | Register new user (creates Keycloak user + local record) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `UserUpdateRequest { status }` | `User` | Update user status (e.g., approve registration) |
| `GET` | `/api/v1/bank-users` | Query: `page`, `size`, `sort` | `List<User>` (paginated) | List all registered users |
| `GET` | `/api/v1/bank-users/{id}` | — | `User` | Get user by internal ID |

### 3.3 Fund Transfer Service (`/api/v1/transfer/...` on port 8084)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | `FundTransferRequest { fromAccount, toAccount, amount, authID }` | `FundTransferResponse { message, transactionId }` | Initiate fund transfer (persists locally, delegates to core banking) |
| `GET` | `/api/v1/transfer` | Query: `page`, `size`, `sort` | `List<FundTransfer>` (paginated) | List all fund transfers |

### 3.4 Utility Payment Service (`/api/v1/utility-payment/...` on port 8085)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | `UtilityPaymentRequest { providerId, amount, referenceNumber, account }` | `UtilityPaymentResponse { message, transactionId }` | Process utility payment (persists locally, delegates to core banking) |
| `GET` | `/api/v1/utility-payment` | Query: `page`, `size`, `sort` | `List<UtilityPayment>` (paginated) | List all utility payments |

### 3.5 API Gateway Routes (port 8082)

The gateway routes are configured via Spring Cloud Config (external Git repo). Based on the security configuration, the following path prefixes are used:

| Path Prefix | Routed To | Auth Required |
|---|---|---|
| `/user/**` | `internet-banking-user-service` | Yes (except `/user/api/v1/bank-users/register`) |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` | Yes |
| `/banking-core/**` | `core-banking-service` | Yes |
| `/utility-payment/**` | `internet-banking-utility-payment-service` | Yes |
| `/actuator/**` | Various | No (permitAll) |

### 3.6 OpenAPI / Swagger

All four business services include `springdoc-openapi-starter-webflux-ui:2.1.0` and use `@Tag` / `@Operation` annotations. Swagger UI is available at `/swagger-ui.html` on each service port.

---

## 4. Key Business Logic Inventory

### 4.1 Fund Transfer Rules (Core Banking — `TransactionService`)

1. **Balance validation**: Source account's `actualBalance` must be ≥ transfer `amount` and ≥ 0.
2. **Debit source**: `actualBalance -= amount`, `availableBalance = actualBalance - amount`.
3. **Credit destination**: `actualBalance += amount`, `availableBalance = actualBalance + amount`.
4. **Transaction recording**: Two `TransactionEntity` records per transfer (one debit, one credit) sharing the same `transactionId` (UUID).
5. **Atomicity**: `@Transactional` on the service class (single database transaction).

### 4.2 Utility Payment Rules (Core Banking — `TransactionService`)

1. **Balance validation**: Same as fund transfer.
2. **Debit source**: `actualBalance -= amount`, `availableBalance = actualBalance - amount`.
3. **Transaction recording**: Single `TransactionEntity` with type `UTILITY_PAYMENT`.
4. **No external provider call**: Comment in code notes "we can call third party API to process UTIL payment from payment provider from here" — not implemented.

### 4.3 Fund Transfer Orchestration (Fund Transfer Service — `FundTransferService`)

1. Save `FundTransferEntity` with status `PENDING`.
2. Call core banking's `/api/v1/transaction/fund-transfer` via Feign.
3. Update entity with `transactionReference` and status `SUCCESS`.
4. **No failure handling**: If the Feign call fails, the entity remains `PENDING` with no retry or compensation logic.

### 4.4 Utility Payment Orchestration (Utility Payment Service — `UtilityPaymentService`)

1. Save `UtilityPaymentEntity` with status `PROCESSING`.
2. Call core banking's `/api/v1/transaction/util-payment` via Feign.
3. Update entity with `transactionId` and status `SUCCESS`.
4. **Same gap**: No failure handling or compensation.

### 4.5 User Registration Flow (User Service — `UserService.createUser`)

1. Check if email already exists in Keycloak → reject with `UserAlreadyRegisteredException`.
2. Look up user in core banking by identification number via Feign.
3. Validate email matches core banking record → reject with `InvalidEmailException`.
4. Create Keycloak user (disabled, email not verified, with provided password).
5. If Keycloak returns 201, save local `UserEntity` with status `PENDING`.
6. If core banking user not found → throw `InvalidBankingUserException`.

### 4.6 User Approval Flow (User Service — `UserService.updateUser`)

1. Look up local user entity.
2. If new status is `APPROVED`, enable the Keycloak user and mark email as verified.
3. Update local entity status.

---

## 5. Integration Points

### 5.1 Keycloak (Identity Provider)

- **Version**: 23.0.7 (Quay.io image)
- **Backend**: PostgreSQL 15
- **Integration**: `keycloak-admin-client:24.0.4` in user-service for programmatic user CRUD.
- **OAuth2 flow**: API Gateway acts as OAuth2 Resource Server, validating JWTs via `jwk-set-uri`.
- **Realm import**: Pre-configured realm data loaded on startup via volume mount (`/opt/keycloak/data/import`).
- **Configuration**: Server URL, realm, client ID, and client secret injected via Spring Cloud Config properties (`app.config.keycloak.*`).
- **Singleton pattern**: `KeycloakProperties` uses a static singleton for the Keycloak client instance (not thread-safe in a lazy-init scenario).

### 5.2 RabbitMQ (Message Broker)

- **Status**: Referenced in README as planned infrastructure for notification service.
- **Current state**: **Not implemented**. No RabbitMQ dependency in any `build.gradle`, no Docker Compose service definition, no message producer/consumer code.

### 5.3 Zipkin (Distributed Tracing)

- **Version**: 3 (Docker image `openzipkin/zipkin:3`)
- **Integration**: All business services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave`.
- **Port**: 9411

### 5.4 Database Connections

- **MySQL 8.4.0**: Shared instance for all business services (core-banking, user, fund-transfer, utility-payment).
- **PostgreSQL 15**: Dedicated to Keycloak.
- **Schema management**: Flyway migrations for core-banking-service only; other services rely on JPA/Hibernate auto-DDL (`spring.jpa.hibernate.ddl-auto` via config server).
- **Connection configuration**: Managed via Spring Cloud Config Server (external Git repo).

### 5.5 OpenFeign (Inter-Service Communication)

| Caller | Target | Feign Client | Endpoints Called |
|---|---|---|---|
| user-service | core-banking-service | `BankingCoreRestClient` | `GET /api/v1/user/{identification}` |
| fund-transfer-service | core-banking-service | `BankingCoreFeignClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/fund-transfer` |
| utility-payment-service | core-banking-service | `BankingCoreRestClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/util-payment` |

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build tool**: Gradle (per-service `build.gradle`, no multi-project root build).
- **Java**: 21 (source compatibility).
- **Spring Boot**: 3.2.4 with Spring Cloud 2023.0.0 BOM.
- **Plugins**: `spring-boot`, `dependency-management`, `gradle-git-properties` (generates `git.properties` for `/actuator/info`).

### 6.2 Docker

- **Base image**: `eclipse-temurin:21.0.2_13-jre-alpine`
- **Pattern**: Each service has a `Dockerfile` that copies the built JAR and a `wait-for-it.sh` script.
- **Startup ordering**: Docker Compose uses `wait-for-it.sh` with `--timeout=50` to wait for config-server, service-registry, and MySQL before starting the application JAR.
- **Profile**: Docker containers run with `-Dspring.profiles.active=docker`, which picks up `bootstrap-docker.yml` pointing to Docker-network hostnames.

### 6.3 Docker Compose

Two compose files are provided:

1. **`docker-compose.yml`** — Full stack (all services + infrastructure).
2. **`docker-compose-support-apps.yml`** — Infrastructure only (Zipkin, Keycloak, PostgreSQL, MySQL, config-server, service-registry) for local development where business services run on the host.

- **Network**: Custom bridge network `javatodev_ib_network` with subnet `172.25.0.0/16` and static IP assignments per container.
- **Volumes**: Named volumes for MySQL and PostgreSQL data persistence.
- **Image source**: Pre-built images from `javatodev/*` Docker Hub (no local build step in compose).

### 6.4 CI/CD

- **GitHub Actions**: Workflows were removed (per commit history: "Remove GH Actions workflows").
- **No CI pipeline** is currently configured in the repository.

### 6.5 Test Data

Flyway migration `V1.0.20210427174721__temp_data.sql` seeds:
- 4 users with identification numbers.
- 14 bank accounts across those users.
- 6 utility provider accounts (VODAFONE, VERIZON, SINGTEL, HUTCH, AIRTEL, GIO).
- Keycloak realm data pre-imported with matching user credentials (`ib_admin@javatodev.com / 5V7huE3G86uB`).
