# Application Knowledge Base

> **Repository:** `ts-java-spring-boot-internet-banking-microservices`
> **Stack:** Java 21 / Spring Boot 3.2.4 / Spring Cloud 2023.0.0
> **Last Updated:** 2026-05-07

---

## 1. Architecture Overview

### 1.1 Service Inventory

| # | Service | Port | Type | Description |
|---|---------|------|------|-------------|
| 1 | `internet-banking-service-registry` | 8081 | Infrastructure | Netflix Eureka discovery server |
| 2 | `internet-banking-config-server` | 8090 | Infrastructure | Spring Cloud Config server (Git-backed) |
| 3 | `internet-banking-api-gateway` | 8082 | Infrastructure | Spring Cloud Gateway with OAuth2/Keycloak security |
| 4 | `core-banking-service` | 8092 | Business | System of record: accounts, users, transactions, ledger |
| 5 | `internet-banking-user-service` | 8083 | Business | User registration, profile management, Keycloak integration |
| 6 | `internet-banking-fund-transfer-service` | 8084 | Business | Account-to-account fund transfers |
| 7 | `internet-banking-utility-payment-service` | 8085 | Business | Third-party utility bill payments |

> **Note:** A Notification Service is mentioned in the README as "PENDING Development" and is not implemented.

### 1.2 Communication Patterns

```
                         +-----------------+
  Client  ──────────────>│  API Gateway    │  (OAuth2 JWT validation)
                         │  :8082          │
                         +--------+--------+
                                  │
                    ┌─────────────┼─────────────┐
                    │             │             │
              ┌─────▼──────┐ ┌───▼────────┐ ┌──▼──────────┐
              │ User Svc   │ │ Fund Xfer  │ │ Util Payment│
              │ :8083      │ │ :8084      │ │ :8085       │
              └─────┬──────┘ └───┬────────┘ └──┬──────────┘
                    │            │             │
                    └────────────┼─────────────┘
                                 │  OpenFeign (sync HTTP)
                         ┌───────▼───────┐
                         │ Core Banking  │
                         │ :8092         │
                         └───────────────┘
```

- **Synchronous (HTTP/REST via OpenFeign):** All inter-service calls are synchronous. The three business-facing services (User, Fund Transfer, Utility Payment) call Core Banking via OpenFeign clients using Eureka service discovery.
- **Service Discovery:** All services register with Netflix Eureka. Feign clients use logical service names (e.g., `core-banking-service`) resolved via Eureka.
- **Configuration:** All services fetch configuration at bootstrap from the Config Server, which pulls from a Git repository (`internet-banking-microservices-configurations`).
- **Asynchronous (RabbitMQ):** Mentioned in the README for notification events but **not implemented** in the current codebase. No RabbitMQ producer or consumer code exists.

### 1.3 Infrastructure Components

| Component | Image / Version | Purpose | Docker IP |
|-----------|----------------|---------|-----------|
| MySQL 8 | Custom Dockerfile | Primary datastore for all business services | 172.25.0.9 |
| PostgreSQL 15 | `postgres:15` | Keycloak identity store | 172.25.0.10 |
| Keycloak 23.0.7 | `quay.io/keycloak/keycloak:23.0.7` | IAM: OAuth2/OIDC provider | 172.25.0.11 |
| Zipkin 3 | `openzipkin/zipkin:3` | Distributed tracing collector | 172.25.0.12 |

All infrastructure runs on a Docker bridge network (`javatodev_ib_network`, subnet `172.25.0.0/16`) with fixed IPs.

### 1.4 Bootstrap Order

Services use `wait-for-it.sh` scripts in Docker entrypoints to enforce startup ordering:

1. **Config Server** and **Service Registry** start first (no dependencies).
2. Business services wait for Service Registry (:8081) + Config Server (:8090) + MySQL (:3306) before launching.
3. API Gateway waits for Service Registry + Config Server.

---

## 2. Data Model Documentation

### 2.1 Core Banking Service (MySQL: `banking_core_service`)

The core banking service uses **Flyway** for schema migration.

#### Entities

**`banking_core_user`**

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `first_name` | VARCHAR(255) | |
| `last_name` | VARCHAR(255) | |
| `email` | VARCHAR(255) | |
| `identification_number` | VARCHAR(255) | NIC/National ID |

**`banking_core_account`**

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `number` | VARCHAR(255) | Account number |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `ACTIVE` |
| `actual_balance` | DECIMAL(19,2) | |
| `available_balance` | DECIMAL(19,2) | |
| `user_id` | BIGINT | FK -> `banking_core_user.id` |

**`banking_core_utility_account`**

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `number` | VARCHAR(255) | Provider account number |
| `provider_name` | VARCHAR(255) | e.g., VODAFONE, AIRTEL |

**`banking_core_transaction`**

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `amount` | DECIMAL(19,2) | Signed (negative = debit) |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Target account or ref |
| `transaction_id` | VARCHAR(50) | UUID correlation ID |
| `account_id` | BIGINT | FK -> `banking_core_account.id` |

#### Relationships
```
banking_core_user  1───*  banking_core_account  1───1  banking_core_transaction
                                                         (OneToOne in JPA)
banking_core_utility_account  (standalone, no FK relationships)
```

### 2.2 Internet Banking User Service (MySQL)

**`user`** (JPA-managed, no Flyway)

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `auth_id` | VARCHAR | Keycloak user UUID |
| `identification` | VARCHAR | Maps to core banking NIC |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED` |
| `created_by` | VARCHAR | Audit field |
| `created_at` | DATETIME | Audit field |
| `updated_by` | VARCHAR | Audit field |
| `updated_at` | DATETIME | Audit field |

Extends `AuditAware` base class with `@EntityListeners(AuditingEntityListener.class)`.

### 2.3 Internet Banking Fund Transfer Service (MySQL)

**`fund_transfer`** (JPA-managed, no Flyway)

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `transaction_reference` | VARCHAR | UUID from core banking |
| `status` | VARCHAR | Enum: `PENDING`, `SUCCESS` |
| `created_by` | VARCHAR | Audit field |
| `created_at` | DATETIME | Audit field |
| `updated_by` | VARCHAR | Audit field |
| `updated_at` | DATETIME | Audit field |

### 2.4 Internet Banking Utility Payment Service (MySQL)

**`utility_payment`** (JPA-managed, no Flyway)

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `provider_id` | BIGINT | Utility provider reference |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Customer reference |
| `account` | VARCHAR | Source account number |
| `transaction_id` | VARCHAR | UUID from core banking |
| `status` | VARCHAR | Enum: `PROCESSING`, `SUCCESS` |
| `created_by` | VARCHAR | Audit field |
| `created_at` | DATETIME | Audit field |
| `updated_by` | VARCHAR | Audit field |
| `updated_at` | DATETIME | Audit field |

---

## 3. API Surface Map

### 3.1 API Gateway Routes

All client traffic enters via the Gateway on `:8082`. Route prefixes:

| Prefix | Target Service |
|--------|---------------|
| `/user/**` | `internet-banking-user-service` |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` |
| `/utility-payment/**` | `internet-banking-utility-payment-service` |
| `/banking-core/**` | `core-banking-service` |

**Security:**
- `/user/api/v1/bank-users/register` - **Permitted** (no auth)
- `/actuator/**` (all services) - **Permitted**
- All other endpoints - **Require JWT** (Keycloak OAuth2)

### 3.2 Core Banking Service (`:8092`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | - | `BankAccount` (id, number, type, status, availableBalance, actualBalance, user) |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | - | `UtilityAccount` (id, number, providerName) |
| `GET` | `/api/v1/user/{identification}` | Get user by identification (NIC) | - | `User` (id, firstName, lastName, email, identificationNumber, bankAccounts[]) |
| `GET` | `/api/v1/user` | List users (paginated) | `?page=&size=&sort=` | `List<User>` |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer | `FundTransferRequest` (fromAccount, toAccount, amount) | `FundTransferResponse` (message, transactionId) |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `UtilityPaymentRequest` (providerId, amount, referenceNumber, account) | `UtilityPaymentResponse` (message, transactionId) |

### 3.3 Internet Banking User Service (`:8083`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/bank-users/register` | Register new user | `User` (firstName, lastName, email, password, identification) | `User` |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user (approve/reject) | `UserUpdateRequest` (status) | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | `?page=&size=` | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | - | `User` |

### 3.4 Internet Banking Fund Transfer Service (`:8084`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `FundTransferRequest` (fromAccount, toAccount, amount) | `FundTransferResponse` (message, transactionId) |
| `GET` | `/api/v1/transfer` | List fund transfers (paginated) | `?page=&size=` | `List<FundTransfer>` |

### 3.5 Internet Banking Utility Payment Service (`:8085`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `UtilityPaymentRequest` (providerId, amount, referenceNumber, account) | `UtilityPaymentResponse` (message, transactionId) |
| `GET` | `/api/v1/utility-payment` | List utility payments (paginated) | `?page=&size=` | `List<UtilityPayment>` |

### 3.6 Infrastructure Endpoints

| Service | Endpoint | Purpose |
|---------|----------|---------|
| All business services | `/actuator/info` | Application info (git properties) |
| All business services | `/actuator/health` | Health check |
| Service Registry | `/eureka/apps` | Registered services |
| Config Server | `/{application}/{profile}` | Configuration properties |

### 3.7 OpenAPI / Swagger

All business services include `springdoc-openapi-starter-webflux-ui:2.1.0` and annotate controllers with `@Tag` and `@Operation`. Swagger UI should be available at `/swagger-ui.html` on each service, though the webflux-ui variant may conflict with the servlet-based stack (see Gap Analysis).

---

## 4. Key Business Logic Inventory

### 4.1 User Registration Flow

1. Client calls `POST /api/v1/bank-users/register` (via Gateway, no auth required).
2. User Service checks if email already exists in Keycloak.
3. User Service calls Core Banking (`GET /api/v1/user/{identification}`) via Feign to validate the NIC exists.
4. Validates email matches the core banking record.
5. Creates user in Keycloak (disabled, email unverified) with provided password.
6. Saves local user record with `status=PENDING` and Keycloak `authId`.
7. Admin later calls `PATCH /api/v1/bank-users/update/{id}` with `status=APPROVED` to enable the Keycloak account.

### 4.2 Fund Transfer Flow

1. Client calls `POST /api/v1/transfer` (via Gateway, JWT required).
2. Fund Transfer Service saves a local `FundTransferEntity` with `status=PENDING`.
3. Fund Transfer Service calls Core Banking (`POST /api/v1/transaction/fund-transfer`) via Feign.
4. Core Banking validates source account balance >= transfer amount.
5. Core Banking debits source account, credits destination account.
6. Core Banking creates two `TransactionEntity` records (debit + credit) with shared `transactionId`.
7. Fund Transfer Service updates local entity with `transactionReference` and `status=SUCCESS`.

**Balance validation rule:** `actualBalance >= 0 AND actualBalance >= amount`

### 4.3 Utility Payment Flow

1. Client calls `POST /api/v1/utility-payment` (via Gateway, JWT required).
2. Utility Payment Service saves a local `UtilityPaymentEntity` with `status=PROCESSING`.
3. Utility Payment Service calls Core Banking (`POST /api/v1/transaction/util-payment`) via Feign.
4. Core Banking validates source account balance.
5. Core Banking debits source account and records a `UTILITY_PAYMENT` transaction.
6. Utility Payment Service updates local entity with `transactionId` and `status=SUCCESS`.

### 4.4 Transaction Types

| Type | Description |
|------|-------------|
| `FUND_TRANSFER` | Internal account-to-account movement |
| `UTILITY_PAYMENT` | Payment to a utility provider |

### 4.5 Account & Transaction Status Enums

| Enum | Values |
|------|--------|
| `AccountType` | `SAVINGS_ACCOUNT` |
| `AccountStatus` | `ACTIVE` |
| `TransactionStatus` (Fund Transfer) | `PENDING`, `SUCCESS` |
| `TransactionStatus` (Utility Payment) | `PROCESSING`, `SUCCESS` |
| `TransactionType` (Core Banking) | `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `Status` (User) | `PENDING`, `APPROVED` |

---

## 5. Integration Points

### 5.1 Keycloak

- **Version:** 23.0.7
- **Integration:** User Service uses `keycloak-admin-client:24.0.4` to manage users in a Keycloak realm.
- **Authentication flow:** `client_credentials` grant type.
- **Configuration properties:** `app.config.keycloak.server-url`, `app.config.keycloak.realm`, `app.config.keycloak.clientId`, `app.config.keycloak.client-secret` (supplied via Config Server).
- **Gateway integration:** JWT validation via `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`.
- **Realm import:** `docker-compose/keycloak/realm-export.json` is auto-imported on container start.
- **Singleton pattern:** `KeycloakProperties` holds a static `Keycloak` client instance (not thread-safe, see Gap Analysis).

### 5.2 RabbitMQ

- **Status:** Referenced in README but **not implemented**. No RabbitMQ dependency in any `build.gradle`. No producer/consumer code exists.
- **Intended use:** Notification events from Fund Transfer and Utility Payment services.

### 5.3 Zipkin (Distributed Tracing)

- **Version:** Zipkin 3 (via Docker)
- **Client library:** Micrometer Tracing Bridge Brave (`io.micrometer:micrometer-tracing-bridge-brave`) + Zipkin Reporter (`io.zipkin.reporter2:zipkin-reporter-brave`).
- **Scope:** All business services and the API Gateway include tracing dependencies.
- **Configuration:** Trace export URI configured via Config Server.

### 5.4 Database Connections

| Service | Database | Connector |
|---------|----------|-----------|
| Core Banking | MySQL (shared `banking_core_service` schema) | `mysql-connector-j:8.4.0` |
| User Service | MySQL (same server, separate schema via JPA `spring.jpa.hibernate.ddl-auto`) | `mysql-connector-j:8.4.0` |
| Fund Transfer Service | MySQL (same server) | `mysql-connector-j:8.4.0` |
| Utility Payment Service | MySQL (same server) | `mysql-connector-j:8.4.0` |
| Keycloak | PostgreSQL (dedicated) | Internal |

All four business services connect to the **same MySQL instance** (`mysql_javatodev_app` on port 3306). Database names, credentials, and JPA settings are managed by Config Server.

### 5.5 Inter-Service Communication (OpenFeign)

| Caller | Feign Client | Target Service | Endpoints Called |
|--------|-------------|---------------|-----------------|
| User Service | `BankingCoreRestClient` | `core-banking-service` | `GET /api/v1/user/{identification}` |
| Fund Transfer Service | `BankingCoreFeignClient` | `core-banking-service` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/fund-transfer` |
| Utility Payment Service | `BankingCoreRestClient` | `core-banking-service` | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/util-payment` |

### 5.6 Auth Propagation

The API Gateway extracts the authenticated principal name and forwards it as the `X-Auth-Id` HTTP header to downstream services. Business services use a servlet `Filter` (`AppAuthUserFilter`) to read this header and store it in a `ThreadLocal` (`ApiRequestContextHolder`) for audit purposes.

---

## 6. Build and Deployment Pipeline Summary

### 6.1 Build System

- **Build tool:** Gradle (per-service, no root `build.gradle` or multi-project setup)
- **Each service** is a standalone Gradle project with its own `build.gradle`, `gradlew`, and `settings.gradle`.
- **No shared library or common module** exists; exception classes, DTOs, mappers, and config classes are copy-pasted across services.
- **Plugin:** `com.gorylenko.gradle-git-properties` embeds git commit info into `actuator/info` (on most services).

### 6.2 Docker Build

Each service has a `Dockerfile`:
```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh
RUN apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

**Build workflow:**
1. `./gradlew bootJar` (in each service directory)
2. `docker build -t javatodev/<service-name> .`
3. `docker-compose up -d` (from `docker-compose/`)

### 6.3 Docker Compose

- **`docker-compose.yml`:** Full stack (infrastructure + all microservices)
- **`docker-compose-support-apps.yml`:** Infrastructure only (Zipkin, Keycloak, MySQL, Config Server, Service Registry)
- **Network:** Static IPs on bridge network `172.25.0.0/16`
- **MySQL init:** Custom `Dockerfile` + `privileges.sql` for schema/user setup
- **Keycloak init:** Realm JSON auto-imported from volume mount

### 6.4 CI/CD

- **GitHub Actions:** No workflow files present (`.github/` only contains `FUNDING.yml`).
- **No CI pipeline** is configured. No automated testing, building, or deployment automation exists.

### 6.5 Test Infrastructure

- **Test framework:** JUnit 5 via `spring-boot-starter-test`
- **Test DB:** H2 in-memory (`com.h2database:h2:2.2.224`) for services that have test dependencies
- **Existing tests:**
  - `core-banking-service`: 3 test classes (`AccountServiceTest`, `UserServiceTest`, `TransactionServiceTest`) with ~16 unit tests using Mockito
  - Other services: Only auto-generated Spring context test classes (e.g., `InternetBankingUserServiceApplicationTests`)
