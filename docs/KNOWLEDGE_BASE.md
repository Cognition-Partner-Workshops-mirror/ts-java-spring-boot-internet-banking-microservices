# Knowledge Base

## Architecture Overview

- 7 deployable units: API Gateway (:8082), Service Registry (:8081), Config Server (:8090), User Service (:8083), Fund Transfer Service (:8084), Utility Payment Service (:8085), Core Banking Service (:8092)
- External infrastructure: Keycloak (:8080) with PostgreSQL, MySQL (:3306), Zipkin (:9411)
- Communication: All synchronous REST via OpenFeign; services resolve each other by Eureka service names
- API Gateway uses Spring Cloud Gateway (reactive) with OAuth2 resource server (JWT validation via Keycloak JWK URI)
- Gateway injects `X-Auth-Id` header via GlobalFilter in `GatewayConfiguration.java`
- Config Server pulls from external Git repo: `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`
- All business services use `bootstrap.yml` to connect to Config Server before starting
- Docker Compose uses a bridge network `javatodev_ib_network` (172.25.0.0/16) with static IPs
- Services use `wait-for-it.sh` scripts to wait for dependencies (registry, config server, MySQL)

## Data Model Documentation

### Core Banking Service (database: `banking_core_service`, Flyway-managed)

- `banking_core_user` (UserEntity): id, firstName, lastName, email, identificationNumber; has OneToMany to BankAccountEntity
- `banking_core_account` (BankAccountEntity): id, number, type (enum: AccountType), status (enum: AccountStatus), availableBalance, actualBalance; ManyToOne to UserEntity
- `banking_core_transaction` (TransactionEntity): id, amount, transactionType (enum: FUND_TRANSFER/UTILITY_PAYMENT), referenceNumber, transactionId; OneToOne to BankAccountEntity (NOTE: this is a bug, should be ManyToOne)
- `banking_core_utility_account` (UtilityAccountEntity): id, number, providerName

### User Service (database: `banking_core_user_service`)

- `user` (UserEntity extends AuditAware): id, authId, identification, status (enum: PENDING/APPROVED)

### Fund Transfer Service (database: `banking_core_fund_transfer_service`)

- `fund_transfer` (FundTransferEntity extends AuditAware): id, transactionReference, fromAccount, toAccount, amount, status (enum: PENDING/SUCCESS)

### Utility Payment Service (database: `banking_core_utility_payment_service`)

- `utility_payment` (UtilityPaymentEntity extends AuditAware): id, providerId, amount, referenceNumber, account, transactionId, status (enum: PROCESSING/SUCCESS)

AuditAware is a MappedSuperclass providing createdBy/createdDate/lastModifiedBy/lastModifiedDate fields.

## API Surface Map

### Core Banking Service (`/api/v1/...`)

- `GET /api/v1/account/bank-account/{account_number}` → BankAccount DTO
- `GET /api/v1/account/util-account/{account_name}` → UtilityAccount DTO
- `POST /api/v1/transaction/fund-transfer` → FundTransferResponse {message, transactionId}; Body: FundTransferRequest {fromAccount, toAccount, amount}
- `POST /api/v1/transaction/util-payment` → UtilityPaymentResponse {message, transactionId}; Body: UtilityPaymentRequest {providerId, amount, referenceNumber, account}
- `GET /api/v1/user/{identification}` → User DTO
- `GET /api/v1/user` (paginated) → List<User>

### User Service (`/api/v1/bank-users/...`)

- `POST /api/v1/bank-users/register` → User; Body: User {email, password, identification}
- `PATCH /api/v1/bank-users/update/{id}` → User; Body: UserUpdateRequest {status}
- `GET /api/v1/bank-users` (paginated) → List<User>
- `GET /api/v1/bank-users/{id}` → User

### Fund Transfer Service (`/api/v1/transfer/...`)

- `POST /api/v1/transfer` → FundTransferResponse; Body: FundTransferRequest {fromAccount, toAccount, amount, authID}
- `GET /api/v1/transfer` (paginated) → List<FundTransfer>

### Utility Payment Service (`/api/v1/utility-payment/...`)

- `POST /api/v1/utility-payment` → UtilityPaymentResponse; Body: UtilityPaymentRequest {providerId, amount, referenceNumber, account}
- `GET /api/v1/utility-payment` (paginated) → List<UtilityPayment>

### Gateway Routes (prefixed paths routed to services)

- `/user/**` → internet-banking-user-service
- `/fund-transfer/**` → internet-banking-fund-transfer-service
- `/utility-payment/**` → internet-banking-utility-payment-service
- `/banking-core/**` → core-banking-service

## Key Business Logic Inventory

### Fund Transfer Flow

1. Fund Transfer Service receives request, saves entity with PENDING status
2. Calls Core Banking Service via Feign (`BankingCoreFeignClient.fundTransfer()`)
3. Core Banking validates both accounts exist, validates sender has sufficient balance
4. Core Banking debits sender, credits receiver, creates two TransactionEntity records (one per account)
5. Fund Transfer Service updates local entity to SUCCESS with transaction reference
6. BUG: In `TransactionService.internalFundTransfer()`, availableBalance is set to actualBalance minus amount AFTER actualBalance was already reduced, causing double-subtraction

### Utility Payment Flow

1. Utility Payment Service receives request, saves entity with PROCESSING status
2. Calls Core Banking Service via Feign (`BankingCoreRestClient.utilityPayment()`)
3. Core Banking validates account exists and has sufficient balance, looks up utility provider
4. Core Banking debits account, creates TransactionEntity
5. Utility Payment Service updates local entity to SUCCESS
6. BUG: In `TransactionService.utilPayment()`, line 64 subtracts from already-subtracted actualBalance for availableBalance

### User Registration Flow

1. Check if email already exists in Keycloak
2. Verify user exists in Core Banking Service by identification number
3. Validate email matches Core Banking record
4. Create user in Keycloak with credentials (initially disabled, email unverified)
5. Save local UserEntity with PENDING status and Keycloak authId
6. Admin later approves via PATCH endpoint, which enables user in Keycloak

## Integration Points

- **Keycloak**: User Service uses `keycloak-admin-client:24.0.4` via `KeycloakManager`/`KeycloakUserService` for user CRUD. Gateway validates JWT tokens against Keycloak's JWK endpoint. Realm: `javatodev-internet-banking`. Two clients: `javatodev-internet-banking-api-client` (for API auth) and `javatodev-internet-banking-kc-api-client` (for admin operations).
- **RabbitMQ**: Mentioned in README but NOT implemented. No RabbitMQ dependency in any build.gradle. Notification Service is "PENDING Development".
- **Zipkin**: All services include `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` for distributed tracing. Zipkin runs at :9411.
- **MySQL**: Single MySQL 8.4.0 instance with 4 databases created via `privileges.sql`. Core Banking uses Flyway for migrations. Other services likely use JPA auto-DDL.
- **Spring Cloud Config**: All services (except registry) fetch config from Config Server, which pulls from a GitHub repo.

## Build and Deployment

- Each service is a standalone Gradle project (no multi-project build, no settings.gradle at root)
- All use Spring Boot 3.2.4, Spring Cloud 2023.0.0, Java 21
- Docker Compose orchestrates all containers with static IPs
- No CI/CD pipeline (`.github/` directory is empty)
- No Dockerfile in service directories (images are pre-built as `javatodev/*`)
- MySQL uses a custom Dockerfile that copies `privileges.sql` for init
