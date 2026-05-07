# Application Knowledge Base

## 1. Architecture Overview

### System Summary

This is a Java 21 / Spring Boot 3.2.4 internet banking application composed of **6 microservices** that communicate via synchronous REST (OpenFeign) and are orchestrated through Spring Cloud infrastructure components. The system implements a layered banking architecture where an API Gateway fronts all client traffic, business-domain services handle user management, fund transfers, and utility payments, and a core banking service acts as the system of record for accounts and transactions.

### Service Inventory

| Service | Port | Role |
|---|---|---|
| `internet-banking-api-gateway` | 8082 | Edge service: routing, OAuth2/JWT enforcement, header injection |
| `internet-banking-config-server` | 8090 | Centralized configuration (Git-backed Spring Cloud Config) |
| `internet-banking-service-registry` | 8081 | Service discovery (Netflix Eureka Server) |
| `internet-banking-user-service` | 8083 | User registration, approval workflow, Keycloak integration |
| `internet-banking-fund-transfer-service` | 8084 | Fund transfer orchestration, delegates to core banking |
| `internet-banking-utility-payment-service` | 8085 | Utility bill payment orchestration, delegates to core banking |
| `core-banking-service` | 8092 | System of record: accounts, users, transactions, balances |

### Communication Patterns

```
                         ┌──────────────────┐
                         │   API Gateway    │ (8082)
                         │ OAuth2 + JWT     │
                         └──────┬───────────┘
                                │
              ┌─────────────────┼─────────────────┐
              │                 │                  │
     ┌────────▼──────┐  ┌──────▼──────┐  ┌───────▼──────────┐
     │ User Service  │  │ Fund Xfer   │  │ Utility Payment  │
     │   (8083)      │  │  Service    │  │  Service (8085)  │
     │               │  │  (8084)     │  │                  │
     └───────┬───────┘  └──────┬──────┘  └───────┬──────────┘
             │                 │                  │
             │    OpenFeign    │   OpenFeign      │  OpenFeign
             │                 │                  │
             └─────────────────┼──────────────────┘
                               │
                      ┌────────▼────────┐
                      │  Core Banking   │
                      │  Service (8092) │
                      └─────────────────┘
```

- **Synchronous REST via OpenFeign**: All inter-service communication uses Spring Cloud OpenFeign clients registered through Eureka service discovery. There is no asynchronous messaging currently implemented (RabbitMQ is listed in the tech stack but not present in code).
- **Service Discovery**: Netflix Eureka — all services register with and discover each other through the Eureka server.
- **Centralized Configuration**: Spring Cloud Config Server backed by a remote Git repository (`https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`).
- **API Gateway**: Spring Cloud Gateway with OAuth2 resource server (JWT validation via Keycloak). Injects `X-Auth-Id` header from JWT principal into downstream requests.

### Infrastructure Components

| Component | Technology | Purpose |
|---|---|---|
| Database | MySQL 8.x | Persistent storage for all business services |
| Identity Provider | Keycloak 23.0.7 (PostgreSQL 15 backend) | OAuth2/OIDC authentication and user management |
| Distributed Tracing | Zipkin 3 + Micrometer Brave | Request tracing across services |
| Service Registry | Netflix Eureka | Service discovery |
| Config Server | Spring Cloud Config (Git-backed) | Externalized configuration |
| Containerization | Docker + Docker Compose | Local development and deployment |

---

## 2. Data Model Documentation

### Core Banking Service (MySQL: `banking_core_service`)

#### `banking_core_user`
| Column | Type | Constraints |
|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `first_name` | VARCHAR(255) | |
| `last_name` | VARCHAR(255) | |
| `email` | VARCHAR(255) | |
| `identification_number` | VARCHAR(255) | National ID / NIC |

#### `banking_core_account`
| Column | Type | Constraints |
|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `number` | VARCHAR(255) | Account number (e.g., `100015003000`) |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `ACTIVE` |
| `actual_balance` | DECIMAL(19,2) | |
| `available_balance` | DECIMAL(19,2) | |
| `user_id` | BIGINT | FK → `banking_core_user.id` |

**Relationships**: Many accounts → one user (`@ManyToOne`).

#### `banking_core_transaction`
| Column | Type | Constraints |
|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `amount` | DECIMAL(19,2) | Positive for credits, negative for debits |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Target account number or utility reference |
| `transaction_id` | VARCHAR(50) | UUID for correlating paired entries |
| `account_id` | BIGINT | FK → `banking_core_account.id` |

**Relationships**: One-to-one with account (`@OneToOne`, though semantically many transactions belong to one account — this is a design issue).

#### `banking_core_utility_account`
| Column | Type | Constraints |
|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `number` | VARCHAR(255) | Utility provider account number |
| `provider_name` | VARCHAR(255) | e.g., `VODAFONE`, `HUTCH`, `AIRTEL` |

### Fund Transfer Service (MySQL: `banking_core_fund_transfer_service`)

#### `fund_transfer`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `transaction_reference` | VARCHAR | UUID from core banking response |
| `status` | VARCHAR | Enum: `PENDING`, `SUCCESS` |
| `created_date` | TIMESTAMP | Audit (from `AuditAware`) |
| `created_by` | VARCHAR | Audit |
| `modified_date` | TIMESTAMP | Audit |
| `modified_by` | VARCHAR | Audit |
| `version` | BIGINT | Optimistic locking |

### User Service (MySQL: `banking_core_user_service`)

#### `user`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `auth_id` | VARCHAR | Keycloak user UUID |
| `identification` | VARCHAR | NIC / national ID |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED` |
| `created_date` | TIMESTAMP | Audit (from `AuditAware`) |
| `created_by` | VARCHAR | Audit |
| `modified_date` | TIMESTAMP | Audit |
| `modified_by` | VARCHAR | Audit |
| `version` | BIGINT | Optimistic locking |

### Utility Payment Service (MySQL: `banking_core_utility_payment_service`)

#### `utility_payment`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Customer reference (e.g., phone number) |
| `account` | VARCHAR | Source bank account number |
| `transaction_id` | VARCHAR | UUID from core banking response |
| `status` | VARCHAR | Enum: `PROCESSING`, `SUCCESS` |
| `created_date` | TIMESTAMP | Audit |
| `created_by` | VARCHAR | Audit |
| `modified_date` | TIMESTAMP | Audit |
| `modified_by` | VARCHAR | Audit |
| `version` | BIGINT | Optimistic locking |

### Database Schema Management

- **Core Banking Service**: Uses **Flyway** migrations (`V1.0.20210427174638`, `V1.0.20210427174721`, `V1.0.20210429210839`) for schema creation and seed data.
- **Other Services**: Rely on **JPA/Hibernate auto-DDL** (no explicit migrations found).

---

## 3. API Surface Map

### API Gateway Routes

All endpoints are accessed through the gateway at `http://localhost:8082`. The gateway routes by path prefix:

| Prefix | Target Service |
|---|---|
| `/user/**` | `internet-banking-user-service` |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` |
| `/payment/**` | `internet-banking-utility-payment-service` |
| `/core/**` | `core-banking-service` |

### Core Banking Service (`/api/v1/...`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | — | `BankAccount` (number, type, status, availableBalance, actualBalance) |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | — | `UtilityAccount` (id, number, providerName) |
| `GET` | `/api/v1/user/{identification}` | Get user by NIC | — | `User` (id, firstName, lastName, email, identificationNumber) |
| `GET` | `/api/v1/user` | List users (paginated) | — | `List<User>` |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer | `{fromAccount, toAccount, amount}` | `{message, transactionId}` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` |

### User Service (`/api/v1/bank-users/...`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Register new user | `{password, email, identification}` | `User` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user (e.g., approve) | `{status}` | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | — | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | — | `User` |

### Fund Transfer Service (`/api/v1/transfer/...`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `{fromAccount, toAccount, amount}` | `{message, transactionId}` |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | — | `List<FundTransfer>` |

### Utility Payment Service (`/api/v1/utility-payment/...`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | — | `List<UtilityPayment>` |

### Actuator Endpoints (All Services)

All services expose Spring Boot Actuator at `/actuator/health` (permitted without authentication at the gateway level).

---

## 4. Key Business Logic Inventory

### Fund Transfer Flow

1. **Client** → `POST /fund-transfer/api/v1/transfer` (via API Gateway)
2. **Fund Transfer Service** saves a `PENDING` record in its local DB
3. **Fund Transfer Service** calls **Core Banking Service** via Feign: `POST /api/v1/transaction/fund-transfer`
4. **Core Banking Service**:
   - Reads both source and destination `BankAccount` records
   - Validates source account has sufficient balance (`actualBalance >= amount`)
   - Debits source: `actualBalance -= amount`, `availableBalance = actualBalance - amount`
   - Credits destination: `actualBalance += amount`, `availableBalance = actualBalance + amount`
   - Creates two `TransactionEntity` records (debit + credit) with same `transactionId`
   - Returns `{message, transactionId}`
5. **Fund Transfer Service** updates local record to `SUCCESS` with transaction reference
6. Response returned to client

**Business Rules**:
- Both accounts must exist (throws `EntityNotFoundException` otherwise)
- Source balance must be >= transfer amount (throws `InsufficientFundsException`)
- No maximum transfer limit enforced
- No duplicate transfer detection
- No currency validation

### Utility Payment Flow

1. **Client** → `POST /payment/api/v1/utility-payment` (via API Gateway)
2. **Utility Payment Service** saves a `PROCESSING` record locally
3. **Utility Payment Service** calls **Core Banking Service** via Feign: `POST /api/v1/transaction/util-payment`
4. **Core Banking Service**:
   - Reads source `BankAccount` and validates balance
   - Reads `UtilityAccount` by provider ID
   - Debits source account (note: double-deduction bug — subtracts from `actualBalance`, then sets `availableBalance` to `actualBalance - amount` again)
   - Creates one `TransactionEntity` with negative amount
   - Returns `{message, transactionId}`
5. **Utility Payment Service** updates local record to `SUCCESS`

**Business Rules**: Same as fund transfer, plus utility provider must exist.

### User Registration Flow

1. **Client** → `POST /user/api/v1/bank-users/register` (public endpoint, no auth required)
2. **User Service**:
   - Checks Keycloak for existing user with same email (rejects duplicates)
   - Calls **Core Banking Service** via Feign to validate NIC exists: `GET /api/v1/user/{identification}`
   - Validates email matches the core banking record
   - Creates Keycloak user (disabled, email not verified)
   - Saves local `UserEntity` with `PENDING` status and Keycloak auth ID
3. **Admin approves** → `PATCH /user/api/v1/bank-users/update/{id}` with `{status: "APPROVED"}`
4. **User Service** enables the Keycloak user and marks email as verified

---

## 5. Integration Points

### Keycloak (Identity Provider)

- **Version**: 23.0.7
- **Realm**: `javatodev-internet-banking`
- **Client ID**: `internet-banking-core-client`
- **Integration Points**:
  - **API Gateway**: Validates JWT tokens via `jwk-set-uri` (OAuth2 resource server)
  - **User Service**: Uses `keycloak-admin-client` (v24.0.4) for user CRUD operations
- **Configuration**: Server URL, realm, client ID, and client secret injected via Spring Cloud Config properties (`app.config.keycloak.*`)
- **Docker Setup**: Auto-imports realm configuration from `docker-compose/keycloak/realm-export.json`

### Database (MySQL)

- **Container**: `mysql_javatodev_app` on port 3306
- **Databases**: 4 separate databases (`banking_core_service`, `banking_core_fund_transfer_service`, `banking_core_user_service`, `banking_core_utility_payment_service`)
- **Credentials**: User `javatodev_development` / `oPItyPticIAt` (created via `privileges.sql`)
- **Root Password**: `woVERANKliGharym`
- **Driver**: `mysql-connector-j:8.4.0`

### Zipkin (Distributed Tracing)

- **Version**: 3
- **Port**: 9411
- **Integration**: Micrometer Tracing Bridge Brave (`micrometer-tracing-bridge-brave`) + Zipkin Reporter (`zipkin-reporter-brave`)
- **Coverage**: All 6 services include tracing dependencies; Feign calls include `feign-micrometer` for automatic span propagation

### RabbitMQ

- **Status**: Listed in README tech stack but **NOT implemented** in the codebase
- **Intended Use**: Notification service message queue (notification service marked as "PENDING Development")

### OpenFeign Inter-Service Calls

| Caller | Target | Feign Client | Endpoints Called |
|---|---|---|---|
| User Service | Core Banking Service | `BankingCoreRestClient` | `GET /api/v1/user/{identification}` |
| Fund Transfer Service | Core Banking Service | `BankingCoreFeignClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/fund-transfer` |
| Utility Payment Service | Core Banking Service | `BankingCoreRestClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/util-payment` |

---

## 6. Build and Deployment Pipeline Summary

### Build System

- **Build Tool**: Gradle (each service has its own `build.gradle` — no multi-project build)
- **Java**: 21 (`sourceCompatibility = '21'`)
- **Spring Boot**: 3.2.4
- **Spring Cloud**: 2023.0.0
- **Key Plugins**:
  - `org.springframework.boot` (3.2.4)
  - `io.spring.dependency-management` (1.1.4)
  - `com.gorylenko.gradle-git-properties` (2.4.2) — generates `git.properties` for build info

### Docker

- **Base Image**: `eclipse-temurin:21.0.2_13-jre-alpine`
- **Pattern**: Each service has a `Dockerfile` that copies the fat JAR and a `wait-for-it.sh` script
- **Startup**: Uses `wait-for-it.sh` to wait for Config Server, Service Registry, and MySQL before starting
- **Profile**: Activates `docker` Spring profile (`-Dspring.profiles.active=docker`)

### Docker Compose

Two compose files in `docker-compose/`:

1. **`docker-compose.yml`**: Full stack — all services + infrastructure
2. **`docker-compose-support-apps.yml`**: Infrastructure only (Zipkin, Keycloak, MySQL, Config Server, Service Registry)

**Network**: Custom bridge network `javatodev_ib_network` (subnet `172.25.0.0/16`) with static IPs assigned to each container.

### CI/CD

- **GitHub Actions**: No workflow files present (`.github/` only contains `FUNDING.yml`)
- **Kubernetes**: Mentioned in tech stack but no manifests present in the repository

### Configuration Management

- **Spring Cloud Config**: Centralized config served from Git repo `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Profile Hierarchy**: `bootstrap.yml` (local) → `bootstrap-dev.yml` (dev IP) → `bootstrap-docker.yml` (Docker hostnames)
- **Each service** has minimal local `application.yml` (just `spring.application.name`); all other config is externalized

### Test Infrastructure

- **Test Framework**: JUnit 5 (via `spring-boot-starter-test`)
- **Test Database**: H2 in-memory (only `core-banking-service` has test config)
- **Flyway**: Disabled in test profile
- **Test Coverage**: Only `core-banking-service` has unit tests (3 test classes: `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`). All other services have only the default Spring Boot test class (context load test).
