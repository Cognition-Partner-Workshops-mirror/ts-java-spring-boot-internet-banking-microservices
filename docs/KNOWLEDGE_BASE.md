# Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Summary

This is a **Java 21 / Spring Boot 3.2.4** internet banking application built on a microservices architecture using **Spring Cloud 2023.0.0**. The system simulates core banking operations including user management, fund transfers, and utility payments.

### 1.2 Microservices Inventory

| Service | Port | Description | Has DB | Spring Profile |
|---|---|---|---|---|
| **core-banking-service** | 8092 | Core banking engine — accounts, users, transactions | Yes (MySQL) | `docker` |
| **internet-banking-user-service** | 8083 | User registration & management, Keycloak integration | Yes (MySQL) | `docker` |
| **internet-banking-fund-transfer-service** | 8084 | Fund transfer orchestration between accounts | Yes (MySQL) | `docker` |
| **internet-banking-utility-payment-service** | 8085 | Utility bill payment processing | Yes (MySQL) | `docker` |
| **internet-banking-api-gateway** | 8082 | API Gateway — routing, security, auth enforcement | No | `docker` |
| **internet-banking-config-server** | 8090 | Centralized configuration (Spring Cloud Config) | No | N/A |
| **internet-banking-service-registry** | 8081 | Service discovery (Netflix Eureka) | No | N/A |

### 1.3 Communication Patterns

```
                        ┌──────────────────┐
                        │   Keycloak (IdP)  │ :8080
                        └────────┬─────────┘
                                 │ OAuth2/JWT
                                 ▼
 Client ──► API Gateway (:8082) ──┬──► User Service (:8083) ──► Core Banking (:8092)
            (Spring Cloud Gateway) │                              (OpenFeign)
                                   ├──► Fund Transfer (:8084) ──► Core Banking (:8092)
                                   │                              (OpenFeign)
                                   └──► Utility Payment (:8085)──► Core Banking (:8092)
                                                                   (OpenFeign)
            All services register with Eureka (:8081)
            All services fetch config from Config Server (:8090)
```

- **Synchronous REST**: All inter-service communication uses **OpenFeign** clients over HTTP (service-to-service calls resolved via Eureka service names).
- **Service Discovery**: Netflix Eureka Server with all business services registering as Eureka clients.
- **API Gateway**: Spring Cloud Gateway handles routing, OAuth2 token validation (JWT), and injects `X-Auth-Id` header for downstream services.
- **Centralized Configuration**: Spring Cloud Config Server backed by a **Git repository** (`https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`).
- **Distributed Tracing**: Micrometer Tracing with Brave bridge, reporting to **Zipkin** (:9411).
- **Auth Identity Propagation**: The gateway extracts the authenticated principal and passes it as `X-Auth-Id` header; downstream services read it via `AppAuthUserFilter` and store in a `ThreadLocal` (`ApiRequestContextHolder`) for JPA auditing.

### 1.4 Infrastructure Components

| Component | Image/Version | Purpose |
|---|---|---|
| **MySQL** | `mysql:8.4.0` | Primary data store for all business services (4 databases) |
| **Keycloak** | `quay.io/keycloak/keycloak:23.0.7` | Identity provider (OAuth2/OIDC) |
| **PostgreSQL** | `postgres:15` | Keycloak's backing database |
| **Zipkin** | `openzipkin/zipkin:3` | Distributed tracing UI and collector |

### 1.5 Database Layout

Four MySQL databases are created via the init script:

| Database | Used By |
|---|---|
| `banking_core_service` | core-banking-service |
| `banking_core_fund_transfer_service` | internet-banking-fund-transfer-service |
| `banking_core_user_service` | internet-banking-user-service |
| `banking_core_utility_payment_service` | internet-banking-utility-payment-service |

MySQL user: `javatodev_development` / `oPItyPticIAt` (created in `privileges.sql`).

---

## 2. Data Model Documentation

### 2.1 Core Banking Service

#### `banking_core_user`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `first_name` | VARCHAR(255) | |
| `last_name` | VARCHAR(255) | |
| `email` | VARCHAR(255) | |
| `identification_number` | VARCHAR(255) | National ID / NIC |

#### `banking_core_account`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `number` | VARCHAR(255) | 12-digit account number |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `actual_balance` | DECIMAL(19,2) | |
| `available_balance` | DECIMAL(19,2) | |
| `user_id` | BIGINT (FK → `banking_core_user.id`) | |

#### `banking_core_transaction`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `amount` | DECIMAL(19,2) | Negative for debits |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Target account number or reference |
| `transaction_id` | VARCHAR(50) | UUID |
| `account_id` | BIGINT (FK → `banking_core_account.id`) | |

#### `banking_core_utility_account`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `number` | VARCHAR(255) | Provider account number |
| `provider_name` | VARCHAR(255) | e.g., VODAFONE, VERIZON |

**Relationships:**
- `banking_core_user` 1:N `banking_core_account` (via `user_id` FK)
- `banking_core_account` 1:N `banking_core_transaction` (via `account_id` FK)

### 2.2 User Service

#### `user`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `auth_id` | VARCHAR(255) | Keycloak user UUID |
| `identification` | VARCHAR(255) | Maps to core banking NIC |
| `status` | VARCHAR(255) | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | TIMESTAMP | JPA audit |
| `created_by` | VARCHAR(255) | JPA audit |
| `modified_date` | TIMESTAMP | JPA audit |
| `modified_by` | VARCHAR(255) | JPA audit |
| `version` | BIGINT | Optimistic locking |

### 2.3 Fund Transfer Service

#### `fund_transfer`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `transaction_reference` | VARCHAR(255) | UUID from core banking |
| `from_account` | VARCHAR(255) | Source account number |
| `to_account` | VARCHAR(255) | Destination account number |
| `amount` | DECIMAL(19,2) | |
| `status` | VARCHAR(255) | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date`, `created_by`, `modified_date`, `modified_by`, `version` | — | JPA audit fields |

### 2.4 Utility Payment Service

#### `utility_payment`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL(19,2) | |
| `reference_number` | VARCHAR(255) | Bill reference |
| `account` | VARCHAR(255) | Payer's bank account number |
| `transaction_id` | VARCHAR(255) | UUID from core banking |
| `status` | VARCHAR(255) | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date`, `created_by`, `modified_date`, `modified_by`, `version` | — | JPA audit fields |

---

## 3. API Surface Map

### 3.1 API Gateway Routes

All traffic enters via `:8082`. The gateway routes by path prefix to downstream services (resolved via Eureka service names). The routing configuration is externalized to the Spring Cloud Config git repo.

**Public endpoints** (no JWT required):
- `POST /user/api/v1/bank-users/register`
- `GET /actuator/**` (all services)

**Authenticated endpoints** (JWT required): all other paths.

### 3.2 Core Banking Service (`:8092`)

| Method | Path | Description | Request | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/user/{identification}` | Get user by NIC | Path: identification (String) | `User { id, firstName, lastName, email, identificationNumber, bankAccounts[] }` |
| `GET` | `/api/v1/user` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account | Path: account_number (String) | `BankAccount { id, number, type, status, availableBalance, actualBalance }` |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider | Path: account_name (String) | `UtilityAccount { id, number, providerName }` |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |

### 3.3 User Service (`:8083`)

| Method | Path | Description | Request | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Register new user | `{ email, identification, password }` | `User { id, email, identification, authId, status }` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | Query: `page`, `size`, `sort` | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | Path: id (Long) | `User` |
| `PUT` | `/api/v1/bank-users/{id}` | Update user status | `{ status }` (PENDING/APPROVED/DISABLED/BLACKLIST) | `User` |

### 3.4 Fund Transfer Service (`:8084`)

| Method | Path | Description | Request | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `{ fromAccount, toAccount, amount, authID }` | `{ message, transactionId }` |
| `GET` | `/api/v1/transfer` | List transfers (paginated) | Query: `page`, `size`, `sort` | `List<FundTransfer>` |

### 3.5 Utility Payment Service (`:8085`)

| Method | Path | Description | Request | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/utility-payment` | List payments (paginated) | Query: `page`, `size`, `sort` | `List<UtilityPayment>` |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow
1. Client calls `POST /user/api/v1/bank-users/register` with `{ email, identification, password }`.
2. **User Service** checks if email already exists in Keycloak → throws `UserAlreadyRegisteredException`.
3. Calls **Core Banking Service** via Feign to verify the identification exists → throws `InvalidBankingUserException` if not found.
4. Validates the email matches the core banking record → throws `InvalidEmailException` on mismatch.
5. Creates user in **Keycloak** (disabled, email unverified) with the provided credentials.
6. Persists a local `UserEntity` with `status=PENDING` and the Keycloak `authId`.
7. Admin later calls `PUT /api/v1/bank-users/{id}` with `status=APPROVED` to enable the Keycloak user.

### 4.2 Fund Transfer Flow
1. Client calls `POST /api/v1/transfer` with `{ fromAccount, toAccount, amount }`.
2. **Fund Transfer Service** saves a `FundTransferEntity` with `status=PENDING`.
3. Calls **Core Banking Service** `POST /api/v1/transaction/fund-transfer` via Feign.
4. **Core Banking** validates both accounts exist, checks sufficient balance (`actualBalance >= amount`).
5. Debit from-account, credit to-account, create two `TransactionEntity` records (one per account).
6. Returns `transactionId` (UUID). Fund Transfer Service updates local record to `status=SUCCESS`.

### 4.3 Utility Payment Flow
1. Client calls `POST /api/v1/utility-payment` with `{ providerId, amount, referenceNumber, account }`.
2. **Utility Payment Service** saves a `UtilityPaymentEntity` with `status=PROCESSING`.
3. Calls **Core Banking Service** `POST /api/v1/transaction/util-payment` via Feign.
4. **Core Banking** validates account balance, looks up utility provider, debits the account, records the transaction.
5. Returns `transactionId`. Utility Payment Service updates local record to `status=SUCCESS`.

### 4.4 Balance Validation Rules
- `actualBalance` must be >= 0 **and** >= requested transfer/payment amount.
- Throws `InsufficientFundsException` with code `BANKING-CORE-SERVICE-1001`.

### 4.5 JPA Auditing
- Services (user, fund-transfer, utility-payment) implement JPA auditing via `AuditAware` base class.
- The `X-Auth-Id` header (set by API Gateway) is captured by `AppAuthUserFilter` into a `ThreadLocal` and used as the `createdBy`/`modifiedBy` value.
- Optimistic locking via `@Version` field.

---

## 5. Integration Points

### 5.1 Keycloak
- **Realm**: `javatodev-internet-banking`
- **Client**: `internet-banking-core-client` (confidential client for API gateway)
- **Admin client**: `internet-banking-api-client` (used by User Service's `KeycloakManager` for user provisioning via Keycloak Admin REST API)
- **Library**: `keycloak-admin-client:24.0.4`
- **Configuration**: `KeycloakProperties` reads `app.config.keycloak.*` from Spring Cloud Config.
- **JWT Validation**: API Gateway validates JWTs using the JWKS endpoint from Keycloak.

### 5.2 RabbitMQ
- **Mentioned** in README as a messaging component for notifications.
- **Not implemented**: No RabbitMQ dependencies exist in any `build.gradle`. The Notification Service is marked as "PENDING Development".

### 5.3 Zipkin (Distributed Tracing)
- All business services include Micrometer Tracing Brave bridge and Zipkin reporter.
- Traces are exported to Zipkin at `http://localhost:9411` (or Docker network equivalent).
- Available at `:9411` in Docker Compose.

### 5.4 Database Connections
- **Core Banking → MySQL** (`banking_core_service`): Flyway-managed schema migrations.
- **User Service → MySQL** (`banking_core_user_service`): JPA DDL auto via Hibernate (no Flyway migrations).
- **Fund Transfer → MySQL** (`banking_core_fund_transfer_service`): JPA DDL auto (no Flyway migrations).
- **Utility Payment → MySQL** (`banking_core_utility_payment_service`): JPA DDL auto (no Flyway migrations).
- **Test profiles**: All services use H2 in-memory with Flyway disabled.

### 5.5 Spring Cloud Config (Git-backed)
- Config Server serves configurations from: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- Branch: `main`, search path: `configuration/`
- All services connect via `bootstrap.yml` (localhost / Docker / dev profiles).

### 5.6 Inter-Service Feign Clients

| Source Service | Target Service | Feign Client Interface | Endpoints Called |
|---|---|---|---|
| User Service | Core Banking | `BankingCoreRestClient` | `GET /api/v1/user/{identification}` |
| Fund Transfer | Core Banking | `BankingCoreFeignClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/fund-transfer` |
| Utility Payment | Core Banking | `BankingCoreRestClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/util-payment` |

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System
- **Gradle 8.6** (each service is an independent Gradle project with its own `build.gradle` and wrapper).
- No multi-module Gradle build — each service is built independently.
- **Java 21** source compatibility across all services.
- Plugin: `com.gorylenko.gradle-git-properties` generates `git.properties` at build time.

### 6.2 Docker
- Each service has a `Dockerfile` based on `eclipse-temurin:21.0.2_13-jre-alpine`.
- Pattern: `ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar` + `ENTRYPOINT java -jar -Dspring.profiles.active=docker /app.jar`.
- Services include `wait-for-it.sh` for startup ordering (waits for config server, service registry, and MySQL).

### 6.3 Docker Compose
- **Full stack** (`docker-compose.yml`): All 7 services + MySQL + Keycloak + PostgreSQL + Zipkin on a custom bridge network (`172.25.0.0/16`).
- **Support only** (`docker-compose-support-apps.yml`): Infrastructure services only (Zipkin, Keycloak, PostgreSQL, MySQL, Config Server, Service Registry).
- MySQL is built from a custom `Dockerfile` that copies `privileges.sql` for database/user initialization.
- Keycloak imports realm config from `./keycloak/realm-export.json`.

### 6.4 Build & Run Steps
```bash
# Build each service
cd <service-directory>
./gradlew clean build

# Build Docker images
docker build -t javatodev/<service-name> .

# Start everything
cd docker-compose
docker-compose up -d
```

### 6.5 CI/CD
- No GitHub Actions workflows are present (removed per commit history).
- No Jenkinsfile, GitLab CI, or other CI/CD pipeline definitions exist in the repository.

### 6.6 Test Data
- Core Banking Service includes Flyway seed data: 4 users, 14 bank accounts, 6 utility providers.
- Keycloak realm export includes pre-configured client and realm settings.
- Test credentials: `ib_admin@javatodev.com` / `5V7huE3G86uB`.
