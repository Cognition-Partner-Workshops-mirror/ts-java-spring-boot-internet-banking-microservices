# Application Knowledge Base

## 1. Architecture Overview

### System Summary

Internet Banking Concept — a Java 21 / Spring Boot 3.2.4 microservices application implementing core banking operations (fund transfers, utility payments, user management) with 6 independently deployable services.

### Services

| Service | Port | Responsibility |
|---------|------|----------------|
| **core-banking-service** | 8092 | Core banking ledger — accounts, users, transactions, balance management |
| **internet-banking-fund-transfer-service** | 8084 | Orchestrates fund transfers between accounts via core banking |
| **internet-banking-user-service** | 8083 | User registration, approval workflows, Keycloak identity management |
| **internet-banking-utility-payment-service** | 8085 | Processes utility bill payments (telecom, electricity) |
| **internet-banking-api-gateway** | 8082 | Spring Cloud Gateway — routing, OAuth2 security, header propagation |
| **internet-banking-service-registry** | 8081 | Netflix Eureka — service discovery |
| **internet-banking-config-server** | 8090 | Spring Cloud Config — centralized configuration from Git |

### Communication Patterns

```
┌─────────────┐     OAuth2/JWT      ┌───────────────────┐
│   Client    │ ──────────────────► │   API Gateway     │
└─────────────┘                      │   (8082)          │
                                     └────────┬──────────┘
                                              │ Routes via Eureka
                    ┌─────────────────────────┼─────────────────────────┐
                    │                         │                         │
           ┌───────▼────────┐   ┌────────────▼──────────┐   ┌─────────▼──────────┐
           │  User Service  │   │  Fund Transfer Service │   │ Utility Payment Svc│
           │    (8083)      │   │       (8084)           │   │      (8085)        │
           └───────┬────────┘   └────────────┬──────────┘   └─────────┬──────────┘
                   │  Feign                   │ Feign                   │ Feign
                   └─────────────────────────►│◄──────────────────────┘
                                     ┌────────▼──────────┐
                                     │ Core Banking Svc  │
                                     │     (8092)        │
                                     └───────────────────┘
```

- **Synchronous:** OpenFeign HTTP clients between services (via Eureka service names)
- **Gateway → Services:** Spring Cloud Gateway routes with path-prefix stripping
- **Auth propagation:** API Gateway extracts JWT principal and injects `X-Auth-Id` header downstream
- **Asynchronous:** RabbitMQ mentioned in README for notifications but **not yet implemented**

### Infrastructure Components

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Service Discovery | Netflix Eureka | Runtime service registration and lookup |
| Configuration | Spring Cloud Config Server | Git-backed centralized configuration |
| API Gateway | Spring Cloud Gateway | Single entry point, routing, security |
| Identity Provider | Keycloak 23.0.7 | OAuth2/OIDC authentication, user realm management |
| Database | MySQL 8 | Persistent storage (4 separate databases) |
| Distributed Tracing | Zipkin 3 + Micrometer Brave | Request correlation across services |
| Containerization | Docker + Docker Compose | Deployment orchestration |

---

## 2. Data Model Documentation

### Core Banking Service (MySQL: `banking_core_service`)

#### `banking_core_user`
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK AUTO_INCREMENT | Internal user ID |
| first_name | VARCHAR(255) | First name |
| last_name | VARCHAR(255) | Last name |
| email | VARCHAR(255) | Email address |
| identification_number | VARCHAR(255) | National ID / NIC number |

#### `banking_core_account`
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK AUTO_INCREMENT | Internal account ID |
| number | VARCHAR(255) | Account number (12-digit string) |
| type | VARCHAR(255) ENUM | SAVINGS_ACCOUNT |
| status | VARCHAR(255) ENUM | ACTIVE, INACTIVE |
| actual_balance | DECIMAL(19,2) | Actual ledger balance |
| available_balance | DECIMAL(19,2) | Available for withdrawal |
| user_id | BIGINT FK → banking_core_user.id | Account owner |

#### `banking_core_transaction`
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK AUTO_INCREMENT | Transaction ID |
| amount | DECIMAL(19,2) | Signed amount (negative = debit) |
| transaction_type | VARCHAR(30) | FUND_TRANSFER, UTILITY_PAYMENT |
| reference_number | VARCHAR(50) | Destination account or reference |
| transaction_id | VARCHAR(50) | UUID grouping related entries |
| account_id | BIGINT FK → banking_core_account.id | Associated account |

#### `banking_core_utility_account`
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK AUTO_INCREMENT | Provider ID |
| number | VARCHAR(255) | Provider account number |
| provider_name | VARCHAR(255) | Provider name (VODAFONE, VERIZON, etc.) |

### Fund Transfer Service (MySQL: `banking_core_fund_transfer_service`)

#### `fund_transfer` (JPA auto-created)
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK AUTO_INCREMENT | Record ID |
| from_account | VARCHAR | Source account number |
| to_account | VARCHAR | Destination account number |
| amount | DECIMAL | Transfer amount |
| transaction_reference | VARCHAR | UUID from core banking |
| status | VARCHAR ENUM | PENDING, SUCCESS, FAILED |
| created_at / updated_at | TIMESTAMP | Audit fields (via AuditAware) |

### User Service (MySQL: `banking_core_user_service`)

#### `user` (JPA auto-created)
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK AUTO_INCREMENT | Record ID |
| auth_id | VARCHAR | Keycloak user UUID |
| identification | VARCHAR | NIC number (links to core banking) |
| status | VARCHAR ENUM | PENDING, APPROVED |
| created_at / updated_at | TIMESTAMP | Audit fields |

### Utility Payment Service (MySQL: `banking_core_utility_payment_service`)

#### `utility_payment` (JPA auto-created)
| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK AUTO_INCREMENT | Record ID |
| provider_id | BIGINT | Utility provider ID |
| amount | DECIMAL | Payment amount |
| reference_number | VARCHAR | Bill reference |
| account | VARCHAR | Source account number |
| transaction_id | VARCHAR | UUID from core banking |
| status | VARCHAR ENUM | PROCESSING, SUCCESS, FAILED |
| created_at / updated_at | TIMESTAMP | Audit fields |

---

## 3. API Surface Map

### Core Banking Service (`:8092`)

| Method | Endpoint | Request | Response | Description |
|--------|----------|---------|----------|-------------|
| GET | `/api/v1/account/bank-account/{account_number}` | Path: account_number | `BankAccount` (id, number, type, status, availableBalance, actualBalance) | Lookup bank account |
| GET | `/api/v1/account/util-account/{account_name}` | Path: provider name | `UtilityAccount` (id, number, providerName) | Lookup utility provider |
| GET | `/api/v1/user/{identification}` | Path: NIC number | `User` (id, firstName, lastName, email, identificationNumber, accounts[]) | Lookup user by NIC |
| GET | `/api/v1/user` | Query: page, size, sort | `List<User>` (paginated) | List all users |
| POST | `/api/v1/transaction/fund-transfer` | `{fromAccount, toAccount, amount}` | `{message, transactionId}` | Execute fund transfer |
| POST | `/api/v1/transaction/util-payment` | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` | Execute utility payment |

### Internet Banking User Service (`:8083`)

| Method | Endpoint | Request | Response | Description |
|--------|----------|---------|----------|-------------|
| POST | `/api/v1/bank-users/register` | `{email, identification, password}` | `User` | Register new banking user |
| PATCH | `/api/v1/bank-users/update/{id}` | `{status: "APPROVED"}` | `User` | Approve/update user |
| GET | `/api/v1/bank-users` | Query: page, size, sort | `List<User>` | List registered users |
| GET | `/api/v1/bank-users/{id}` | Path: user ID | `User` | Get user by ID |

### Internet Banking Fund Transfer Service (`:8084`)

| Method | Endpoint | Request | Response | Description |
|--------|----------|---------|----------|-------------|
| POST | `/api/v1/transfer` | `{fromAccount, toAccount, amount, authID}` | `{message, transactionId}` | Initiate fund transfer |
| GET | `/api/v1/transfer` | Query: page, size, sort | `List<FundTransfer>` | List transfers (paginated) |

### Internet Banking Utility Payment Service (`:8085`)

| Method | Endpoint | Request | Response | Description |
|--------|----------|---------|----------|-------------|
| POST | `/api/v1/utility-payment` | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` | Process utility payment |
| GET | `/api/v1/utility-payment` | Query: page, size, sort | `List<UtilityPayment>` | List payments (paginated) |

### API Gateway Routes (`:8082`)

| Prefix | Target Service |
|--------|---------------|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/banking-core/**` | core-banking-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |

### Service Registry (`:8081`)

| Endpoint | Description |
|----------|-------------|
| Eureka Dashboard | Service health and registration UI |

### Actuator Endpoints (all services)

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Health check |
| `/actuator/info` | Build info (git properties) |

---

## 4. Key Business Logic Inventory

### Fund Transfer Rules

1. **Balance Validation:** Source account `actualBalance` must be ≥ transfer amount and > 0
2. **Double-Entry Bookkeeping:** Creates two `TransactionEntity` records — debit on source (negative amount), credit on destination (positive amount)
3. **Orchestration Pattern:** Fund Transfer Service saves a PENDING record, calls Core Banking, updates to SUCCESS with transaction reference
4. **No idempotency key:** Duplicate requests will create duplicate transfers
5. **No transaction rollback:** If Core Banking call succeeds but local DB save fails, the system is inconsistent

### Utility Payment Processing

1. **Balance Validation:** Same as fund transfer — checks source account balance
2. **Provider Lookup:** Validates utility provider exists by ID
3. **Debit Only:** Debits source account; third-party provider API call is placeholder (commented)
4. **Orchestration:** Payment Service saves PROCESSING record, calls Core Banking, updates to SUCCESS

### User Management

1. **Registration Flow:**
   - Validates email not already in Keycloak
   - Validates NIC exists in Core Banking user table
   - Cross-checks email matches Core Banking record
   - Creates Keycloak user (disabled, email unverified)
   - Saves local user record with PENDING status
2. **Approval Flow:**
   - Admin PATCHes user status to APPROVED
   - Enables Keycloak user and marks email as verified
3. **Identity Bridge:** `authId` links local user record to Keycloak UUID

---

## 5. Integration Points

### Keycloak (Identity Provider)

- **Version:** 23.0.7
- **Connection:** Admin Client SDK (`keycloak-admin-client:24.0.4`)
- **Auth:** Client credentials grant (`client_credentials`)
- **Configuration:** `app.config.keycloak.server-url`, `realm`, `clientId`, `client-secret`
- **Operations:** User CRUD, email search, enable/disable users
- **Realm:** Pre-configured with imported realm JSON at startup

### RabbitMQ (Message Broker)

- **Status:** Referenced in README but **NOT implemented** in code
- **Intended Use:** Notification service consuming transfer/payment events

### Zipkin (Distributed Tracing)

- **Version:** 3
- **Port:** 9411
- **Integration:** `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave`
- **Coverage:** All 6 services instrument trace propagation via Brave

### Database Connections

| Service | Database | Driver | Migration |
|---------|----------|--------|-----------|
| core-banking-service | banking_core_service | MySQL 8.4 | Flyway (3 migrations) |
| fund-transfer-service | banking_core_fund_transfer_service | MySQL 8.4 | JPA auto (hibernate.ddl-auto) |
| user-service | banking_core_user_service | MySQL 8.4 | JPA auto |
| utility-payment-service | banking_core_utility_payment_service | MySQL 8.4 | JPA auto |

### Inter-Service Communication (Feign Clients)

| Source Service | Target Service | Operations |
|---------------|----------------|------------|
| fund-transfer-service | core-banking-service | `readAccount`, `fundTransfer` |
| user-service | core-banking-service | `readUser` (by NIC) |
| utility-payment-service | core-banking-service | `readAccount`, `utilityPayment` |

---

## 6. Build and Deployment Pipeline Summary

### Build System

- **Tool:** Gradle (per-service, no multi-project build)
- **Java:** 21 (Eclipse Temurin)
- **Spring Boot:** 3.2.4
- **Spring Cloud:** 2023.0.0
- **Plugins:** `spring-boot`, `spring-dependency-management`, `gradle-git-properties`

### Docker Build

Each service has an identical Dockerfile pattern:
```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
COPY wait-for-it.sh wait-for-it.sh
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

### Docker Compose Deployment

- **Network:** Custom bridge `javatodev_ib_network` (subnet 172.25.0.0/16)
- **Startup ordering:** `wait-for-it.sh` scripts ensure services wait for registry + config server + MySQL
- **Profiles:** `docker` profile activates docker-specific bootstrap configuration
- **Volumes:** MySQL data and Postgres (Keycloak) data persisted via named volumes

### Build Steps (Manual)

```bash
# Per service:
cd <service-directory>
./gradlew clean build
docker build -t javatodev/<service-name> .

# Deploy all:
cd docker-compose
docker-compose up -d
```

### CI/CD

- **GitHub Actions:** Previously configured but **removed** (PAT scope compatibility issue)
- **No automated pipeline** currently exists in the repository
