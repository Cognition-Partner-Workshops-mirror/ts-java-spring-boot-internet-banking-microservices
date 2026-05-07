# Application Knowledge Base

## 1. Architecture Overview

### System Summary

This is a **Java 21 / Spring Boot 3.2.4** internet banking application built on a microservices architecture using **Spring Cloud 2023.0.0**. It models a simplified banking system with user registration, fund transfers, and utility payments.

### Services

| Service | Port | Role |
|---|---|---|
| **internet-banking-config-server** | 8090 | Centralized configuration via Spring Cloud Config (backed by Git) |
| **internet-banking-service-registry** | 8081 | Service discovery via Netflix Eureka Server |
| **internet-banking-api-gateway** | 8082 | Edge gateway via Spring Cloud Gateway; handles routing, OAuth2/OIDC security |
| **internet-banking-user-service** | 8083 | User registration, approval workflow; integrates Keycloak + core banking |
| **internet-banking-fund-transfer-service** | 8084 | Fund transfer orchestration; delegates to core banking |
| **internet-banking-utility-payment-service** | 8085 | Utility payment orchestration; delegates to core banking |
| **core-banking-service** | 8092 | Core banking engine: accounts, users, transactions, balance management |

### Communication Patterns

```
                          ┌──────────────────────┐
                          │   API Gateway (:8082) │
                          │  (OAuth2 + Routing)   │
                          └──────┬───────────────┘
                                 │ HTTP (Eureka-resolved)
              ┌──────────────────┼──────────────────┐
              │                  │                   │
     ┌────────▼───────┐  ┌──────▼─────────┐  ┌─────▼──────────────┐
     │  User Service   │  │ Fund Transfer  │  │ Utility Payment    │
     │    (:8083)      │  │   (:8084)      │  │    (:8085)         │
     └───────┬─────────┘  └──────┬─────────┘  └─────┬─────────────┘
             │ Feign              │ Feign              │ Feign
             ▼                   ▼                    ▼
     ┌───────────────────────────────────────────────────────┐
     │              Core Banking Service (:8092)              │
     │         (Accounts, Transactions, Users)                │
     └───────────────────────────────────────────────────────┘
```

- **Synchronous REST (OpenFeign):** All inter-service calls use Spring Cloud OpenFeign with Eureka service discovery. The user, fund-transfer, and utility-payment services call the core-banking-service via Feign clients.
- **Service Discovery:** Netflix Eureka Server provides service registration and lookup.
- **Centralized Config:** Spring Cloud Config Server fetches configuration from a remote Git repository (`internet-banking-microservices-configurations`).
- **API Gateway:** Spring Cloud Gateway routes external traffic to internal services, enforcing OAuth2/OIDC security via Keycloak.
- **Distributed Tracing:** Micrometer Tracing with Brave bridge exports spans to Zipkin.

### Infrastructure Components

| Component | Technology | Purpose |
|---|---|---|
| Database | MySQL 8 | Primary datastore for all business services |
| Identity Provider | Keycloak 23.0.7 (PostgreSQL-backed) | OAuth2/OIDC authentication and user management |
| Distributed Tracing | Zipkin 3 | Trace collection and visualization |
| Service Registry | Netflix Eureka | Service discovery |
| Config Store | Git repository | Centralized externalized configuration |
| Containerization | Docker / Docker Compose | Deployment orchestration |

---

## 2. Data Model Documentation

### Core Banking Service

#### `banking_core_user`

| Field | Type | Description |
|---|---|---|
| `id` | Long (PK, auto) | Primary key |
| `firstName` | String | User's first name |
| `lastName` | String | User's last name |
| `email` | String | Email address |
| `identificationNumber` | String | National ID or identification number |

**Relationships:** One-to-Many with `banking_core_account`

#### `banking_core_account`

| Field | Type | Description |
|---|---|---|
| `id` | Long (PK, auto) | Primary key |
| `number` | String | Account number |
| `type` | Enum (`SAVINGS_ACCOUNT`, `FIXED_DEPOSIT`, `LOAN_ACCOUNT`) | Account type |
| `status` | Enum (`PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED`) | Account status |
| `availableBalance` | BigDecimal | Available balance |
| `actualBalance` | BigDecimal | Actual balance |
| `user_id` | FK -> `banking_core_user.id` | Owning user |

#### `banking_core_transaction`

| Field | Type | Description |
|---|---|---|
| `id` | Long (PK, auto) | Primary key |
| `amount` | BigDecimal | Transaction amount (negative for debits) |
| `transactionType` | Enum (`FUND_TRANSFER`, `UTILITY_PAYMENT`) | Transaction type |
| `referenceNumber` | String | Reference (destination account or utility ref) |
| `transactionId` | String | UUID-based transaction identifier |
| `account_id` | FK -> `banking_core_account.id` | Associated account |

#### `banking_core_utility_account`

| Field | Type | Description |
|---|---|---|
| `id` | Long (PK, auto) | Primary key |
| `number` | String | Utility account number |
| `providerName` | String | Utility provider name |

### Internet Banking User Service

#### `user`

| Field | Type | Description |
|---|---|---|
| `id` | Long (PK, auto) | Primary key |
| `authId` | String | Keycloak user ID |
| `identification` | String | National ID (maps to core banking user) |
| `status` | Enum (`PENDING`, `APPROVED`, `DISABLED`, `BLACKLIST`) | User approval status |
| `createdDate` | Instant | Audit: creation timestamp |
| `createdBy` | String | Audit: creator |
| `modifiedDate` | Instant | Audit: last modification timestamp |
| `modifiedBy` | String | Audit: last modifier |
| `version` | long | Optimistic locking version |

### Internet Banking Fund Transfer Service

#### `fund_transfer`

| Field | Type | Description |
|---|---|---|
| `id` | Long (PK, auto) | Primary key |
| `transactionReference` | String | Transaction ID from core banking |
| `fromAccount` | String | Source account number |
| `toAccount` | String | Destination account number |
| `amount` | BigDecimal | Transfer amount |
| `status` | Enum (`PENDING`, `PROCESSING`, `SUCCESS`, `FAILED`) | Transfer status |
| `createdDate` / `modifiedDate` | Instant | Audit timestamps |
| `version` | long | Optimistic locking version |

### Internet Banking Utility Payment Service

#### `utility_payment`

| Field | Type | Description |
|---|---|---|
| `id` | Long (PK, auto) | Primary key |
| `providerId` | Long | Utility provider ID |
| `amount` | BigDecimal | Payment amount |
| `referenceNumber` | String | Payment reference |
| `account` | String | Source account number |
| `transactionId` | String | Transaction ID from core banking |
| `status` | Enum (`PENDING`, `PROCESSING`, `SUCCESS`, `FAILED`) | Payment status |
| `createdDate` / `modifiedDate` | Instant | Audit timestamps |
| `version` | long | Optimistic locking version |

---

## 3. API Surface Map

### Core Banking Service (`:8092`)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `GET` | `/api/v1/account/bank-account/{account_number}` | - | `BankAccount` | Lookup bank account by number |
| `GET` | `/api/v1/account/util-account/{account_name}` | - | `UtilityAccount` | Lookup utility account by provider name |
| `GET` | `/api/v1/user/{identification}` | - | `User` | Read user by identification number |
| `GET` | `/api/v1/user` | Pageable params | `List<User>` | List users (paginated) |
| `POST` | `/api/v1/transaction/fund-transfer` | `FundTransferRequest { fromAccount, toAccount, amount }` | `FundTransferResponse { message, transactionId }` | Process fund transfer |
| `POST` | `/api/v1/transaction/util-payment` | `UtilityPaymentRequest { providerId, amount, referenceNumber, account }` | `UtilityPaymentResponse { message, transactionId }` | Process utility payment |

### Internet Banking User Service (`:8083`)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/bank-users/register` | `User { email, identification, password }` | `User` | Register new banking user (creates Keycloak user + local record) |
| `PATCH` | `/api/v1/bank-users/update/{id}` | `UserUpdateRequest { status }` | `User` | Update user status (approval workflow) |
| `GET` | `/api/v1/bank-users` | Pageable params | `List<User>` | List all registered users |
| `GET` | `/api/v1/bank-users/{id}` | - | `User` | Read user by ID |

### Internet Banking Fund Transfer Service (`:8084`)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/transfer` | `FundTransferRequest { fromAccount, toAccount, amount, authID }` | `FundTransferResponse { message, transactionId }` | Initiate fund transfer |
| `GET` | `/api/v1/transfer` | Pageable params | `List<FundTransfer>` | List all fund transfers |

### Internet Banking Utility Payment Service (`:8085`)

| Method | Endpoint | Request Body | Response | Description |
|---|---|---|---|---|
| `POST` | `/api/v1/utility-payment` | `UtilityPaymentRequest { providerId, amount, referenceNumber, account }` | `UtilityPaymentResponse { message, transactionId }` | Process utility payment |
| `GET` | `/api/v1/utility-payment` | Pageable params | `List<UtilityPayment>` | List all utility payments |

### API Gateway (`:8082`)

The gateway routes all external requests to internal services. Routes are configured via Spring Cloud Config (external Git repo). It enforces OAuth2/OIDC authentication via Keycloak before forwarding requests.

---

## 4. Key Business Logic Inventory

### User Registration Flow (`internet-banking-user-service`)

1. Check if email already exists in Keycloak. If so, throw `UserAlreadyRegisteredException`.
2. Call core-banking-service via Feign to verify the identification number exists.
3. Validate that the email matches the core banking record. If not, throw `InvalidEmailException`.
4. Create user in Keycloak (disabled, email unverified) with the provided password.
5. If Keycloak returns HTTP 201, save user locally with `PENDING` status.
6. If user not found in core banking, throw `InvalidBankingUserException`.

### User Approval Flow (`internet-banking-user-service`)

1. Admin updates user status via `PATCH /api/v1/bank-users/update/{id}`.
2. If new status is `APPROVED`, enable the Keycloak user and mark email as verified.
3. Update local user entity status.

### Fund Transfer Flow

1. **internet-banking-fund-transfer-service** receives transfer request.
2. Creates a local `FundTransferEntity` with `PENDING` status.
3. Delegates to **core-banking-service** via Feign (`POST /api/v1/transaction/fund-transfer`).
4. **core-banking-service**:
   - Reads both source and destination accounts.
   - Validates source account has sufficient balance.
   - Debits source account (`actualBalance -= amount`, `availableBalance = actualBalance - amount`).
   - Credits destination account (`actualBalance += amount`, `availableBalance = actualBalance + amount`).
   - Creates two transaction records (debit and credit) with a shared `transactionId`.
5. Fund transfer service updates local entity to `SUCCESS` with the transaction reference.

### Utility Payment Flow

1. **internet-banking-utility-payment-service** receives payment request.
2. Creates local `UtilityPaymentEntity` with `PROCESSING` status.
3. Delegates to **core-banking-service** via Feign (`POST /api/v1/transaction/util-payment`).
4. **core-banking-service**:
   - Reads source bank account and validates balance.
   - Reads utility account by provider ID.
   - Debits source account balance.
   - Creates a transaction record with `UTILITY_PAYMENT` type.
5. Utility payment service updates local entity to `SUCCESS` with transaction ID.

### Balance Validation Rule

- Balance check: `actualBalance >= 0 AND actualBalance >= transferAmount`
- On failure: `InsufficientFundsException` with error code `BANKING-CORE-SERVICE-1001`

---

## 5. Integration Points

### Keycloak (Identity Provider)

- **Version:** 23.0.7
- **Connection:** `internet-banking-user-service` connects via `keycloak-admin-client:24.0.4`
- **Configuration:** Server URL, realm, client ID, and client secret via `app.config.keycloak.*` properties (externalized in Config Server)
- **Grant Type:** `client_credentials`
- **Operations:** Create user, read user by email, read user by ID, update user (enable/verify)
- **API Gateway:** OAuth2 resource server and client configuration for token validation

### Zipkin (Distributed Tracing)

- **Version:** Zipkin 3
- **Integration:** Micrometer Tracing with Brave bridge (`micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`)
- **Port:** 9411
- **Coverage:** All 5 business services + API gateway include tracing dependencies

### MySQL (Database)

- **Connection:** All business services (core-banking, user, fund-transfer, utility-payment) connect to a shared MySQL instance
- **Port:** 3306 (Docker network: `172.25.0.9`)
- **Root Password:** Configured in `docker-compose.yml`
- **Schema Migrations:** Core banking service uses Flyway (`flyway-core:10.12.0`, `flyway-mysql:10.12.0`)

### RabbitMQ (Message Broker)

- **Status:** Referenced in README as part of the architecture for notification messages, but **not implemented** in the current codebase.
- **Intended Use:** Fund transfer and utility payment services would publish notification events to RabbitMQ for a Notification service to consume.
- **Notification Service:** Listed as "PENDING Development" in the README.

### Spring Cloud Config Server

- **Git URI:** `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Search Path:** `configuration`
- **Branch:** `main`
- **Consumers:** All services except the config server itself fetch config via bootstrap profiles (`localhost:8090` / `internet-banking-config-server:8090` for Docker)

### Netflix Eureka (Service Registry)

- **Port:** 8081
- **All services** register as Eureka clients
- **Feign clients** use service names (e.g., `core-banking-service`) resolved via Eureka

---

## 6. Build and Deployment Pipeline Summary

### Build System

- **Build Tool:** Gradle (each service has its own `build.gradle`, no multi-project build)
- **Java Version:** 21
- **Spring Boot:** 3.2.4
- **Spring Cloud:** 2023.0.0
- **Git Properties Plugin:** `com.gorylenko.gradle-git-properties:2.4.2` (on most services)

### Docker Deployment

- **Primary:** `docker-compose/docker-compose.yml` — Full stack (all 7 services + MySQL + Keycloak + Zipkin)
- **Support Only:** `docker-compose/docker-compose-support-apps.yml` — Infrastructure only (Zipkin, Keycloak, MySQL, Config Server, Service Registry)
- **Network:** Custom bridge network `javatodev_ib_network` (`172.25.0.0/16`) with static IPs
- **Startup Ordering:** `wait-for-it.sh` scripts ensure services wait for Config Server, Service Registry, and MySQL before starting
- **Profiles:** Services use `-Dspring.profiles.active=docker` when running in Docker, which activates `bootstrap-docker.yml` (points config URI to Docker hostname)

### Key Dependencies Per Service

| Service | Notable Dependencies |
|---|---|
| core-banking-service | Spring Data JPA, Flyway, MySQL Connector, springdoc-openapi, Lombok |
| internet-banking-user-service | Spring Data JPA, OpenFeign, Keycloak Admin Client, MySQL Connector, springdoc-openapi |
| internet-banking-fund-transfer-service | Spring Data JPA, OpenFeign, MySQL Connector, springdoc-openapi |
| internet-banking-utility-payment-service | Spring Data JPA, OpenFeign, MySQL Connector, springdoc-openapi |
| internet-banking-api-gateway | Spring Cloud Gateway, OAuth2 Client/Resource Server, Spring Security, WebFlux |
| internet-banking-service-registry | Spring Cloud Netflix Eureka Server |
| internet-banking-config-server | Spring Cloud Config Server |

### CI/CD

- No CI/CD pipelines are present in the repository (GitHub Actions workflows were removed).
- Deployment relies entirely on Docker Compose for local/development environments.
- No Kubernetes manifests are present despite K8s being listed in the technology stack.
