# Application Knowledge Base

## Table of Contents

- [1. Architecture Overview](#1-architecture-overview)
- [2. Data Model Documentation](#2-data-model-documentation)
- [3. API Surface Map](#3-api-surface-map)
- [4. Key Business Logic Inventory](#4-key-business-logic-inventory)
- [5. Integration Points](#5-integration-points)
- [6. Build and Deployment Pipeline Summary](#6-build-and-deployment-pipeline-summary)

---

## 1. Architecture Overview

### 1.1 High-Level Architecture

The application follows a **Spring Cloud microservices** pattern with 7 deployable units (6 business/infrastructure services + 1 config server):

```
                         ┌─────────────────────┐
                         │    API Gateway       │
                         │  (Spring Cloud GW)   │
                         │  :8082               │
                         │  OAuth2 + Keycloak   │
                         └──────┬──┬──┬─────────┘
                                │  │  │
              ┌─────────────────┘  │  └──────────────────┐
              ▼                    ▼                      ▼
   ┌──────────────────┐ ┌──────────────────┐ ┌────────────────────────┐
   │  User Service    │ │ Fund Transfer    │ │ Utility Payment        │
   │  :8083           │ │ Service :8084    │ │ Service :8085          │
   │                  │ │                  │ │                        │
   │  MySQL + Keycloak│ │  MySQL           │ │  MySQL                 │
   └────────┬─────────┘ └───────┬──────────┘ └──────────┬─────────────┘
            │                   │                       │
            │         ┌────────▼────────┐               │
            └────────►│ Core Banking    │◄──────────────┘
                      │ Service :8092   │
                      │ MySQL (Flyway)  │
                      └─────────────────┘

   ┌───────────────────┐    ┌──────────────────────┐
   │ Service Registry   │    │ Config Server         │
   │ (Eureka) :8081     │    │ (Spring Cloud) :8090  │
   └───────────────────┘    └──────────────────────┘
```

### 1.2 Services

| Service | Port | Purpose | Database |
|---------|------|---------|----------|
| `internet-banking-api-gateway` | 8082 | Single entry point; OAuth2/Keycloak auth, route proxying | None |
| `internet-banking-user-service` | 8083 | User registration, profile management, Keycloak integration | MySQL (`banking_core_user_service`) |
| `internet-banking-fund-transfer-service` | 8084 | Account-to-account fund transfers | MySQL (`banking_core_fund_transfer_service`) |
| `internet-banking-utility-payment-service` | 8085 | Third-party utility bill payments | MySQL (`banking_core_utility_payment_service`) |
| `core-banking-service` | 8092 | System of record: accounts, users, transactions, ledger | MySQL (`banking_core_service`) |
| `internet-banking-service-registry` | 8081 | Netflix Eureka discovery server | None |
| `internet-banking-config-server` | 8090 | Centralized Spring Cloud Config (Git-backed) | None |

### 1.3 Communication Patterns

| Pattern | Technology | Usage |
|---------|-----------|-------|
| **Synchronous REST** | OpenFeign (service-to-service) | User Service -> Core Banking, Fund Transfer -> Core Banking, Utility Payment -> Core Banking |
| **Service Discovery** | Netflix Eureka | All business services register; Feign clients resolve by service name |
| **Centralized Config** | Spring Cloud Config Server | Git-backed config repo; services fetch via bootstrap context |
| **API Gateway** | Spring Cloud Gateway | Route-based proxying with OAuth2 resource server |
| **Auth** | Keycloak (OAuth2/OIDC) | API Gateway enforces tokens; User Service manages Keycloak users via Admin Client |
| **Distributed Tracing** | Micrometer Tracing + Brave + Zipkin | All services export traces to Zipkin |

### 1.4 Infrastructure Components

| Component | Technology | Purpose |
|-----------|-----------|---------|
| MySQL 8.4 | Relational DB | Persistence for all 4 business services (separate databases) |
| PostgreSQL 15 | Relational DB | Keycloak identity store |
| Keycloak 23.0.7 | IAM | OAuth2/OIDC provider, user/role management |
| Zipkin 3 | Distributed Tracing | Trace collection and visualization |
| Docker / Docker Compose | Container Orchestration | Local deployment with fixed-IP bridge network |

### 1.5 Network Topology (Docker)

All containers run on a custom bridge network `javatodev_ib_network` (`172.25.0.0/16`):

| Container | IP |
|-----------|----|
| Zipkin | 172.25.0.12 |
| Keycloak | 172.25.0.11 |
| Keycloak PostgreSQL | 172.25.0.10 |
| MySQL | 172.25.0.9 |
| Config Server | 172.25.0.8 |
| Service Registry | 172.25.0.7 |
| API Gateway | 172.25.0.6 |
| User Service | 172.25.0.5 |
| Fund Transfer Service | 172.25.0.4 |
| Utility Payment Service | 172.25.0.3 |
| Core Banking Service | 172.25.0.2 |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL: `banking_core_service`)

Managed by **Flyway** migrations.

#### `banking_core_user`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGINT` (auto-increment) | PK | Internal user ID |
| `first_name` | `VARCHAR(255)` | | User's first name |
| `last_name` | `VARCHAR(255)` | | User's last name |
| `email` | `VARCHAR(255)` | | Email address |
| `identification_number` | `VARCHAR(255)` | | National ID / NIC number |

#### `banking_core_account`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGINT` (auto-increment) | PK | Internal account ID |
| `number` | `VARCHAR(255)` | | Account number (12-digit string) |
| `type` | `VARCHAR(255)` | | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | `VARCHAR(255)` | | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `actual_balance` | `DECIMAL(19,2)` | | Ledger balance |
| `available_balance` | `DECIMAL(19,2)` | | Available for withdrawal |
| `user_id` | `BIGINT` | FK -> `banking_core_user.id` | Owner |

#### `banking_core_transaction`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGINT` (auto-increment) | PK | Internal transaction ID |
| `amount` | `DECIMAL(19,2)` | | Transaction amount (negative = debit) |
| `transaction_type` | `VARCHAR(30)` | NOT NULL | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | `VARCHAR(50)` | NOT NULL | Destination account or reference |
| `transaction_id` | `VARCHAR(50)` | NOT NULL | UUID correlating debit/credit legs |
| `account_id` | `BIGINT` | FK -> `banking_core_account.id` | Associated account |

#### `banking_core_utility_account`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | `BIGINT` (auto-increment) | PK | Internal ID |
| `number` | `VARCHAR(255)` | | Utility provider account number |
| `provider_name` | `VARCHAR(255)` | | Provider name (e.g., VODAFONE, AIRTEL) |

**Relationships:**
- `banking_core_user` 1:N `banking_core_account` (via `user_id`)
- `banking_core_account` 1:1 `banking_core_transaction` (via `account_id`)

### 2.2 User Service (MySQL: `banking_core_user_service`)

Uses JPA auto-DDL (Hibernate). Entities extend `AuditAware` (created/modified timestamps + optimistic locking).

#### `user`

| Column | Type | Description |
|--------|------|-------------|
| `id` | `BIGINT` (auto-increment) | PK |
| `auth_id` | `VARCHAR` | Keycloak user UUID |
| `identification` | `VARCHAR` | NIC / national ID number |
| `status` | `VARCHAR` | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | `INSTANT` | Audit: creation timestamp |
| `created_by` | `VARCHAR` | Audit: creator |
| `modified_date` | `INSTANT` | Audit: last modification |
| `modified_by` | `VARCHAR` | Audit: last modifier |
| `version` | `BIGINT` | Optimistic locking version |

### 2.3 Fund Transfer Service (MySQL: `banking_core_fund_transfer_service`)

Uses JPA auto-DDL. Entities extend `AuditAware`.

#### `fund_transfer`

| Column | Type | Description |
|--------|------|-------------|
| `id` | `BIGINT` (auto-increment) | PK |
| `transaction_reference` | `VARCHAR` | UUID from Core Banking response |
| `from_account` | `VARCHAR` | Source account number |
| `to_account` | `VARCHAR` | Destination account number |
| `amount` | `DECIMAL` | Transfer amount |
| `status` | `VARCHAR` | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `modified_date` | `INSTANT` | Audit fields |
| `version` | `BIGINT` | Optimistic locking |

### 2.4 Utility Payment Service (MySQL: `banking_core_utility_payment_service`)

Uses JPA auto-DDL. Entities extend `AuditAware`.

#### `utility_payment`

| Column | Type | Description |
|--------|------|-------------|
| `id` | `BIGINT` (auto-increment) | PK |
| `provider_id` | `BIGINT` | Utility provider ID |
| `amount` | `DECIMAL` | Payment amount |
| `reference_number` | `VARCHAR` | External reference |
| `account` | `VARCHAR` | Payer account number |
| `transaction_id` | `VARCHAR` | UUID from Core Banking response |
| `status` | `VARCHAR` | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` / `modified_date` | `INSTANT` | Audit fields |
| `version` | `BIGINT` | Optimistic locking |

---

## 3. API Surface Map

### 3.1 API Gateway Routes

The API Gateway proxies requests via Spring Cloud Gateway using Eureka-based service discovery. Routes are defined in the external config repo. Based on the `api_test.http` file and service names:

| Gateway Path Prefix | Target Service |
|---------------------|----------------|
| `/user/**` | `internet-banking-user-service` |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` |
| `/utility-payment/**` | `internet-banking-utility-payment-service` |

All routes are protected via OAuth2 Resource Server (Keycloak JWT).

### 3.2 Core Banking Service (`:8092`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | - | `BankAccount` (id, number, type, status, availableBalance, actualBalance, user) |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | - | `UtilityAccount` (id, number, providerName) |
| `GET` | `/api/v1/user/{identification}` | Get user by identification number | - | `User` (id, firstName, lastName, email, identificationNumber, bankAccounts[]) |
| `GET` | `/api/v1/user` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer | `FundTransferRequest` (fromAccount, toAccount, amount) | `FundTransferResponse` (message, transactionId) |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `UtilityPaymentRequest` (providerId, amount, referenceNumber, account) | `UtilityPaymentResponse` (message, transactionId) |

### 3.3 User Service (`:8083`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/bank-users/register` | Register new user | `User` (email, identification, password) | `User` (id, email, identification, authId, status) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user (e.g., approve) | `UserUpdateRequest` (status) | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | - | `User` |

### 3.4 Fund Transfer Service (`:8084`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `FundTransferRequest` (fromAccount, toAccount, amount, authID) | `FundTransferResponse` (message, transactionId) |
| `GET` | `/api/v1/transfer` | List all transfers (paginated) | Query: `page`, `size`, `sort` | `List<FundTransfer>` |

### 3.5 Utility Payment Service (`:8085`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `UtilityPaymentRequest` (providerId, amount, referenceNumber, account) | `UtilityPaymentResponse` (message, transactionId) |
| `GET` | `/api/v1/utility-payment` | List payments (paginated) | Query: `page`, `size`, `sort` | `List<UtilityPayment>` |

### 3.6 Service Registry (`:8081`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/` | Eureka dashboard (UI) |
| `GET` | `/eureka/apps` | Registered services (REST) |

### 3.7 Config Server (`:8090`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/{application}/{profile}` | Fetch config for application + profile |
| `GET` | `/{application}/{profile}/{label}` | Fetch config for application + profile + git label |

### 3.8 Actuator Endpoints (All Services)

All services with `spring-boot-starter-actuator` expose:

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Health check |
| `/actuator/info` | Build info (git properties plugin) |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

**Service:** User Service -> Core Banking + Keycloak

1. Check if email already exists in Keycloak (via admin API search)
2. If email exists, throw `UserAlreadyRegisteredException`
3. Fetch user from Core Banking by identification number (via Feign)
4. Validate email matches Core Banking record; throw `InvalidEmailException` if not
5. Create Keycloak user (disabled, email unverified) with provided password
6. If Keycloak returns 201, fetch Keycloak user to get `authId`
7. Save local user entity with `authId`, `identification`, and `PENDING` status
8. If Core Banking user not found, throw `InvalidBankingUserException`

### 4.2 User Approval Flow

**Service:** User Service + Keycloak

1. Admin sends `PATCH /api/v1/bank-users/update/{id}` with `{status: "APPROVED"}`
2. Service reads Keycloak user by `authId`
3. Enables Keycloak user and marks email as verified
4. Updates local entity status to `APPROVED`

### 4.3 Fund Transfer Flow

**Service:** Fund Transfer Service -> Core Banking

1. Client sends transfer request (fromAccount, toAccount, amount)
2. Fund Transfer Service saves entity with `PENDING` status
3. Delegates to Core Banking via Feign `POST /api/v1/transaction/fund-transfer`
4. Core Banking:
   - Reads both source and destination bank accounts
   - Validates source account has sufficient balance (`actualBalance >= amount`)
   - Debits source: `actualBalance -= amount`, `availableBalance = actualBalance - amount`
   - Creates debit transaction record (negative amount)
   - Credits destination: `actualBalance += amount`, `availableBalance = actualBalance + amount`
   - Creates credit transaction record (positive amount)
   - Returns `transactionId` (UUID)
5. Fund Transfer Service updates entity with `transactionReference` and `SUCCESS` status

**Business Rules:**
- No negative balance allowed (throws `InsufficientFundsException`)
- Entire operation is `@Transactional` in Core Banking
- Two transaction records per transfer (debit + credit with same `transactionId`)
- No self-transfer validation
- No transfer limits enforced
- No currency validation

### 4.4 Utility Payment Flow

**Service:** Utility Payment Service -> Core Banking

1. Client sends payment request (providerId, amount, referenceNumber, account)
2. Utility Payment Service saves entity with `PROCESSING` status
3. Delegates to Core Banking via Feign `POST /api/v1/transaction/util-payment`
4. Core Banking:
   - Reads payer bank account
   - Validates sufficient balance
   - Reads utility account by provider ID
   - Debits payer: `actualBalance -= amount`, `availableBalance = actualBalance - amount`
   - Creates transaction record (negative amount, type `UTILITY_PAYMENT`)
   - Returns `transactionId` (UUID)
5. Utility Payment Service updates entity with `transactionId` and `SUCCESS` status

**Business Rules:**
- Same balance validation as fund transfer
- No actual third-party API integration (comment in code: "we can call third party API")
- Reference number stored but not validated

### 4.5 Error Handling Pattern

Each service has a copy of the exception framework:

- `SimpleBankingGlobalException` - Base runtime exception with `code` + `message`
- `GlobalExceptionHandler` - `@ControllerAdvice` that catches exceptions and returns `ErrorResponse`
- `ErrorResponse` - DTO with `code` and `message` fields
- Service-specific exceptions (e.g., `InsufficientFundsException`, `EntityNotFoundException`, `UserAlreadyRegisteredException`)
- All errors return HTTP 400 (Bad Request) regardless of actual error type

---

## 5. Integration Points

### 5.1 Keycloak (IAM)

| Aspect | Detail |
|--------|--------|
| **Version** | 23.0.7 |
| **Admin Console** | `http://localhost:8080` (admin/password) |
| **Realm** | Configured via `realm-export.json` import |
| **Integration Point 1** | API Gateway: OAuth2 Resource Server (JWT validation) |
| **Integration Point 2** | User Service: Keycloak Admin Client (`keycloak-admin-client:24.0.4`) for user CRUD |
| **Auth Flow** | Client Credentials grant for admin operations; end-user tokens via OIDC |
| **Config Properties** | `app.config.keycloak.server-url`, `realm`, `clientId`, `client-secret` |

### 5.2 RabbitMQ

| Aspect | Detail |
|--------|--------|
| **Status** | Referenced in README but **not implemented** in code |
| **Intended Use** | Fund Transfer and Utility Payment services push notification messages |
| **Notification Service** | Mentioned as "PENDING Development" in README |

### 5.3 Zipkin (Distributed Tracing)

| Aspect | Detail |
|--------|--------|
| **Version** | Zipkin 3 (Docker image: `openzipkin/zipkin:3`) |
| **Port** | 9411 |
| **Libraries** | `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` |
| **Coverage** | All 4 business services + API Gateway include tracing dependencies |
| **Feign Integration** | `feign-micrometer` for propagating trace context across Feign calls |

### 5.4 Database Connections

| Service | Database | Technology | Schema Management |
|---------|----------|-----------|-------------------|
| Core Banking | MySQL `banking_core_service` | Spring Data JPA + MySQL Connector 8.4 | **Flyway** (versioned migrations) |
| User Service | MySQL `banking_core_user_service` | Spring Data JPA + MySQL Connector 8.4 | JPA auto-DDL (Hibernate) |
| Fund Transfer | MySQL `banking_core_fund_transfer_service` | Spring Data JPA + MySQL Connector 8.4 | JPA auto-DDL (Hibernate) |
| Utility Payment | MySQL `banking_core_utility_payment_service` | Spring Data JPA + MySQL Connector 8.4 | JPA auto-DDL (Hibernate) |
| Keycloak | PostgreSQL `keycloak` | Internal Keycloak persistence | Keycloak managed |

### 5.5 Inter-Service Communication (OpenFeign)

| Caller | Target | Feign Client | Endpoints Called |
|--------|--------|-------------|-----------------|
| User Service | Core Banking | `BankingCoreRestClient` | `GET /api/v1/user/{identification}` |
| Fund Transfer Service | Core Banking | `BankingCoreFeignClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/fund-transfer` |
| Utility Payment Service | Core Banking | `BankingCoreRestClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/util-payment` |

### 5.6 Spring Cloud Config

| Aspect | Detail |
|--------|--------|
| **Config Repo** | `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` |
| **Search Path** | `/configuration` |
| **Default Branch** | `main` |
| **Bootstrap** | Services use `bootstrap.yml` / `bootstrap-docker.yml` to locate config server |

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build Tool:** Gradle (per-service, no multi-project build)
- **Java Version:** 21 (Eclipse Temurin JRE Alpine for Docker images)
- **Spring Boot:** 3.2.4
- **Spring Cloud:** 2023.0.0
- **Plugins:** `spring-boot`, `spring-dependency-management`, `gradle-git-properties`

### 6.2 Docker Images

Each service has its own `Dockerfile`:

```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
COPY wait-for-it.sh wait-for-it.sh
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

- Uses `wait-for-it.sh` for startup ordering
- Docker profile activates `bootstrap-docker.yml` (points to container hostnames)

### 6.3 Docker Compose

Two compose files:

1. **`docker-compose.yml`** - Full stack (all services + infrastructure)
2. **`docker-compose-support-apps.yml`** - Infrastructure only (MySQL, PostgreSQL, Keycloak, Zipkin, Config Server, Service Registry)

**Startup Order (via `wait-for-it.sh`):**

1. MySQL, PostgreSQL, Zipkin (infrastructure)
2. Config Server, Service Registry
3. API Gateway (waits for Registry + Config)
4. Business services (wait for Registry + Config + MySQL)

### 6.4 Build Commands

```bash
# Build a single service
cd <service-directory>
./gradlew clean build

# Build Docker image (after Gradle build)
docker build -t javatodev/<service-name> .

# Start full stack
cd docker-compose
docker-compose up -d
```

### 6.5 Test Data

Flyway migration `V1.0.20210427174721__temp_data.sql` seeds:
- 4 users with NIC numbers
- 14 savings accounts with various balances
- 6 utility provider accounts (VODAFONE, VERIZON, SINGTEL, HUTCH, AIRTEL, GIO)

Test credentials: `ib_admin@javatodev.com` / `5V7huE3G86uB`

### 6.6 CI/CD

- No CI/CD pipeline files present in the repository (`.github/` contains only `FUNDING.yml`)
- No GitHub Actions workflows
- No Jenkinsfile or equivalent
- Deployment is manual via Docker Compose
