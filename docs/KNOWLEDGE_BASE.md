# Application Knowledge Base

## 1. Architecture Overview

### System Summary

This is a Java 21 / Spring Boot 3.2.4 internet banking application built using a microservices architecture with Spring Cloud 2023.0.0. The system implements a layered banking platform with 6 independently deployable services communicating via REST (OpenFeign) and coordinated through Netflix Eureka service discovery.

### Microservices

| Service | Port | Purpose |
|---------|------|---------|
| **core-banking-service** | 8092 | Core banking engine - manages accounts, users, and transaction processing |
| **internet-banking-user-service** | 8083 | User registration and management with Keycloak integration |
| **internet-banking-fund-transfer-service** | 8084 | Fund transfer orchestration between accounts |
| **internet-banking-utility-payment-service** | 8085 | Utility bill payment processing |
| **internet-banking-api-gateway** | 8082 | API gateway with OAuth2/JWT security (Spring Cloud Gateway) |
| **internet-banking-service-registry** | 8081 | Netflix Eureka service discovery server |
| **internet-banking-config-server** | 8090 | Spring Cloud Config Server (Git-backed) |

### Communication Patterns

```
                    ┌─────────────────────┐
                    │   API Gateway       │
                    │   (Port 8082)       │
                    │   OAuth2/JWT Auth   │
                    └─────────┬───────────┘
                              │
              ┌───────────────┼───────────────┐
              │               │               │
              ▼               ▼               ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│  User Service   │ │ Fund Transfer   │ │ Utility Payment │
│  (Port 8083)    │ │ (Port 8084)     │ │ (Port 8085)     │
└────────┬────────┘ └────────┬────────┘ └────────┬────────┘
         │                   │                   │
         │     OpenFeign     │     OpenFeign     │
         ▼                   ▼                   ▼
         ┌───────────────────────────────────────┐
         │         Core Banking Service          │
         │            (Port 8092)                │
         └───────────────────────────────────────┘
```

- **Synchronous REST (OpenFeign):** All inter-service communication uses Spring Cloud OpenFeign clients with Eureka service discovery for load-balanced calls.
- **Service Discovery:** Netflix Eureka handles service registration and discovery.
- **Centralized Configuration:** Spring Cloud Config Server pulls configuration from a remote Git repository.
- **Authentication Flow:** API Gateway validates JWT tokens from Keycloak and injects `X-Auth-Id` header into downstream requests.
- **RabbitMQ:** Referenced in architecture but not yet implemented (notification service is pending development).

### Infrastructure Components

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Service Discovery | Netflix Eureka | Service registration and lookup |
| API Gateway | Spring Cloud Gateway | Routing, security, header injection |
| Config Server | Spring Cloud Config | Centralized externalized configuration |
| Identity Provider | Keycloak 23.0.7 | OAuth2/OIDC authentication and user management |
| Database | MySQL 8.x | Primary data store for all services |
| Distributed Tracing | Zipkin 3 + Micrometer Brave | Request tracing across services |
| Message Queue | RabbitMQ | Notification messaging (planned, not implemented) |
| Container Runtime | Docker / Docker Compose | Local deployment and orchestration |

---

## 2. Data Model Documentation

### Core Banking Service

#### `banking_core_user`
| Field | Type | Description |
|-------|------|-------------|
| id | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| first_name | VARCHAR(255) | User's first name |
| last_name | VARCHAR(255) | User's last name |
| email | VARCHAR(255) | User's email address |
| identification_number | VARCHAR(255) | National ID / identification number |

#### `banking_core_account`
| Field | Type | Description |
|-------|------|-------------|
| id | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| number | VARCHAR(255) | Account number (e.g., "100015003000") |
| type | VARCHAR(255) | Account type enum: `SAVINGS_ACCOUNT` |
| status | VARCHAR(255) | Account status enum: `ACTIVE` |
| actual_balance | DECIMAL(19,2) | Current actual balance |
| available_balance | DECIMAL(19,2) | Available balance for transactions |
| user_id | BIGINT (FK) | Reference to `banking_core_user.id` |

#### `banking_core_utility_account`
| Field | Type | Description |
|-------|------|-------------|
| id | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| number | VARCHAR(255) | Utility account number |
| provider_name | VARCHAR(255) | Provider name (e.g., VODAFONE, VERIZON) |

#### `banking_core_transaction`
| Field | Type | Description |
|-------|------|-------------|
| id | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| amount | DECIMAL(19,2) | Transaction amount (negative for debits) |
| transaction_type | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| reference_number | VARCHAR(50) | Reference (target account or reference) |
| transaction_id | VARCHAR(50) | UUID-based transaction identifier |
| account_id | BIGINT (FK) | Reference to `banking_core_account.id` |

#### Relationships
- `banking_core_user` 1:N `banking_core_account` (via `user_id`)
- `banking_core_account` 1:N `banking_core_transaction` (via `account_id`)

### Internet Banking User Service

#### `user`
| Field | Type | Description |
|-------|------|-------------|
| id | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| auth_id | VARCHAR | Keycloak user ID |
| identification | VARCHAR | National identification number |
| status | VARCHAR (Enum) | `PENDING`, `APPROVED` |
| created_date | TIMESTAMP | Audit: creation timestamp |
| created_by | VARCHAR | Audit: creator |
| modified_date | TIMESTAMP | Audit: last modification |
| modified_by | VARCHAR | Audit: modifier |
| version | BIGINT | Optimistic locking version |

### Internet Banking Fund Transfer Service

#### `fund_transfer`
| Field | Type | Description |
|-------|------|-------------|
| id | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| transaction_reference | VARCHAR | UUID from core banking |
| from_account | VARCHAR | Source account number |
| to_account | VARCHAR | Destination account number |
| amount | DECIMAL | Transfer amount |
| status | VARCHAR (Enum) | `PENDING`, `SUCCESS`, `FAILED` |
| created_date | TIMESTAMP | Audit field |
| created_by | VARCHAR | Audit field |
| modified_date | TIMESTAMP | Audit field |
| modified_by | VARCHAR | Audit field |
| version | BIGINT | Optimistic locking |

### Internet Banking Utility Payment Service

#### `utility_payment`
| Field | Type | Description |
|-------|------|-------------|
| id | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| provider_id | BIGINT | Utility provider ID |
| amount | DECIMAL | Payment amount |
| reference_number | VARCHAR | Customer reference number |
| account | VARCHAR | Source bank account number |
| transaction_id | VARCHAR | UUID from core banking |
| status | VARCHAR (Enum) | `PROCESSING`, `SUCCESS`, `FAILED` |
| created_date | TIMESTAMP | Audit field |
| created_by | VARCHAR | Audit field |
| modified_date | TIMESTAMP | Audit field |
| modified_by | VARCHAR | Audit field |
| version | BIGINT | Optimistic locking |

### Database Separation

Each service uses its own MySQL database schema:
- `banking_core_service` - Core banking data
- `banking_core_user_service` - User registration data
- `banking_core_fund_transfer_service` - Fund transfer records
- `banking_core_utility_payment_service` - Utility payment records

Schema management: Core banking uses **Flyway** migrations; other services use **JPA `ddl-auto`** (hibernate auto-generation).

---

## 3. API Surface Map

### Core Banking Service (Port 8092)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| GET | `/api/v1/account/bank-account/{account_number}` | Get bank account details | Path: account_number | `BankAccount{number, type, status, availableBalance, actualBalance}` |
| GET | `/api/v1/account/util-account/{account_name}` | Get utility account by provider | Path: account_name (provider) | `UtilityAccount{id, number, providerName}` |
| GET | `/api/v1/user/{identification}` | Get user by ID number | Path: identification | `User{id, firstName, lastName, email, identificationNumber, accounts[]}` |
| GET | `/api/v1/user` | List users (paginated) | Query: page, size, sort | `List<User>` |
| POST | `/api/v1/transaction/fund-transfer` | Process fund transfer | `{fromAccount, toAccount, amount}` | `{message, transactionId}` |
| POST | `/api/v1/transaction/util-payment` | Process utility payment | `{account, providerId, amount, referenceNumber}` | `{message, transactionId}` |

### Internet Banking User Service (Port 8083)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| POST | `/api/v1/bank-users/register` | Register new user | `{email, identification, password}` | `User{id, email, identification, authId, status}` |
| PATCH | `/api/v1/bank-users/update/{id}` | Update user (approve/status) | Path: id, Body: `{status}` | `User` |
| GET | `/api/v1/bank-users` | List users (paginated) | Query: page, size, sort | `List<User>` |
| GET | `/api/v1/bank-users/{id}` | Get user by ID | Path: id | `User` |

### Internet Banking Fund Transfer Service (Port 8084)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| POST | `/api/v1/transfer` | Initiate fund transfer | `{fromAccount, toAccount, amount, authID}` | `{message, transactionId}` |
| GET | `/api/v1/transfer` | List fund transfers (paginated) | Query: page, size, sort | `List<FundTransfer>` |

### Internet Banking Utility Payment Service (Port 8085)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| POST | `/api/v1/utility-payment` | Process utility payment | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` |
| GET | `/api/v1/utility-payment` | List utility payments (paginated) | Query: page, size, sort | `List<UtilityPayment>` |

### API Gateway Routes (Port 8082)

The API Gateway routes requests to backend services with these path prefixes:
- `/user/**` -> `internet-banking-user-service`
- `/fund-transfer/**` -> `internet-banking-fund-transfer-service`
- `/banking-core/**` -> `core-banking-service`
- `/utility-payment/**` -> `internet-banking-utility-payment-service`

### Actuator Endpoints (All Services)

All services expose Spring Boot Actuator at `/actuator/**` (permitted without authentication at the gateway level).

---

## 4. Key Business Logic Inventory

### User Registration Flow
1. Check if email already exists in Keycloak
2. Verify user exists in core banking system (by identification number)
3. Validate email matches core banking record
4. Create user in Keycloak (disabled, email unverified)
5. Save user in local database with `PENDING` status
6. Admin approves user -> enables Keycloak account and verifies email

### Fund Transfer Rules
1. Save transfer request locally with `PENDING` status
2. Call core banking service via Feign client
3. Core banking validates sender has sufficient balance (`actualBalance >= amount`)
4. Debit sender account (subtract from both `actualBalance` and `availableBalance`)
5. Credit receiver account (add to both `actualBalance` and `availableBalance`)
6. Create transaction records for both accounts
7. Update local transfer record to `SUCCESS` with transaction reference

### Utility Payment Processing
1. Save payment request locally with `PROCESSING` status
2. Call core banking service via Feign client
3. Core banking validates sufficient balance in source account
4. Debit source account
5. Create transaction record
6. Update local payment record to `SUCCESS` with transaction ID

### Balance Validation Rule
```java
if (actualBalance < 0 || actualBalance < amount) {
    throw InsufficientFundsException
}
```

### Authentication/Authorization Flow
1. Client obtains JWT token from Keycloak
2. Request sent to API Gateway with Bearer token
3. Gateway validates JWT using Keycloak's JWK Set URI
4. Gateway extracts principal name and injects as `X-Auth-Id` header
5. Downstream services read `X-Auth-Id` from request header via `AppAuthUserFilter`
6. Filter stores auth ID in thread-local `ApiRequestContextHolder`

---

## 5. Integration Points

### Keycloak (Identity Provider)
- **Version:** 23.0.7
- **Connection:** Admin client via `keycloak-admin-client:24.0.4`
- **Configuration:** Server URL, realm, client ID, client secret (externalized via config server)
- **Usage:** User creation, email verification, account enabling, user search
- **Auth Flow:** OAuth2 Resource Server with JWT (JWK Set URI validation at gateway)
- **Database:** PostgreSQL 15 (separate from application DB)

### Zipkin (Distributed Tracing)
- **Version:** 3
- **Port:** 9411
- **Integration:** Micrometer Tracing Bridge Brave (`micrometer-tracing-bridge-brave`)
- **Reporter:** `zipkin-reporter-brave`
- **Coverage:** All services include tracing dependencies

### RabbitMQ (Message Queue)
- **Status:** Referenced in architecture documentation but NOT implemented in code
- **Planned Use:** Notification service for fund transfer and payment confirmations

### MySQL (Primary Database)
- **Connection:** MySQL 8.x via `mysql-connector-j:8.4.0`
- **Databases:** 4 separate schemas (one per business service)
- **User:** `javatodev_development` with full CRUD privileges
- **ORM:** Spring Data JPA with Hibernate
- **Migration:** Flyway (core-banking only), JPA auto-DDL (other services)

### Spring Cloud Config Server
- **Source:** Git repository at `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch:** `main`
- **Search Path:** `configuration/`
- **Bootstrap:** All services connect to config server at startup via `bootstrap.yml`

### Netflix Eureka (Service Discovery)
- **Server:** Standalone instance on port 8081
- **Clients:** All business services register with Eureka
- **Load Balancing:** Feign clients use Eureka for service name resolution

---

## 6. Build and Deployment Pipeline Summary

### Build System
- **Build Tool:** Gradle (per-service `build.gradle`, no root-level multi-project build)
- **Java Version:** 21
- **Spring Boot:** 3.2.4
- **Spring Cloud:** 2023.0.0
- **Plugins:** `spring-boot`, `spring-dependency-management`, `gradle-git-properties`

### Docker Compose Deployment
- **File:** `docker-compose/docker-compose.yml`
- **Network:** Custom bridge network `javatodev_ib_network` (subnet `172.25.0.0/16`)
- **Service Ordering:** Uses `wait-for-it.sh` scripts for startup dependencies
- **Profiles:** `docker` profile activated via `-Dspring.profiles.active=docker`
- **Images:** Pre-built images from `javatodev/*` Docker Hub registry

### Startup Order
1. MySQL, PostgreSQL (Keycloak DB), Zipkin
2. Keycloak (depends on PostgreSQL)
3. Config Server
4. Service Registry
5. API Gateway (waits for registry + config)
6. Business services (wait for registry + config + MySQL)

### CI/CD
- **GitHub Actions:** No workflow files present (`.github/` only contains `FUNDING.yml`)
- **Containerization:** Each service has its own Dockerfile (implied by Docker Compose image references)
- **No automated testing pipeline** configured in the repository

### Local Development
- **Profiles:** `dev` (local), `docker` (containerized)
- **Test Database:** H2 in-memory for unit tests
- **Config:** Bootstrap configuration points to `localhost:8090` for dev profile
