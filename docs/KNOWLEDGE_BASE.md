# Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Summary

This is a **Java 21 / Spring Boot 3.2.4** internet banking application built on a microservices architecture using **Spring Cloud 2023.0.0**. The system simulates core banking operations including user management, fund transfers, and utility payments.

### 1.2 Microservices

| Service | Port | Purpose |
|---|---|---|
| **core-banking-service** | 8092 | Dummy banking core — accounts, users, transactions, balance management |
| **internet-banking-user-service** | 8083 | User registration/management with Keycloak integration |
| **internet-banking-fund-transfer-service** | 8084 | Orchestrates fund transfers via the core banking service |
| **internet-banking-utility-payment-service** | 8085 | Orchestrates utility bill payments via the core banking service |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway — routing, security (OAuth2/JWT) |
| **internet-banking-service-registry** | 8081 | Netflix Eureka service discovery |
| **internet-banking-config-server** | 8090 | Spring Cloud Config Server (Git-backed) |

### 1.3 Communication Patterns

```
┌──────────────┐
│  API Gateway │  (OAuth2 / JWT validation via Keycloak)
│   :8082      │
└──────┬───────┘
       │  Routes by path prefix
       ├──► /user/**            → internet-banking-user-service
       ├──► /fund-transfer/**   → internet-banking-fund-transfer-service
       ├──► /utility-payment/** → internet-banking-utility-payment-service
       └──► /banking-core/**    → core-banking-service
```

- **Synchronous (REST/OpenFeign):** All inter-service calls use Spring Cloud OpenFeign clients resolved through Eureka.
  - `internet-banking-user-service` → `core-banking-service` (user lookup by identification)
  - `internet-banking-fund-transfer-service` → `core-banking-service` (fund transfer execution)
  - `internet-banking-utility-payment-service` → `core-banking-service` (utility payment execution)
- **Asynchronous (RabbitMQ):** Listed in the architecture diagram for notifications, but **not yet implemented** (Notification Service is pending development).
- **Service Discovery:** All services register with Eureka; Feign clients resolve service names via Eureka.
- **Centralized Configuration:** All services (except config-server and service-registry) pull configuration from the Config Server, which reads from a [Git repository](https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git).

### 1.4 Infrastructure Components

| Component | Technology | Purpose |
|---|---|---|
| Service Registry | Netflix Eureka | Service discovery and registration |
| API Gateway | Spring Cloud Gateway | Routing, security enforcement, header injection (`X-Auth-Id`) |
| Config Server | Spring Cloud Config | Centralized externalized configuration from Git |
| Identity Provider | Keycloak 23.0.7 | OAuth2/OIDC authentication, user realm management |
| Database | MySQL 8.4.0 | Persistent storage for all business services |
| Distributed Tracing | Zipkin 3 + Micrometer Brave | Request tracing across services |
| Schema Migration | Flyway 10.12.0 | Database schema versioning (core-banking-service only) |
| Containerization | Docker / Docker Compose | Multi-container deployment |

---

## 2. Data Model Documentation

### 2.1 core-banking-service (MySQL: `banking_core_service`)

#### `banking_core_user`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto) | |
| `first_name` | VARCHAR(255) | |
| `last_name` | VARCHAR(255) | |
| `email` | VARCHAR(255) | |
| `identification_number` | VARCHAR(255) | National ID (e.g., `808829932V`) |

#### `banking_core_account`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto) | |
| `number` | VARCHAR(255) | Account number (e.g., `100015003000`) |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `ACTIVE` |
| `actual_balance` | DECIMAL(19,2) | |
| `available_balance` | DECIMAL(19,2) | |
| `user_id` | BIGINT (FK → `banking_core_user.id`) | |

#### `banking_core_transaction`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto) | |
| `amount` | DECIMAL(19,2) | Negative for debits |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Target account number or reference |
| `transaction_id` | VARCHAR(50) | UUID correlation ID |
| `account_id` | BIGINT (FK → `banking_core_account.id`) | |

#### `banking_core_utility_account`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto) | |
| `number` | VARCHAR(255) | Provider account number |
| `provider_name` | VARCHAR(255) | e.g., `VODAFONE`, `VERIZON` |

**Relationships:**
- `banking_core_user` 1:N `banking_core_account`
- `banking_core_account` 1:N `banking_core_transaction`
- `banking_core_utility_account` is standalone (no FK)

### 2.2 internet-banking-user-service (MySQL)

#### `user`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto) | |
| `auth_id` | VARCHAR | Keycloak user UUID |
| `identification` | VARCHAR | Maps to core banking identification |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED` |
| `created_at` | TIMESTAMP | Audit field (via `AuditAware`) |
| `updated_at` | TIMESTAMP | Audit field |
| `created_by` | VARCHAR | Audit field |
| `updated_by` | VARCHAR | Audit field |

### 2.3 internet-banking-fund-transfer-service (MySQL)

#### `fund_transfer`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto) | |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `transaction_reference` | VARCHAR | Core banking transaction ID |
| `status` | VARCHAR | Enum: `PENDING`, `SUCCESS` |
| `created_at` / `updated_at` / `created_by` / `updated_by` | | Audit fields |

### 2.4 internet-banking-utility-payment-service (MySQL)

#### `utility_payment`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, auto) | |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Bill reference |
| `account` | VARCHAR | Source bank account number |
| `transaction_id` | VARCHAR | Core banking transaction ID |
| `status` | VARCHAR | Enum: `PROCESSING`, `SUCCESS` |
| `created_at` / `updated_at` / `created_by` / `updated_by` | | Audit fields |

---

## 3. API Surface Map

### 3.1 core-banking-service (`:8092`)

| Method | Endpoint | Description | Request | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | Path: `account_number` | `BankAccount` (number, type, status, balances, user) |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider | Path: `account_name` | `UtilityAccount` (id, number, providerName) |
| `GET` | `/api/v1/user/{identification}` | Get user by ID number | Path: `identification` | `User` (id, firstName, lastName, email, identificationNumber, accounts) |
| `GET` | `/api/v1/user` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |
| `POST` | `/api/v1/transaction/fund-transfer` | Execute fund transfer | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `POST` | `/api/v1/transaction/util-payment` | Execute utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |

### 3.2 internet-banking-user-service (`:8083`)

| Method | Endpoint | Description | Request | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Register new user (public) | `{ firstName, lastName, email, password, identification }` | `User` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user (approve/reject) | Path: `id`, Body: `{ status }` | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | Path: `id` | `User` |

### 3.3 internet-banking-fund-transfer-service (`:8084`)

| Method | Endpoint | Description | Request | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `GET` | `/api/v1/transfer` | List transfers (paginated) | Query: `page`, `size`, `sort` | `List<FundTransfer>` |

### 3.4 internet-banking-utility-payment-service (`:8085`)

| Method | Endpoint | Description | Request | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/utility-payment` | List payments (paginated) | Query: `page`, `size`, `sort` | `List<UtilityPayment>` |

### 3.5 API Gateway Routes (`:8082`)

All requests are routed through the gateway with path prefixes:
- `/user/**` → `internet-banking-user-service`
- `/fund-transfer/**` → `internet-banking-fund-transfer-service`
- `/utility-payment/**` → `internet-banking-utility-payment-service`
- `/banking-core/**` → `core-banking-service`

**Public endpoints (no auth required):**
- `POST /user/api/v1/bank-users/register`
- `GET /actuator/**` (all services)

**All other endpoints require a valid JWT Bearer token** issued by Keycloak.

---

## 4. Key Business Logic Inventory

### 4.1 Fund Transfer Rules (`TransactionService.fundTransfer`)

1. Look up source (`fromAccount`) and destination (`toAccount`) bank accounts from the core banking database.
2. **Validate balance:** Source account `actualBalance` must be ≥ transfer `amount` and ≥ 0. Throws `InsufficientFundsException` if violated.
3. **Debit source:** Subtract `amount` from `actualBalance`; set `availableBalance = actualBalance - amount` (note: double-subtraction bug — see Gap Analysis).
4. Record a `FUND_TRANSFER` transaction for the source account (negative amount).
5. **Credit destination:** Add `amount` to `actualBalance`; set `availableBalance = actualBalance + amount` (note: double-addition bug — see Gap Analysis).
6. Record a `FUND_TRANSFER` transaction for the destination account (positive amount).
7. Return the generated `transactionId` (UUID).
8. The entire operation is `@Transactional`.

### 4.2 Utility Payment Processing (`TransactionService.utilPayment`)

1. Look up source bank account.
2. **Validate balance:** Same rules as fund transfer.
3. Look up utility provider account by `providerId`.
4. Debit source account (same double-subtraction pattern).
5. Record a `UTILITY_PAYMENT` transaction.
6. Return `transactionId`.
7. Note: No actual third-party API call is made — marked as a placeholder in code comments.

### 4.3 User Registration (`UserService.createUser` in user-service)

1. Check if email already exists in Keycloak → throw `UserAlreadyRegisteredException`.
2. Look up user in core banking by `identification` number (via Feign to core-banking-service).
3. Validate that the provided email matches the core banking record → throw `InvalidEmailException`.
4. Create user in Keycloak (disabled, email unverified) with provided password.
5. On successful Keycloak creation (HTTP 201), fetch Keycloak user to get `authId`.
6. Save user locally with `PENDING` status.
7. If core banking user not found → throw `InvalidBankingUserException`.

### 4.4 User Approval (`UserService.updateUser`)

1. Find user by ID in the local database.
2. If new status is `APPROVED`:
   - Fetch user from Keycloak by `authId`.
   - Set `enabled = true` and `emailVerified = true` in Keycloak.
3. Update local user status.

---

## 5. Integration Points

### 5.1 Keycloak (Identity Provider)

- **Version:** 23.0.7
- **Connection:** Keycloak Admin Client SDK (`keycloak-admin-client:24.0.4`)
- **Configuration:** Via `app.config.keycloak.*` properties (server-url, realm, clientId, client-secret)
- **Usage:** User CRUD, authentication realm management
- **Singleton Pattern:** `KeycloakProperties` uses a lazy-initialized static singleton for the Keycloak client (not thread-safe — see Gap Analysis).
- **JWT Validation:** The API Gateway validates JWT tokens against Keycloak's JWK Set URI.

### 5.2 RabbitMQ (Message Broker)

- **Listed in architecture** but **not implemented**. No RabbitMQ dependencies in any `build.gradle`. The Notification Service that would consume messages is marked as "PENDING Development."

### 5.3 Zipkin (Distributed Tracing)

- **Version:** Zipkin 3 (Docker image: `openzipkin/zipkin:3`)
- **Integration:** `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` in all business services
- **Port:** 9411

### 5.4 Database Connections

- **MySQL 8.4.0:** All business services connect to the same MySQL instance via Spring Data JPA.
- **Flyway:** Only `core-banking-service` uses Flyway for schema migration (3 migration scripts).
- **H2:** Used for test profiles (in-memory).

### 5.5 Spring Cloud Config (Externalized Configuration)

- **Config Server** reads from: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch:** `main`
- **Search path:** `configuration/`
- Each service's `application.yml` contains only `spring.application.name`; all other config (ports, DB URLs, Eureka, Keycloak, etc.) is externalized.
- Services use `spring-cloud-starter-bootstrap` to load config on startup.

### 5.6 Eureka Service Registry

- Self-contained Eureka server on port 8081.
- All other services register as Eureka clients.
- Feign clients resolve service names (`core-banking-service`) via Eureka.

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Gradle** (per-service `build.gradle`, no multi-project root build)
- Each service is an independent Gradle project with its own `settings.gradle`
- Java 21 source compatibility
- Key Gradle plugins: `spring-boot`, `spring-dependency-management`, `gradle-git-properties`

### 6.2 Docker

- Each service has a `Dockerfile` based on `eclipse-temurin:21.0.2_13-jre-alpine`
- Images include `wait-for-it.sh` to handle startup ordering
- Docker profiles: `-Dspring.profiles.active=docker`

### 6.3 Docker Compose

Two compose files in `docker-compose/`:

1. **`docker-compose.yml`** — Full stack (all services + infrastructure)
2. **`docker-compose-support-apps.yml`** — Infrastructure only (Zipkin, Keycloak, MySQL, Config Server, Service Registry)

**Network:** Custom bridge network `javatodev_ib_network` (subnet `172.25.0.0/16`) with static IPs assigned per container.

**Startup Dependencies:** Managed via `wait-for-it.sh` entrypoints:
- Business services wait for Service Registry (8081) → Config Server (8090) → MySQL (3306)

### 6.4 CI/CD

- No GitHub Actions workflows (removed per commit history).
- No Gradle wrapper (`gradlew`) checked in — relies on local Gradle installation.
- No CI pipeline configuration present in the repository.

### 6.5 Seed Data

- Flyway migrations (core-banking-service) create tables and insert test data:
  - 4 users, 14 bank accounts, 6 utility provider accounts
- Keycloak realm is imported via mounted volume (`docker-compose/keycloak/`) with pre-configured realm, client, and user data.
  - Test credentials: `ib_admin@javatodev.com` / `5V7huE3G86uB`
