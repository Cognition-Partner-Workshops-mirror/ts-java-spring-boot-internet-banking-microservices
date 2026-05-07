# Application Knowledge Base

## 1. Architecture Overview

### System Topology

This is a Java 21 / Spring Boot 3.2.4 internet banking application composed of **6 microservices** communicating via REST (OpenFeign) with centralized configuration and service discovery.

```
┌──────────────────────────────────────────────────────────────────────────┐
│                           Client Applications                            │
└────────────────────────────────────┬─────────────────────────────────────┘
                                     │ HTTPS (OAuth2 Bearer Token)
                                     ▼
┌──────────────────────────────────────────────────────────────────────────┐
│              internet-banking-api-gateway (:8082)                         │
│              Spring Cloud Gateway + OAuth2 Resource Server               │
└───────┬──────────────┬────────────────────┬──────────────┬───────────────┘
        │              │                    │              │
        ▼              ▼                    ▼              ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ user-service │ │fund-transfer │ │utility-payment│ │core-banking  │
│   (:8083)    │ │  (:8084)     │ │   (:8085)    │ │  (:8092)     │
└──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └──────────────┘
       │                │                │                ▲
       │                └────────────────┴────────────────┘
       │                    OpenFeign (service-to-service)
       ▼
┌──────────────┐
│   Keycloak   │
│   (:8080)    │
└──────────────┘
```

### Microservices

| Service | Port | Role | Database |
|---------|------|------|----------|
| `internet-banking-api-gateway` | 8082 | API Gateway, OAuth2 enforcement, request routing | None |
| `internet-banking-user-service` | 8083 | User registration, profile management, Keycloak integration | `banking_core_user_service` (MySQL) |
| `internet-banking-fund-transfer-service` | 8084 | Account-to-account fund transfers | `banking_core_fund_transfer_service` (MySQL) |
| `internet-banking-utility-payment-service` | 8085 | Third-party utility bill payments | `banking_core_utility_payment_service` (MySQL) |
| `core-banking-service` | 8092 | System of record: accounts, users, ledger, transactions | `banking_core_service` (MySQL) |
| `internet-banking-service-registry` | 8081 | Netflix Eureka discovery server | None |
| `internet-banking-config-server` | 8090 | Spring Cloud Config (Git-backed) | None |

### Communication Patterns

| Pattern | Technology | Usage |
|---------|-----------|-------|
| Synchronous REST | Spring Cloud OpenFeign | Service-to-service calls (fund-transfer → core-banking, utility-payment → core-banking, user-service → core-banking) |
| Service Discovery | Netflix Eureka | All services register/discover via Eureka |
| Centralized Config | Spring Cloud Config Server | Configuration stored in external Git repo, fetched at bootstrap |
| API Gateway Routing | Spring Cloud Gateway | Path-based routing to downstream services |
| Authentication | OAuth2 / Keycloak | JWT validation at gateway, `X-Auth-Id` header propagation |
| Distributed Tracing | Micrometer Tracing + Zipkin | Trace propagation across all services |
| Async Messaging | RabbitMQ | Mentioned in architecture but **not yet implemented** (Notification Service pending) |

### Infrastructure Components

| Component | Image/Version | Purpose |
|-----------|--------------|---------|
| MySQL 8.4.0 | Custom Dockerfile | Primary database for all business services |
| PostgreSQL 15 | `postgres:15` | Keycloak identity store |
| Keycloak 23.0.7 | `quay.io/keycloak/keycloak:23.0.7` | Identity & access management |
| Zipkin 3 | `openzipkin/zipkin:3` | Distributed tracing backend |
| Eureka | Embedded in service-registry | Service discovery |

---

## 2. Data Model Documentation

### Core Banking Service (`banking_core_service`)

#### `banking_core_user`
| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Internal user ID |
| `first_name` | VARCHAR(255) | User's first name |
| `last_name` | VARCHAR(255) | User's last name |
| `email` | VARCHAR(255) | User's email address |
| `identification_number` | VARCHAR(255) | National ID / NIC number |

#### `banking_core_account`
| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Internal account ID |
| `number` | VARCHAR(255) | Account number (e.g., "100015003000") |
| `type` | VARCHAR(255) / ENUM | Account type: `SAVINGS_ACCOUNT` |
| `status` | VARCHAR(255) / ENUM | Account status: `ACTIVE`, `CLOSED` |
| `actual_balance` | DECIMAL(19,2) | Actual ledger balance |
| `available_balance` | DECIMAL(19,2) | Available balance (may differ from actual) |
| `user_id` | BIGINT (FK → `banking_core_user.id`) | Account owner |

#### `banking_core_transaction`
| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Transaction record ID |
| `amount` | DECIMAL(19,2) | Transaction amount (negative for debits) |
| `transaction_type` | VARCHAR(30) | `FUND_TRANSFER` or `UTILITY_PAYMENT` |
| `reference_number` | VARCHAR(50) | Reference (destination account or bill ref) |
| `transaction_id` | VARCHAR(50) | UUID transaction identifier |
| `account_id` | BIGINT (FK → `banking_core_account.id`) | Associated account |

#### `banking_core_utility_account`
| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Utility provider ID |
| `number` | VARCHAR(255) | Provider account number |
| `provider_name` | VARCHAR(255) | Provider name (e.g., VODAFONE, AIRTEL) |

### Internet Banking User Service (`banking_core_user_service`)

#### `user` (JPA-managed, Hibernate auto-DDL)
| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Internal ID |
| `auth_id` | VARCHAR | Keycloak user UUID |
| `identification` | VARCHAR | NIC/National ID linking to core banking user |
| `status` | VARCHAR / ENUM | `PENDING`, `APPROVED` |
| `created_date` | INSTANT | Audit: creation timestamp |
| `created_by` | VARCHAR | Audit: creator |
| `modified_date` | INSTANT | Audit: last modification |
| `modified_by` | VARCHAR | Audit: modifier |
| `version` | BIGINT | Optimistic locking version |

### Fund Transfer Service (`banking_core_fund_transfer_service`)

#### `fund_transfer` (JPA-managed, Hibernate auto-DDL)
| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Internal ID |
| `from_account` | VARCHAR | Source account number |
| `to_account` | VARCHAR | Destination account number |
| `amount` | DECIMAL | Transfer amount |
| `transaction_reference` | VARCHAR | UUID from core banking |
| `status` | VARCHAR / ENUM | `PENDING`, `SUCCESS`, `FAILED` |
| `created_date` | INSTANT | Audit: creation timestamp |
| `created_by` | VARCHAR | Audit: creator |
| `modified_date` | INSTANT | Audit: last modification |
| `modified_by` | VARCHAR | Audit: modifier |
| `version` | BIGINT | Optimistic locking version |

### Utility Payment Service (`banking_core_utility_payment_service`)

#### `utility_payment` (JPA-managed, Hibernate auto-DDL)
| Field | Type | Description |
|-------|------|-------------|
| `id` | BIGINT (PK, auto-increment) | Internal ID |
| `provider_id` | BIGINT | Utility provider ID |
| `amount` | DECIMAL | Payment amount |
| `reference_number` | VARCHAR | Bill reference number |
| `account` | VARCHAR | Source account number |
| `transaction_id` | VARCHAR | UUID from core banking |
| `status` | VARCHAR / ENUM | `PROCESSING`, `SUCCESS`, `FAILED` |
| `created_date` | INSTANT | Audit: creation timestamp |
| `created_by` | VARCHAR | Audit: creator |
| `modified_date` | INSTANT | Audit: last modification |
| `modified_by` | VARCHAR | Audit: modifier |
| `version` | BIGINT | Optimistic locking version |

### Entity Relationships

```
banking_core_user (1) ──────< (N) banking_core_account
banking_core_account (1) ────< (N) banking_core_transaction
banking_core_utility_account (standalone - referenced by ID)

user-service.user ──── links via identification ──── core-banking.banking_core_user
user-service.user ──── links via auth_id ──── Keycloak user UUID
fund_transfer ──── references account numbers from core-banking
utility_payment ──── references provider_id from core-banking utility accounts
```

---

## 3. API Surface Map

### API Gateway Routes (all prefixed through gateway at `:8082`)

| Gateway Path Prefix | Target Service |
|---------------------|---------------|
| `/user/**` | internet-banking-user-service |
| `/fund-transfer/**` | internet-banking-fund-transfer-service |
| `/utility-payment/**` | internet-banking-utility-payment-service |
| `/banking-core/**` | core-banking-service |

### Core Banking Service (`:8092`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| GET | `/api/v1/account/bank-account/{account_number}` | Get bank account by number | - | `BankAccount{number, type, status, availableBalance, actualBalance}` |
| GET | `/api/v1/account/util-account/{account_name}` | Get utility account by provider name | - | `UtilityAccount{id, number, providerName}` |
| POST | `/api/v1/transaction/fund-transfer` | Process internal fund transfer | `{fromAccount, toAccount, amount}` | `{message, transactionId}` |
| POST | `/api/v1/transaction/util-payment` | Process utility payment | `{providerId, amount, referenceNumber, account}` | `{message, transactionId}` |
| GET | `/api/v1/user/{identification}` | Get user by NIC/identification | - | `User{id, firstName, lastName, email, identificationNumber, accounts[]}` |
| GET | `/api/v1/user` | List users (paginated) | Pageable params | `Page<User>` |

### Internet Banking User Service (`:8083`)

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| POST | `/api/v1/bank-users/register` | Register new banking user | `{email, password, identification}` | `User{id, authId, identification, status}` |
| PATCH | `/api/v1/bank-users/update/{id}` | Update user (approve/reject) | `{status}` | `User{id, authId, identification, status}` |
| GET | `/api/v1/bank-users` | List all users (paginated) | Pageable params | `List<User>` |
| GET | `/api/v1/bank-users/{id}` | Get user by ID | - | `User{id, authId, identification, status}` |

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

### Service Registry (`:8081`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/eureka/apps` | List registered services |
| GET | `/` | Eureka dashboard (web UI) |

### Config Server (`:8090`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/{application}/{profile}` | Fetch config for application/profile |
| GET | `/{application}/{profile}/{label}` | Fetch config for specific Git label |

### Actuator Endpoints (all services)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/actuator/health` | Health check |
| GET | `/actuator/info` | Application info (git properties) |
| GET | `/actuator` | List available actuator endpoints |

---

## 4. Key Business Logic Inventory

### User Registration Flow

1. Client calls `POST /api/v1/bank-users/register` with `{email, password, identification}`
2. User service checks Keycloak for existing email (prevents duplicates)
3. User service calls core-banking `/api/v1/user/{identification}` to verify the user exists in the banking core
4. Validates email matches the core banking record
5. Creates Keycloak user (disabled, email unverified) with provided password
6. Stores local user record with `status=PENDING` and Keycloak `authId`
7. Admin later calls `PATCH /api/v1/bank-users/update/{id}` with `status=APPROVED` to enable the Keycloak user

### Fund Transfer Flow

1. Client calls `POST /api/v1/transfer` with `{fromAccount, toAccount, amount}`
2. Fund transfer service saves a `PENDING` record in its local database
3. Fund transfer service calls core-banking `POST /api/v1/transaction/fund-transfer` via Feign
4. Core banking validates:
   - Both accounts exist (throws `EntityNotFoundException` otherwise)
   - Source account has sufficient balance (throws `InsufficientFundsException` if balance < amount)
5. Core banking executes transfer atomically (`@Transactional`):
   - Debits source account (actualBalance - amount, availableBalance recalculated)
   - Credits destination account (actualBalance + amount, availableBalance recalculated)
   - Creates two transaction records (debit and credit) with same `transactionId`
6. Fund transfer service updates local record to `SUCCESS` with transaction reference
7. Returns success response to client

### Utility Payment Flow

1. Client calls `POST /api/v1/utility-payment` with `{providerId, amount, referenceNumber, account}`
2. Utility payment service saves a `PROCESSING` record in its local database
3. Utility payment service calls core-banking `POST /api/v1/transaction/util-payment` via Feign
4. Core banking validates:
   - Source account exists
   - Sufficient balance
   - Utility provider exists (by `providerId`)
5. Core banking debits source account and creates transaction record
6. Utility payment service updates local record to `SUCCESS` with transaction ID
7. Returns success response to client

### Balance Validation Rules

- `actualBalance` must be >= 0
- `actualBalance` must be >= requested transfer/payment amount
- Violation throws `InsufficientFundsException` with error code `INSUFFICIENT_FUNDS`

### Authentication & Authorization

- API Gateway enforces OAuth2 JWT validation on all routes except:
  - `POST /user/api/v1/bank-users/register` (public)
  - `/actuator/**` endpoints (public)
- Gateway extracts `Principal.getName()` from JWT and forwards as `X-Auth-Id` header
- Downstream services read `X-Auth-Id` via servlet filter (`AppAuthUserFilter`)
- User identity stored in thread-local `ApiRequestContextHolder`
- Used for JPA auditing (`createdBy`, `modifiedBy`)

---

## 5. Integration Points

### Keycloak (Identity & Access Management)

| Aspect | Details |
|--------|---------|
| Version | 23.0.7 |
| Admin URL | `http://keycloak_web:8080` (Docker) / `http://localhost:8080` (local) |
| Realm | Imported from `docker-compose/keycloak/realm-export.json` |
| Integration | `keycloak-admin-client:24.0.4` in user-service |
| Auth Flow | Client credentials (`client_credentials` grant) for admin operations |
| Config Properties | `app.config.keycloak.server-url`, `app.config.keycloak.realm`, `app.config.keycloak.clientId`, `app.config.keycloak.client-secret` |
| Gateway JWT | `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` (JWK endpoint for token validation) |

### Zipkin (Distributed Tracing)

| Aspect | Details |
|--------|---------|
| Version | 3 (OpenZipkin) |
| URL | `http://172.25.0.12:9411` (Docker) / `http://localhost:9411` (local) |
| Integration | `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` in all services |
| Trace Propagation | Automatic via Micrometer instrumentation + Feign micrometer |

### RabbitMQ (Async Messaging)

| Aspect | Details |
|--------|---------|
| Status | **NOT YET IMPLEMENTED** |
| Planned Use | Notification service to consume messages from fund-transfer and utility-payment services |
| Note | Referenced in README but no RabbitMQ container in docker-compose, no AMQP dependencies in any service |

### Database Connections

| Service | Database | Schema | Migration |
|---------|----------|--------|-----------|
| core-banking-service | MySQL 8.4.0 | `banking_core_service` | Flyway (3 versioned migrations) |
| internet-banking-user-service | MySQL 8.4.0 | `banking_core_user_service` | Hibernate auto-DDL |
| internet-banking-fund-transfer-service | MySQL 8.4.0 | `banking_core_fund_transfer_service` | Hibernate auto-DDL |
| internet-banking-utility-payment-service | MySQL 8.4.0 | `banking_core_utility_payment_service` | Hibernate auto-DDL |
| Keycloak | PostgreSQL 15 | `keycloak` | Managed by Keycloak |

### Inter-Service Communication (Feign Clients)

| Source Service | Target Service | Endpoints Called |
|---------------|---------------|-----------------|
| user-service | core-banking-service | `GET /api/v1/user/{identification}` |
| fund-transfer-service | core-banking-service | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/fund-transfer` |
| utility-payment-service | core-banking-service | `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/util-payment` |

---

## 6. Build and Deployment Pipeline Summary

### Build System

- **Build Tool**: Gradle (per-service, no multi-project build)
- **Java Version**: 21 (Eclipse Temurin 21.0.2_13 JRE Alpine in Docker)
- **Spring Boot**: 3.2.4
- **Spring Cloud**: 2023.0.0
- **Plugins**: `spring-boot`, `spring-dependency-management`, `gradle-git-properties`

### Docker Build

Each service has its own `Dockerfile`:
```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/<service>-0.0.1-SNAPSHOT.jar app.jar
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh && apk add --no-cache bash
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

### Docker Compose Deployment

- **File**: `docker-compose/docker-compose.yml`
- **Network**: Custom bridge network `javatodev_ib_network` (subnet `172.25.0.0/16`) with static IPs
- **Startup Ordering**: `wait-for-it.sh` scripts ensure dependencies are healthy before app startup
- **Profiles**: `docker` profile activated via `-Dspring.profiles.active=docker`
- **Volumes**: Persistent MySQL data (`mysqldata`), PostgreSQL data (`postgres_data`)

### Startup Order (via wait-for-it)

1. MySQL, PostgreSQL, Zipkin, Keycloak (infrastructure, no dependencies)
2. `internet-banking-config-server` (no dependencies)
3. `internet-banking-service-registry` (no dependencies)
4. `internet-banking-api-gateway` (waits for: service-registry, config-server)
5. `internet-banking-user-service` (waits for: service-registry, config-server, MySQL)
6. `internet-banking-fund-transfer-service` (waits for: service-registry, config-server, MySQL)
7. `internet-banking-utility-payment-service` (waits for: service-registry, config-server, MySQL)
8. `core-banking-service` (waits for: service-registry, config-server, MySQL)

### Configuration Management

- **Config Server**: Backed by Git repository at `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- **Bootstrap Context**: Each service uses `bootstrap.yml` / `bootstrap-docker.yml` to locate config server
- **Profile-specific**: `dev` and `docker` profiles with separate bootstrap configs
- **Externalized**: Database URLs, Keycloak settings, Eureka URLs all managed centrally

### CI/CD

- **GitHub Actions**: No CI/CD workflows present (`.github/` contains only `FUNDING.yml`)
- **Testing**: JUnit 5 with H2 in-memory database for unit tests (Flyway disabled in test profile)
- **Postman Collection**: Available in `postman_collection/` for manual API testing
