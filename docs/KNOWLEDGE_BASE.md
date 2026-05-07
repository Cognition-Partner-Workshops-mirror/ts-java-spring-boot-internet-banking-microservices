# Application Knowledge Base

## 1. Architecture Overview

### System Summary

Internet Banking Concept is a Java 21 / Spring Boot 3.2.4 microservices application implementing an internet banking API. It uses Spring Cloud 2023.0.0 for service orchestration and follows a gateway-routed architecture where all external traffic enters through a single API Gateway.

### Services

| Service | Port | Role |
|---------|------|------|
| **internet-banking-api-gateway** | 8082 | Entry point for all API traffic. Routes requests to downstream services, enforces OAuth2/JWT authentication via Keycloak. |
| **internet-banking-service-registry** | 8081 | Netflix Eureka server. All services register here for service discovery. |
| **internet-banking-config-server** | 8090 | Spring Cloud Config server. Serves centralized configuration from a [Git repository](https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git). |
| **core-banking-service** | 8092 | Core banking engine. Manages users, bank accounts, utility accounts, and processes fund transfers and utility payments at the database level. |
| **internet-banking-user-service** | 8083 | User registration and management. Integrates with Keycloak for identity management and with core-banking-service for user validation. |
| **internet-banking-fund-transfer-service** | 8084 | Orchestrates fund transfers. Persists transfer records locally, delegates actual balance operations to core-banking-service via Feign. |
| **internet-banking-utility-payment-service** | 8085 | Orchestrates utility payments. Persists payment records locally, delegates actual balance operations to core-banking-service via Feign. |

> **Note:** A Notification Service is referenced in the README as "PENDING Development" and is not implemented.

### Communication Patterns

```
                        ┌─────────────────────┐
                        │   Keycloak (IdP)     │
                        │   Port 8080          │
                        └────────┬────────────┘
                                 │ JWT validation
┌──────────┐   HTTP    ┌────────▼────────────┐
│  Client   │─────────▶│  API Gateway (8082) │
└──────────┘           └────────┬────────────┘
                                │ Routes via Eureka
              ┌─────────────────┼─────────────────┐
              │                 │                  │
     ┌────────▼──────┐ ┌───────▼────────┐ ┌──────▼───────────┐
     │ User Service  │ │ Fund Transfer  │ │ Utility Payment  │
     │   (8083)      │ │ Service (8084) │ │ Service (8085)   │
     └───────┬───────┘ └───────┬────────┘ └──────┬───────────┘
             │                 │                  │
             │  OpenFeign      │  OpenFeign       │  OpenFeign
             │                 │                  │
     ┌───────▼─────────────────▼──────────────────▼──────┐
     │              Core Banking Service (8092)           │
     └───────────────────────┬───────────────────────────┘
                             │
                    ┌────────▼────────┐
                    │  MySQL (3306)   │
                    └─────────────────┘
```

- **Synchronous (HTTP/REST):** All inter-service communication uses OpenFeign clients resolved via Eureka service discovery.
- **Asynchronous (RabbitMQ):** Referenced in the README for notification messages but not implemented in the current codebase.
- **Service Discovery:** Netflix Eureka (register + discover).
- **Centralized Configuration:** Spring Cloud Config Server backed by a Git repository.
- **Distributed Tracing:** Micrometer Tracing with Brave bridge, reporting to Zipkin.

### Infrastructure Components

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Database | MySQL 8.4.0 | Persistent storage for all business services |
| Identity Provider | Keycloak 23.0.7 (PostgreSQL 15 backend) | OAuth2/OIDC authentication and user management |
| Distributed Tracing | Zipkin 3 | Trace collection and visualization |
| Service Registry | Netflix Eureka | Service discovery |
| Config Server | Spring Cloud Config | Centralized externalized configuration |
| Message Broker | RabbitMQ | Planned for notifications (not yet implemented) |

---

## 2. Data Model Documentation

### Core Banking Service

This service owns the primary banking data model using Flyway for schema migrations.

#### `banking_core_user`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `first_name` | VARCHAR(255) | User's first name |
| `last_name` | VARCHAR(255) | User's last name |
| `email` | VARCHAR(255) | User's email address |
| `identification_number` | VARCHAR(255) | National identification number |

#### `banking_core_account`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `number` | VARCHAR(255) | Account number (e.g., `100015003000`) |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `ACTIVE` |
| `actual_balance` | DECIMAL(19,2) | Current actual balance |
| `available_balance` | DECIMAL(19,2) | Available balance |
| `user_id` | BIGINT (FK → `banking_core_user.id`) | Account owner |

#### `banking_core_utility_account`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `number` | VARCHAR(255) | Utility provider account number |
| `provider_name` | VARCHAR(255) | Provider name (e.g., VODAFONE, VERIZON) |

#### `banking_core_transaction`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `amount` | DECIMAL(19,2) | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Reference (target account or ref number) |
| `transaction_id` | VARCHAR(50) | UUID transaction identifier |
| `account_id` | BIGINT (FK → `banking_core_account.id`) | Associated account |

#### Entity Relationships
```
banking_core_user (1) ──── (N) banking_core_account
banking_core_account (1) ──── (N) banking_core_transaction
banking_core_utility_account (standalone)
```

### Internet Banking User Service

#### `user` (entity: `UserEntity extends AuditAware`)
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `auth_id` | VARCHAR | Keycloak user ID |
| `identification` | VARCHAR | National ID (links to core banking) |
| `status` | VARCHAR (Enum) | `PENDING`, `APPROVED` |
| `created_date` | INSTANT | Audit: creation timestamp |
| `created_by` | VARCHAR | Audit: creator |
| `modified_date` | INSTANT | Audit: last modified timestamp |
| `modified_by` | VARCHAR | Audit: last modifier |
| `version` | LONG | Optimistic locking version |

### Internet Banking Fund Transfer Service

#### `fund_transfer` (entity: `FundTransferEntity extends AuditAware`)
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `transaction_reference` | VARCHAR | Core banking transaction ID |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `status` | VARCHAR (Enum) | `PENDING`, `SUCCESS`, `PROCESSING` |
| Audit fields | (inherited) | `created_date`, `created_by`, `modified_date`, `modified_by`, `version` |

### Internet Banking Utility Payment Service

#### `utility_payment` (entity: `UtilityPaymentEntity extends AuditAware`)
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Primary key |
| `provider_id` | LONG | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Customer reference (e.g., phone number) |
| `account` | VARCHAR | Source bank account number |
| `transaction_id` | VARCHAR | Core banking transaction ID |
| `status` | VARCHAR (Enum) | `PROCESSING`, `SUCCESS` |
| Audit fields | (inherited) | `created_date`, `created_by`, `modified_date`, `modified_by`, `version` |

### Enums

| Enum | Values | Service |
|------|--------|---------|
| `AccountType` | `SAVINGS_ACCOUNT` | core-banking-service |
| `AccountStatus` | `ACTIVE` | core-banking-service |
| `TransactionType` | `FUND_TRANSFER`, `UTILITY_PAYMENT` | core-banking-service |
| `TransactionStatus` | `PENDING`, `PROCESSING`, `SUCCESS` | fund-transfer, utility-payment |
| `Status` | `PENDING`, `APPROVED` | user-service |

---

## 3. API Surface Map

### Core Banking Service (Port 8092)

Base path: `/api/v1`

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | — | `BankAccount` (id, number, type, status, availableBalance, actualBalance, user) |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | — | `UtilityAccount` (id, number, providerName) |
| `GET` | `/api/v1/user/{identification}` | Get user by identification number | — | `User` (id, firstName, lastName, email, identificationNumber, bankAccounts[]) |
| `GET` | `/api/v1/user` | List users (paginated) | Pageable params | `List<User>` |
| `POST` | `/api/v1/transaction/fund-transfer` | Process fund transfer | `{ fromAccount, toAccount, amount }` | `{ message, transactionId }` |
| `POST` | `/api/v1/transaction/util-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |

### Internet Banking User Service (Port 8083)

Gateway prefix: `/user`

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/bank-users/register` | Register new user | `{ email, identification, password }` | `User` (id, email, identification, authId, status) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user (approve/change status) | `{ status }` | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | Pageable params | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | — | `User` |

### Internet Banking Fund Transfer Service (Port 8084)

Gateway prefix: `/fund-transfer`

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `{ fromAccount, toAccount, amount, authID }` | `{ message, transactionId }` |
| `GET` | `/api/v1/transfer` | List transfers (paginated) | Pageable params | `List<FundTransfer>` |

### Internet Banking Utility Payment Service (Port 8085)

Gateway prefix: `/payment`

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `{ providerId, amount, referenceNumber, account }` | `{ message, transactionId }` |
| `GET` | `/api/v1/utility-payment` | List payments (paginated) | Pageable params | `List<UtilityPayment>` |

### API Gateway Routes (Port 8082)

All requests go through the API Gateway which applies JWT authentication:

| Route Prefix | Target Service | Auth Required |
|-------------|---------------|---------------|
| `/user/**` | internet-banking-user-service | Yes (except `/user/api/v1/bank-users/register`) |
| `/fund-transfer/**` | internet-banking-fund-transfer-service | Yes |
| `/payment/**` | internet-banking-utility-payment-service | Yes |
| `/core/**` | core-banking-service | Yes |
| `/actuator/**` | (gateway self) | No |
| `/{service}/actuator/**` | (respective services) | No |

### Actuator Endpoints

All services expose Spring Boot Actuator endpoints (health, info, etc.) at `/actuator/**`.

---

## 4. Key Business Logic Inventory

### User Registration Flow

1. Client sends `POST /user/api/v1/bank-users/register` with `{ email, identification, password }`.
2. User Service checks if email is already registered in Keycloak → throws `UserAlreadyRegisteredException` if exists.
3. User Service calls Core Banking Service via Feign (`GET /api/v1/user/{identification}`) to validate the user exists in the banking core.
4. If the user exists, validates that the email matches the core banking record → throws `InvalidEmailException` if mismatch.
5. Creates a Keycloak user with `enabled=false`, `emailVerified=false`.
6. On successful Keycloak creation (HTTP 201), persists a local `UserEntity` with `status=PENDING`.
7. If the user is not found in core banking → throws `InvalidBankingUserException`.

### User Approval Flow

1. Admin sends `PATCH /user/api/v1/bank-users/update/{id}` with `{ status: "APPROVED" }`.
2. If status is `APPROVED`, the Keycloak user is updated to `enabled=true`, `emailVerified=true`.
3. Local entity status is updated to `APPROVED`.

### Fund Transfer Flow

1. Client sends `POST /fund-transfer/api/v1/transfer` with `{ fromAccount, toAccount, amount }`.
2. Fund Transfer Service creates a local `FundTransferEntity` with `status=PENDING`.
3. Delegates to Core Banking Service via Feign (`POST /api/v1/transaction/fund-transfer`).
4. Core Banking Service:
   - Reads both accounts, validates sufficient balance.
   - Debits the source account (`actualBalance -= amount`, `availableBalance = actualBalance - amount`).
   - Credits the destination account (`actualBalance += amount`, `availableBalance = actualBalance + amount`).
   - Creates two transaction records (debit and credit).
   - Returns `{ message, transactionId }`.
5. Fund Transfer Service updates local entity with `transactionReference` and `status=SUCCESS`.

### Utility Payment Flow

1. Client sends `POST /payment/api/v1/utility-payment` with `{ providerId, amount, referenceNumber, account }`.
2. Utility Payment Service creates a local `UtilityPaymentEntity` with `status=PROCESSING`.
3. Delegates to Core Banking Service via Feign (`POST /api/v1/transaction/util-payment`).
4. Core Banking Service:
   - Reads the bank account, validates sufficient balance.
   - Reads the utility account by provider ID.
   - Debits the bank account.
   - Creates a transaction record.
   - Returns `{ message, transactionId }`.
5. Utility Payment Service updates local entity with `transactionId` and `status=SUCCESS`.

### Balance Validation Rules

- `actualBalance` must be >= 0.
- `actualBalance` must be >= transfer/payment `amount`.
- Throws `InsufficientFundsException` if validation fails.

---

## 5. Integration Points

### Keycloak (Identity Provider)

- **Version:** 23.0.7
- **Connection:** User Service connects via `keycloak-admin-client` SDK (version 24.0.4).
- **Configuration:** Server URL, realm, client ID, and client secret configured via `app.config.keycloak.*` properties.
- **Realm:** Imported on startup from `docker-compose/keycloak/` volume mount.
- **Singleton pattern:** `KeycloakProperties` uses a static singleton for the Keycloak client instance.
- **Operations:** Create user, update user, search by email, read user by ID.
- **Gateway integration:** API Gateway validates JWTs using `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`.

### MySQL

- **Version:** 8.4.0
- **Databases:** Four separate databases:
  - `banking_core_service` — core banking entities
  - `banking_core_fund_transfer_service` — fund transfer records
  - `banking_core_user_service` — user service entities
  - `banking_core_utility_payment_service` — utility payment records
- **Credentials:** Root password and app user credentials are defined in `docker-compose/mysql/Dockerfile` and `docker-compose/mysql/privileges.sql`.
- **Schema management:** Core Banking Service uses Flyway for migrations; other services use JPA auto-DDL (via `spring.jpa.hibernate.ddl-auto`).

### Zipkin (Distributed Tracing)

- **Version:** 3
- **Port:** 9411
- **Integration:** All business services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` dependencies.
- **Configuration:** Managed via centralized config server.

### RabbitMQ (Planned)

- Referenced in README for notification message passing.
- **Not implemented** in the current codebase. No RabbitMQ dependency in any `build.gradle`.

### Spring Cloud Config Server

- **Port:** 8090
- **Backend:** Git repository at `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` (branch: `main`, path: `configuration`).
- **Consumers:** All services except the registry use `spring-cloud-starter-config` and `spring-cloud-starter-bootstrap`.
- **Profiles:** `dev` (localhost config server), `docker` (container-name-based config server URL).

### Netflix Eureka

- **Server port:** 8081
- **Configuration:** Self-registration disabled, fetch-registry disabled (standalone mode).
- **Clients:** All business services register as Eureka clients.

---

## 6. Build and Deployment Pipeline Summary

### Build System

- **Build tool:** Gradle (per-service, no root build file / multi-module setup)
- **Java version:** 21 (Eclipse Temurin 21.0.2)
- **Spring Boot:** 3.2.4 (via `org.springframework.boot` Gradle plugin)
- **Spring Cloud:** 2023.0.0

Each service is built independently:
```bash
cd <service-directory>
./gradlew build
```

### Docker

Each service has its own `Dockerfile`:
```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh
RUN apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

- Uses `wait-for-it.sh` for startup ordering (waits for config server, service registry, and MySQL).
- Images are tagged as `javatodev/<service-name>`.

### Docker Compose

Two compose files in `docker-compose/`:

| File | Contents |
|------|----------|
| `docker-compose.yml` | Full stack: all 6 services + MySQL + Keycloak + PostgreSQL + Zipkin |
| `docker-compose-support-apps.yml` | Infrastructure only: MySQL + Keycloak + PostgreSQL + Zipkin + Config Server + Service Registry |

- Custom bridge network `javatodev_ib_network` with subnet `172.25.0.0/16`.
- Static IP addresses assigned to each container.
- Named volumes for MySQL and PostgreSQL data persistence.

### CI/CD

- No GitHub Actions workflows present (removed per commit history).
- No Kubernetes manifests present despite README mention.
- Deployment is manual via `docker-compose up -d`.

### Gradle Plugins

| Plugin | Purpose |
|--------|---------|
| `org.springframework.boot` 3.2.4 | Spring Boot application packaging |
| `io.spring.dependency-management` 1.1.4 | BOM-based dependency management |
| `com.gorylenko.gradle-git-properties` 2.4.2 | Generates `git.properties` for actuator `/info` endpoint |

### Test Data

Flyway seed migration (`V1.0.20210427174721__temp_data.sql`) pre-populates:
- 4 users with identification numbers
- 14 savings accounts with balances ranging from 12,000 to 889,000.33
- 6 utility provider accounts (VODAFONE, VERIZON, SINGTEL, HUTCH, AIRTEL, GIO)

Test credentials are documented in the project README.
