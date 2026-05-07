# Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Summary

This is a Java 21 / Spring Boot 3.2.4 internet banking platform composed of **6 microservices** that communicate via synchronous REST (OpenFeign) and are orchestrated through Spring Cloud infrastructure components.

### 1.2 Service Inventory

| Service | Port | Type | Database | Description |
|---|---|---|---|---|
| `core-banking-service` | 8092 | Business | MySQL (`banking_core_service`) | System of record for accounts, users, and ledger transactions |
| `internet-banking-user-service` | 8083 | Business | MySQL (`banking_core_user_service`) | User registration, profile management, Keycloak integration |
| `internet-banking-fund-transfer-service` | 8084 | Business | MySQL (`banking_core_fund_transfer_service`) | Account-to-account fund transfers |
| `internet-banking-utility-payment-service` | 8085 | Business | MySQL (`banking_core_utility_payment_service`) | Third-party utility bill payments |
| `internet-banking-api-gateway` | 8082 | Infrastructure | None | Spring Cloud Gateway; single entry point, OAuth2/JWT enforcement |
| `internet-banking-service-registry` | 8081 | Infrastructure | None | Netflix Eureka discovery server |
| `internet-banking-config-server` | 8090 | Infrastructure | None | Spring Cloud Config Server (Git-backed) |

### 1.3 Communication Patterns

```
                        ┌──────────────────┐
         Client ──────► │   API Gateway    │ (OAuth2 JWT validation)
                        │   :8082          │
                        └──────┬───────────┘
                               │ Routes via Eureka
                ┌──────────────┼──────────────┐
                ▼              ▼              ▼
        ┌──────────┐  ┌──────────────┐  ┌──────────────┐
        │  User    │  │ Fund Transfer│  │   Utility    │
        │ Service  │  │   Service    │  │  Payment     │
        │  :8083   │  │   :8084      │  │  Service     │
        │          │  │              │  │   :8085      │
        └────┬─────┘  └──────┬───────┘  └──────┬───────┘
             │ Feign         │ Feign           │ Feign
             ▼               ▼                 ▼
        ┌────────────────────────────────────────────┐
        │            Core Banking Service            │
        │                  :8092                     │
        └────────────────────────────────────────────┘
                          │
                    ┌─────┴─────┐
                    │   MySQL   │
                    │   :3306   │
                    └───────────┘
```

**Communication Style:** All inter-service communication is **synchronous REST** via **Spring Cloud OpenFeign** clients. Services discover each other through **Netflix Eureka**. The API Gateway injects an `X-Auth-Id` header (extracted from the JWT principal) into all proxied requests.

**Note:** RabbitMQ is referenced in the README for a planned Notification Service, but no messaging code or RabbitMQ dependency exists in the current codebase.

### 1.4 Infrastructure Components

| Component | Image/Version | Purpose |
|---|---|---|
| **Keycloak** | `quay.io/keycloak/keycloak:23.0.7` | IAM: OAuth2/OIDC provider, realm/client/user management |
| **PostgreSQL** | `postgres:15` | Keycloak's backing database |
| **MySQL** | Custom Dockerfile | Application databases (4 schemas) |
| **Zipkin** | `openzipkin/zipkin:3` | Distributed tracing collection and visualization |
| **Eureka** | Embedded | Service discovery |
| **Spring Cloud Config** | Embedded (Git-backed) | Centralized configuration from [GitHub repo](https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git) |

### 1.5 Docker Network

All containers are deployed on a custom bridge network `javatodev_ib_network` (`172.25.0.0/16`) with static IP assignments. Startup ordering is managed by `wait-for-it.sh` scripts that block until dependencies (config server, service registry, MySQL) are healthy.

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL: `banking_core_service`)

#### `banking_core_user`
| Column | Type | Description |
|---|---|---|
| `id` | `BIGINT` PK AUTO_INCREMENT | Surrogate key |
| `first_name` | `VARCHAR(255)` | User's first name |
| `last_name` | `VARCHAR(255)` | User's last name |
| `email` | `VARCHAR(255)` | Email address |
| `identification_number` | `VARCHAR(255)` | National ID / NIC number |

#### `banking_core_account`
| Column | Type | Description |
|---|---|---|
| `id` | `BIGINT` PK AUTO_INCREMENT | Surrogate key |
| `number` | `VARCHAR(255)` | Account number (e.g., `100015003000`) |
| `type` | `VARCHAR(255)` ENUM | `SAVINGS_ACCOUNT` (only type seeded) |
| `status` | `VARCHAR(255)` ENUM | `ACTIVE` / other statuses |
| `actual_balance` | `DECIMAL(19,2)` | Actual balance |
| `available_balance` | `DECIMAL(19,2)` | Available balance |
| `user_id` | `BIGINT` FK | References `banking_core_user.id` |

**Relationship:** `banking_core_user` 1:N `banking_core_account`

#### `banking_core_transaction`
| Column | Type | Description |
|---|---|---|
| `id` | `BIGINT` PK AUTO_INCREMENT | Surrogate key |
| `amount` | `DECIMAL(19,2)` | Transaction amount (negative for debits) |
| `transaction_type` | `VARCHAR(30)` | `FUND_TRANSFER` or `UTILITY_PAYMENT` |
| `reference_number` | `VARCHAR(50)` | Destination account number or reference |
| `transaction_id` | `VARCHAR(50)` | UUID grouping related entries |
| `account_id` | `BIGINT` FK | References `banking_core_account.id` |

**Relationship:** `banking_core_account` 1:1 `banking_core_transaction` (mapped as `@OneToOne` in JPA, though logically 1:N)

#### `banking_core_utility_account`
| Column | Type | Description |
|---|---|---|
| `id` | `BIGINT` PK AUTO_INCREMENT | Surrogate key |
| `number` | `VARCHAR(255)` | Utility provider account number |
| `provider_name` | `VARCHAR(255)` | Provider name (e.g., VODAFONE, AIRTEL) |

**Schema migration:** Managed by **Flyway** with 3 migration scripts in `db/migration/`.

### 2.2 Internet Banking User Service (MySQL: `banking_core_user_service`)

#### `user`
| Column | Type | Description |
|---|---|---|
| `id` | `BIGINT` PK AUTO_INCREMENT | Surrogate key |
| `auth_id` | `VARCHAR(255)` | Keycloak user UUID |
| `identification` | `VARCHAR(255)` | National ID / NIC |
| `status` | `VARCHAR(255)` ENUM | `PENDING` / `APPROVED` |
| `created_by` | `VARCHAR(255)` | Audit field (from `AuditAware`) |
| `created_on` | `DATETIME` | Audit field |
| `updated_by` | `VARCHAR(255)` | Audit field |
| `updated_on` | `DATETIME` | Audit field |

**Note:** Schema is auto-generated via JPA `ddl-auto` (no Flyway migrations).

### 2.3 Internet Banking Fund Transfer Service (MySQL: `banking_core_fund_transfer_service`)

#### `fund_transfer`
| Column | Type | Description |
|---|---|---|
| `id` | `BIGINT` PK AUTO_INCREMENT | Surrogate key |
| `from_account` | `VARCHAR(255)` | Source account number |
| `to_account` | `VARCHAR(255)` | Destination account number |
| `amount` | `DECIMAL(19,2)` | Transfer amount |
| `status` | `VARCHAR(255)` ENUM | `PENDING` / `SUCCESS` |
| `transaction_reference` | `VARCHAR(255)` | UUID from core banking |
| `created_by` / `created_on` / `updated_by` / `updated_on` | Audit fields | From `AuditAware` |

### 2.4 Internet Banking Utility Payment Service (MySQL: `banking_core_utility_payment_service`)

#### `utility_payment`
| Column | Type | Description |
|---|---|---|
| `id` | `BIGINT` PK AUTO_INCREMENT | Surrogate key |
| `provider_id` | `BIGINT` | Utility provider ID |
| `amount` | `DECIMAL(19,2)` | Payment amount |
| `reference_number` | `VARCHAR(255)` | Customer reference |
| `account` | `VARCHAR(255)` | Source bank account number |
| `transaction_id` | `VARCHAR(255)` | UUID from core banking |
| `status` | `VARCHAR(255)` ENUM | `PROCESSING` / `SUCCESS` |
| `created_by` / `created_on` / `updated_by` / `updated_on` | Audit fields | From `AuditAware` |

---

## 3. API Surface Map

### 3.1 API Gateway Routes (Port 8082)

All requests pass through the gateway with prefix-based routing:

| Prefix | Target Service |
|---|---|
| `/user/**` | `internet-banking-user-service` |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` |
| `/banking-core/**` | `core-banking-service` |
| `/utility-payment/**` | `internet-banking-utility-payment-service` |

**Auth Rules:**
- `POST /user/api/v1/bank-users/register` - **Public** (no auth required)
- `/actuator/**` on all services - **Public**
- All other endpoints - **JWT Bearer token required**

### 3.2 Core Banking Service Endpoints (`/api/v1/...`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | - | `BankAccount { number, type, status, availableBalance, actualBalance }` |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | - | `UtilityAccount { id, number, providerName }` |
| `GET` | `/api/v1/user/{identification}` | Get user by NIC/ID | - | `User { id, firstName, lastName, email, identificationNumber }` |
| `GET` | `/api/v1/user` | List users (paginated) | `Pageable` query params | `List<User>` |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `{ account, providerId, amount, referenceNumber }` | `{ message, transactionId }` |

### 3.3 Internet Banking User Service Endpoints (`/api/v1/bank-users/...`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Register new user | `{ email, identification, password }` | `User { id, email, identification, authId, status }` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user (approve/status) | `{ status }` | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | `Pageable` query params | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | - | `User` |

### 3.4 Internet Banking Fund Transfer Service Endpoints (`/api/v1/transfer/...`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `{ fromAccount, toAccount, amount, authID }` | `{ message, transactionId }` |
| `GET` | `/api/v1/transfer` | List transfers (paginated) | `Pageable` query params | `List<FundTransfer>` |

### 3.5 Internet Banking Utility Payment Service Endpoints (`/api/v1/utility-payment/...`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/utility-payment` | List payments (paginated) | `Pageable` query params | `List<UtilityPayment>` |

### 3.6 Actuator Endpoints (All Services)

Each service exposes Spring Boot Actuator at `/actuator/**` including:
- `/actuator/info` - Application info (git properties)
- `/actuator/health` - Health status

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

1. Client `POST /user/api/v1/bank-users/register` with `{ email, identification, password }`
2. **User Service** checks Keycloak for existing email (rejects duplicates with `UserAlreadyRegisteredException`)
3. Calls **Core Banking Service** via Feign to validate the identification number exists in the banking core
4. Validates email matches the core banking record (rejects mismatch with `InvalidEmailException`)
5. Creates Keycloak user (disabled, email unverified, with provided password)
6. On HTTP 201 from Keycloak, retrieves the Keycloak auth ID
7. Saves user to local DB with `status=PENDING`
8. Admin later approves via `PATCH /update/{id}` with `{ status: APPROVED }`, which enables the Keycloak user and sets email as verified

### 4.2 Fund Transfer Flow

1. Client `POST /fund-transfer/api/v1/transfer` with `{ fromAccount, toAccount, amount }`
2. **Fund Transfer Service** creates a local `FundTransferEntity` with `status=PENDING`
3. Calls **Core Banking Service** via Feign `POST /api/v1/transaction/fund-transfer`
4. **Core Banking** validates both accounts exist
5. Validates sender has sufficient balance (`actualBalance >= amount`)
6. Debits sender: `actualBalance -= amount`, `availableBalance = actualBalance - amount`
7. Credits receiver: `actualBalance += amount`, `availableBalance = actualBalance + amount`
8. Creates two `TransactionEntity` records (debit + credit) with same `transactionId`
9. Returns `{ transactionId }` to Fund Transfer Service
10. Fund Transfer Service updates local record with `status=SUCCESS` and `transactionReference`

**Business Rules:**
- Insufficient funds throws `InsufficientFundsException`
- Non-existent accounts throw `EntityNotFoundException`
- No transfer limits, daily limits, or self-transfer prevention implemented
- No idempotency key or duplicate detection

### 4.3 Utility Payment Flow

1. Client `POST /utility-payment/api/v1/utility-payment` with `{ providerId, amount, referenceNumber, account }`
2. **Utility Payment Service** creates local `UtilityPaymentEntity` with `status=PROCESSING`
3. Calls **Core Banking Service** via Feign `POST /api/v1/transaction/util-payment`
4. **Core Banking** validates the source account and balance
5. Looks up the utility provider by ID
6. Debits the account (same balance logic as fund transfer)
7. Creates a `TransactionEntity` with `type=UTILITY_PAYMENT`
8. Returns `{ transactionId }` to Utility Payment Service
9. Utility Payment Service updates local record with `status=SUCCESS`

**Note:** No actual third-party payment provider integration exists; the comment in code says "we can call third party API to process UTIL payment from payment provider from here."

### 4.4 Balance Calculation Bug

In `TransactionService.utilPayment()` and `internalFundTransfer()`, there is a bug in balance calculation:
```java
fromAccount.setActualBalance(fromAccount.getActualBalance().subtract(amount));
fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(amount));
// ^ availableBalance is set to actualBalance MINUS amount AGAIN (double subtraction)
```
This causes `availableBalance` to be reduced by `2x` the intended amount.

---

## 5. Integration Points

### 5.1 Keycloak (IAM)

- **Used by:** `internet-banking-user-service`
- **Protocol:** Keycloak Admin Client SDK (`keycloak-admin-client:24.0.4`)
- **Connection:** Configured via `app.config.keycloak.*` properties (server-url, realm, clientId, client-secret)
- **Operations:** Create user, update user (enable/verify email), read user by ID, search users by email
- **Realm:** Imported at startup from `docker-compose/keycloak/` volume mount
- **Auth Flow:** API Gateway validates JWT tokens against Keycloak's JWK set URI (`spring.security.oauth2.resourceserver.jwt.jwk-set-uri`)
- **Singleton Pattern:** `KeycloakProperties` uses a non-thread-safe static singleton for the Keycloak client instance

### 5.2 Zipkin (Distributed Tracing)

- **Used by:** All services (via `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave`)
- **Port:** 9411
- **Coverage:** Auto-instrumented HTTP requests and Feign calls
- **Dashboard:** Available at `http://localhost:9411`

### 5.3 MySQL

- **Connection:** Each business service connects to its own schema on the same MySQL instance
- **Schemas:** `banking_core_service`, `banking_core_user_service`, `banking_core_fund_transfer_service`, `banking_core_utility_payment_service`
- **User:** `javatodev_development` with broad privileges (`CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES`)
- **Schema Management:**
  - Core Banking: Flyway migrations (`db/migration/`)
  - Other services: JPA auto DDL generation (no migrations)

### 5.4 Spring Cloud Config Server

- **Git Repository:** `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch:** `main`
- **Search Path:** `configuration/`
- **Bootstrap:** Each service has `bootstrap.yml` (localhost) and `bootstrap-docker.yml` (container name) pointing to the config server

### 5.5 Netflix Eureka

- **Server:** `internet-banking-service-registry` at port 8081
- **Clients:** All other services register with Eureka
- **Usage:** Feign clients resolve service names (e.g., `core-banking-service`) through Eureka

### 5.6 RabbitMQ (Planned)

- Referenced in README documentation for a planned Notification Service
- **No RabbitMQ dependencies or code exist in any service**

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Tool:** Gradle (per-service `build.gradle`, no root multi-project build)
- **Java:** 21
- **Spring Boot:** 3.2.4 (with `spring-boot-gradle-plugin`)
- **Spring Cloud:** 2023.0.0
- **Plugins:** `gradle-git-properties` for build info in actuator

### 6.2 Docker Build

Each service has a `Dockerfile` (not inspected in detail) that produces a JAR-based container image published under the `javatodev/` namespace.

### 6.3 Docker Compose Deployment

**Full stack** (`docker-compose.yml`):
1. Infrastructure: Zipkin, Keycloak + PostgreSQL, MySQL
2. Platform: Config Server, Service Registry
3. Services: API Gateway, User Service, Fund Transfer Service, Utility Payment Service, Core Banking Service

**Support only** (`docker-compose-support-apps.yml`):
- Zipkin, Keycloak + PostgreSQL, MySQL, Config Server, Service Registry (for local development of business services)

**Startup Order:** Managed by `wait-for-it.sh` scripts embedded in each service's `entrypoint`:
- All services wait for: Service Registry (8081) + Config Server (8090)
- Business services additionally wait for: MySQL (3306)

### 6.4 CI/CD

- No CI/CD pipeline configuration found in the repository (`.github/workflows/` is empty after workflow removal)
- No Kubernetes manifests despite Kubernetes being listed in the tech stack
- No Helm charts or deployment descriptors

### 6.5 Test Infrastructure

- **Framework:** JUnit 5 (via `spring-boot-starter-test`)
- **Test DB:** H2 in-memory (dependency present in core, user, fund-transfer, utility-payment services)
- **Tests exist only in `core-banking-service`:** `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`
- **Other services:** Only have empty Spring Boot context load tests (e.g., `InternetBankingUserServiceApplicationTests`)
