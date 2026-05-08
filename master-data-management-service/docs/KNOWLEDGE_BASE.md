# Master Data Management Service — Knowledge Base

## 1. Architecture Overview

### 1.1 Platform Context

The `master-data-management-service` is a new microservice added to the **Internet Banking Microservices** platform. It replicates EBX MDM (Master Data Management) capabilities within the existing Spring Boot ecosystem.

**Platform services:**

| Service | Port | IP (Docker) | Description |
|---|---|---|---|
| internet-banking-config-server | 8090 | 172.25.0.8 | Spring Cloud Config Server (centralized config) |
| internet-banking-service-registry | 8081 | 172.25.0.7 | Eureka Service Registry (service discovery) |
| internet-banking-api-gateway | 8082 | 172.25.0.6 | Spring Cloud Gateway (API routing) |
| internet-banking-user-service | 8083 | 172.25.0.5 | User authentication & profile management |
| internet-banking-fund-transfer-service | 8084 | 172.25.0.4 | Fund transfer operations |
| internet-banking-utility-payment-service | 8085 | 172.25.0.3 | Utility bill payments |
| core-banking-service | 8092 | 172.25.0.2 | Core banking operations (accounts, transactions) |
| **master-data-management-service** | **8093** | **172.25.0.13** | **EBX MDM POC (this service)** |

**Supporting infrastructure:**

| Component | Port | IP (Docker) | Description |
|---|---|---|---|
| MySQL 8.4 | 3306 | 172.25.0.9 | Primary relational database |
| Keycloak 23.0.7 | 8080 | 172.25.0.11 | Identity provider (OAuth2/OIDC) |
| PostgreSQL 15 | — | 172.25.0.10 | Keycloak backing store |
| Zipkin 3 | 9411 | 172.25.0.12 | Distributed tracing |

### 1.2 Communication Patterns

- **Service Discovery**: Eureka client registration — the MDM service registers with the Eureka server on startup.
- **Config Server**: Externalized configuration via Spring Cloud Config (`bootstrap.yml` / `bootstrap-docker.yml`).
- **API Gateway**: All external traffic routes through the API Gateway on port 8082.
- **JDBC**: Direct JDBC connections to target environment databases for environment sync/comparison.
- **Distributed Tracing**: Micrometer + Brave + Zipkin for request tracing across services.

### 1.3 Technology Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 21 |
| Framework | Spring Boot | 3.2.4 |
| Cloud | Spring Cloud | 2023.0.0 |
| ORM | Spring Data JPA / Hibernate | (managed by Boot) |
| Database | MySQL | 8.4 |
| Security | Spring Security + BCrypt | (managed by Boot) |
| API Docs | springdoc-openapi (Swagger UI) | 2.1.0 |
| Build | Gradle | 8.x (wrapper) |
| Container | Docker (eclipse-temurin:21 Alpine) | — |

---

## 2. Data Model

### 2.1 Entity-Relationship Summary

```
DataspaceEntity (1) ──< (N) DatasetEntity (1) ──< (N) TableDefinitionEntity (1) ──< (N) ColumnDefinitionEntity
                                                           │
                                                           ├──< (N) MasterDataRecordEntity ──< (N) RecordHistoryEntity
                                                           │
                                                           └──< (N) WorkflowEntity

SnapshotEntity (1) ──< (N) SnapshotDataEntity
DataspaceEntity (1) ──< (N) SnapshotEntity

MdmUserEntity (1) ──< (N) PermissionEntity

EnvironmentConfigEntity (standalone)
```

### 2.2 Entity Details

#### DataspaceEntity (`mdm_dataspace`)
Isolated versioned environment for master data. Supports parent-child branching for release management.

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | Long | PK, auto-generated | Primary key |
| name | String | NOT NULL, UNIQUE | Dataspace name |
| description | String | — | Optional description |
| parentDataspaceId | Long | — | FK to parent dataspace (null for root) |
| status | DataspaceStatus | NOT NULL | OPEN / CLOSED / MERGED |
| createdDate, createdBy, modifiedDate, modifiedBy | Audit fields | — | Via `AuditAware` superclass |
| version | long | @Version | Optimistic locking |

#### DatasetEntity (`mdm_dataset`)
Named collection of tables within a dataspace.

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | Long | PK | Primary key |
| name | String | NOT NULL | Dataset name |
| description | String | — | Optional description |
| dataspace_id | Long (FK) | NOT NULL | Owning dataspace |
| Audit + version fields | — | — | Via `AuditAware` |

#### TableDefinitionEntity (`mdm_table_definition`)
Schema definition for a master data table.

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | Long | PK | Primary key |
| name | String | NOT NULL | Table name (validated as SQL identifier) |
| description | String | — | Optional description |
| dataset_id | Long (FK) | NOT NULL | Owning dataset |
| columns | List | @OneToMany(cascade ALL, orphanRemoval) | Column definitions, ordered by ordinal |
| Audit + version fields | — | — | Via `AuditAware` |

#### ColumnDefinitionEntity (`mdm_column_definition`)
Single column schema within a table definition.

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | Long | PK | Primary key |
| name | String | NOT NULL | Column name |
| dataType | String | NOT NULL | STRING, INTEGER, DECIMAL, DATE, BOOLEAN |
| required | boolean | — | Whether mandatory |
| uniqueKey | boolean | — | Whether unique across records |
| ordinal | int | — | Display/processing order |
| table_definition_id | Long (FK) | NOT NULL | Owning table (@ManyToOne, owning side) |

#### MasterDataRecordEntity (`mdm_record`)
Single row of master data stored as JSON.

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | Long | PK | Primary key |
| table_definition_id | Long (FK) | NOT NULL | Owning table |
| data | String | JSON column | `{"column_name": "value", ...}` |
| status | RecordStatus | NOT NULL | DRAFT / PENDING_APPROVAL / APPROVED / REJECTED |
| Audit + version fields | — | — | Via `AuditAware` |

#### RecordHistoryEntity (`mdm_record_history`)
Audit trail for record changes.

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | Long | PK | Primary key |
| recordId | Long | — | The record that changed |
| tableDefinitionId | Long | — | Owning table |
| previousData | String | LONGTEXT | JSON before change |
| newData | String | LONGTEXT | JSON after change |
| changeType | String | — | INSERT / UPDATE / DELETE |
| changedBy | String | — | User who made the change |
| changedAt | Instant | — | Timestamp |

#### SnapshotEntity (`mdm_snapshot`)
Point-in-time capture of a dataspace. Immutable once created.

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | Long | PK | Primary key |
| name | String | NOT NULL | Snapshot name |
| description | String | — | Optional description |
| dataspace_id | Long (FK) | NOT NULL | Source dataspace |
| createdBy | String | — | User who created |
| createdAt | Instant | — | Timestamp |

#### SnapshotDataEntity (`mdm_snapshot_data`)
Serialized record data for one table within a snapshot.

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | Long | PK | Primary key |
| snapshot_id | Long (FK) | NOT NULL | Owning snapshot |
| tableDefinitionId | Long | — | Table reference |
| tableName | String | — | Denormalized table name |
| recordsJson | String | LONGTEXT | JSON array of all approved records |

#### WorkflowEntity (`mdm_workflow`)
Maker-checker approval request for a data change.

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | Long | PK | Primary key |
| recordId | Long | — | Target record (null for new inserts) |
| tableDefinitionId | Long | — | Owning table |
| changeType | ChangeType | NOT NULL | INSERT / UPDATE / DELETE |
| proposedData | String | JSON | New data proposed |
| previousData | String | JSON | Data before change |
| status | WorkflowStatus | NOT NULL | PENDING / APPROVED / REJECTED |
| requestedBy | String | — | User who requested |
| reviewedBy | String | — | Reviewer user |
| reviewComment | String | — | Reviewer comment |
| reviewedAt | Instant | — | Review timestamp |
| Audit + version fields | — | — | Via `AuditAware` |

#### MdmUserEntity (`mdm_user`)
MDM platform user with role-based access.

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | Long | PK | Primary key |
| username | String | NOT NULL, UNIQUE | Login username |
| email | String | NOT NULL, UNIQUE | Email address |
| password | String | NOT NULL | BCrypt-hashed password |
| role | MdmUserRole | NOT NULL | ADMIN / DATA_STEWARD / VIEWER |
| status | MdmUserStatus | NOT NULL | ACTIVE / INACTIVE |
| Audit + version fields | — | — | Via `AuditAware` |

#### PermissionEntity (`mdm_permission`)
Fine-grained access control on resources.

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | Long | PK | Primary key |
| user_id | Long (FK) | NOT NULL | Target user |
| resourceType | ResourceType | NOT NULL | DATASPACE / DATASET / TABLE |
| resourceId | Long | NOT NULL | Specific resource instance |
| level | PermissionLevel | NOT NULL | READ / WRITE / ADMIN |

#### EnvironmentConfigEntity (`mdm_environment_config`)
JDBC connection details for target environments.

| Column | Type | Constraints | Description |
|---|---|---|---|
| id | Long | PK | Primary key |
| name | String | NOT NULL, UNIQUE | Display name (e.g., "DIT", "UAT") |
| type | EnvironmentType | NOT NULL | EBX / DIT / SIT / UAT / PROD |
| dbUrl | String | NOT NULL | JDBC URL |
| dbUsername | String | NOT NULL | DB username |
| dbPassword | String | NOT NULL | DB password (plaintext in POC) |
| Audit + version fields | — | — | Via `AuditAware` |

### 2.3 Enumerations

| Enum | Values | Description |
|---|---|---|
| DataspaceStatus | OPEN, CLOSED, MERGED | Dataspace lifecycle |
| RecordStatus | DRAFT, PENDING_APPROVAL, APPROVED, REJECTED | Record approval state |
| WorkflowStatus | PENDING, APPROVED, REJECTED | Workflow review state |
| ChangeType | INSERT, UPDATE, DELETE | Data change type |
| MdmUserRole | ADMIN, DATA_STEWARD, VIEWER | User roles |
| MdmUserStatus | ACTIVE, INACTIVE | User activation state |
| PermissionLevel | READ, WRITE, ADMIN | Access levels |
| ResourceType | DATASPACE, DATASET, TABLE | Securable resource types |
| EnvironmentType | EBX, DIT, SIT, UAT, PROD | Target environment types |

---

## 3. API Surface Map

All endpoints are served under port **8093** with base path `/api/v1`. Swagger UI is available at `/swagger-ui.html`.

### 3.1 Dataspace API (`/api/v1/dataspaces`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| POST | `/api/v1/dataspaces` | Create dataspace | `DataspaceRequest` (name, description, parentDataspaceId) | `DataspaceResponse` |
| GET | `/api/v1/dataspaces` | List dataspaces (paginated) | — (query: page, size, sort) | `List<DataspaceResponse>` |
| GET | `/api/v1/dataspaces/{id}` | Get dataspace by ID | — | `DataspaceResponse` |
| PUT | `/api/v1/dataspaces/{id}` | Update dataspace | `DataspaceRequest` | `DataspaceResponse` |
| DELETE | `/api/v1/dataspaces/{id}` | Close dataspace | — | `DataspaceResponse` |
| POST | `/api/v1/dataspaces/{id}/merge` | Merge child into parent | — | `DataspaceResponse` |

### 3.2 Dataset API (`/api/v1/datasets`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| POST | `/api/v1/datasets` | Create dataset | `DatasetRequest` (name, description, dataspaceId) | `DatasetResponse` |
| GET | `/api/v1/datasets/dataspace/{dataspaceId}` | List datasets by dataspace | — | `List<DatasetResponse>` |
| GET | `/api/v1/datasets/{id}` | Get dataset by ID | — | `DatasetResponse` |
| PUT | `/api/v1/datasets/{id}` | Update dataset | `DatasetRequest` | `DatasetResponse` |
| DELETE | `/api/v1/datasets/{id}` | Delete dataset | — | `200 OK` |

### 3.3 Table Definition API (`/api/v1/tables`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| POST | `/api/v1/tables` | Create table with columns | `TableDefinitionRequest` (name, description, datasetId, columns[]) | `TableDefinitionResponse` |
| GET | `/api/v1/tables/dataset/{datasetId}` | List tables by dataset | — | `List<TableDefinitionResponse>` |
| GET | `/api/v1/tables/{id}` | Get table with columns | — | `TableDefinitionResponse` |
| PUT | `/api/v1/tables/{id}` | Update table | `TableDefinitionRequest` | `TableDefinitionResponse` |
| DELETE | `/api/v1/tables/{id}` | Delete table and columns | — | `200 OK` |

### 3.4 Master Data Record API (`/api/v1/records`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| POST | `/api/v1/records` | Insert record (triggers workflow) | `MasterDataRecordRequest` (tableId, data{}) | `MasterDataRecordResponse` |
| GET | `/api/v1/records/table/{tableId}` | List records by table (paginated) | — | `List<MasterDataRecordResponse>` |
| GET | `/api/v1/records/{id}` | Get record by ID | — | `MasterDataRecordResponse` |
| PUT | `/api/v1/records/{id}` | Update record (triggers workflow) | `MasterDataRecordRequest` | `MasterDataRecordResponse` |
| DELETE | `/api/v1/records/{id}` | Request deletion (triggers workflow) | — | `200 OK` |
| GET | `/api/v1/records/{id}/history` | Get audit history | — | `List<RecordHistoryResponse>` |

### 3.5 Snapshot API (`/api/v1/snapshots`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| POST | `/api/v1/snapshots` | Create snapshot of dataspace | `SnapshotRequest` (name, description, dataspaceId) | `SnapshotResponse` |
| GET | `/api/v1/snapshots/dataspace/{dataspaceId}` | List snapshots by dataspace | — | `List<SnapshotResponse>` |
| GET | `/api/v1/snapshots/{id}` | Get snapshot by ID | — | `SnapshotResponse` |
| POST | `/api/v1/snapshots/compare` | Compare two snapshots | `SnapshotCompareRequest` (sourceSnapshotId, targetSnapshotId) | `SnapshotCompareResponse` |

### 3.6 Workflow API (`/api/v1/workflows`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| GET | `/api/v1/workflows` | List pending workflows (paginated) | — | `List<WorkflowResponse>` |
| GET | `/api/v1/workflows/{id}` | Get workflow by ID | — | `WorkflowResponse` |
| POST | `/api/v1/workflows/{id}/approve` | Approve workflow | `WorkflowReviewRequest` (reviewedBy, reviewComment) | `WorkflowResponse` |
| POST | `/api/v1/workflows/{id}/reject` | Reject workflow | `WorkflowReviewRequest` | `WorkflowResponse` |

### 3.7 MDM User API (`/api/v1/mdm-users`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| POST | `/api/v1/mdm-users` | Create user | `MdmUserRequest` (username, email, password, role) | `MdmUserResponse` |
| GET | `/api/v1/mdm-users` | List users (paginated) | — | `List<MdmUserResponse>` |
| GET | `/api/v1/mdm-users/{id}` | Get user by ID | — | `MdmUserResponse` |
| PUT | `/api/v1/mdm-users/{id}` | Update user | `MdmUserRequest` | `MdmUserResponse` |
| DELETE | `/api/v1/mdm-users/{id}` | Deactivate user (soft delete) | — | `MdmUserResponse` |

### 3.8 Permission API (`/api/v1/permissions`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| POST | `/api/v1/permissions` | Grant permission | `PermissionRequest` (userId, resourceType, resourceId, level) | `PermissionResponse` |
| GET | `/api/v1/permissions/user/{userId}` | Get user permissions | — | `List<PermissionResponse>` |
| DELETE | `/api/v1/permissions/{id}` | Revoke permission | — | `200 OK` |
| GET | `/api/v1/permissions/check` | Check permission | Query: userId, resourceType, resourceId, level | `{"hasPermission": true/false}` |

### 3.9 Environment Sync API (`/api/v1/sync`)

| Method | Path | Description | Request Body | Response |
|---|---|---|---|---|
| POST | `/api/v1/sync/environments` | Register target environment | `EnvironmentConfigRequest` (name, type, dbUrl, dbUsername, dbPassword) | `EnvironmentConfigResponse` |
| GET | `/api/v1/sync/environments` | List registered environments | — | `List<EnvironmentConfigResponse>` |
| POST | `/api/v1/sync/compare` | Compare snapshot vs environment | `EnvironmentCompareRequest` (snapshotId, environmentId, tableName?) | `EnvironmentCompareResponse` |

---

## 4. Key Business Logic Inventory

### 4.1 Dataspace Lifecycle
- **Create**: New dataspace with optional `parentDataspaceId` for branching. Starts in `OPEN` status.
- **Close**: Transitions to `CLOSED`, preventing further modifications.
- **Merge**: Deep-copies all datasets, table definitions (with columns), and `APPROVED` records from child to parent. Child transitions to `MERGED`.

### 4.2 Maker-Checker Workflow
Every data change (INSERT, UPDATE, DELETE) goes through approval:
1. **Insert**: Record created as `DRAFT` + `WorkflowEntity` created as `PENDING`.
2. **Update**: Previous data saved to `RecordHistoryEntity`, record set to `PENDING_APPROVAL`, new workflow created.
3. **Delete**: Record set to `PENDING_APPROVAL`, workflow created with `DELETE` type.
4. **Approve**: Proposed data applied to record (`APPROVED` status). For deletes, record is physically deleted. History entry logged.
5. **Reject**: For inserts, draft record deleted. For updates/deletes, record reverted to `APPROVED` with previous data.

### 4.3 Snapshot & Comparison
- **Create Snapshot**: Iterates all datasets/tables in a dataspace, serializes all `APPROVED` records as JSON arrays into `SnapshotDataEntity`.
- **Compare Snapshots**: Diffs two snapshots by `_record_id`, identifying added, removed, and modified records with changed field names.

### 4.4 Environment Synchronization
- **Register**: Store JDBC connection details for a target environment (DIT, SIT, UAT, PROD, EBX).
- **Compare**: Reads MDM snapshot data, connects to target DB via JDBC, reads target table data, computes INSERT/UPDATE/DELETE diff.
- **SQL Script Generation**: Generates ready-to-execute SQL statements (INSERT INTO, UPDATE SET, DELETE FROM) with proper value formatting and quoted table identifiers.

### 4.5 User Management & Permissions
- **Users**: CRUD with BCrypt password hashing. Roles: ADMIN, DATA_STEWARD, VIEWER. Soft-delete via status toggle.
- **Permissions**: Grant/revoke fine-grained access (READ, WRITE, ADMIN) on specific resources (DATASPACE, DATASET, TABLE). Check endpoint for runtime authorization queries.

---

## 5. Integration Points

| Integration | Type | Details |
|---|---|---|
| Eureka Service Registry | Service Discovery | Registers on startup; other services can discover via Eureka |
| Spring Cloud Config Server | Configuration | Externalized properties via `bootstrap.yml` (expects `master-data-management-service.yml` in config repo) |
| MySQL 8.4 | Persistence | Database: `banking_core_mdm_service`. JPA with Hibernate DDL auto-generation |
| Target Environment DBs | JDBC | Direct connections to DIT/SIT/UAT/PROD databases for environment sync |
| Keycloak 23.0.7 | Auth (Gateway-level) | Authentication handled at API Gateway; MDM service permits all requests internally |
| Zipkin | Distributed Tracing | Micrometer + Brave tracing bridge for cross-service request tracking |
| Swagger/OpenAPI | API Documentation | springdoc-openapi auto-generates API docs at `/swagger-ui.html` |

---

## 6. Build & Deployment

### 6.1 Build
```bash
cd master-data-management-service
./gradlew build
```
Produces: `build/libs/master-data-management-service-0.0.1-SNAPSHOT.jar`

### 6.2 Docker
```dockerfile
FROM eclipse-temurin:21.0.2_13-jre-alpine
ADD build/libs/master-data-management-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8093
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "/app.jar"]
```

### 6.3 Docker Compose
The service is defined in `docker-compose/docker-compose.yml`:
- Image: `javatodev/master-data-management-service`
- Port: `8093:8093`
- Network: `javatodev_ib_network` (172.25.0.13)
- Startup dependencies: waits for Service Registry, Config Server, and MySQL via `wait-for-it.sh`

### 6.4 Database Setup
MySQL database `banking_core_mdm_service` with user `javatodev_development` is provisioned via `docker-compose/mysql/privileges.sql`. JPA auto-generates tables via `spring.jpa.hibernate.ddl-auto` (configured in Config Server).

### 6.5 Prerequisites for Runtime
The service requires a `master-data-management-service.yml` configuration file in the Spring Cloud Config repository containing:
- `spring.datasource.url`, `username`, `password`
- `spring.jpa.hibernate.ddl-auto`
- `server.port: 8093`
- `eureka.client.serviceUrl.defaultZone`
