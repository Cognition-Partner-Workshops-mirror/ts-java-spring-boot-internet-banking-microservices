# Application Knowledge Base

## 1. Architecture Overview

### System Summary

This is a Java 21 / Spring Boot 3.2.4 internet banking application built using a microservices architecture with Spring Cloud 2023.0.0. The system models a simplified internet banking platform supporting user registration, fund transfers, and utility bill payments.

### Microservices Inventory

| Service | Port | Description |
|---------|------|-------------|
| **internet-banking-service-registry** | 8081 | Netflix Eureka service registry for service discovery |
| **internet-banking-config-server** | 8090 | Spring Cloud Config Server backed by a Git repository |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway with OAuth2/Keycloak security |
| **internet-banking-user-service** | 8083 | User registration/management with Keycloak integration |
| **internet-banking-fund-transfer-service** | 8084 | Fund transfer processing between bank accounts |
| **internet-banking-utility-payment-service** | 8085 | Utility bill payment processing |
| **core-banking-service** | 8092 | Core banking engine: accounts, users, transactions |

### Communication Patterns

```
┌──────────────┐
│   Client     │
└──────┬───────┘
       │ HTTP (OAuth2 JWT)
       ▼
┌──────────────────────────┐
│   API Gateway (8082)     │  ◄── Keycloak JWT validation
│   Spring Cloud Gateway   │      Routes by path prefix
└──────┬───────────────────┘
       │ HTTP (via Eureka)
       ▼
┌──────────────────────────────────────────────────────┐
│                 Business Services                     │
│                                                       │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐│
│  │ User Service │  │ Fund Transfer│  │ Utility Pay  ││
│  │   (8083)     │  │   (8084)     │  │   (8085)     ││
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘│
│         │ Feign           │ Feign           │ Feign   │
│         ▼                 ▼                 ▼         │
│  ┌──────────────────────────────────────────────────┐ │
│  │         Core Banking Service (8092)              │ │
│  │   Accounts, Users, Transactions, Utility Accts   │ │
│  └──────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────┘
```

- **Synchronous (HTTP/REST):** All inter-service communication uses Spring Cloud OpenFeign over HTTP, resolved through Eureka service discovery.
- **Service Discovery:** Netflix Eureka (`internet-banking-service-registry`) — all services register and discover each other via Eureka.
- **Centralized Config:** Spring Cloud Config Server fetches configuration from a remote Git repository (`https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`).
- **API Gateway Routing:** Spring Cloud Gateway routes requests by path prefix to downstream services.
- **Authentication Flow:** API Gateway validates JWT tokens issued by Keycloak. It extracts the principal name and forwards it as an `X-Auth-Id` header to downstream services.

### Infrastructure Components

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Database** | MySQL 8.4.0 | Primary data store for all services |
| **Identity Provider** | Keycloak 23.0.7 (PostgreSQL 15 backend) | OAuth2/OIDC authentication and user management |
| **Distributed Tracing** | Zipkin 3 + Micrometer Brave | Request tracing across services |
| **Service Discovery** | Netflix Eureka | Service registration and discovery |
| **Configuration** | Spring Cloud Config (Git-backed) | Centralized externalized configuration |
| **Containerization** | Docker / Docker Compose | Deployment and orchestration |

---

## 2. Data Model Documentation

### Core Banking Service (MySQL: `banking_core_service`)

#### `banking_core_user`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto) | Primary key |
| `first_name` | VARCHAR(255) | User's first name |
| `last_name` | VARCHAR(255) | User's last name |
| `email` | VARCHAR(255) | User's email address |
| `identification_number` | VARCHAR(255) | National ID / identification number |

#### `banking_core_account`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto) | Primary key |
| `number` | VARCHAR(255) | Account number |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED` |
| `actual_balance` | DECIMAL(19,2) | Actual account balance |
| `available_balance` | DECIMAL(19,2) | Available balance |
| `user_id` | BIGINT (FK → `banking_core_user.id`) | Account owner |

#### `banking_core_transaction`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto) | Primary key |
| `amount` | DECIMAL(19,2) | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Reference (target account number or utility ref) |
| `transaction_id` | VARCHAR(50) | UUID for the transaction |
| `account_id` | BIGINT (FK → `banking_core_account.id`) | Associated account |

#### `banking_core_utility_account`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto) | Primary key |
| `number` | VARCHAR(255) | Utility account number |
| `provider_name` | VARCHAR(255) | Utility provider name (e.g., VODAFONE, VERIZON) |

**Relationships:**
- `banking_core_user` 1:N `banking_core_account` (via `user_id`)
- `banking_core_account` 1:1 `banking_core_transaction` (via `account_id`)

### Fund Transfer Service (MySQL: `banking_core_fund_transfer_service`)

#### `fund_transfer`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto) | Primary key |
| `transaction_reference` | VARCHAR | Reference returned from core banking |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `status` | VARCHAR | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` | TIMESTAMP | Audit: creation timestamp |
| `created_by` | VARCHAR | Audit: creator (from `X-Auth-Id` header) |
| `modified_date` | TIMESTAMP | Audit: last modified timestamp |
| `modified_by` | VARCHAR | Audit: modifier |
| `version` | BIGINT | Optimistic locking version |

### User Service (MySQL: `banking_core_user_service`)

#### `user`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto) | Primary key |
| `auth_id` | VARCHAR | Keycloak user ID |
| `identification` | VARCHAR | National identification number |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST` |
| `created_date` | TIMESTAMP | Audit: creation timestamp |
| `created_by` | VARCHAR | Audit: creator |
| `modified_date` | TIMESTAMP | Audit: last modified timestamp |
| `modified_by` | VARCHAR | Audit: modifier |
| `version` | BIGINT | Optimistic locking version |

### Utility Payment Service (MySQL: `banking_core_utility_payment_service`)

#### `utility_payment`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto) | Primary key |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Payment reference |
| `account` | VARCHAR | Source bank account number |
| `transaction_id` | VARCHAR | Transaction ID from core banking |
| `status` | VARCHAR | Enum: `PENDING`, `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` | TIMESTAMP | Audit: creation timestamp |
| `created_by` | VARCHAR | Audit: creator |
| `modified_date` | TIMESTAMP | Audit: last modified timestamp |
| `modified_by` | VARCHAR | Audit: modifier |
| `version` | BIGINT | Optimistic locking version |

### Database Initialization

- **Flyway migrations** (core-banking-service only): `V1.0.20210427174638__create_base_table_structure.sql` creates the base tables, `V1.0.20210429210839__create_transaction_table.sql` adds the transaction table, and `V1.0.20210427174721__temp_data.sql` seeds test data (4 users, 14 accounts, 6 utility providers).
- **Other services**: Use JPA auto-DDL (Hibernate `ddl-auto`) for schema creation.
- **MySQL init script**: `docker-compose/mysql/privileges.sql` creates the `javatodev_development` user and the four databases.

---

## 3. API Surface Map

### API Gateway Routes (port 8082)

All requests flow through the API Gateway. Routes are prefixed and stripped before forwarding:

| Gateway Path Prefix | Target Service |
|---------------------|---------------|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |
| `/banking-core/**` | core-banking-service |

### Core Banking Service (`:8092`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `GET` | `/api/v1/user/{identification}` | Get user by national ID | — | `User` (id, firstName, lastName, email, identificationNumber, bankAccounts[]) |
| `GET` | `/api/v1/user` | List users (paginated) | — | `List<User>` |
| `GET` | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | — | `BankAccount` (id, number, type, status, actualBalance, availableBalance) |
| `GET` | `/api/v1/account/util-account/{account_name}` | Get utility account by provider | — | `UtilityAccount` (id, number, providerName) |
| `POST` | `/api/v1/transaction/fund-transfer` | Execute fund transfer | `{fromAccount, toAccount, amount}` | `{message, transactionId}` |
| `POST` | `/api/v1/transaction/util-payment` | Execute utility payment | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` |

### User Service (`:8083`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/bank-users/register` | Register new user | `{email, identification, password}` | `User` (id, email, identification, authId, status) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | Update user status | `{status}` (PENDING/APPROVED/DISABLED/BLACKLIST) | `User` |
| `GET` | `/api/v1/bank-users` | List users (paginated) | — | `List<User>` |
| `GET` | `/api/v1/bank-users/{id}` | Get user by ID | — | `User` |

### Fund Transfer Service (`:8084`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/transfer` | Initiate fund transfer | `{fromAccount, toAccount, amount, authID}` | `{message, transactionId}` |
| `GET` | `/api/v1/transfer` | List transfers (paginated) | — | `List<FundTransfer>` |

### Utility Payment Service (`:8085`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|-------------|----------|
| `POST` | `/api/v1/utility-payment` | Process utility payment | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` |
| `GET` | `/api/v1/utility-payment` | List payments (paginated) | — | `List<UtilityPayment>` |

### Infrastructure Endpoints

| Endpoint | Service | Description |
|----------|---------|-------------|
| `/actuator/**` | All services | Spring Boot Actuator health/info |
| `GET /eureka/apps` | Service Registry (8081) | Eureka dashboard and API |

---

## 4. Key Business Logic Inventory

### User Registration Flow (`UserService.createUser`)

1. Check if email is already registered in Keycloak; throw `UserAlreadyRegisteredException` if exists.
2. Call core-banking-service to verify the user's national identification number exists.
3. Validate that the provided email matches the core banking user's email; throw `InvalidEmailException` if mismatch.
4. Create a Keycloak user representation (disabled, email unverified) with provided credentials.
5. On successful Keycloak creation (HTTP 201), persist the user locally with status `PENDING`.
6. If the core banking user is not found, throw `InvalidBankingUserException`.

### User Approval Flow (`UserService.updateUser`)

1. When status is changed to `APPROVED`, the corresponding Keycloak user is enabled and email is marked as verified.
2. The local user entity status is updated accordingly.

### Fund Transfer Flow (`FundTransferService.fundTransfer`)

1. Create a local `FundTransferEntity` with status `PENDING`.
2. Delegate to core-banking-service via Feign (`BankingCoreFeignClient.fundTransfer`).
3. Core banking validates balances (throws `InsufficientFundsException` if insufficient).
4. Core banking debits the source account and credits the destination account within a `@Transactional` boundary.
5. Two `TransactionEntity` records are created (debit and credit).
6. On success, update local entity to `SUCCESS` with the transaction reference.

### Utility Payment Flow (`UtilityPaymentService.utilPayment`)

1. Create a local `UtilityPaymentEntity` with status `PROCESSING`.
2. Delegate to core-banking-service via Feign (`BankingCoreRestClient.utilityPayment`).
3. Core banking validates balance, debits the source account, creates a transaction record.
4. On success, update local entity to `SUCCESS` with the transaction ID.

### Balance Validation (`TransactionService.validateBalance`)

- Checks that `actualBalance >= 0` AND `actualBalance >= transferAmount`.
- Throws `InsufficientFundsException` with code `BANKING-CORE-SERVICE-1001` on failure.

### Audit Trail

- Fund Transfer, Utility Payment, and User services use JPA auditing (`@EnableJpaAuditing`) with `AuditAware` base class.
- Tracks `createdDate`, `createdBy`, `modifiedDate`, `modifiedBy` automatically.
- The `createdBy`/`modifiedBy` is populated from the `X-Auth-Id` HTTP header via `AppAuthUserFilter` → `ApiRequestContextHolder` → `AuditorAwareConfig`.
- Optimistic locking is enabled via `@Version` field.

---

## 5. Integration Points

### Keycloak (Identity Provider)

- **Version:** 23.0.7
- **Connection:** User service connects via `keycloak-admin-client` (v24.0.4) using client credentials grant.
- **Configuration:** `app.config.keycloak.server-url`, `app.config.keycloak.realm`, `app.config.keycloak.clientId`, `app.config.keycloak.client-secret` (externalized via Config Server).
- **Realm:** Pre-configured realm imported on startup via Docker volume mount (`docker-compose/keycloak/`).
- **Gateway Integration:** API Gateway validates JWTs using `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`.
- **Singleton Pattern:** `KeycloakProperties` uses a static singleton for the Keycloak client instance.

### MySQL Database

- **Version:** 8.4.0
- **Connection User:** `javatodev_development` / `oPItyPticIAt`
- **Root Password:** `woVERANKliGharym`
- **Databases:** `banking_core_service`, `banking_core_fund_transfer_service`, `banking_core_user_service`, `banking_core_utility_payment_service`
- **Schema Management:** Flyway for core-banking-service; JPA auto-DDL for others.

### Zipkin (Distributed Tracing)

- **Version:** 3
- **Port:** 9411
- **Integration:** All business services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` dependencies.
- **Feign Tracing:** `feign-micrometer` dependency enables trace propagation through Feign calls.

### RabbitMQ

- **Status:** Referenced in documentation as planned for notification service, but **not implemented** in the current codebase.
- No RabbitMQ dependencies exist in any `build.gradle` file.

### Spring Cloud Config Server

- **Git Repository:** `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch:** `main`
- **Search Path:** `configuration/`
- **Bootstrap:** Each service has `bootstrap.yml` (localhost), `bootstrap-dev.yml` (dev IP), and `bootstrap-docker.yml` (Docker network) profiles pointing to the config server.

### OpenAPI / Swagger

- Services include `springdoc-openapi-starter-webflux-ui` (v2.1.0) for API documentation.
- Available at `/swagger-ui.html` on each service.

---

## 6. Build and Deployment Pipeline Summary

### Build System

- **Build Tool:** Gradle (wrapper, v8.6)
- **Java Version:** 21 (Eclipse Temurin 21.0.2_13)
- **Spring Boot:** 3.2.4
- **Spring Cloud:** 2023.0.0
- **Each service is an independent Gradle project** (no multi-module Gradle build).

### Docker

- **Base Image:** `eclipse-temurin:21.0.2_13-jre-alpine`
- **Build Pattern:** Each service has its own `Dockerfile` that copies the built JAR and a `wait-for-it.sh` script.
- **Startup Orchestration:** Services use `wait-for-it.sh` to wait for the config server, service registry, and MySQL before starting.
- **Active Profile:** All Docker containers run with `-Dspring.profiles.active=docker`.

### Docker Compose

Two compose files are provided:

1. **`docker-compose.yml`** — Full stack: all infrastructure + all microservices.
2. **`docker-compose-support-apps.yml`** — Infrastructure only: Zipkin, Keycloak, MySQL, Config Server, Service Registry.

**Network:** Custom bridge network `javatodev_ib_network` with subnet `172.25.0.0/16` and static IP assignments for each container.

### Build Steps

```bash
# Build a single service
cd <service-directory>
./gradlew clean build

# Build Docker image
docker build -t javatodev/<service-name> .

# Start full stack
cd docker-compose
docker-compose up -d
```

### Test Data

Default test credentials: `ib_admin@javatodev.com` / `5V7huE3G86uB`

Seed data includes 4 users, 14 bank accounts, and 6 utility provider accounts (VODAFONE, VERIZON, SINGTEL, HUTCH, AIRTEL, GIO).

### Git Properties

Services use the `com.gorylenko.gradle-git-properties` plugin (v2.4.2) to embed Git commit info into the built artifact, exposed via Spring Boot Actuator's `/actuator/info` endpoint.
