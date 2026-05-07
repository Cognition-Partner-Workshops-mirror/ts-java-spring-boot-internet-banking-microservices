# Application Knowledge Base

## 1. Architecture Overview

### 1.1 Services

The application comprises **6 microservices** built with Java 21 and Spring Boot 3.2.4 (Spring Cloud 2023.0.0):

| Service | Port | Purpose |
|---------|------|---------|
| **core-banking-service** | 8092 | Core banking engine: accounts, users, transactions. Acts as the system of record for bank accounts and processes fund transfers and utility payments at the database level. |
| **internet-banking-user-service** | 8083 | Internet banking user lifecycle: registration, approval, retrieval. Integrates with Keycloak for identity management and delegates to core-banking-service for user verification. |
| **internet-banking-fund-transfer-service** | 8084 | Orchestrates fund transfer requests. Persists transfer records locally and delegates actual balance mutations to core-banking-service via Feign. |
| **internet-banking-utility-payment-service** | 8085 | Orchestrates utility payment requests. Persists payment records locally and delegates actual balance mutations to core-banking-service via Feign. |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway. Routes external traffic to downstream services, enforces OAuth2/JWT authentication via Keycloak, and propagates the authenticated user identity (`X-Auth-Id` header). |
| **internet-banking-service-registry** | 8081 | Netflix Eureka server. All business services register here for service discovery. |
| **internet-banking-config-server** | 8090 | Spring Cloud Config Server backed by a remote Git repository (`internet-banking-microservices-configurations`). Provides externalized configuration to all services. |

### 1.2 Communication Patterns

| Pattern | Implementation | Usage |
|---------|----------------|-------|
| **Synchronous REST (Feign)** | Spring Cloud OpenFeign | `fund-transfer-service -> core-banking-service`, `utility-payment-service -> core-banking-service`, `user-service -> core-banking-service` |
| **Service Discovery** | Netflix Eureka | All business services register with `internet-banking-service-registry` and resolve peer addresses via Eureka client |
| **API Gateway** | Spring Cloud Gateway | Single entry point on port 8082; routes by path prefix (`/user/**`, `/fund-transfer/**`, `/banking-core/**`, `/utility-payment/**`) |
| **Centralized Config** | Spring Cloud Config Server | Git-backed config repo; services pull configuration on startup via `bootstrap.yml` pointing to `http://localhost:8090` (or Docker hostname) |
| **Distributed Tracing** | Micrometer Tracing + Brave + Zipkin | Trace context propagated across Feign calls; spans exported to Zipkin on port 9411 |
| **Asynchronous Messaging** | RabbitMQ (planned) | Mentioned in architecture for notification service; **not yet implemented** |

### 1.3 Infrastructure Components

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Database | MySQL 8.4.0 | Shared MySQL instance for all data-persisting services (core-banking, user, fund-transfer, utility-payment) |
| Identity Provider | Keycloak 23.0.7 (PostgreSQL 15 backend) | OAuth2/OIDC provider; realm imported at startup from `realm-export.json` |
| Distributed Tracing | Zipkin 3 | Trace collection and visualization |
| Service Registry | Netflix Eureka | Service registration and discovery |
| Config Store | Git repository | Externalized YAML configurations per service per profile |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL)

#### `banking_core_user`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `first_name` | VARCHAR | |
| `last_name` | VARCHAR | |
| `email` | VARCHAR | |
| `identification_number` | VARCHAR | Unique national/government ID |

#### `banking_core_account`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `number` | VARCHAR | Account number |
| `type` | ENUM (`SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT`) | |
| `status` | ENUM (`PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED`) | |
| `available_balance` | DECIMAL | |
| `actual_balance` | DECIMAL | |
| `user_id` | BIGINT (FK -> `banking_core_user.id`) | ManyToOne |

#### `banking_core_transaction`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `amount` | DECIMAL | Negative for debits |
| `transaction_type` | ENUM (`FUND_TRANSFER`, `UTILITY_PAYMENT`) | |
| `reference_number` | VARCHAR | Counterparty account number or utility ref |
| `transaction_id` | VARCHAR (UUID) | Correlation ID |
| `account_id` | BIGINT (FK -> `banking_core_account.id`) | OneToOne (CascadeType.ALL) |

#### `banking_core_utility_account`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `number` | VARCHAR | |
| `provider_name` | VARCHAR | Utility provider name |

**Flyway migrations** (3 scripts):
1. `V1.0.20210427174638` - Creates `banking_core_user`, `banking_core_account`, `banking_core_utility_account` tables
2. `V1.0.20210427174721` - Inserts seed data (test users, accounts, utility providers)
3. `V1.0.20210429210839` - Creates `banking_core_transaction` table

### 2.2 Internet Banking User Service (MySQL)

#### `user`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `auth_id` | VARCHAR | Keycloak user ID |
| `identification` | VARCHAR | Links to core banking `identification_number` |
| `status` | ENUM (`PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST`) | |
| `created_date` | TIMESTAMP | JPA Auditing |
| `created_by` | VARCHAR | JPA Auditing (from `X-Auth-Id` header) |
| `modified_date` | TIMESTAMP | JPA Auditing |
| `modified_by` | VARCHAR | JPA Auditing |
| `version` | BIGINT | Optimistic locking |

### 2.3 Internet Banking Fund Transfer Service (MySQL)

#### `fund_transfer`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `transaction_reference` | VARCHAR | UUID from core-banking-service |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | |
| `status` | ENUM (`PENDING`, `PROCESSING`, `SUCCESS`, `FAILED`) | |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | Audit fields | JPA Auditing via `AuditAware` |

### 2.4 Internet Banking Utility Payment Service (MySQL)

#### `utility_payment`
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT (PK, auto-increment) | |
| `provider_id` | BIGINT | FK to utility provider |
| `amount` | DECIMAL | |
| `reference_number` | VARCHAR | |
| `account` | VARCHAR | Payer account number |
| `transaction_id` | VARCHAR | UUID from core-banking-service |
| `status` | ENUM (`PENDING`, `PROCESSING`, `SUCCESS`, `FAILED`) | |
| `created_date` / `created_by` / `modified_date` / `modified_by` / `version` | Audit fields | JPA Auditing via `AuditAware` |

---

## 3. API Surface Map

### 3.1 Core Banking Service (port 8092)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | - | `BankAccount` (id, number, type, status, availableBalance, actualBalance) |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | - | `UtilityAccount` (id, number, providerName) |
| `GET` | `/api/v1/user/{identification}` | Get user by identification number | - | `User` (id, firstName, lastName, email, identificationNumber, accounts[]) |
| `GET` | `/api/v1/user` | List users (paginated) | `Pageable` query params | `List<User>` |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |

### 3.2 Internet Banking User Service (port 8083)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/bank-users/register` | Register new banking user | `{ email, identification, password }` | `User` |
| `GET` | `/api/v1/bank-users` | List all users (paginated) | `Pageable` query params | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | - | `User` |
| `PUT` | `/api/v1/bank-users/{id}` | Update user status | `{ status }` | `User` |

### 3.3 Internet Banking Fund Transfer Service (port 8084)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `{ fromAccount, toAccount, amount, authID }` | `{ message, transactionId }` |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | `Pageable` query params | `List<FundTransfer>` |

### 3.4 Internet Banking Utility Payment Service (port 8085)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | `Pageable` query params | `List<UtilityPayment>` |

### 3.5 API Gateway Routes (port 8082)

All external requests pass through the gateway with path-prefix-based routing:

| Path Prefix | Target Service |
|-------------|----------------|
| `/user/**` | `internet-banking-user-service` |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` |
| `/banking-core/**` | `core-banking-service` |
| `/utility-payment/**` | `internet-banking-utility-payment-service` |

**Public endpoints** (no auth required):
- `POST /user/api/v1/bank-users/register`
- `/actuator/**` endpoints for all services

All other endpoints require a valid JWT Bearer token issued by Keycloak.

---

## 4. Key Business Logic Inventory

### 4.1 Fund Transfer Flow

1. **Client** sends `POST /api/v1/transfer` to fund-transfer-service (via gateway)
2. **Fund Transfer Service**:
   - Creates a `FundTransferEntity` with status `PENDING`
   - Calls `core-banking-service` via Feign: `POST /api/v1/transaction/fund-transfer`
3. **Core Banking Service** (`TransactionService.fundTransfer`):
   - Reads both source and destination `BankAccount` records
   - **Validates balance**: source `actualBalance >= 0` AND `actualBalance >= transferAmount`; throws `InsufficientFundsException` otherwise
   - Calls `internalFundTransfer`:
     - Debits source: `actualBalance -= amount`, `availableBalance = actualBalance - amount`
     - Credits destination: `actualBalance += amount`, `availableBalance = actualBalance + amount`
     - Creates two `TransactionEntity` records (debit + credit) with the same `transactionId` (UUID)
4. **Fund Transfer Service** updates local entity with `transactionReference` and status `SUCCESS`

**Known issue**: The `availableBalance` calculation applies the subtraction twice (once in `actualBalance` mutation, once in `availableBalance` assignment), producing incorrect available balance values.

### 4.2 Utility Payment Flow

1. **Client** sends `POST /api/v1/utility-payment` to utility-payment-service (via gateway)
2. **Utility Payment Service**:
   - Creates a `UtilityPaymentEntity` with status `PROCESSING`
   - Calls `core-banking-service` via Feign: `POST /api/v1/transaction/util-payment`
3. **Core Banking Service** (`TransactionService.utilPayment`):
   - Reads payer `BankAccount`, validates balance
   - Reads `UtilityAccount` by provider ID
   - Debits payer account (same double-subtraction issue as fund transfer)
   - Creates one `TransactionEntity` record
4. **Utility Payment Service** updates local entity with `transactionId` and status `SUCCESS`

### 4.3 User Registration Flow

1. **Client** sends `POST /user/api/v1/bank-users/register` (public endpoint)
2. **User Service**:
   - Checks Keycloak: if email already registered, throws `UserAlreadyRegisteredException`
   - Calls `core-banking-service` via Feign to verify user by `identification` number
   - Validates email matches core banking record
   - Creates Keycloak user (disabled, unverified) with provided password
   - Persists `UserEntity` with status `PENDING` and Keycloak `authId`
3. **Admin** calls `PUT /api/v1/bank-users/{id}` with `status: APPROVED`
   - Enables Keycloak user and marks email as verified
   - Updates local entity status

### 4.4 Authentication Flow

1. Client obtains JWT from Keycloak (`/realms/{realm}/protocol/openid-connect/token`)
2. Gateway validates JWT via `jwk-set-uri`
3. Gateway extracts principal name and passes it as `X-Auth-Id` header to downstream services
4. Downstream services capture `X-Auth-Id` via `AppAuthUserFilter` and use it for JPA auditing

---

## 5. Integration Points

### 5.1 Keycloak (Identity & Access Management)

- **Version**: 23.0.7
- **Backend**: PostgreSQL 15
- **Realm**: Imported from `docker-compose/keycloak/realm-export.json`
- **Integration**:
  - **API Gateway**: OAuth2 Resource Server validates JWTs using `jwk-set-uri`
  - **User Service**: Keycloak Admin Client (`keycloak-admin-client:24.0.4`) for user CRUD operations
- **Configuration**: `app.config.keycloak.server-url`, `app.config.keycloak.realm`, `app.config.keycloak.clientId`, `app.config.keycloak.client-secret`
- **Test Credentials**: `ib_admin@javatodev.com / 5V7huE3G86uB`

### 5.2 RabbitMQ (Planned)

- Referenced in README architecture diagram for notification service
- **Not yet implemented** in codebase; no RabbitMQ dependencies in any `build.gradle`

### 5.3 Zipkin (Distributed Tracing)

- **Version**: 3
- **Port**: 9411
- All business services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` dependencies
- Trace context is propagated across Feign calls via `feign-micrometer`

### 5.4 Database Connections

All services connect to the same **MySQL 8.4.0** instance:

| Service | Database (logical) | Connection |
|---------|-------------------|------------|
| core-banking-service | core banking tables | Via Spring Cloud Config (externalized) |
| user-service | `user` table | Via Spring Cloud Config (externalized) |
| fund-transfer-service | `fund_transfer` table | Via Spring Cloud Config (externalized) |
| utility-payment-service | `utility_payment` table | Via Spring Cloud Config (externalized) |

MySQL is initialized with `privileges.sql` via Docker entrypoint.

### 5.5 Spring Cloud Config

- Config Server fetches from: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- Branch: `main`, search path: `configuration`
- Each service has a `bootstrap.yml` pointing to the config server
- Profile-specific bootstrap files exist for `docker` and `dev` environments

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Gradle** (wrapper 8.6) per service; each service is an independent Gradle project (no multi-project build)
- **Key plugins**: `org.springframework.boot:3.2.4`, `io.spring.dependency-management:1.1.4`, `com.gorylenko.gradle-git-properties:2.4.2`
- **Test framework**: JUnit 5 (`useJUnitPlatform()`), H2 in-memory database for tests
- **Database migrations**: Flyway (`flyway-core:10.12.0`, `flyway-mysql:10.12.0`) in core-banking-service only

### 6.2 Docker

Each service has a `Dockerfile` following the same pattern:

```dockerfile
FROM eclipse-temurin:21-jre-alpine
ADD build/libs/*.jar /app.jar
ADD wait-for-it.sh /wait-for-it.sh
RUN chmod +x /wait-for-it.sh
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

Support containers:
- `mysql` - Custom Dockerfile extending `mysql:8.4.0` with `privileges.sql` init script
- `keycloak` - Custom Dockerfile extending Keycloak with realm import

### 6.3 Docker Compose

Two compose files:

1. **`docker-compose.yml`** - Full stack: all 7 services + MySQL + Keycloak + PostgreSQL + Zipkin
2. **`docker-compose-support-apps.yml`** - Infrastructure only: MySQL, Keycloak, PostgreSQL, Zipkin, Config Server, Service Registry

All services on a custom bridge network (`172.25.0.0/16`) with static IPs. Application services use `wait-for-it.sh` to handle startup ordering (wait for service registry, config server, and MySQL).

### 6.4 CI/CD

- No CI/CD pipeline files present (GitHub Actions workflows were removed per commit `fa1c445`)
- No Kubernetes manifests despite Kubernetes being listed in the technology stack
- Deployment is manual via `docker-compose up -d`
