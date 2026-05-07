# Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Summary

Internet Banking Concept is a Java 21 / Spring Boot 3.2.4 microservices application implementing internet banking operations. The system comprises **6 microservices** coordinated through Spring Cloud infrastructure components.

**Technology Stack:**
- Java 21, Spring Boot 3.2.4, Spring Cloud 2023.0.0
- Netflix Eureka (service discovery), Spring Cloud Gateway (API gateway)
- Spring Cloud Config (centralized configuration), OpenFeign (inter-service communication)
- MySQL 8.4 (persistence), Flyway (database migrations — core-banking-service only)
- Keycloak 23.0.7 (identity & access management), OAuth 2.0 / JWT
- Zipkin + Micrometer Brave (distributed tracing)
- Docker / Docker Compose (containerization)
- Gradle 8.6 (build tool), Lombok (boilerplate reduction)
- SpringDoc OpenAPI (Swagger UI for fund-transfer, utility-payment, core-banking, and user services)

### 1.2 Microservices

| Service | Port | Purpose |
|---|---|---|
| `internet-banking-service-registry` | 8081 | Netflix Eureka server — service discovery |
| `internet-banking-config-server` | 8090 | Spring Cloud Config server — centralized configuration from Git |
| `internet-banking-api-gateway` | 8082 | Spring Cloud Gateway — single entry point, JWT validation, routing |
| `internet-banking-user-service` | 8083 | User registration/management via Keycloak + local DB |
| `internet-banking-fund-transfer-service` | 8084 | Fund transfer orchestration between accounts |
| `internet-banking-utility-payment-service` | 8085 | Utility bill payment processing |
| `core-banking-service` | 8092 | Core banking — accounts, users, transactions, balance management |

### 1.3 Communication Patterns

```
                          ┌─────────────────────┐
                          │   API Gateway (:8082)│
                          │  (JWT validation)    │
                          └──────┬──────────────┘
                                 │ routes via Eureka
                  ┌──────────────┼──────────────────┐
                  ▼              ▼                   ▼
          ┌──────────┐  ┌───────────────┐  ┌────────────────┐
          │  User    │  │ Fund Transfer │  │ Utility Payment│
          │  Service │  │   Service     │  │   Service      │
          │  (:8083) │  │   (:8084)     │  │   (:8085)      │
          └────┬─────┘  └──────┬────────┘  └───────┬────────┘
               │ Feign         │ Feign              │ Feign
               ▼               ▼                    ▼
          ┌─────────────────────────────────────────────┐
          │           Core Banking Service (:8092)       │
          │  (accounts, users, transactions, balances)   │
          └─────────────────────────────────────────────┘
```

- **Synchronous:** All inter-service calls use **OpenFeign** HTTP clients over Eureka service discovery. No async messaging is currently implemented (RabbitMQ is mentioned in the README but not present in the codebase).
- **API Gateway routing:** Requests are prefixed with service identifiers (`/user/`, `/fund-transfer/`, `/payment/`, `/core/`) and routed to respective services.
- **Authentication flow:** The API Gateway validates JWT tokens from Keycloak and injects `X-Auth-Id` header into proxied requests. Downstream services extract this header via `AppAuthUserFilter`.

### 1.4 Infrastructure Components

| Component | Image/Tech | Purpose |
|---|---|---|
| MySQL 8.4 | `mysql:8.4.0` | Primary data store for all business services |
| Keycloak 23.0.7 | `quay.io/keycloak/keycloak:23.0.7` | OAuth 2.0 / OpenID Connect identity provider |
| PostgreSQL 15 | `postgres:15` | Keycloak's backing database |
| Zipkin 3 | `openzipkin/zipkin:3` | Distributed tracing UI |
| Config Git Repo | GitHub | Externalized configuration source |

### 1.5 Configuration Management

The **Config Server** fetches configuration from a Git repository:
- **Repository:** `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Search path:** `configuration/`
- **Branch:** `main`

Each service uses Spring Cloud Bootstrap to connect to the Config Server at startup. Profile-specific bootstrap files (`bootstrap-dev.yml`, `bootstrap-docker.yml`) point to different Config Server URIs for local dev vs Docker environments.

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL: `banking_core_service`)

#### `banking_core_user`
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | User identifier |
| `first_name` | VARCHAR(255) | First name |
| `last_name` | VARCHAR(255) | Last name |
| `email` | VARCHAR(255) | Email address |
| `identification_number` | VARCHAR(255) | National ID / identification number |

#### `banking_core_account`
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | Account identifier |
| `number` | VARCHAR(255) | Account number |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `available_balance` | DECIMAL(19,2) | Available balance |
| `actual_balance` | DECIMAL(19,2) | Actual balance |
| `user_id` | BIGINT (FK → `banking_core_user.id`) | Owning user |

**Relationship:** `banking_core_user` 1 ← N `banking_core_account`

#### `banking_core_transaction`
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | Transaction identifier |
| `amount` | DECIMAL(19,2) | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Reference (destination account or utility ref) |
| `transaction_id` | VARCHAR(50) | UUID grouping related debit/credit entries |
| `account_id` | BIGINT (FK → `banking_core_account.id`) | Related account |

#### `banking_core_utility_account`
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | Utility account identifier |
| `number` | VARCHAR(255) | Utility account number |
| `provider_name` | VARCHAR(255) | Provider name (e.g., VODAFONE, VERIZON) |

### 2.2 Fund Transfer Service (MySQL: `banking_core_fund_transfer_service`)

#### `fund_transfer` (JPA-managed, no Flyway migration)
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | Transfer record identifier |
| `transaction_reference` | VARCHAR | Core banking transaction ID |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `status` | VARCHAR | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| Audit fields | | `created_date`, `created_by`, `modified_date`, `modified_by`, `version` |

### 2.3 User Service (MySQL: `banking_core_user_service`)

#### `user` (JPA-managed, no Flyway migration)
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | User record identifier |
| `auth_id` | VARCHAR | Keycloak user UUID |
| `identification` | VARCHAR | National ID linked to core banking user |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| Audit fields | | `created_date`, `created_by`, `modified_date`, `modified_by`, `version` |

### 2.4 Utility Payment Service (MySQL: `banking_core_utility_payment_service`)

#### `utility_payment` (JPA-managed, no Flyway migration)
| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK, auto-increment) | Payment record identifier |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Customer reference number |
| `account` | VARCHAR | Source bank account number |
| `transaction_id` | VARCHAR | Core banking transaction ID |
| `status` | VARCHAR | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| Audit fields | | `created_date`, `created_by`, `modified_date`, `modified_by`, `version` |

### 2.5 Seed Data

The core banking service includes Flyway-managed seed data:
- **4 users** with identification numbers
- **14 savings accounts** with balances ranging from 12,000 to 889,000.33
- **6 utility providers**: VODAFONE, VERIZON, SINGTEL, HUTCH, AIRTEL, GIO

---

## 3. API Surface Map

### 3.1 Core Banking Service (`:8092`)

| Method | Endpoint | Request | Response | Description |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Path: account number | `BankAccount` DTO | Get bank account by number |
| `GET` | `/api/v1/account/util-account/{account_name}` | Path: provider name | `UtilityAccount` DTO | Get utility account by provider |
| `POST` | `/api/v1/transaction/fund-transfer` | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` | Process fund transfer |
| `POST` | `/api/v1/transaction/util-payment` | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` | Process utility payment |
| `GET` | `/api/v1/user/{identification}` | Path: identification number | `User` DTO with bank accounts | Get user by ID number |
| `GET` | `/api/v1/user` | Query: Pageable | `List<User>` | List users (paginated) |

### 3.2 User Service (`:8083`)

| Method | Endpoint | Request | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | `{ email, identification, password }` | `User` DTO | Register new internet banking user |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `{ status }` | `User` DTO | Update user status (e.g., approve) |
| `GET` | `/api/v1/bank-users` | Query: Pageable | `List<User>` | List all users (paginated) |
| `GET` | `/api/v1/bank-users/{id}` | Path: user ID | `User` DTO | Get user by ID |

### 3.3 Fund Transfer Service (`:8084`)

| Method | Endpoint | Request | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | `{ fromAccount, toAccount, amount, authID }` | `{ message, transactionId }` | Initiate fund transfer |
| `GET` | `/api/v1/transfer` | Query: Pageable | `List<FundTransfer>` | List all transfers (paginated) |

### 3.4 Utility Payment Service (`:8085`)

| Method | Endpoint | Request | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` | Process utility payment |
| `GET` | `/api/v1/utility-payment` | Query: Pageable | `List<UtilityPayment>` | List all payments (paginated) |

### 3.5 API Gateway Routes (`:8082`)

All requests flow through the gateway with these prefixes:
| Gateway Prefix | Target Service |
|---|---|
| `/user/**` | `internet-banking-user-service` |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` |
| `/payment/**` | `internet-banking-utility-payment-service` |
| `/core/**` | `core-banking-service` |

**Public endpoint:** `POST /user/api/v1/bank-users/register` (no JWT required)
**All other endpoints** require a valid Keycloak JWT Bearer token.
**Actuator endpoints** are publicly accessible (`/actuator/**`, `/{service}/actuator/**`).

---

## 4. Key Business Logic Inventory

### 4.1 Fund Transfer Flow

1. **Fund Transfer Service** receives request (`fromAccount`, `toAccount`, `amount`)
2. Saves a `FundTransferEntity` with status `PENDING`
3. Calls **Core Banking Service** via Feign (`POST /api/v1/transaction/fund-transfer`)
4. **Core Banking Service:**
   a. Reads both from/to `BankAccount` via `AccountService`
   b. Validates sender has sufficient balance (`actualBalance >= amount`)
   c. Generates UUID `transactionId`
   d. Debits from-account: `actualBalance -= amount`, `availableBalance = actualBalance - amount`
   e. Credits to-account: `actualBalance += amount`, `availableBalance = actualBalance + amount`
   f. Creates two `TransactionEntity` records (debit + credit) linked by `transactionId`
   g. Returns `{ message, transactionId }`
5. **Fund Transfer Service** updates entity status to `SUCCESS` with transaction reference

**Business rules:**
- Balance check: `actualBalance >= 0 AND actualBalance >= transferAmount`
- Both accounts must exist in the core banking system
- Entire operation runs within a `@Transactional` boundary in the core service

**Known issue:** Available balance calculation has a double-subtraction bug — `availableBalance` is set to `actualBalance - amount` *after* `actualBalance` has already been reduced, resulting in double the deduction from `availableBalance`.

### 4.2 Utility Payment Flow

1. **Utility Payment Service** receives request (`providerId`, `amount`, `referenceNumber`, `account`)
2. Saves a `UtilityPaymentEntity` with status `PROCESSING`
3. Calls **Core Banking Service** via Feign (`POST /api/v1/transaction/util-payment`)
4. **Core Banking Service:**
   a. Reads the source `BankAccount`
   b. Validates sufficient balance
   c. Looks up the `UtilityAccount` by provider ID
   d. Deducts amount from the source account
   e. Creates a `TransactionEntity` record
   f. Returns `{ message, transactionId }`
5. **Utility Payment Service** updates entity status to `SUCCESS`

**Same double-subtraction bug** exists in utility payment balance calculation.

### 4.3 User Registration Flow

1. **User Service** receives registration request (`email`, `identification`, `password`)
2. Checks if email already exists in Keycloak → `UserAlreadyRegisteredException`
3. Calls **Core Banking Service** via Feign to read user by identification number
4. Validates email matches the core banking record → `InvalidEmailException`
5. Creates user in Keycloak with credentials (email, password, first/last name)
6. If Keycloak returns 201, reads back the user to get `authId`
7. Saves `UserEntity` locally with status `PENDING`
8. Returns the created user

**User Approval Flow:**
1. Admin calls `PATCH /api/v1/bank-users/update/{id}` with `{ status: "APPROVED" }`
2. Service enables the user in Keycloak (`setEnabled(true)`, `setEmailVerified(true)`)
3. Updates local status to `APPROVED`

### 4.4 Authentication & Authorization

- **Keycloak realm:** `javatodev-internet-banking`
- **Client:** `internet-banking-api-client` (client_credentials grant for admin API)
- **Gateway** validates JWT tokens using Keycloak's JWK Set URI
- **Gateway injects** `X-Auth-Id` header (principal name from JWT) into all proxied requests
- **Downstream services** extract `X-Auth-Id` via `AppAuthUserFilter` and store in `ApiRequestContextHolder` (ThreadLocal)
- The `AuditorAwareConfig` uses `ApiRequestContextHolder` to populate JPA audit fields (`createdBy`, `modifiedBy`)

---

## 5. Integration Points

### 5.1 Keycloak

- **Purpose:** User identity management, OAuth 2.0 token issuance
- **Accessed by:** User Service (admin client for CRUD), API Gateway (JWT validation)
- **Connection:** `KeycloakBuilder` with client_credentials grant, configured via `app.config.keycloak.*` properties
- **Realm:** `javatodev-internet-banking` (imported via realm-export.json in Docker)
- **Test credentials:** `ib_admin@javatodev.com / 5V7huE3G86uB`

### 5.2 MySQL

- **Databases:** 4 separate schemas (`banking_core_service`, `banking_core_fund_transfer_service`, `banking_core_user_service`, `banking_core_utility_payment_service`)
- **Provisioned by:** Docker MySQL container with `privileges.sql` init script
- **Migrations:** Flyway manages core-banking-service schema only; other services use `spring.jpa.hibernate.ddl-auto` (configured externally via Config Server)
- **User:** `javatodev_development` with broad privileges

### 5.3 Zipkin (Distributed Tracing)

- **Purpose:** Trace propagation across service calls
- **Libraries:** `micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`, `feign-micrometer`
- **Configuration:** Managed externally via Config Server (trace export URL, sampling rate)
- **All 4 business services** and the API Gateway include tracing dependencies

### 5.4 Spring Cloud Config Server

- **Purpose:** Externalized configuration for all services
- **Source:** Git repository with per-service YAML files
- **Bootstrap:** Each service reads `bootstrap.yml` → connects to Config Server → fetches runtime config (DB URLs, Keycloak settings, Eureka registration, tracing endpoints, etc.)

### 5.5 Eureka Service Registry

- **Purpose:** Service discovery for inter-service communication and gateway routing
- **All services** register as Eureka clients
- **OpenFeign clients** use logical service names (e.g., `core-banking-service`) resolved via Eureka

### 5.6 RabbitMQ (Planned, Not Implemented)

- Mentioned in the README for notification service integration
- **No RabbitMQ dependency** exists in any `build.gradle`
- **Notification Service** is listed as "PENDING Development"

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

Each microservice is an **independent Gradle project** (no multi-project build). Each has its own:
- `build.gradle` with Spring Boot 3.2.4 and Spring Cloud 2023.0.0
- `settings.gradle` defining the project name
- `gradlew` wrapper (Gradle 8.6)
- Git properties plugin (`com.gorylenko.gradle-git-properties`) for build metadata

**Build command per service:**
```bash
cd <service-directory> && ./gradlew build
```

### 6.2 Docker

Each service has a `Dockerfile` based on `eclipse-temurin:21.0.2_13-jre-alpine`:
1. Copies the built JAR (`build/libs/*.jar`)
2. Includes `wait-for-it.sh` for dependency ordering
3. Activates the `docker` Spring profile

### 6.3 Docker Compose

Two compose files in `docker-compose/`:

1. **`docker-compose.yml`** — Full stack (all services + infrastructure)
   - Starts Zipkin, Keycloak, PostgreSQL (for Keycloak), MySQL, Config Server, Service Registry, API Gateway, and all 4 business services
   - Uses `wait-for-it.sh` to ensure dependency ordering (registry → config → mysql → app)
   - Custom bridge network `javatodev_ib_network` (172.25.0.0/16) with static IPs

2. **`docker-compose-support-apps.yml`** — Infrastructure only
   - Zipkin, Keycloak, PostgreSQL, MySQL, Config Server, Service Registry
   - Useful for local development where business services run from IDE

### 6.4 CI/CD

- **No CI/CD pipeline** is defined (no GitHub Actions workflows, Jenkinsfile, or equivalent)
- `.github/FUNDING.yml` exists for GitHub Sponsors (Patreon) but no workflow files
- Build and deployment is **manual** — build JARs locally, then `docker-compose up`

### 6.5 Deployment Topology (Docker)

```
MySQL (:3306)  ←── Core Banking Service (:8092)
                   Fund Transfer Service (:8084)
                   Utility Payment Service (:8085)
                   User Service (:8083)

PostgreSQL     ←── Keycloak (:8080)

Config Server (:8090) ←── [All services fetch config on startup]

Service Registry (:8081) ←── [All services register]

API Gateway (:8082) ──→ [Routes to all services via Eureka]

Zipkin (:9411) ←── [All services export traces]
```
