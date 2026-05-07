# Application Knowledge Base

## 1. Architecture Overview

### System Summary

This is a Java 21 / Spring Boot 3.2.4 internet banking application composed of **6 microservices** following a typical Spring Cloud architecture pattern with centralized configuration, service discovery, and API gateway.

### Services

| Service | Port | Role |
|---------|------|------|
| `internet-banking-config-server` | 8090 | Centralized configuration via Spring Cloud Config (Git-backed) |
| `internet-banking-service-registry` | 8081 | Service discovery via Netflix Eureka |
| `internet-banking-api-gateway` | 8082 | API Gateway (Spring Cloud Gateway) with OAuth2/Keycloak security |
| `internet-banking-user-service` | 8083 | User registration, management, and Keycloak integration |
| `internet-banking-fund-transfer-service` | 8084 | Fund transfer processing between bank accounts |
| `internet-banking-utility-payment-service` | 8085 | Utility bill payment processing |
| `core-banking-service` | 8092 | Core banking engine: accounts, users, transactions |

### Communication Patterns

```
┌──────────────┐
│  API Gateway │  (OAuth2 Resource Server / Keycloak JWT)
│   :8082      │
└──────┬───────┘
       │ Routes via Eureka service names
       ├──────────────────────────────────────────────────────┐
       │                    │                                  │
┌──────▼───────┐   ┌───────▼──────────┐   ┌──────────────────▼───────┐
│ User Service │   │ Fund Transfer    │   │ Utility Payment Service  │
│   :8083      │   │ Service :8084    │   │   :8085                  │
└──────┬───────┘   └───────┬──────────┘   └──────────┬───────────────┘
       │ Feign             │ Feign                    │ Feign
       │                   │                          │
       └───────────────────┼──────────────────────────┘
                           │
                   ┌───────▼──────────┐
                   │ Core Banking     │
                   │ Service :8092    │
                   └──────────────────┘
```

- **Synchronous (HTTP/REST via OpenFeign):** All inter-service communication uses OpenFeign clients routed through Eureka service discovery.
- **Gateway → Downstream:** Spring Cloud Gateway routes requests based on service-name prefixes to downstream services.
- **Auth Propagation:** The API Gateway extracts the JWT principal and passes it as an `X-Auth-Id` header to downstream services via a `GlobalFilter`.

### Infrastructure Components

| Component | Purpose | Version/Image |
|-----------|---------|---------------|
| MySQL | Primary data store for all business services | 8.4.0 |
| PostgreSQL | Keycloak identity store | 15 |
| Keycloak | Identity & Access Management (OAuth2/OIDC) | 23.0.7 |
| Zipkin | Distributed tracing | 3 |
| Netflix Eureka | Service registry/discovery | Spring Cloud 2023.0.0 |
| Spring Cloud Config | Centralized configuration (Git-backed) | Spring Cloud 2023.0.0 |
| Docker / Docker Compose | Container orchestration | - |

---

## 2. Data Model Documentation

### Core Banking Service Database (`banking_core_service`)

#### `banking_core_user`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| `email` | VARCHAR(255) | User email |
| `first_name` | VARCHAR(255) | First name |
| `last_name` | VARCHAR(255) | Last name |
| `identification_number` | VARCHAR(255) | National ID / identification |

#### `banking_core_account`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| `number` | VARCHAR(255) | Account number |
| `type` | VARCHAR(255) | Enum: `SAVINGS_ACCOUNT` |
| `status` | VARCHAR(255) | Enum: `ACTIVE`, `INACTIVE` |
| `actual_balance` | DECIMAL(19,2) | Actual (ledger) balance |
| `available_balance` | DECIMAL(19,2) | Available balance |
| `user_id` | BIGINT (FK → `banking_core_user.id`) | Account owner |

#### `banking_core_utility_account`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| `number` | VARCHAR(255) | Utility account number |
| `provider_name` | VARCHAR(255) | Provider name (e.g., VODAFONE, AIRTEL) |

#### `banking_core_transaction`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| `amount` | DECIMAL(19,2) | Transaction amount |
| `transaction_type` | VARCHAR(30) | Enum: `FUND_TRANSFER`, `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Reference (destination account or ref#) |
| `transaction_id` | VARCHAR(50) | UUID transaction identifier |
| `account_id` | BIGINT (FK → `banking_core_account.id`) | Associated account |

### Fund Transfer Service Database (`banking_core_fund_transfer_service`)

#### `fund_transfer`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| `transaction_reference` | VARCHAR | Core banking transaction ID |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `status` | VARCHAR | Enum: `PENDING`, `SUCCESS`, `FAILED` |
| `created_date` | INSTANT | Audit: creation timestamp |
| `created_by` | VARCHAR | Audit: creator |
| `modified_date` | INSTANT | Audit: last modified timestamp |
| `modified_by` | VARCHAR | Audit: modifier |
| `version` | BIGINT | Optimistic locking version |

### User Service Database (`banking_core_user_service`)

#### `user`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| `auth_id` | VARCHAR | Keycloak user UUID |
| `identification` | VARCHAR | National ID number |
| `status` | VARCHAR | Enum: `PENDING`, `APPROVED` |
| `created_date` | INSTANT | Audit: creation timestamp |
| `created_by` | VARCHAR | Audit: creator |
| `modified_date` | INSTANT | Audit: last modified timestamp |
| `modified_by` | VARCHAR | Audit: modifier |
| `version` | BIGINT | Optimistic locking version |

### Utility Payment Service Database (`banking_core_utility_payment_service`)

#### `utility_payment`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | Primary key |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Bill reference number |
| `account` | VARCHAR | Source bank account number |
| `transaction_id` | VARCHAR | Core banking transaction ID |
| `status` | VARCHAR | Enum: `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` | INSTANT | Audit: creation timestamp |
| `created_by` | VARCHAR | Audit: creator |
| `modified_date` | INSTANT | Audit: last modified timestamp |
| `modified_by` | VARCHAR | Audit: modifier |
| `version` | BIGINT | Optimistic locking version |

### Entity Relationships

```
banking_core_user (1) ──── (N) banking_core_account (1) ──── (N) banking_core_transaction
                                                                        │
                                                                        │ referenced by
                                                                        ▼
                                                              fund_transfer (fund-transfer-service)
                                                              utility_payment (utility-payment-service)
```

---

## 3. API Surface Map

### Core Banking Service (`:8092`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| GET | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | - | `BankAccount` (id, number, type, status, availableBalance, actualBalance, user) |
| GET | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | - | `UtilityAccount` (id, number, providerName) |
| GET | `/api/v1/user/{identification}` | Get user by identification number | - | `User` (id, firstName, lastName, email, identificationNumber, accounts[]) |
| GET | `/api/v1/user` | List users (paginated) | Pageable params | `List<User>` |
| POST | `/api/v1/transaction/fund-transfer` | Process fund transfer | `{fromAccount, toAccount, amount}` | `{message, transactionId}` |
| POST | `/api/v1/transaction/util-payment` | Process utility payment | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` |

### Internet Banking User Service (`:8083`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| POST | `/api/v1/bank-users/register` | Register new user | `{email, identification, password}` | `User` (id, email, identification, authId, status) |
| PATCH | `/api/v1/bank-users/update/{id}` | Update user (approve/reject) | `{status}` | `User` |
| GET | `/api/v1/bank-users` | List users (paginated) | Pageable params | `List<User>` |
| GET | `/api/v1/bank-users/{id}` | Get user by ID | - | `User` |

### Internet Banking Fund Transfer Service (`:8084`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| POST | `/api/v1/transfer` | Initiate fund transfer | `{fromAccount, toAccount, amount, authID}` | `{message, transactionId}` |
| GET | `/api/v1/transfer` | List fund transfers (paginated) | Pageable params | `List<FundTransfer>` |

### Internet Banking Utility Payment Service (`:8085`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| POST | `/api/v1/utility-payment` | Process utility payment | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` |
| GET | `/api/v1/utility-payment` | List utility payments (paginated) | Pageable params | `List<UtilityPayment>` |

### API Gateway Route Prefixes

| Prefix | Target Service |
|--------|---------------|
| `/user/**` | `internet-banking-user-service` |
| `/fund-transfer/**` | `internet-banking-fund-transfer-service` |
| `/utility-payment/**` | `internet-banking-utility-payment-service` |
| `/banking-core/**` | `core-banking-service` |

---

## 4. Key Business Logic Inventory

### Fund Transfer Flow

1. **Client** sends `POST /api/v1/transfer` via API Gateway (JWT required)
2. **Fund Transfer Service** receives request, saves entity with `PENDING` status
3. **Fund Transfer Service** calls Core Banking via Feign: `POST /api/v1/transaction/fund-transfer`
4. **Core Banking Service**:
   - Looks up source and destination accounts
   - Validates source account has sufficient balance (`actualBalance >= amount`)
   - Debits source account (subtracts from `actualBalance` and `availableBalance`)
   - Credits destination account (adds to `actualBalance` and `availableBalance`)
   - Creates two `TransactionEntity` records (debit + credit)
   - Returns `transactionId` (UUID)
5. **Fund Transfer Service** updates local entity with `SUCCESS` status and `transactionReference`

### Utility Payment Flow

1. **Client** sends `POST /api/v1/utility-payment` via API Gateway (JWT required)
2. **Utility Payment Service** receives request, saves entity with `PROCESSING` status
3. **Utility Payment Service** calls Core Banking via Feign: `POST /api/v1/transaction/util-payment`
4. **Core Banking Service**:
   - Validates source account balance
   - Looks up utility provider account
   - Debits source account
   - Creates `TransactionEntity` record
   - Returns `transactionId` (UUID)
5. **Utility Payment Service** updates local entity with `SUCCESS` status and `transactionId`

### User Registration Flow

1. **Client** sends `POST /api/v1/bank-users/register` (no JWT required - publicly accessible)
2. **User Service**:
   - Checks Keycloak for existing email (rejects duplicates)
   - Calls Core Banking Service via Feign to validate user exists by identification number
   - Validates email matches the core banking user record
   - Creates Keycloak user (disabled, email unverified)
   - Saves local user record with `PENDING` status and Keycloak `authId`
3. **Admin** approves user via `PATCH /api/v1/bank-users/update/{id}` with `{status: "APPROVED"}`
   - Enables Keycloak user and marks email as verified

### Business Rules

- **Insufficient funds:** Transfer/payment is rejected if `actualBalance < 0` OR `actualBalance < amount`
- **User registration:** Requires matching identification number in core banking + unique email in Keycloak
- **User approval:** Two-step process - registration creates disabled account; admin approval enables it
- **Transaction atomicity:** Core banking fund transfers use `@Transactional` annotation

---

## 5. Integration Points

### Keycloak (Identity Provider)

- **Version:** 23.0.7
- **Protocol:** OAuth2 / OpenID Connect
- **Integration Type:** 
  - API Gateway: JWT Resource Server validation via JWK Set URI
  - User Service: Admin Client (`keycloak-admin-client:24.0.4`) for user CRUD
- **Configuration:** Server URL, realm, client ID, client secret (from Spring Cloud Config)
- **Grant Type:** `client_credentials` for admin operations

### Service Discovery (Eureka)

- All business services register with Eureka
- Feign clients resolve service names via Eureka (`core-banking-service`)
- Config: `spring.cloud.netflix.eureka.client`

### Distributed Tracing (Zipkin)

- **Library:** Micrometer Tracing Bridge Brave + Zipkin Reporter
- **Port:** 9411
- **Coverage:** All services include tracing dependencies
- Feign calls instrumented via `feign-micrometer`

### Spring Cloud Config (Git-backed)

- **Config repo:** `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch:** `main`
- **Path:** `configuration/`
- Services bootstrap from Config Server before registering with Eureka

### Database Connections

- **MySQL 8.4.0:** Shared MySQL instance with separate databases per service
  - `banking_core_service` → Core Banking Service
  - `banking_core_fund_transfer_service` → Fund Transfer Service
  - `banking_core_user_service` → User Service
  - `banking_core_utility_payment_service` → Utility Payment Service
- **PostgreSQL 15:** Keycloak-only

### RabbitMQ (Planned)

- Mentioned in architecture documentation for notification service
- **Not yet implemented** - notification service is marked "PENDING Development"

---

## 6. Build and Deployment Pipeline Summary

### Build System

- **Build Tool:** Gradle (per-service, no multi-project build)
- **Java Version:** 21 (Eclipse Temurin)
- **Spring Boot:** 3.2.4
- **Spring Cloud:** 2023.0.0
- **Plugins:** `spring-boot`, `dependency-management`, `gradle-git-properties`

### Docker

Each service has its own `Dockerfile`:
```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
COPY wait-for-it.sh wait-for-it.sh
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

### Docker Compose

- **File:** `docker-compose/docker-compose.yml`
- **Network:** Custom bridge network `javatodev_ib_network` (subnet `172.25.0.0/16`)
- **Startup ordering:** `wait-for-it.sh` scripts ensure services wait for Config Server, Service Registry, and MySQL
- **Profiles:** `docker` profile activated in containers

### Database Migrations

- **Tool:** Flyway (Core Banking Service only)
- **Migration location:** `src/main/resources/db/migration/`
- **Migrations:**
  - `V1.0.20210427174638` - Create base tables (users, accounts, utility accounts)
  - `V1.0.20210427174721` - Seed test data
  - `V1.0.20210429210839` - Create transaction table

### Testing

- **Framework:** JUnit 5 (JUnit Platform)
- **Test DB:** H2 in-memory (for services with tests)
- **Test coverage:** Only `core-banking-service` has unit tests (AccountServiceTest, TransactionServiceTest, UserServiceTest)
- Other services have only empty `ApplicationTests` context-load tests
