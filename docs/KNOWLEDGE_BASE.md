# Application Knowledge Base

## 1. Architecture Overview

### System Summary

This is a Java 21 / Spring Boot 3.2.4 internet banking platform built on a microservices architecture using Spring Cloud 2023.0.0. The system processes fund transfers and utility payments through a layered service mesh with centralized configuration, service discovery, and API gateway routing.

### Services

| Service | Port | Purpose |
|---------|------|---------|
| `internet-banking-api-gateway` | 8082 | Single entry point; routes traffic, enforces OAuth2 JWT auth |
| `internet-banking-service-registry` | 8081 | Netflix Eureka server for service discovery |
| `internet-banking-config-server` | 8090 | Spring Cloud Config server (Git-backed) |
| `internet-banking-user-service` | 8083 | User registration, profile management, Keycloak integration |
| `internet-banking-fund-transfer-service` | 8084 | Account-to-account fund transfers |
| `internet-banking-utility-payment-service` | 8085 | Third-party utility bill payments |
| `core-banking-service` | 8092 | System of record for accounts, users, ledger transactions |

### Communication Patterns

```
Client -> API Gateway (8082) -> [Eureka Discovery] -> Downstream Services
                                                         |
                                                         v
User Service ----OpenFeign----> Core Banking Service
Fund Transfer Service --OpenFeign--> Core Banking Service
Utility Payment Service --OpenFeign--> Core Banking Service
```

- **Synchronous REST (OpenFeign):** All inter-service communication uses Spring Cloud OpenFeign declarative HTTP clients with Eureka-based service discovery (no hardcoded URLs).
- **API Gateway routing:** Spring Cloud Gateway routes requests by path prefix to downstream services.
- **Auth propagation:** The gateway extracts the JWT principal and forwards it as an `X-Auth-Id` header to downstream services via a `GlobalFilter`.
- **Service Discovery:** All services register with Eureka and resolve each other by service name.
- **Centralized Config:** All services bootstrap by fetching configuration from the Config Server (backed by a Git repository: `internet-banking-microservices-configurations`).

### Infrastructure Components

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Service Registry | Netflix Eureka | Dynamic service location |
| Config Server | Spring Cloud Config (Git) | Centralized configuration |
| API Gateway | Spring Cloud Gateway | Routing, security enforcement |
| Identity Provider | Keycloak 23.0.7 | OAuth2/OIDC authentication |
| Primary Database | MySQL 8.x | Application data (4 schemas) |
| Keycloak Database | PostgreSQL 15 | Keycloak internal state |
| Message Broker | RabbitMQ | Async notifications (planned) |
| Distributed Tracing | Zipkin 3 + Micrometer Brave | Request tracing across services |
| Container Orchestration | Docker Compose | Local/deployment orchestration |

---

## 2. Data Model Documentation

### Core Banking Service (MySQL: `banking_core_service`)

#### `banking_core_user`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto) | Internal user ID |
| `first_name` | VARCHAR(255) | User's first name |
| `last_name` | VARCHAR(255) | User's last name |
| `email` | VARCHAR(255) | Email address |
| `identification_number` | VARCHAR(255) | National ID / NIC number |

#### `banking_core_account`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto) | Internal account ID |
| `number` | VARCHAR(255) | Account number (e.g., 100015003000) |
| `type` | VARCHAR(255) | Enum: SAVINGS_ACCOUNT, FIXED_DEPOSIT, LOAN_ACCOUNT |
| `status` | VARCHAR(255) | Enum: ACTIVE (others implied) |
| `actual_balance` | DECIMAL(19,2) | Ledger balance |
| `available_balance` | DECIMAL(19,2) | Available balance |
| `user_id` | BIGINT (FK -> banking_core_user.id) | Account owner |

#### `banking_core_transaction`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto) | Internal transaction ID |
| `amount` | DECIMAL(19,2) | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | Enum: FUND_TRANSFER, UTILITY_PAYMENT |
| `reference_number` | VARCHAR(50) | Counterparty account or reference |
| `transaction_id` | VARCHAR(50) | UUID grouping related entries |
| `account_id` | BIGINT (FK -> banking_core_account.id) | Associated account |

#### `banking_core_utility_account`
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto) | Internal utility account ID |
| `number` | VARCHAR(255) | Provider account number |
| `provider_name` | VARCHAR(255) | Provider name (VODAFONE, VERIZON, etc.) |

### User Service (MySQL: `banking_core_user_service`)

#### `user` (extends AuditAware)
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto) | Internal ID |
| `auth_id` | VARCHAR | Keycloak user UUID |
| `identification` | VARCHAR | NIC/National ID |
| `status` | VARCHAR | Enum: PENDING, APPROVED, DISABLED, BLACKLIST |
| `created_date` | INSTANT | Audit: creation timestamp |
| `created_by` | VARCHAR | Audit: creator |
| `modified_date` | INSTANT | Audit: last modification |
| `modified_by` | VARCHAR | Audit: last modifier |
| `version` | BIGINT | Optimistic locking version |

### Fund Transfer Service (MySQL: `banking_core_fund_transfer_service`)

#### `fund_transfer` (extends AuditAware)
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto) | Internal ID |
| `transaction_reference` | VARCHAR | UUID from core banking |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `status` | VARCHAR | Enum: PENDING, PROCESSING, SUCCESS, FAILED |
| Audit fields | | createdDate, createdBy, modifiedDate, modifiedBy, version |

### Utility Payment Service (MySQL: `banking_core_utility_payment_service`)

#### `utility_payment` (extends AuditAware)
| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT (PK, auto) | Internal ID |
| `provider_id` | BIGINT | Utility provider reference |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Bill/customer reference |
| `account` | VARCHAR | Source account number |
| `transaction_id` | VARCHAR | UUID from core banking |
| `status` | VARCHAR | Enum: PENDING, PROCESSING, SUCCESS, FAILED |
| Audit fields | | createdDate, createdBy, modifiedDate, modifiedBy, version |

### Entity Relationships

```
banking_core_user (1) ----< (N) banking_core_account
banking_core_account (1) ----< (N) banking_core_transaction
banking_core_utility_account (standalone - utility providers)

user_service.user --> maps to keycloak via auth_id
user_service.user --> maps to core_banking.banking_core_user via identification
```

---

## 3. API Surface Map

### API Gateway Routes (prefix-based routing)

| Path Prefix | Target Service |
|------------|---------------|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |
| `/banking-core/**` | core-banking-service |

### Core Banking Service (`/api/v1`)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| GET | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | Path: account_number | `BankAccount{number, type, status, availableBalance, actualBalance}` |
| GET | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | Path: account_name | `UtilityAccount{id, number, providerName}` |
| POST | `/api/v1/transaction/fund-transfer` | Process fund transfer | `{fromAccount, toAccount, amount}` | `{message, transactionId}` |
| POST | `/api/v1/transaction/util-payment` | Process utility payment | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` |
| GET | `/api/v1/user/{identification}` | Get user by NIC | Path: identification | `User{id, firstName, lastName, email, identificationNumber, accounts[]}` |
| GET | `/api/v1/user` | List users (paginated) | Query: page, size, sort | `Page<User>` |

### User Service (`/api/v1/bank-users`)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| POST | `/api/v1/bank-users/register` | Register new user | `{email, identification, password}` | `{id, email, identification, authId, status}` |
| PATCH | `/api/v1/bank-users/update/{id}` | Update user status | Path: id, Body: `{status}` | `{id, email, identification, authId, status}` |
| GET | `/api/v1/bank-users` | List users (paginated) | Query: page, size, sort | `List<User>` |
| GET | `/api/v1/bank-users/{id}` | Get user by ID | Path: id | `{id, email, identification, authId, status}` |

### Fund Transfer Service (`/api/v1/transfer`)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| POST | `/api/v1/transfer` | Initiate fund transfer | `{fromAccount, toAccount, amount, authID}` | `{message, transactionId}` |
| GET | `/api/v1/transfer` | List transfers (paginated) | Query: page, size, sort | `List<FundTransfer>` |

### Utility Payment Service (`/api/v1/utility-payment`)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| POST | `/api/v1/utility-payment` | Process utility payment | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` |
| GET | `/api/v1/utility-payment` | List payments (paginated) | Query: page, size, sort | `List<UtilityPayment>` |

### Actuator Endpoints (all services)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/actuator/health` | Health check |
| GET | `/actuator/info` | Application info (git properties) |

---

## 4. Key Business Logic Inventory

### Fund Transfer Rules (Core Banking)

1. **Balance Validation:** Source account `actualBalance` must be >= 0 AND >= transfer amount; otherwise `InsufficientFundsException` is thrown.
2. **Double-Entry Bookkeeping:** Each transfer creates TWO transaction records:
   - Debit entry on source account (negative amount)
   - Credit entry on destination account (positive amount)
3. **Balance Update:** Both `actualBalance` and `availableBalance` are updated on source and destination accounts.
4. **Transaction ID:** A UUID is generated and shared across both ledger entries to link them.
5. **Atomicity:** The entire operation runs within a `@Transactional` boundary.

### Fund Transfer Flow (Internet Banking Layer)

1. Fund Transfer Service receives request, saves a `PENDING` record.
2. Calls Core Banking Service via Feign to execute the actual transfer.
3. On success, updates local record to `SUCCESS` with the transaction reference.
4. Returns response to caller.

### Utility Payment Processing

1. Utility Payment Service receives request, saves a `PROCESSING` record.
2. Calls Core Banking Service via Feign for balance deduction and transaction recording.
3. On success, updates local record to `SUCCESS` with the transaction ID.
4. Returns response to caller.

### User Registration Flow

1. Check if email already exists in Keycloak (reject duplicates).
2. Verify user exists in Core Banking by identification number.
3. Validate email matches Core Banking record.
4. Create user in Keycloak (disabled, email unverified).
5. Save local user record with `PENDING` status and Keycloak `authId`.
6. Admin later approves via PATCH (enables Keycloak user, sets email verified, updates status to `APPROVED`).

### User Status Lifecycle

```
PENDING -> APPROVED -> DISABLED -> BLACKLIST
```

---

## 5. Integration Points

### Keycloak Integration

- **Version:** 23.0.7
- **Protocol:** Admin REST API via `keycloak-admin-client` library
- **Authentication:** Client credentials grant (service account)
- **Configuration:**
  - `app.config.keycloak.server-url` - Keycloak base URL
  - `app.config.keycloak.realm` - Target realm
  - `app.config.keycloak.clientId` - Client ID
  - `app.config.keycloak.client-secret` - Client secret
- **Operations:** Create user, update user, search by email, read by ID
- **Realm Import:** Pre-configured realm exported at `docker-compose/keycloak/realm-export.json`
- **API Gateway JWT:** Validates tokens against Keycloak's JWK endpoint (`spring.security.oauth2.resourceserver.jwt.jwk-set-uri`)

### RabbitMQ (Planned)

- Referenced in README as notification mechanism for fund transfers and payments.
- **Not yet implemented** in the current codebase - no RabbitMQ dependencies or message publishers exist.

### Zipkin Distributed Tracing

- **Version:** Zipkin 3 (Docker image: `openzipkin/zipkin:3`)
- **Port:** 9411
- **Integration:** Micrometer Tracing with Brave bridge (`micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`)
- **Coverage:** All services include tracing dependencies; Feign calls are instrumented via `feign-micrometer`.

### Database Connections

| Service | Database | Schema |
|---------|----------|--------|
| Core Banking | MySQL | `banking_core_service` |
| User Service | MySQL | `banking_core_user_service` |
| Fund Transfer Service | MySQL | `banking_core_fund_transfer_service` |
| Utility Payment Service | MySQL | `banking_core_utility_payment_service` |
| Keycloak | PostgreSQL 15 | `keycloak` |

- **Connection Details:** Managed via Spring Cloud Config (remote Git repo).
- **Migration:** Flyway for Core Banking Service (3 migration scripts); other services use JPA auto-DDL.
- **Test DB:** H2 in-memory for unit tests.

### Spring Cloud Config

- **Config Repository:** `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Branch:** `main`
- **Search Path:** `configuration/`
- **Profile-specific bootstrap:** Each service has `bootstrap.yml` (local) and `bootstrap-docker.yml` (Docker) pointing to the config server.

---

## 6. Build and Deployment Pipeline Summary

### Build System

- **Build Tool:** Gradle (per-service `build.gradle`, no multi-module root build)
- **Java Version:** 21 (Eclipse Temurin)
- **Spring Boot:** 3.2.4
- **Spring Cloud:** 2023.0.0
- **Artifact:** Fat JAR per service (`*-0.0.1-SNAPSHOT.jar`)

### Docker

- **Base Image:** `eclipse-temurin:21.0.2_13-jre-alpine`
- **Dockerfile Pattern:** Each service has its own Dockerfile:
  1. Copy fat JAR as `app.jar`
  2. Copy `wait-for-it.sh` for startup ordering
  3. Install bash (Alpine)
  4. Entrypoint: `java -jar -Dspring.profiles.active=docker /app.jar`
- **Images:** Published as `javatodev/<service-name>`

### Docker Compose Orchestration

- **Network:** Bridge network `javatodev_ib_network` with subnet `172.25.0.0/16`
- **Static IPs:** Each container has a fixed IP address
- **Startup Order:** `wait-for-it.sh` ensures services wait for:
  1. Service Registry (port 8081)
  2. Config Server (port 8090)
  3. MySQL (port 3306) - for data services
- **Volumes:** Persistent MySQL and PostgreSQL data volumes
- **MySQL Init:** Custom Dockerfile runs `privileges.sql` to create user and 4 databases

### Deployment Profiles

| Profile | Config Source | Database Host |
|---------|-------------|--------------|
| `default` (dev) | localhost:8090 | localhost:3306 |
| `docker` | internet-banking-config-server:8090 | mysql_core_db:3306 |

### Testing

- **Framework:** JUnit 5 (via `spring-boot-starter-test`)
- **Test DB:** H2 in-memory
- **Coverage:** Unit tests exist only in `core-banking-service` (AccountServiceTest, TransactionServiceTest, UserServiceTest). Other services have only the default Spring Boot application context test.
