# Application Knowledge Base

## 1. Architecture Overview

### 1.1 System Summary

Internet Banking Concept is a microservices-based banking platform built with **Java 21**, **Spring Boot 3.2.4**, and **Spring Cloud 2023.0.0**. It implements a simplified internet banking system with user registration, fund transfers, and utility payments.

### 1.2 Microservices Inventory

| Service | Port | Purpose | Database |
|---|---|---|---|
| **internet-banking-service-registry** | 8081 | Netflix Eureka discovery server | None |
| **internet-banking-config-server** | 8090 | Centralized configuration (Git-backed) | None |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway + OAuth2 security | None |
| **internet-banking-user-service** | 8083 | User registration, profile management, Keycloak integration | MySQL (shared) |
| **internet-banking-fund-transfer-service** | 8084 | Account-to-account fund transfers | MySQL (shared) |
| **internet-banking-utility-payment-service** | 8085 | Third-party utility bill payments | MySQL (shared) |
| **core-banking-service** | 8092 | System of record for accounts, users, transactions | MySQL (shared) |

> **Note:** A Notification Service is mentioned in the README as "PENDING Development" and is not implemented.

### 1.3 Communication Patterns

```
                          ┌──────────────────┐
                          │   API Gateway    │
                          │  (8082, WebFlux) │
                          │  OAuth2 / JWT    │
                          └────────┬─────────┘
                                   │
              ┌────────────────────┼────────────────────┐
              │                    │                     │
     ┌────────▼───────┐  ┌────────▼───────┐  ┌─────────▼──────┐
     │  User Service  │  │ Fund Transfer  │  │ Utility Payment│
     │    (8083)      │  │   Service      │  │   Service      │
     │                │  │    (8084)      │  │    (8085)      │
     └───────┬────────┘  └───────┬────────┘  └────────┬───────┘
             │                   │                     │
             │     OpenFeign     │     OpenFeign       │
             │                   │                     │
             └───────────────────┼─────────────────────┘
                                 │
                        ┌────────▼───────┐
                        │  Core Banking  │
                        │   Service      │
                        │    (8092)      │
                        └────────────────┘
```

- **Synchronous:** All inter-service communication uses **OpenFeign** declarative REST clients via Eureka service discovery.
- **Asynchronous:** RabbitMQ is referenced in the README for notification messages but is **not implemented** in the current codebase.
- **Service Discovery:** Netflix Eureka (service registry at port 8081).
- **Configuration:** Spring Cloud Config Server backed by a remote Git repository (`https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`).
- **Security:** OAuth2/JWT via Keycloak, enforced at the API Gateway level. Individual services extract the authenticated user ID from the `X-Auth-Id` HTTP header injected by the gateway's `GlobalFilter`.

### 1.4 Infrastructure Components

| Component | Technology | Docker IP | Port |
|---|---|---|---|
| Service Registry | Netflix Eureka | 172.25.0.7 | 8081 |
| Config Server | Spring Cloud Config | 172.25.0.8 | 8090 |
| API Gateway | Spring Cloud Gateway | 172.25.0.6 | 8082 |
| Identity Provider | Keycloak 23.0.7 | 172.25.0.11 | 8080 |
| Keycloak Database | PostgreSQL 15 | 172.25.0.10 | 5432 (internal) |
| Application Database | MySQL (custom image) | 172.25.0.9 | 3306 |
| Distributed Tracing | Zipkin 3 | 172.25.0.12 | 9411 |

### 1.5 Docker Network

All containers run on a custom bridge network `javatodev_ib_network` with subnet `172.25.0.0/16` and static IP assignments for predictable inter-container communication.

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL — `banking_core_service` schema)

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
| `number` | VARCHAR(255) | Account number |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `ACTIVE` |
| `actual_balance` | DECIMAL(19,2) | |
| `available_balance` | DECIMAL(19,2) | |
| `user_id` | BIGINT (FK → `banking_core_user.id`) | |

#### `banking_core_utility_account`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `number` | VARCHAR(255) | Utility provider account number |
| `provider_name` | VARCHAR(255) | e.g., VODAFONE, VERIZON |

#### `banking_core_transaction`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `amount` | DECIMAL(19,2) | Negative for debits |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Target account or reference |
| `transaction_id` | VARCHAR(50) | UUID |
| `account_id` | BIGINT (FK → `banking_core_account.id`) | |

**Relationships:**
- `banking_core_user` 1:N `banking_core_account`
- `banking_core_account` 1:N `banking_core_transaction`

### 2.2 User Service (MySQL — shared database)

#### `user`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `auth_id` | VARCHAR | Keycloak user UUID |
| `identification` | VARCHAR | NIC / National ID |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED` |
| `created_date` | TIMESTAMP | JPA audit |
| `created_by` | VARCHAR | JPA audit |
| `modified_date` | TIMESTAMP | JPA audit |
| `modified_by` | VARCHAR | JPA audit |
| `version` | BIGINT | Optimistic locking |

### 2.3 Fund Transfer Service (MySQL — shared database)

#### `fund_transfer`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `transaction_reference` | VARCHAR | UUID from core banking |
| `status` | VARCHAR | Enum: `PENDING`, `SUCCESS` |
| `created_date` / `modified_date` | TIMESTAMP | JPA audit |
| `created_by` / `modified_by` | VARCHAR | JPA audit |
| `version` | BIGINT | Optimistic locking |

### 2.4 Utility Payment Service (MySQL — shared database)

#### `utility_payment`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK, AUTO_INCREMENT) | |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Customer reference |
| `account` | VARCHAR | Source account number |
| `transaction_id` | VARCHAR | UUID from core banking |
| `status` | VARCHAR | Enum: `PROCESSING`, `SUCCESS` |
| `created_date` / `modified_date` | TIMESTAMP | JPA audit |
| `created_by` / `modified_by` | VARCHAR | JPA audit |
| `version` | BIGINT | Optimistic locking |

### 2.5 Schema Management

- **Core Banking Service:** Uses **Flyway** for schema migrations (3 migration scripts in `src/main/resources/db/migration/`).
- **User, Fund Transfer, Utility Payment services:** Rely on **JPA/Hibernate auto-DDL** (no Flyway migrations).

---

## 3. API Surface Map

### 3.1 Core Banking Service (`/api/v1/...`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `GET` | `/api/v1/user/{identification}` | Read user by NIC | — | `User` (id, firstName, lastName, email, identificationNumber) |
| `GET` | `/api/v1/user` | List users (paginated) | — | `List<User>` |
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account details | — | `BankAccount` (number, type, status, actualBalance, availableBalance) |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider | — | `UtilityAccount` (id, number, providerName) |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer | `FundTransferRequest` (fromAccount, toAccount, amount) | `FundTransferResponse` (message, transactionId) |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `UtilityPaymentRequest` (account, providerId, amount, referenceNumber) | `UtilityPaymentResponse` (message, transactionId) |

### 3.2 User Service (`/api/v1/bank-users/...`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | Register new user | `User` (email, identification, password) | `User` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user (approve/reject) | `UserUpdateRequest` (status) | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | — | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | — | `User` |

### 3.3 Fund Transfer Service (`/api/v1/transfer/...`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `FundTransferRequest` (fromAccount, toAccount, amount) | `FundTransferResponse` (message, transactionId) |
| `GET` | `/api/v1/transfer` | List transfers (paginated) | — | `List<FundTransfer>` |

### 3.4 Utility Payment Service (`/api/v1/utility-payment/...`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `UtilityPaymentRequest` (providerId, amount, referenceNumber, account) | `UtilityPaymentResponse` (message, transactionId) |
| `GET` | `/api/v1/utility-payment` | List payments (paginated) | — | `List<UtilityPayment>` |

### 3.5 API Gateway Routes

The gateway proxies requests to downstream services via Eureka using path-based routing. Based on the configuration pattern:

| Gateway Path Prefix | Target Service |
|---|---|
| `/user/**` | `internet-banking-user-service` |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` |
| `/banking-core/**` | `core-banking-service` |
| `/utility-payment/**` | `internet-banking-utility-payment-service` |

**Public endpoints** (no JWT required):
- `POST /user/api/v1/bank-users/register`
- `GET /actuator/**` (all services)

### 3.6 Actuator Endpoints

All services expose Spring Boot Actuator endpoints:
- `/actuator/info` — Application info (git properties)
- `/actuator/health` — Health check

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

1. Client calls `POST /user/api/v1/bank-users/register` with `{ email, identification, password }`.
2. **User Service** checks if the email already exists in **Keycloak** (rejects with `USER-SERVICE-1001` if duplicate).
3. Calls **Core Banking Service** via Feign to verify the user exists by `identification` (NIC).
4. Validates the email matches the core banking record (rejects with `USER-SERVICE-1002` if mismatch).
5. Creates a **Keycloak user** (disabled, email unverified) with the provided password.
6. Persists a local `UserEntity` with `status = PENDING` and the Keycloak `authId`.
7. An admin later calls `PATCH /update/{id}` with `status = APPROVED`, which enables the Keycloak user and marks email as verified.

### 4.2 Fund Transfer Flow

1. Client calls `POST /fund-transfer/api/v1/transfer` with `{ fromAccount, toAccount, amount }`.
2. **Fund Transfer Service** persists a `FundTransferEntity` with `status = PENDING`.
3. Calls **Core Banking Service** via Feign (`POST /api/v1/transaction/fund-transfer`).
4. **Core Banking Service**:
   - Reads both source and destination accounts.
   - Validates the source account has sufficient funds (`actualBalance >= amount`).
   - Debits the source account (`actualBalance -= amount`, `availableBalance = actualBalance - amount`).
   - Credits the destination account (`actualBalance += amount`, `availableBalance = actualBalance + amount`).
   - Records two `TransactionEntity` entries (debit and credit) with a shared `transactionId` (UUID).
5. **Fund Transfer Service** updates the entity to `status = SUCCESS` with the transaction reference.

### 4.3 Utility Payment Flow

1. Client calls `POST /utility-payment/api/v1/utility-payment` with `{ providerId, amount, referenceNumber, account }`.
2. **Utility Payment Service** persists a `UtilityPaymentEntity` with `status = PROCESSING`.
3. Calls **Core Banking Service** via Feign (`POST /api/v1/transaction/util-payment`).
4. **Core Banking Service**:
   - Reads the source bank account and validates sufficient funds.
   - Reads the utility provider account by `providerId`.
   - Debits the source account.
   - Records a `TransactionEntity` with `transactionType = UTILITY_PAYMENT`.
5. **Utility Payment Service** updates the entity to `status = SUCCESS`.

### 4.4 Balance Validation Rules

```java
if (actualBalance < 0 || actualBalance < requestedAmount) {
    throw InsufficientFundsException;
}
```

- No minimum balance requirement beyond zero.
- No daily/transaction limits.
- No same-account transfer check.

### 4.5 Authentication & Authorization

- **Keycloak** manages user identities with a pre-configured realm.
- **API Gateway** enforces JWT validation using the Keycloak JWK set URI.
- A `GlobalFilter` in the gateway extracts the `Principal` name and injects it as an `X-Auth-Id` header on proxied requests.
- Downstream services use an `AppAuthUserFilter` (servlet filter) to read `X-Auth-Id` and set it in a thread-local `ApiRequestContextHolder`.
- **No role-based access control (RBAC)** is implemented — all authenticated users can access all endpoints.

---

## 5. Integration Points

### 5.1 Keycloak (Identity & Access Management)

- **Version:** 23.0.7
- **Connection:** User Service connects via the `keycloak-admin-client` library (v24.0.4).
- **Configuration:** Server URL, realm, client ID, and client secret are injected via `@Value` from Spring Cloud Config.
- **Realm:** Pre-configured realm exported as `realm-export.json` and auto-imported on container startup.
- **Operations:** Create user, update user (enable/disable), search by email, read by auth ID.
- **Singleton pattern:** `KeycloakProperties` maintains a static `Keycloak` instance (not thread-safe).

### 5.2 RabbitMQ (Message Queue)

- **Status:** Referenced in documentation but **not implemented** in the codebase.
- **Planned usage:** Push notification messages from Fund Transfer and Utility Payment services to a centralized queue consumed by a Notification Service.

### 5.3 Zipkin (Distributed Tracing)

- **Version:** Zipkin 3
- **Integration:** All services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` dependencies.
- **Configuration:** Trace collection is configured via Spring Cloud Config (remote Git repo).
- **Port:** 9411

### 5.4 Database Connections

- **MySQL:** Custom Docker image with pre-configured databases and privileges. All business services share a single MySQL instance.
- **PostgreSQL:** Dedicated instance for Keycloak's internal data store.
- **H2 (test):** Used as an in-memory database for unit tests in core-banking-service.

### 5.5 Spring Cloud Config (Remote Configuration)

- **Git repository:** `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch:** `main`
- **Search path:** `configuration/`
- **Bootstrap:** Services use `bootstrap.yml` (dev) and `bootstrap-docker.yml` (Docker profile) to point to the config server.

### 5.6 OpenFeign Clients

| Source Service | Target Service | Feign Client Interface | Endpoints Called |
|---|---|---|---|
| User Service | Core Banking | `BankingCoreRestClient` | `GET /api/v1/user/{identification}` |
| Fund Transfer Service | Core Banking | `BankingCoreFeignClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/fund-transfer` |
| Utility Payment Service | Core Banking | `BankingCoreRestClient` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/util-payment` |

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build tool:** Gradle (each service has its own `build.gradle` and Gradle wrapper)
- **No multi-project Gradle build** — each service is independently built.
- **Plugin:** `gradle-git-properties` (v2.4.2) generates `git.properties` for actuator `/info` endpoint.

### 6.2 Docker Build

Each service has its own `Dockerfile`:

```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh && apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

### 6.3 Docker Compose Deployment

Two Docker Compose files are provided:

| File | Purpose |
|---|---|
| `docker-compose.yml` | Full stack (all services + infrastructure) |
| `docker-compose-support-apps.yml` | Infrastructure only (Zipkin, Keycloak, MySQL, Config Server, Service Registry) |

**Startup ordering** is managed via `wait-for-it.sh` scripts in entrypoints:
1. Service Registry (8081)
2. Config Server (8090)
3. MySQL (3306)
4. Then application services

### 6.4 Configuration Profiles

| Profile | Config Source | Usage |
|---|---|---|
| `default` (dev) | `bootstrap.yml` → `http://localhost:8090` | Local development |
| `docker` | `bootstrap-docker.yml` → `http://internet-banking-config-server:8090` | Docker deployment |

### 6.5 CI/CD

- No CI/CD pipeline is defined in the repository (GitHub Actions workflows have been removed).
- No Kubernetes manifests are present despite being listed in the technology stack.

### 6.6 Test Infrastructure

- **Core Banking Service:** Has unit tests (`AccountServiceTest`, `UserServiceTest`, `TransactionServiceTest`) using JUnit 5 + Mockito with H2 in-memory database.
- **Other services:** Only have empty Spring Boot context load test classes (no actual test logic).
- **Test framework:** JUnit Platform (JUnit 5), configured via `useJUnitPlatform()` in all `build.gradle` files.
