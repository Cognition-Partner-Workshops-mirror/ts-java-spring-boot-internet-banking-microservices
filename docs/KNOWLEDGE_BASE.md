# Application Knowledge Base

> **Repository:** `ts-java-spring-boot-internet-banking-microservices`
> **Stack:** Java 21 / Spring Boot 3.2.4 / Spring Cloud 2023.0.0
> **Last updated:** 2026-05-07

---

## 1. Architecture Overview

### 1.1 Service Inventory

| # | Service | Type | Port | Description |
|---|---------|------|------|-------------|
| 1 | `internet-banking-service-registry` | Infrastructure | 8081 | Netflix Eureka server for service discovery |
| 2 | `internet-banking-config-server` | Infrastructure | 8090 | Spring Cloud Config Server; fetches configuration from a remote Git repository |
| 3 | `internet-banking-api-gateway` | Infrastructure | 8082 | Spring Cloud Gateway; single entry point with OAuth2/JWT security |
| 4 | `core-banking-service` | Business | 8092 | System-of-record for users, bank accounts, utility accounts, and transaction processing |
| 5 | `internet-banking-user-service` | Business | 8083 | User lifecycle management (registration, approval, update) with Keycloak integration |
| 6 | `internet-banking-fund-transfer-service` | Business | 8084 | Orchestrates peer-to-peer fund transfers between bank accounts |
| 7 | `internet-banking-utility-payment-service` | Business | 8085 | Processes utility bill payments (electricity, water, etc.) |

> **Note:** A Notification Service is referenced in documentation but is **not yet implemented**.

### 1.2 Communication Patterns

```
                          +-----------------------+
                          |   API Gateway (:8082) |
                          |  OAuth2 + JWT Filter  |
                          +-----------+-----------+
                                      |
                    +-----------------+-----------------+
                    |                 |                 |
             User Service      Fund Transfer     Utility Payment
              (:8083)          Service (:8084)    Service (:8085)
                    |                 |                 |
                    +--------+--------+---------+------+
                             |                  |
                      Core Banking Service (:8092)
                             |
                          MySQL DB
```

| Pattern | Technology | Details |
|---------|-----------|---------|
| Synchronous REST | Spring Cloud OpenFeign | User, Fund Transfer, and Utility Payment services call Core Banking Service via Feign clients |
| Service Discovery | Netflix Eureka | All business services register with the Eureka server; Feign clients resolve service names through Eureka |
| Centralized Config | Spring Cloud Config Server | Configuration stored in a remote Git repo (`internet-banking-microservices-configurations`); services fetch config at bootstrap using `bootstrap.yml` / `bootstrap-docker.yml` |
| API Gateway | Spring Cloud Gateway | Routes requests to downstream services; injects `X-Auth-Id` header from JWT principal |
| Authentication | OAuth2 / JWT via Keycloak | Gateway validates JWT tokens; user registration endpoint is publicly accessible |
| Messaging (planned) | RabbitMQ | Referenced in design docs for notification delivery; not wired in current code |
| Distributed Tracing | Micrometer Tracing + Brave + Zipkin | All services include tracing dependencies; traces are exported to a Zipkin collector |

### 1.3 Infrastructure Components

| Component | Image / Version | Docker IP | Purpose |
|-----------|----------------|-----------|---------|
| Zipkin | `openzipkin/zipkin:3` | 172.25.0.12 | Distributed trace collection and visualization |
| Keycloak | `quay.io/keycloak/keycloak:23.0.7` | 172.25.0.11 | Identity and Access Management; realm imported on startup |
| PostgreSQL (Keycloak) | `postgres:15` | 172.25.0.10 | Keycloak's backing database |
| MySQL | `mysql:8.4.0` (custom Dockerfile) | 172.25.0.9 | Shared database for all business services |
| Config Server | `javatodev/internet-banking-config-server` | 172.25.0.8 | Centralized configuration |
| Service Registry | `javatodev/internet-banking-service-registry` | 172.25.0.7 | Eureka server |

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL: `banking_core_service`)

#### `banking_core_user` (UserEntity)

| Column | Type | Notes |
|--------|------|-------|
| `id` | `BIGINT` (PK, auto-increment) | |
| `first_name` | `VARCHAR` | |
| `last_name` | `VARCHAR` | |
| `email` | `VARCHAR` | |
| `identification_number` | `VARCHAR` | Unique NIC/ID used to link across services |

**Relationships:** One user has many bank accounts (`@OneToMany` via `BankAccountEntity.user`).

#### `banking_core_account` (BankAccountEntity)

| Column | Type | Notes |
|--------|------|-------|
| `id` | `BIGINT` (PK, auto-increment) | |
| `number` | `VARCHAR` | Bank account number (lookup key) |
| `type` | `ENUM(AccountType)` | e.g., SAVINGS, CURRENT |
| `status` | `ENUM(AccountStatus)` | e.g., ACTIVE, CLOSED |
| `available_balance` | `DECIMAL` | |
| `actual_balance` | `DECIMAL` | |
| `user_id` | `BIGINT` (FK -> `banking_core_user.id`) | |

#### `banking_core_transaction` (TransactionEntity)

| Column | Type | Notes |
|--------|------|-------|
| `id` | `BIGINT` (PK, auto-increment) | |
| `amount` | `DECIMAL` | Negative for debits, positive for credits |
| `transaction_type` | `ENUM(TransactionType)` | `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | `VARCHAR` | Counterparty account number or reference |
| `transaction_id` | `VARCHAR` (UUID) | Correlates debit/credit legs |
| `account_id` | `BIGINT` (FK -> `banking_core_account.id`) | `@OneToOne` (note: modeling issue - see Gap Analysis) |

#### `banking_core_utility_account` (UtilityAccountEntity)

| Column | Type | Notes |
|--------|------|-------|
| `id` | `BIGINT` (PK, auto-increment) | |
| `number` | `VARCHAR` | Utility provider account number |
| `provider_name` | `VARCHAR` | Lookup key (e.g., "Electricity Board") |

### 2.2 Internet Banking User Service (MySQL: `banking_core_user_service`)

#### `user` (UserEntity extends AuditAware)

| Column | Type | Notes |
|--------|------|-------|
| `id` | `BIGINT` (PK, auto-increment) | |
| `auth_id` | `VARCHAR` | Keycloak user UUID |
| `identification` | `VARCHAR` | NIC linking to core banking user |
| `status` | `ENUM(Status)` | `PENDING`, `APPROVED` |
| `created_by` | `VARCHAR` | Audit field (from AuditAware) |
| `created_at` | `DATETIME` | Audit field |
| `updated_by` | `VARCHAR` | Audit field |
| `updated_at` | `DATETIME` | Audit field |

### 2.3 Fund Transfer Service (MySQL: `banking_core_fund_transfer_service`)

#### `fund_transfer` (FundTransferEntity extends AuditAware)

| Column | Type | Notes |
|--------|------|-------|
| `id` | `BIGINT` (PK, auto-increment) | |
| `transaction_reference` | `VARCHAR` | UUID from core banking response |
| `from_account` | `VARCHAR` | Source account number |
| `to_account` | `VARCHAR` | Destination account number |
| `amount` | `DECIMAL` | |
| `status` | `ENUM(TransactionStatus)` | `PENDING`, `SUCCESS` |
| Audit columns | | `created_by`, `created_at`, `updated_by`, `updated_at` |

### 2.4 Utility Payment Service (MySQL: `banking_core_utility_payment_service`)

#### `utility_payment` (UtilityPaymentEntity extends AuditAware)

| Column | Type | Notes |
|--------|------|-------|
| `id` | `BIGINT` (PK, auto-increment) | |
| `provider_id` | `BIGINT` | Utility provider reference |
| `amount` | `DECIMAL` | |
| `reference_number` | `VARCHAR` | Customer's bill/reference number |
| `account` | `VARCHAR` | Source bank account number |
| `transaction_id` | `VARCHAR` | UUID from core banking response |
| `status` | `ENUM(TransactionStatus)` | `PROCESSING`, `SUCCESS` |
| Audit columns | | `created_by`, `created_at`, `updated_by`, `updated_at` |

### 2.5 Entity Relationship Diagram (Logical)

```
banking_core_user  1───*  banking_core_account  1───1  banking_core_transaction
                                                              |
                                  banking_core_utility_account (standalone)

banking_core_user_service.user ──(identification)──> banking_core_user

fund_transfer ──(from_account/to_account)──> banking_core_account.number

utility_payment ──(account)──> banking_core_account.number
                ──(provider_id)──> banking_core_utility_account.id
```

---

## 3. API Surface Map

### 3.1 API Gateway Routes (all prefixed through gateway at `:8082`)

| Gateway Prefix | Target Service |
|----------------|---------------|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/banking-core/**` | core-banking-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |

### 3.2 Core Banking Service (`:8092`)

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|-------------|----------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | - | `BankAccount` (id, number, type, status, availableBalance, actualBalance) |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | - | `UtilityAccount` (id, number, providerName) |
| `GET` | `/api/v1/user/{identification}` | Get user by identification (NIC) | - | `User` (id, firstName, lastName, email, identificationNumber, accounts[]) |
| `GET` | `/api/v1/user` | List users (paginated) | Pageable params | `List<User>` |
| `POST` | `/api/v1/transaction/fund-transfer` | Process a fund transfer | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `POST` | `/api/v1/transaction/util-payment` | Process a utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |

### 3.3 Internet Banking User Service (`:8083`)

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|-------------|----------|
| `POST` | `/api/v1/bank-users/register` | Register new internet banking user | `{ email, identification, password }` | `User` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user (e.g., approve) | `{ status }` | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | Pageable params | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | - | `User` |

### 3.4 Internet Banking Fund Transfer Service (`:8084`)

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|-------------|----------|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `{ fromAccount, toAccount, amount, authID }` | `{ message, transactionId }` |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | Pageable params | `List<FundTransfer>` |

### 3.5 Internet Banking Utility Payment Service (`:8085`)

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|-------------|----------|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | Pageable params | `List<UtilityPayment>` |

### 3.6 Infrastructure Endpoints

| Service | Endpoint | Purpose |
|---------|----------|---------|
| All services | `/actuator/**` | Health, info, metrics, Prometheus (where configured) |
| Service Registry | `:8081/` | Eureka dashboard |
| Zipkin | `:9411/` | Trace visualization UI |
| Keycloak | `:8080/` | IAM administration console |

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

1. Client sends `POST /api/v1/bank-users/register` with `{ email, identification, password }` through API Gateway.
2. **User Service** checks if email already exists in Keycloak (via `KeycloakUserService.readUserByEmail`).
3. Calls **Core Banking Service** via Feign to validate the identification number exists in the banking core.
4. Validates that the email matches the core banking record.
5. Creates a Keycloak user (disabled, email unverified) with the provided password.
6. Persists a local `UserEntity` with status `PENDING` and the Keycloak `authId`.

### 4.2 User Approval Flow

1. Admin sends `PATCH /api/v1/bank-users/update/{id}` with `{ status: "APPROVED" }`.
2. **User Service** enables the Keycloak user and marks email as verified.
3. Updates the local entity status to `APPROVED`.

### 4.3 Fund Transfer Flow

1. Client sends `POST /api/v1/transfer` with `{ fromAccount, toAccount, amount }`.
2. **Fund Transfer Service** persists a record with status `PENDING`.
3. Calls **Core Banking Service** `POST /api/v1/transaction/fund-transfer` via Feign.
4. **Core Banking Service**:
   - Reads both bank accounts.
   - Validates the source account has sufficient funds (`actualBalance >= amount`).
   - Executes `internalFundTransfer`: debits source, credits destination, creates two `TransactionEntity` records (debit + credit) linked by a shared `transactionId` (UUID).
   - Returns `{ message, transactionId }`.
5. **Fund Transfer Service** updates its record to `SUCCESS` with the transaction reference.

### 4.4 Utility Payment Flow

1. Client sends `POST /api/v1/utility-payment` with `{ providerId, amount, referenceNumber, account }`.
2. **Utility Payment Service** persists a record with status `PROCESSING`.
3. Calls **Core Banking Service** `POST /api/v1/transaction/util-payment` via Feign.
4. **Core Banking Service**:
   - Reads the source bank account and validates sufficient funds.
   - Reads the utility account by provider ID.
   - Debits the source account.
   - Creates a `TransactionEntity` with type `UTILITY_PAYMENT`.
   - Returns `{ message, transactionId }`.
5. **Utility Payment Service** updates its record to `SUCCESS`.

### 4.5 Balance Validation Rules

- Transfer/payment is rejected with `InsufficientFundsException` if:
  - `actualBalance < 0`, OR
  - `actualBalance < requestedAmount`
- Available balance is recalculated as `actualBalance - amount` after the debit (note: potential double-subtraction bug in `utilPayment` -- see Gap Analysis).

### 4.6 Authentication & Authorization

- The API Gateway validates JWT tokens issued by Keycloak.
- The `/user/api/v1/bank-users/register` endpoint is publicly accessible (no token required).
- All actuator endpoints are publicly accessible through the gateway.
- The gateway injects `X-Auth-Id` (the JWT principal name) into downstream requests.
- Downstream services extract `X-Auth-Id` via `AppAuthUserFilter` and store it in a thread-local `ApiRequestContext`.

---

## 5. Integration Points

### 5.1 Keycloak (Identity & Access Management)

| Aspect | Details |
|--------|---------|
| Version | 23.0.7 |
| Realm | Imported from `docker-compose/keycloak/realm-export.json` on first startup |
| Integration | User Service uses `keycloak-admin-client:24.0.4` for programmatic user management |
| Auth Flow | Client credentials grant (`client_credentials`) for admin operations |
| Configuration | `app.config.keycloak.server-url`, `realm`, `clientId`, `client-secret` (from Config Server) |
| Gateway | OAuth2 Resource Server with JWT; JWK Set URI configured via `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` |

### 5.2 RabbitMQ (Message Broker)

| Aspect | Details |
|--------|---------|
| Status | **Referenced in design but NOT wired in current code** |
| Intended Use | Fund Transfer and Utility Payment services push notification messages; Notification Service consumes them |
| Dependencies | No RabbitMQ dependencies in any `build.gradle`; no RabbitMQ container in `docker-compose.yml` |

### 5.3 Zipkin (Distributed Tracing)

| Aspect | Details |
|--------|---------|
| Collector | `openzipkin/zipkin:3` at `:9411` |
| Integration | Micrometer Tracing with Brave bridge (`micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`) |
| Coverage | All 4 business services + API Gateway include tracing dependencies |
| Feign Tracing | `feign-micrometer` included for propagating trace context across Feign calls |

### 5.4 Database Connections

| Service | Database | Schema |
|---------|----------|--------|
| Core Banking Service | MySQL | `banking_core_service` |
| User Service | MySQL | `banking_core_user_service` |
| Fund Transfer Service | MySQL | `banking_core_fund_transfer_service` |
| Utility Payment Service | MySQL | `banking_core_utility_payment_service` |
| Keycloak | PostgreSQL | `keycloak` |

- All MySQL schemas are created via `docker-compose/mysql/privileges.sql`.
- Core Banking Service uses Flyway for schema migrations (`flyway-core:10.12.0`, `flyway-mysql:10.12.0`).
- Other business services rely on JPA `ddl-auto` (configured via Config Server; likely `update` or `create`).

### 5.5 Spring Cloud Config (Centralized Configuration)

| Aspect | Details |
|--------|---------|
| Git Repository | `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` |
| Branch | `main` |
| Search Path | `configuration/` |
| Bootstrap | Services use `bootstrap.yml` (localhost) and `bootstrap-docker.yml` (Docker) profiles |

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build Tool:** Gradle (per-service `build.gradle`; no multi-project root build file)
- **Java Version:** 21 (Eclipse Temurin `21.0.2_13-jre-alpine` for Docker images)
- **Spring Boot:** 3.2.4 via Spring Boot Gradle plugin
- **Spring Cloud:** 2023.0.0 BOM
- **Git Properties:** `com.gorylenko.gradle-git-properties:2.4.2` plugin generates `git.properties` for `/actuator/info`

### 6.2 Docker

Each service has its own `Dockerfile`:

```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh && apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

Build process (manual):
1. `./gradlew build` in each service directory
2. `docker build -t javatodev/<service-name> .` in each service directory
3. `docker-compose up -d` from the `docker-compose/` directory

### 6.3 Docker Compose Orchestration

- **Main file:** `docker-compose/docker-compose.yml` -- full stack (infrastructure + all services)
- **Support file:** `docker-compose/docker-compose-support-apps.yml` -- infrastructure only (Zipkin, Keycloak, MySQL, Config Server, Service Registry)
- **Network:** Custom bridge network `javatodev_ib_network` (`172.25.0.0/16`) with static IP assignments
- **Startup Order:** `wait-for-it.sh` scripts ensure services wait for Config Server, Service Registry, and MySQL before starting
- **Volumes:** Named volumes for MySQL data (`mysqldata`) and PostgreSQL data (`postgres_data`)

### 6.4 Profiles

| Profile | Usage |
|---------|-------|
| `default` | Local development; services connect to `localhost` |
| `docker` | Docker Compose deployment; services connect to container hostnames |
| `dev` | Development profile (bootstrap-dev.yml exists in some services) |

### 6.5 Testing

- **Test Framework:** JUnit 5 (Jupiter) via `spring-boot-starter-test`
- **Test Database:** H2 in-memory (`com.h2database:h2:2.2.224`) for core-banking-service tests
- **Test Coverage:** Only `core-banking-service` has unit tests (AccountServiceTest, TransactionServiceTest, UserServiceTest). Other services have only empty application context tests.

### 6.6 API Documentation

- **OpenAPI/Swagger:** `springdoc-openapi-starter-webflux-ui:2.1.0` included in business services
- **Annotations:** Controllers use `@Tag` and `@Operation` from `io.swagger.v3.oas.annotations`
- **Postman:** Collection and environment file available in `postman_collection/`
