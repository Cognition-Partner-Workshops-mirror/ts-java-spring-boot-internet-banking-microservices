# Master Data Management Service — Screen Design Document

> UI screen specifications for the EBX MDM POC application.
> Each screen maps to existing REST API endpoints documented in [KNOWLEDGE_BASE.md](./KNOWLEDGE_BASE.md).

---

## Table of Contents

1. [Application Layout & Navigation](#1-application-layout--navigation)
2. [Dashboard (Home)](#2-dashboard-home)
3. [Dataspace Management Screens](#3-dataspace-management-screens)
4. [Dataset Management Screens](#4-dataset-management-screens)
5. [Table Definition Screens](#5-table-definition-screens)
6. [Master Data Record Screens](#6-master-data-record-screens)
7. [Workflow Approval Screens](#7-workflow-approval-screens)
8. [Snapshot Management Screens](#8-snapshot-management-screens)
9. [User Management Screens](#9-user-management-screens)
10. [Permission Management Screens](#10-permission-management-screens)
11. [Environment Sync Screens](#11-environment-sync-screens)
12. [Screen Flow Diagrams](#12-screen-flow-diagrams)
13. [Role-Based Visibility Matrix](#13-role-based-visibility-matrix)

---

## 1. Application Layout & Navigation

### 1.1 Global Layout

```
┌─────────────────────────────────────────────────────────────────────┐
│  HEADER BAR                                                         │
│  [Logo] MDM Console    [Notifications 🔔]  [User: admin ▼]         │
├──────────────┬──────────────────────────────────────────────────────┤
│  LEFT NAV    │  MAIN CONTENT AREA                                   │
│              │                                                      │
│  Dashboard   │  (Page content renders here)                         │
│  Dataspaces  │                                                      │
│  Workflows   │                                                      │
│  Snapshots   │                                                      │
│  Env Sync    │                                                      │
│  ─────────   │                                                      │
│  Admin       │                                                      │
│   Users      │                                                      │
│   Permissions│                                                      │
│              │                                                      │
└──────────────┴──────────────────────────────────────────────────────┘
```

### 1.2 Header Bar Components

| Component | Description |
|---|---|
| Logo + Title | Application name "MDM Console" with logo. Links to Dashboard. |
| Notification Bell | Badge count of pending workflows requiring review. |
| User Menu (dropdown) | Shows logged-in username, role badge. Options: Profile, Logout. |

### 1.3 Left Navigation Menu

| Menu Item | Sub-items | Route | Icon |
|---|---|---|---|
| Dashboard | — | `/` | Home |
| Dataspaces | — | `/dataspaces` | Layers |
| Workflows | — | `/workflows` | CheckCircle |
| Snapshots | — | `/snapshots` | Camera |
| Env Sync | — | `/sync` | RefreshCw |
| **Admin** | Users | `/admin/users` | Users |
| | Permissions | `/admin/permissions` | Shield |

**Collapsible behavior**: Left nav collapses to icon-only rail on smaller screens. Admin section visible only to ADMIN role users.

---

## 2. Dashboard (Home)

**Route**: `/`
**Purpose**: Overview of MDM system status with quick-access widgets.
**API calls on load**: `GET /api/v1/dataspaces`, `GET /api/v1/workflows` (pending), `GET /api/v1/snapshots`

### 2.1 Layout

```
┌─────────────────────────────────────────────────────────────┐
│  Dashboard                                                   │
├──────────┬──────────┬──────────┬──────────┬─────────────────┤
│  CARD    │  CARD    │  CARD    │  CARD    │  CARD           │
│  Open    │  Total   │  Pending │  Total   │  Registered     │
│  Data-   │  Data-   │  Work-   │  Snap-   │  Environ-       │
│  spaces  │  sets    │  flows   │  shots   │  ments          │
│  [3]     │  [12]    │  [5]     │  [8]     │  [2]            │
├──────────┴──────────┴──────────┴──────────┴─────────────────┤
│                                                              │
│  RECENT ACTIVITY TABLE                                       │
│  ┌──────────┬──────────┬───────────┬──────────┬──────────┐  │
│  │ Time     │ Action   │ Resource  │ User     │ Status   │  │
│  ├──────────┼──────────┼───────────┼──────────┼──────────┤  │
│  │ 10:32 AM │ Merge    │ DS: R2.1  │ admin    │ MERGED   │  │
│  │ 10:15 AM │ Approve  │ WF: #42   │ steward1 │ APPROVED │  │
│  │ 09:58 AM │ Snapshot │ SS: v2.0  │ admin    │ CREATED  │  │
│  └──────────┴──────────┴───────────┴──────────┴──────────┘  │
│                                                              │
│  PENDING WORKFLOWS (Quick Access)                            │
│  ┌───────┬──────────┬──────────┬──────────┬─────────────┐   │
│  │ ID    │ Type     │ Table    │ Requester│ [Review]    │   │
│  ├───────┼──────────┼──────────┼──────────┼─────────────┤   │
│  │ #45   │ INSERT   │ currency │ user1    │ [Review →]  │   │
│  │ #44   │ UPDATE   │ branch   │ user2    │ [Review →]  │   │
│  └───────┴──────────┴──────────┴──────────┴─────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 Summary Cards

| Card | Value Source | Click Action |
|---|---|---|
| Open Dataspaces | Count from `GET /api/v1/dataspaces` where status = OPEN | Navigate to `/dataspaces` |
| Total Datasets | Count across all dataspaces | Navigate to first dataspace |
| Pending Workflows | Count from `GET /api/v1/workflows` where status = PENDING | Navigate to `/workflows` |
| Total Snapshots | Count from `GET /api/v1/snapshots` | Navigate to `/snapshots` |
| Registered Environments | Count from `GET /api/v1/sync/environments` | Navigate to `/sync` |

---

## 3. Dataspace Management Screens

### 3.1 Dataspace List

**Route**: `/dataspaces`
**API**: `GET /api/v1/dataspaces`
**Purpose**: View all dataspaces with status indicators and actions.

```
┌─────────────────────────────────────────────────────────────────┐
│  Dataspaces                                    [+ New Dataspace] │
├─────────────────────────────────────────────────────────────────┤
│  Filter: [All ▼] [Search...              ]                      │
│                                                                  │
│  ┌────┬──────────────┬───────────┬──────────┬──────────┬──────┐ │
│  │ ID │ Name         │ Parent    │ Status   │ Created  │ Act. │ │
│  ├────┼──────────────┼───────────┼──────────┼──────────┼──────┤ │
│  │ 1  │ Production   │ —         │ 🟢 OPEN  │ Jan 15   │ ⋮    │ │
│  │ 2  │ Release-2.1  │ Prod (#1) │ 🟢 OPEN  │ Feb 01   │ ⋮    │ │
│  │ 3  │ Release-2.0  │ Prod (#1) │ 🔵 MERGED│ Jan 20   │ ⋮    │ │
│  │ 4  │ Hotfix-1.5   │ Prod (#1) │ 🔴 CLOSED│ Dec 10   │ ⋮    │ │
│  └────┴──────────────┴───────────┴──────────┴──────────┴──────┘ │
│                                                                  │
│  Showing 4 of 4 dataspaces                                      │
└─────────────────────────────────────────────────────────────────┘
```

**Action Menu (⋮) per row:**

| Action | Condition | API Call |
|---|---|---|
| View Details | Always | Navigate to `/dataspaces/{id}` |
| Edit | Status = OPEN | `PUT /api/v1/dataspaces/{id}` |
| Close | Status = OPEN | `DELETE /api/v1/dataspaces/{id}` |
| Merge to Parent | Status = OPEN, has parent | `POST /api/v1/dataspaces/{id}/merge` |
| Create Snapshot | Status = OPEN | Navigate to Snapshot create form |

**Status badges**:
- 🟢 OPEN (green) — Active, accepts changes
- 🔴 CLOSED (red) — Frozen, no changes allowed
- 🔵 MERGED (blue) — Data merged into parent

### 3.2 Create / Edit Dataspace Dialog

**Trigger**: Click [+ New Dataspace] or Edit action
**API**: `POST /api/v1/dataspaces` or `PUT /api/v1/dataspaces/{id}`

```
┌──────────────────────────────────────────┐
│  Create New Dataspace           [✕ Close]│
├──────────────────────────────────────────┤
│                                          │
│  Name *                                  │
│  ┌──────────────────────────────────┐    │
│  │ Release-3.0                      │    │
│  └──────────────────────────────────┘    │
│                                          │
│  Description                             │
│  ┌──────────────────────────────────┐    │
│  │ Q3 2026 release dataspace        │    │
│  └──────────────────────────────────┘    │
│                                          │
│  Parent Dataspace                        │
│  ┌──────────────────────────────────┐    │
│  │ Production (#1)              ▼   │    │
│  └──────────────────────────────────┘    │
│  (Optional — leave empty for root)       │
│                                          │
│          [Cancel]  [Create Dataspace]    │
└──────────────────────────────────────────┘
```

**Form fields:**

| Field | Type | Required | Validation | Maps to |
|---|---|---|---|---|
| Name | Text input | Yes | Non-empty, unique | `DataspaceRequest.name` |
| Description | Textarea | No | Max 500 chars | `DataspaceRequest.description` |
| Parent Dataspace | Dropdown (searchable) | No | Must be OPEN dataspace | `DataspaceRequest.parentDataspaceId` |

### 3.3 Dataspace Detail

**Route**: `/dataspaces/{id}`
**API**: `GET /api/v1/dataspaces/{id}`, `GET /api/v1/datasets/dataspace/{id}`
**Purpose**: View dataspace details and navigate into its datasets.

```
┌─────────────────────────────────────────────────────────────────┐
│  ← Back to Dataspaces                                           │
│                                                                  │
│  Dataspace: Release-2.1                            🟢 OPEN      │
│  Parent: Production (#1)                                         │
│  Created: Feb 01, 2026 by admin                                  │
│  ─────────────────────────────────────────────────────────────   │
│                                                                  │
│  Actions: [Edit] [Close] [Merge to Parent] [Create Snapshot]    │
│                                                                  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  TABS:  [Datasets]  [Snapshots]  [Activity Log]          │  │
│  ├───────────────────────────────────────────────────────────┤  │
│  │                                                           │  │
│  │  Datasets                              [+ New Dataset]    │  │
│  │  ┌────┬──────────────┬──────────────┬────────┬──────┐    │  │
│  │  │ ID │ Name         │ Description  │ Tables │ Act. │    │  │
│  │  ├────┼──────────────┼──────────────┼────────┼──────┤    │  │
│  │  │ 1  │ Reference    │ Ref data     │ 3      │ ⋮    │    │  │
│  │  │ 2  │ Customer     │ Customer MDM │ 5      │ ⋮    │    │  │
│  │  └────┴──────────────┴──────────────┴────────┴──────┘    │  │
│  │                                                           │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### 3.4 Merge Confirmation Dialog

**Trigger**: Click [Merge to Parent] on a child dataspace
**API**: `POST /api/v1/dataspaces/{id}/merge`

```
┌──────────────────────────────────────────────┐
│  ⚠️  Confirm Merge                   [✕ Close]│
├──────────────────────────────────────────────┤
│                                              │
│  You are about to merge:                     │
│                                              │
│  Source:  Release-2.1 (#2)                   │
│  Target:  Production (#1)                    │
│                                              │
│  This will copy all datasets, table          │
│  definitions, and APPROVED records from      │
│  Release-2.1 into Production.                │
│                                              │
│  The source dataspace will be marked         │
│  as MERGED and can no longer be modified.    │
│                                              │
│  ⚠️ This action cannot be undone.             │
│                                              │
│          [Cancel]  [Confirm Merge]           │
└──────────────────────────────────────────────┘
```

---

## 4. Dataset Management Screens

### 4.1 Dataset Detail

**Route**: `/dataspaces/{dsId}/datasets/{id}`
**API**: `GET /api/v1/datasets/{id}`, `GET /api/v1/tables/dataset/{id}`
**Purpose**: View dataset details and its table definitions.

```
┌─────────────────────────────────────────────────────────────────┐
│  ← Release-2.1 > Reference Dataset                              │
│                                                                  │
│  Dataset: Reference                                              │
│  Description: Reference data tables for banking operations       │
│  Dataspace: Release-2.1 (#2)                                     │
│  ─────────────────────────────────────────────────────────────   │
│                                                                  │
│  Actions: [Edit] [Delete]                                        │
│                                                                  │
│  Table Definitions                        [+ New Table]          │
│  ┌────┬──────────────┬──────────────┬─────────┬────────┬──────┐ │
│  │ ID │ Name         │ Description  │ Columns │ Records│ Act. │ │
│  ├────┼──────────────┼──────────────┼─────────┼────────┼──────┤ │
│  │ 1  │ currency     │ Currency     │ 4       │ 120    │ ⋮    │ │
│  │ 2  │ branch_code  │ Branch codes │ 6       │ 45     │ ⋮    │ │
│  │ 3  │ country      │ Countries    │ 3       │ 195    │ ⋮    │ │
│  └────┴──────────────┴──────────────┴─────────┴────────┴──────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 Create / Edit Dataset Dialog

**API**: `POST /api/v1/datasets` or `PUT /api/v1/datasets/{id}`

```
┌──────────────────────────────────────────┐
│  Create New Dataset             [✕ Close]│
├──────────────────────────────────────────┤
│                                          │
│  Name *                                  │
│  ┌──────────────────────────────────┐    │
│  │ Reference                        │    │
│  └──────────────────────────────────┘    │
│                                          │
│  Description                             │
│  ┌──────────────────────────────────┐    │
│  │ Reference data for banking       │    │
│  └──────────────────────────────────┘    │
│                                          │
│  Dataspace *                             │
│  ┌──────────────────────────────────┐    │
│  │ Release-2.1 (#2)            ▼    │    │
│  └──────────────────────────────────┘    │
│  (Pre-selected from current context)     │
│                                          │
│          [Cancel]  [Create Dataset]      │
└──────────────────────────────────────────┘
```

**Form fields:**

| Field | Type | Required | Validation | Maps to |
|---|---|---|---|---|
| Name | Text input | Yes | Non-empty | `DatasetRequest.name` |
| Description | Textarea | No | Max 500 chars | `DatasetRequest.description` |
| Dataspace | Dropdown | Yes | Must be OPEN | `DatasetRequest.dataspaceId` |

---

## 5. Table Definition Screens

### 5.1 Create / Edit Table Definition

**Route**: `/dataspaces/{dsId}/datasets/{dsetId}/tables/new` or `/tables/{id}/edit`
**API**: `POST /api/v1/tables` or `PUT /api/v1/tables/{id}`
**Purpose**: Define or modify a table schema with columns.

```
┌─────────────────────────────────────────────────────────────────┐
│  ← Reference Dataset > New Table Definition                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Table Name *                                                    │
│  ┌──────────────────────────────────┐                            │
│  │ currency_code                    │                            │
│  └──────────────────────────────────┘                            │
│  (Must be a valid SQL identifier: letters, digits, underscores)  │
│                                                                  │
│  Description                                                     │
│  ┌──────────────────────────────────┐                            │
│  │ ISO 4217 currency codes          │                            │
│  └──────────────────────────────────┘                            │
│                                                                  │
│  Column Definitions                              [+ Add Column]  │
│  ┌─────┬───────────────┬──────────┬──────────┬────────┬────────┐│
│  │ Ord │ Column Name   │ DataType │ Required │ Unique │ Action ││
│  ├─────┼───────────────┼──────────┼──────────┼────────┼────────┤│
│  │  1  │ code          │ STRING ▼ │ ☑        │ ☑      │ [✕]   ││
│  │  2  │ name          │ STRING ▼ │ ☑        │ ☐      │ [✕]   ││
│  │  3  │ decimal_places│ INTEGER▼ │ ☑        │ ☐      │ [✕]   ││
│  │  4  │ active        │ BOOLEAN▼ │ ☐        │ ☐      │ [✕]   ││
│  └─────┴───────────────┴──────────┴──────────┴────────┴────────┘│
│  (Drag rows to reorder)                                          │
│                                                                  │
│          [Cancel]  [Save Table Definition]                       │
└─────────────────────────────────────────────────────────────────┘
```

**Column Definition Fields:**

| Field | Type | Required | Options / Validation | Maps to |
|---|---|---|---|---|
| Ordinal | Auto-number (drag to reorder) | Auto | Positive integer | `ColumnDefinitionRequest.ordinal` |
| Column Name | Text input | Yes | Valid SQL identifier pattern | `ColumnDefinitionRequest.name` |
| Data Type | Dropdown | Yes | STRING, INTEGER, DECIMAL, DATE, BOOLEAN | `ColumnDefinitionRequest.dataType` |
| Required | Checkbox | — | — | `ColumnDefinitionRequest.required` |
| Unique Key | Checkbox | — | — | `ColumnDefinitionRequest.uniqueKey` |
| Remove (✕) | Button | — | Removes column row | — |

### 5.2 Table Detail (Schema View)

**Route**: `/tables/{id}`
**API**: `GET /api/v1/tables/{id}`, `GET /api/v1/records/table/{id}`

```
┌─────────────────────────────────────────────────────────────────┐
│  ← Reference > currency_code                                     │
│                                                                  │
│  Table: currency_code                                            │
│  Description: ISO 4217 currency codes                            │
│  Dataset: Reference | Dataspace: Release-2.1                     │
│  ─────────────────────────────────────────────────────────────   │
│                                                                  │
│  Actions: [Edit Schema] [Delete Table]                           │
│                                                                  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  TABS:  [Schema]  [Records]  [History]                    │  │
│  ├───────────────────────────────────────────────────────────┤  │
│  │                                                           │  │
│  │  Schema (4 columns)                                       │  │
│  │  ┌─────┬────────────────┬──────────┬──────┬────────┐     │  │
│  │  │ Ord │ Name           │ Type     │ Req. │ Unique │     │  │
│  │  ├─────┼────────────────┼──────────┼──────┼────────┤     │  │
│  │  │  1  │ code           │ STRING   │ ✓    │ ✓      │     │  │
│  │  │  2  │ name           │ STRING   │ ✓    │        │     │  │
│  │  │  3  │ decimal_places │ INTEGER  │ ✓    │        │     │  │
│  │  │  4  │ active         │ BOOLEAN  │      │        │     │  │
│  │  └─────┴────────────────┴──────────┴──────┴────────┘     │  │
│  │                                                           │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 6. Master Data Record Screens

### 6.1 Records List (Table Data View)

**Route**: `/tables/{id}/records`
**API**: `GET /api/v1/records/table/{tableId}`
**Purpose**: View all records in a table, dynamically rendered based on table schema.

```
┌─────────────────────────────────────────────────────────────────────┐
│  ← currency_code > Records                                          │
│                                                                      │
│  Records for: currency_code                        [+ Insert Record] │
│  Filter: [All Statuses ▼]  [Search...              ]                │
│                                                                      │
│  ┌────┬──────┬──────────────┬────────────────┬────────┬──────────┬─┐│
│  │ ID │ code │ name         │ decimal_places │ active │ Status   │⋮││
│  ├────┼──────┼──────────────┼────────────────┼────────┼──────────┼─┤│
│  │ 1  │ USD  │ US Dollar    │ 2              │ true   │ APPROVED │⋮││
│  │ 2  │ EUR  │ Euro         │ 2              │ true   │ APPROVED │⋮││
│  │ 3  │ JPY  │ Japanese Yen │ 0              │ true   │ APPROVED │⋮││
│  │ 4  │ GBP  │ British Pound│ 2              │ true   │ DRAFT    │⋮││
│  └────┴──────┴──────────────┴────────────────┴────────┴──────────┴─┘│
│                                                                      │
│  Showing 4 records                                                   │
└─────────────────────────────────────────────────────────────────────┘
```

**Key behaviors:**
- Column headers are **dynamically generated** from the table's `ColumnDefinitionEntity` ordered by ordinal.
- The `data` JSON field is parsed and each key-value pair rendered in the matching column.
- Status column shows color-coded badge: 🟢 APPROVED, 🟡 DRAFT, 🟠 PENDING_APPROVAL, 🔴 REJECTED.

**Action Menu (⋮) per row:**

| Action | Condition | API |
|---|---|---|
| View Details | Always | Navigate to record detail |
| Edit | Status = APPROVED | `PUT /api/v1/records/{id}` (triggers workflow) |
| Delete | Status = APPROVED | `DELETE /api/v1/records/{id}` (triggers workflow) |
| View History | Always | `GET /api/v1/records/{id}/history` |

### 6.2 Insert / Edit Record Form

**Trigger**: Click [+ Insert Record] or Edit action
**API**: `POST /api/v1/records` or `PUT /api/v1/records/{id}`
**Purpose**: Dynamic form generated from table schema columns.

```
┌──────────────────────────────────────────────┐
│  Insert Record: currency_code       [✕ Close]│
├──────────────────────────────────────────────┤
│                                              │
│  code *                                      │
│  ┌──────────────────────────────────┐        │
│  │ GBP                              │        │
│  └──────────────────────────────────┘        │
│                                              │
│  name *                                      │
│  ┌──────────────────────────────────┐        │
│  │ British Pound                    │        │
│  └──────────────────────────────────┘        │
│                                              │
│  decimal_places *                            │
│  ┌──────────────────────────────────┐        │
│  │ 2                                │        │
│  └──────────────────────────────────┘        │
│                                              │
│  active                                      │
│  [✓] Yes                                     │
│                                              │
│  ℹ️ This record will be submitted as DRAFT.   │
│  A workflow approval is required before       │
│  the record becomes active.                   │
│                                              │
│          [Cancel]  [Submit for Approval]      │
└──────────────────────────────────────────────┘
```

**Dynamic form generation rules:**

| Column DataType | Rendered As | Input Control |
|---|---|---|
| STRING | Text input | `<input type="text">` |
| INTEGER | Number input | `<input type="number" step="1">` |
| DECIMAL | Number input | `<input type="number" step="0.01">` |
| DATE | Date picker | `<input type="date">` |
| BOOLEAN | Checkbox / Toggle | `<input type="checkbox">` |

- Fields marked as `required = true` show asterisk (*) and are mandatory.
- Fields marked as `uniqueKey = true` show "(unique)" hint below the input.

### 6.3 Record Detail

**Route**: `/records/{id}`
**API**: `GET /api/v1/records/{id}`

```
┌─────────────────────────────────────────────────────────────────┐
│  ← currency_code > Record #1                                    │
│                                                                  │
│  Record ID: 1                    Status: 🟢 APPROVED             │
│  Table: currency_code                                            │
│  Created: Jan 15, 2026 by admin                                  │
│  Modified: Feb 01, 2026 by steward1                              │
│  ─────────────────────────────────────────────────────────────   │
│                                                                  │
│  Actions: [Edit] [Delete] [View History]                         │
│                                                                  │
│  Data                                                            │
│  ┌──────────────────┬────────────────────────────────────┐      │
│  │ code             │ USD                                 │      │
│  │ name             │ US Dollar                           │      │
│  │ decimal_places   │ 2                                   │      │
│  │ active           │ true                                │      │
│  └──────────────────┴────────────────────────────────────┘      │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 6.4 Record History

**Route**: `/records/{id}/history`
**API**: `GET /api/v1/records/{id}/history`

```
┌─────────────────────────────────────────────────────────────────┐
│  ← Record #1 > History                                           │
│                                                                  │
│  Change History for Record #1                                    │
│                                                                  │
│  ┌──────────┬──────────┬───────────┬────────────────────────┐   │
│  │ Date     │ Type     │ Changed By│ Changes                │   │
│  ├──────────┼──────────┼───────────┼────────────────────────┤   │
│  │ Feb 01   │ UPDATE   │ steward1  │ name: "US Dollar" →    │   │
│  │          │          │           │ "United States Dollar" │   │
│  ├──────────┼──────────┼───────────┼────────────────────────┤   │
│  │ Jan 15   │ INSERT   │ admin     │ (initial creation)     │   │
│  └──────────┴──────────┴───────────┴────────────────────────┘   │
│                                                                  │
│  [Expand] to see full before/after JSON per entry                │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**Expandable row detail** — when clicked, shows side-by-side diff:

```
  Previous Data (JSON):               New Data (JSON):
  {                                    {
    "code": "USD",                       "code": "USD",
    "name": "US Dollar",          →      "name": "United States Dollar",
    "decimal_places": 2,                 "decimal_places": 2,
    "active": true                       "active": true
  }                                    }
```

---

## 7. Workflow Approval Screens

### 7.1 Workflow Queue

**Route**: `/workflows`
**API**: `GET /api/v1/workflows`
**Purpose**: List all pending workflow items for review (maker-checker).

```
┌─────────────────────────────────────────────────────────────────────┐
│  Workflow Queue                                                      │
│  Filter: [Pending ▼]  [All Types ▼]  [Search...              ]     │
│                                                                      │
│  ┌────┬──────────┬──────────────┬───────────┬──────────┬──────────┐ │
│  │ ID │ Type     │ Table        │ Requester │ Date     │ Action   │ │
│  ├────┼──────────┼──────────────┼───────────┼──────────┼──────────┤ │
│  │ 45 │ 🟢INSERT │ currency     │ user1     │ Feb 03   │ [Review] │ │
│  │ 44 │ 🟡UPDATE │ branch_code  │ user2     │ Feb 02   │ [Review] │ │
│  │ 43 │ 🔴DELETE │ country      │ admin     │ Feb 01   │ [Review] │ │
│  │ 42 │ 🟢INSERT │ currency     │ user1     │ Jan 30   │ Approved │ │
│  │ 41 │ 🟡UPDATE │ branch_code  │ steward1  │ Jan 28   │ Rejected │ │
│  └────┴──────────┴──────────────┴───────────┴──────────┴──────────┘ │
│                                                                      │
│  Showing 5 workflows                                                 │
└─────────────────────────────────────────────────────────────────────┘
```

**Change Type badges**: 🟢 INSERT (green), 🟡 UPDATE (yellow), 🔴 DELETE (red)

### 7.2 Workflow Review Screen

**Route**: `/workflows/{id}`
**API**: `GET /api/v1/workflows/{id}`, `POST /api/v1/workflows/{id}/approve`, `POST /api/v1/workflows/{id}/reject`
**Purpose**: Review proposed changes and approve or reject.

```
┌─────────────────────────────────────────────────────────────────┐
│  ← Workflow Queue > Review #45                                   │
│                                                                  │
│  Workflow #45                        Status: 🟠 PENDING          │
│  Type: INSERT                                                    │
│  Table: currency_code                                            │
│  Requested by: user1 on Feb 03, 2026                             │
│  ─────────────────────────────────────────────────────────────   │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  PROPOSED DATA                                          │    │
│  │                                                         │    │
│  │  ┌──────────────────┬──────────────────────────────┐   │    │
│  │  │ code             │ GBP                           │   │    │
│  │  │ name             │ British Pound                 │   │    │
│  │  │ decimal_places   │ 2                             │   │    │
│  │  │ active           │ true                          │   │    │
│  │  └──────────────────┴──────────────────────────────┘   │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                  │
│  (For UPDATE type, a side-by-side Previous vs Proposed view      │
│   is shown with changed fields highlighted)                      │
│                                                                  │
│  Review Comment                                                  │
│  ┌──────────────────────────────────────────────────────┐       │
│  │ Looks good. Currency code verified.                   │       │
│  └──────────────────────────────────────────────────────┘       │
│                                                                  │
│  Reviewed by *                                                   │
│  ┌──────────────────────────────────────────────────────┐       │
│  │ steward1                                              │       │
│  └──────────────────────────────────────────────────────┘       │
│                                                                  │
│          [Reject]  [Approve]                                     │
└─────────────────────────────────────────────────────────────────┘
```

**Review form fields:**

| Field | Type | Required | Maps to |
|---|---|---|---|
| Reviewed by | Text input (auto-filled from session) | Yes | `WorkflowReviewRequest.reviewedBy` |
| Review Comment | Textarea | No | `WorkflowReviewRequest.reviewComment` |

**Post-action behavior:**
- **Approve**: Record status → APPROVED; for DELETE workflows, record is removed. Success toast notification.
- **Reject**: For INSERT, draft record is deleted. For UPDATE/DELETE, record reverted to APPROVED. Warning toast notification.

---

## 8. Snapshot Management Screens

### 8.1 Snapshot List

**Route**: `/snapshots`
**API**: `GET /api/v1/snapshots/dataspace/{dataspaceId}` (per dataspace, or aggregated)
**Purpose**: List all snapshots with options to compare.

```
┌─────────────────────────────────────────────────────────────────────┐
│  Snapshots                                          [+ New Snapshot] │
│  Filter by Dataspace: [All ▼]                                       │
│                                                                      │
│  ┌────┬──────────────┬──────────────┬──────────┬──────────┬────────┐│
│  │ ID │ Name         │ Dataspace    │ Created  │ By       │ Action ││
│  ├────┼──────────────┼──────────────┼──────────┼──────────┼────────┤│
│  │ 3  │ v2.1-final   │ Release-2.1  │ Feb 03   │ admin    │ ⋮      ││
│  │ 2  │ v2.0-release │ Release-2.0  │ Jan 20   │ admin    │ ⋮      ││
│  │ 1  │ baseline     │ Production   │ Jan 01   │ admin    │ ⋮      ││
│  └────┴──────────────┴──────────────┴──────────┴──────────┴────────┘│
│                                                                      │
│  COMPARE SNAPSHOTS                                                   │
│  ┌──────────────────────┐    ┌──────────────────────┐               │
│  │ Source: baseline (#1)│ vs │ Target: v2.1-final(#3│               │
│  └──────────────────────┘    └──────────────────────┘               │
│                                          [Compare Snapshots]         │
└─────────────────────────────────────────────────────────────────────┘
```

### 8.2 Create Snapshot Dialog

**Trigger**: Click [+ New Snapshot]
**API**: `POST /api/v1/snapshots`

```
┌──────────────────────────────────────────┐
│  Create Snapshot                [✕ Close]│
├──────────────────────────────────────────┤
│                                          │
│  Dataspace *                             │
│  ┌──────────────────────────────────┐    │
│  │ Release-2.1 (#2)            ▼    │    │
│  └──────────────────────────────────┘    │
│                                          │
│  Snapshot Name *                         │
│  ┌──────────────────────────────────┐    │
│  │ v2.1-final                       │    │
│  └──────────────────────────────────┘    │
│                                          │
│  Description                             │
│  ┌──────────────────────────────────┐    │
│  │ Final snapshot before release    │    │
│  └──────────────────────────────────┘    │
│                                          │
│  ℹ️ This will capture all APPROVED        │
│  records across all tables in the         │
│  selected dataspace.                      │
│                                          │
│          [Cancel]  [Create Snapshot]      │
└──────────────────────────────────────────┘
```

**Form fields:**

| Field | Type | Required | Maps to |
|---|---|---|---|
| Dataspace | Dropdown | Yes | `SnapshotRequest.dataspaceId` |
| Name | Text input | Yes | `SnapshotRequest.name` |
| Description | Textarea | No | `SnapshotRequest.description` |

### 8.3 Snapshot Comparison Results

**Trigger**: Click [Compare Snapshots]
**API**: `POST /api/v1/snapshots/compare`
**Request**: `SnapshotCompareRequest { sourceSnapshotId, targetSnapshotId }`

```
┌─────────────────────────────────────────────────────────────────────┐
│  Snapshot Comparison                                                 │
│  Source: baseline (#1)  vs  Target: v2.1-final (#3)                 │
│  ─────────────────────────────────────────────────────────────────   │
│                                                                      │
│  Summary:  🟢 12 Added  |  🟡 3 Modified  |  🔴 1 Removed           │
│                                                                      │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  TABLE: currency_code                                         │  │
│  │                                                               │  │
│  │  Added Records (2):                                           │  │
│  │  ┌──────┬──────────────┬────────────────┬────────┐           │  │
│  │  │ code │ name         │ decimal_places │ active │           │  │
│  │  ├──────┼──────────────┼────────────────┼────────┤           │  │
│  │  │ GBP  │ British Pound│ 2              │ true   │           │  │
│  │  │ CHF  │ Swiss Franc  │ 2              │ true   │           │  │
│  │  └──────┴──────────────┴────────────────┴────────┘           │  │
│  │                                                               │  │
│  │  Modified Records (1):                                        │  │
│  │  ┌──────┬──────────────────────┬─────────────────────────┐   │  │
│  │  │ code │ Field          │ Before       │ After          │   │  │
│  │  ├──────┼──────────────────────┼─────────────────────────┤   │  │
│  │  │ USD  │ name           │ US Dollar    │ United States $ │   │  │
│  │  └──────┴──────────────────────┴─────────────────────────┘   │  │
│  │                                                               │  │
│  │  Removed Records (0)                                          │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  TABLE: branch_code                                           │  │
│  │  Added: 10  |  Modified: 2  |  Removed: 1                    │  │
│  │  [Expand to view details]                                     │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 9. User Management Screens

### 9.1 User List

**Route**: `/admin/users`
**API**: `GET /api/v1/mdm-users`
**Visibility**: ADMIN role only

```
┌─────────────────────────────────────────────────────────────────┐
│  User Management                                  [+ New User]   │
│                                                                  │
│  ┌────┬──────────────┬─────────────────┬─────────────┬────┬────┐│
│  │ ID │ Username     │ Email           │ Role        │St. │Act.││
│  ├────┼──────────────┼─────────────────┼─────────────┼────┼────┤│
│  │ 1  │ admin        │ admin@bank.com  │ 🔴 ADMIN    │ ✓  │ ⋮  ││
│  │ 2  │ steward1     │ stew1@bank.com  │ 🟡 STEWARD  │ ✓  │ ⋮  ││
│  │ 3  │ viewer1      │ view1@bank.com  │ 🟢 VIEWER   │ ✓  │ ⋮  ││
│  │ 4  │ former_user  │ old@bank.com    │ 🟡 STEWARD  │ ✗  │ ⋮  ││
│  └────┴──────────────┴─────────────────┴─────────────┴────┴────┘│
│                                                                  │
│  St. = Status (✓ Active / ✗ Inactive)                            │
└─────────────────────────────────────────────────────────────────┘
```

**Action Menu (⋮)**: Edit, Deactivate (soft delete), Manage Permissions

### 9.2 Create / Edit User Dialog

**API**: `POST /api/v1/mdm-users` or `PUT /api/v1/mdm-users/{id}`

```
┌──────────────────────────────────────────┐
│  Create New User                [✕ Close]│
├──────────────────────────────────────────┤
│                                          │
│  Username *                              │
│  ┌──────────────────────────────────┐    │
│  │ steward2                         │    │
│  └──────────────────────────────────┘    │
│                                          │
│  Email *                                 │
│  ┌──────────────────────────────────┐    │
│  │ steward2@bank.com                │    │
│  └──────────────────────────────────┘    │
│                                          │
│  Password *                              │
│  ┌──────────────────────────────────┐    │
│  │ ••••••••••••                     │    │
│  └──────────────────────────────────┘    │
│  (Min 8 characters)                      │
│                                          │
│  Role *                                  │
│  ┌──────────────────────────────────┐    │
│  │ DATA_STEWARD                 ▼   │    │
│  └──────────────────────────────────┘    │
│  Options: ADMIN, DATA_STEWARD, VIEWER    │
│                                          │
│          [Cancel]  [Create User]         │
└──────────────────────────────────────────┘
```

**Form fields:**

| Field | Type | Required | Validation | Maps to |
|---|---|---|---|---|
| Username | Text input | Yes | Unique, alphanumeric | `MdmUserRequest.username` |
| Email | Email input | Yes | Valid email, unique | `MdmUserRequest.email` |
| Password | Password input | Yes | Min 8 characters | `MdmUserRequest.password` |
| Role | Dropdown | Yes | ADMIN, DATA_STEWARD, VIEWER | `MdmUserRequest.role` |

---

## 10. Permission Management Screens

### 10.1 User Permissions View

**Route**: `/admin/permissions` or `/admin/users/{id}/permissions`
**API**: `GET /api/v1/permissions/user/{userId}`
**Purpose**: View and manage permissions granted to a specific user.

```
┌─────────────────────────────────────────────────────────────────┐
│  Permissions for: steward1                    [+ Grant Permission]│
│  Role: DATA_STEWARD                                              │
│  ─────────────────────────────────────────────────────────────   │
│                                                                  │
│  ┌────┬───────────────┬───────────────────┬───────────┬────────┐│
│  │ ID │ Resource Type │ Resource          │ Level     │ Action ││
│  ├────┼───────────────┼───────────────────┼───────────┼────────┤│
│  │ 1  │ DATASPACE     │ Production (#1)   │ 🔴 ADMIN  │ [Revoke]││
│  │ 2  │ DATASET       │ Reference (#1)    │ 🟡 WRITE  │ [Revoke]││
│  │ 3  │ TABLE         │ currency (#1)     │ 🟢 READ   │ [Revoke]││
│  └────┴───────────────┴───────────────────┴───────────┴────────┘│
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 10.2 Grant Permission Dialog

**Trigger**: Click [+ Grant Permission]
**API**: `POST /api/v1/permissions`

```
┌──────────────────────────────────────────┐
│  Grant Permission               [✕ Close]│
├──────────────────────────────────────────┤
│                                          │
│  User *                                  │
│  ┌──────────────────────────────────┐    │
│  │ steward1 (#2)               ▼    │    │
│  └──────────────────────────────────┘    │
│                                          │
│  Resource Type *                         │
│  ┌──────────────────────────────────┐    │
│  │ DATASPACE                    ▼   │    │
│  └──────────────────────────────────┘    │
│  Options: DATASPACE, DATASET, TABLE      │
│                                          │
│  Resource *                              │
│  ┌──────────────────────────────────┐    │
│  │ Release-2.1 (#2)            ▼    │    │
│  └──────────────────────────────────┘    │
│  (Dropdown populated based on type)      │
│                                          │
│  Permission Level *                      │
│  ┌──────────────────────────────────┐    │
│  │ WRITE                       ▼    │    │
│  └──────────────────────────────────┘    │
│  Options: READ, WRITE, ADMIN             │
│                                          │
│          [Cancel]  [Grant Permission]    │
└──────────────────────────────────────────┘
```

**Form fields:**

| Field | Type | Required | Options | Maps to |
|---|---|---|---|---|
| User | Searchable dropdown | Yes | All active users | `PermissionRequest.userId` |
| Resource Type | Dropdown | Yes | DATASPACE, DATASET, TABLE | `PermissionRequest.resourceType` |
| Resource | Cascading dropdown | Yes | Filtered by type | `PermissionRequest.resourceId` |
| Permission Level | Dropdown | Yes | READ, WRITE, ADMIN | `PermissionRequest.level` |

**Cascading behavior**: When Resource Type changes, the Resource dropdown reloads:
- DATASPACE → calls `GET /api/v1/dataspaces`
- DATASET → calls `GET /api/v1/datasets/dataspace/{id}` (user selects dataspace first)
- TABLE → calls `GET /api/v1/tables/dataset/{id}` (user selects dataset first)

---

## 11. Environment Sync Screens

### 11.1 Environment List

**Route**: `/sync`
**API**: `GET /api/v1/sync/environments`
**Purpose**: View registered target environments and initiate comparisons.

```
┌─────────────────────────────────────────────────────────────────┐
│  Environment Sync                          [+ Register Environment]│
│                                                                  │
│  Registered Environments                                         │
│  ┌────┬──────────────┬──────┬──────────────────────────┬────────┐│
│  │ ID │ Name         │ Type │ DB URL                   │ Action ││
│  ├────┼──────────────┼──────┼──────────────────────────┼────────┤│
│  │ 1  │ DIT Server   │ DIT  │ jdbc:mysql://dit-db:3306 │ ⋮      ││
│  │ 2  │ SIT Server   │ SIT  │ jdbc:mysql://sit-db:3306 │ ⋮      ││
│  │ 3  │ UAT Server   │ UAT  │ jdbc:mysql://uat-db:3306 │ ⋮      ││
│  └────┴──────────────┴──────┴──────────────────────────┴────────┘│
│                                                                  │
│  ─────────────────────────────────────────────────────────────   │
│                                                                  │
│  COMPARE & SYNC                                                  │
│                                                                  │
│  Snapshot *                                                      │
│  ┌──────────────────────────────────────┐                        │
│  │ v2.1-final (#3)                  ▼   │                        │
│  └──────────────────────────────────────┘                        │
│                                                                  │
│  Target Environment *                                            │
│  ┌──────────────────────────────────────┐                        │
│  │ DIT Server (#1)                  ▼   │                        │
│  └──────────────────────────────────────┘                        │
│                                                                  │
│  Table Name (optional)                                           │
│  ┌──────────────────────────────────────┐                        │
│  │ currency_code                        │                        │
│  └──────────────────────────────────────┘                        │
│  (Leave blank to compare all tables)                             │
│                                                                  │
│                               [Compare & Generate Script]        │
└─────────────────────────────────────────────────────────────────┘
```

### 11.2 Register Environment Dialog

**Trigger**: Click [+ Register Environment]
**API**: `POST /api/v1/sync/environments`

```
┌──────────────────────────────────────────┐
│  Register Environment           [✕ Close]│
├──────────────────────────────────────────┤
│                                          │
│  Name *                                  │
│  ┌──────────────────────────────────┐    │
│  │ DIT Server                       │    │
│  └──────────────────────────────────┘    │
│                                          │
│  Type *                                  │
│  ┌──────────────────────────────────┐    │
│  │ DIT                          ▼   │    │
│  └──────────────────────────────────┘    │
│  Options: EBX, DIT, SIT, UAT, PROD      │
│                                          │
│  Database URL *                          │
│  ┌──────────────────────────────────┐    │
│  │ jdbc:mysql://dit-server:3306/mdm │    │
│  └──────────────────────────────────┘    │
│                                          │
│  Database Username *                     │
│  ┌──────────────────────────────────┐    │
│  │ mdm_user                         │    │
│  └──────────────────────────────────┘    │
│                                          │
│  Database Password *                     │
│  ┌──────────────────────────────────┐    │
│  │ ••••••••••••                     │    │
│  └──────────────────────────────────┘    │
│                                          │
│  [Test Connection]                       │
│                                          │
│          [Cancel]  [Register Environment]│
└──────────────────────────────────────────┘
```

**Form fields:**

| Field | Type | Required | Maps to |
|---|---|---|---|
| Name | Text input | Yes | `EnvironmentConfigRequest.name` |
| Type | Dropdown | Yes | `EnvironmentConfigRequest.type` |
| Database URL | Text input | Yes | `EnvironmentConfigRequest.dbUrl` |
| Database Username | Text input | Yes | `EnvironmentConfigRequest.dbUsername` |
| Database Password | Password input | Yes | `EnvironmentConfigRequest.dbPassword` |

### 11.3 Comparison Results & SQL Script

**Trigger**: Click [Compare & Generate Script]
**API**: `POST /api/v1/sync/compare`

```
┌─────────────────────────────────────────────────────────────────────┐
│  Environment Comparison Results                                      │
│  Snapshot: v2.1-final (#3)  →  Target: DIT Server                   │
│  Table: currency_code                                                │
│  ─────────────────────────────────────────────────────────────────   │
│                                                                      │
│  Summary:  🟢 2 Inserts  |  🟡 1 Updates  |  🔴 0 Deletes           │
│                                                                      │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  DIFF VIEW                                                    │  │
│  │                                                               │  │
│  │  Records to INSERT:                                           │  │
│  │  ┌──────┬──────────────┬────────────────┬────────┐           │  │
│  │  │ code │ name         │ decimal_places │ active │           │  │
│  │  ├──────┼──────────────┼────────────────┼────────┤           │  │
│  │  │ GBP  │ British Pound│ 2              │ true   │           │  │
│  │  │ CHF  │ Swiss Franc  │ 2              │ true   │           │  │
│  │  └──────┴──────────────┴────────────────┴────────┘           │  │
│  │                                                               │  │
│  │  Records to UPDATE:                                           │  │
│  │  ┌──────┬───────────┬──────────────┬──────────────────┐      │  │
│  │  │ code │ Field     │ Target (DIT) │ Source (MDM)     │      │  │
│  │  ├──────┼───────────┼──────────────┼──────────────────┤      │  │
│  │  │ USD  │ name      │ US Dollar    │ United States $  │      │  │
│  │  └──────┴───────────┴──────────────┴──────────────────┘      │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  GENERATED SQL SCRIPT                             [Copy] [⬇]  │  │
│  │                                                               │  │
│  │  -- MDM Sync Script                                           │  │
│  │  -- Source: v2.1-final | Target: DIT Server                   │  │
│  │  -- Generated: 2026-02-03 10:45:00                            │  │
│  │                                                               │  │
│  │  INSERT INTO `currency_code`                                  │  │
│  │    (`code`, `name`, `decimal_places`, `active`)               │  │
│  │  VALUES ('GBP', 'British Pound', 2, true);                    │  │
│  │                                                               │  │
│  │  INSERT INTO `currency_code`                                  │  │
│  │    (`code`, `name`, `decimal_places`, `active`)               │  │
│  │  VALUES ('CHF', 'Swiss Franc', 2, true);                      │  │
│  │                                                               │  │
│  │  UPDATE `currency_code`                                       │  │
│  │  SET `name` = 'United States Dollar'                          │  │
│  │  WHERE `_record_id` = 1;                                      │  │
│  │                                                               │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                      │
│          [Back to Environments]  [Download SQL Script]               │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 12. Screen Flow Diagrams

### 12.1 Primary Navigation Flow

```
Dashboard
  ├── Dataspaces (list)
  │     ├── Create Dataspace (dialog)
  │     └── Dataspace Detail
  │           ├── [Datasets tab]
  │           │     ├── Create Dataset (dialog)
  │           │     └── Dataset Detail
  │           │           ├── Create Table (form)
  │           │           └── Table Detail
  │           │                 ├── [Schema tab]
  │           │                 ├── [Records tab] → Record List
  │           │                 │     ├── Insert Record (dialog)
  │           │                 │     ├── Record Detail
  │           │                 │     └── Record History
  │           │                 └── [History tab]
  │           ├── [Snapshots tab]
  │           └── [Activity tab]
  │
  ├── Workflows (list)
  │     └── Workflow Review
  │
  ├── Snapshots (list)
  │     ├── Create Snapshot (dialog)
  │     └── Snapshot Comparison
  │
  ├── Env Sync
  │     ├── Register Environment (dialog)
  │     └── Comparison Results + SQL Script
  │
  └── Admin
        ├── Users (list)
        │     ├── Create/Edit User (dialog)
        │     └── User Permissions
        └── Permissions
              └── Grant Permission (dialog)
```

### 12.2 Data Entry Workflow (Maker-Checker)

```
                    ┌──────────────┐
                    │ User opens   │
                    │ Record List  │
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │ Clicks       │
                    │ [+ Insert]   │
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │ Fills form   │
                    │ and submits  │
                    └──────┬───────┘
                           │
              ┌────────────▼────────────┐
              │  Record saved as DRAFT  │
              │  Workflow created as    │
              │  PENDING                │
              └────────────┬────────────┘
                           │
              ┌────────────▼────────────┐
              │  Reviewer opens         │
              │  Workflow Queue         │
              └────────────┬────────────┘
                           │
                    ┌──────▼───────┐
                    │ Reviews data │
                    │ and decides  │
                    └──────┬───────┘
                           │
                ┌──────────┴──────────┐
                │                     │
         ┌──────▼──────┐      ┌──────▼──────┐
         │  APPROVE    │      │  REJECT     │
         │  Record →   │      │  Record →   │
         │  APPROVED   │      │  deleted or │
         │             │      │  reverted   │
         └─────────────┘      └─────────────┘
```

### 12.3 Environment Sync Workflow

```
       ┌────────────────────┐
       │ Create Snapshot of │
       │ Dataspace          │
       └────────┬───────────┘
                │
       ┌────────▼───────────┐
       │ Navigate to        │
       │ Env Sync page      │
       └────────┬───────────┘
                │
       ┌────────▼───────────┐
       │ Select Snapshot +  │
       │ Target Environment │
       └────────┬───────────┘
                │
       ┌────────▼───────────┐
       │ Click [Compare]    │
       └────────┬───────────┘
                │
       ┌────────▼───────────┐
       │ View diff:         │
       │ Added / Modified / │
       │ Removed records    │
       └────────┬───────────┘
                │
       ┌────────▼───────────┐
       │ Review generated   │
       │ SQL script         │
       └────────┬───────────┘
                │
       ┌────────▼───────────┐
       │ Download & execute │
       │ on target DB       │
       └────────────────────┘
```

---

## 13. Role-Based Visibility Matrix

| Screen / Feature | ADMIN | DATA_STEWARD | VIEWER |
|---|---|---|---|
| Dashboard | Full | Full | Read-only cards |
| Dataspaces — View | ✓ | ✓ | ✓ |
| Dataspaces — Create/Edit/Close | ✓ | ✓ | ✗ |
| Dataspaces — Merge | ✓ | ✗ | ✗ |
| Datasets — View | ✓ | ✓ | ✓ |
| Datasets — Create/Edit/Delete | ✓ | ✓ | ✗ |
| Table Definitions — View | ✓ | ✓ | ✓ |
| Table Definitions — Create/Edit/Delete | ✓ | ✓ | ✗ |
| Records — View | ✓ | ✓ | ✓ |
| Records — Insert/Update/Delete | ✓ | ✓ | ✗ |
| Record History — View | ✓ | ✓ | ✓ |
| Workflows — View Queue | ✓ | ✓ | ✓ |
| Workflows — Approve/Reject | ✓ | ✓ | ✗ |
| Snapshots — View | ✓ | ✓ | ✓ |
| Snapshots — Create | ✓ | ✓ | ✗ |
| Snapshots — Compare | ✓ | ✓ | ✓ |
| Environment Sync — View | ✓ | ✓ | ✓ |
| Environment Sync — Register | ✓ | ✗ | ✗ |
| Environment Sync — Compare | ✓ | ✓ | ✗ |
| User Management | ✓ | ✗ | ✗ |
| Permission Management | ✓ | ✗ | ✗ |

---

## Appendix A: Responsive Behavior

| Viewport | Layout Changes |
|---|---|
| Desktop (≥ 1280px) | Full layout with expanded left nav |
| Tablet (768–1279px) | Left nav collapses to icon-only rail; tables scroll horizontally |
| Mobile (< 768px) | Left nav hidden behind hamburger menu; forms stack vertically; tables become card-based views |

## Appendix B: Notification Rules

| Event | Notification | Recipients |
|---|---|---|
| New workflow PENDING | Badge count increment + toast | All DATA_STEWARD and ADMIN users |
| Workflow APPROVED | Toast notification | Record creator |
| Workflow REJECTED | Toast notification (warning) | Record creator |
| Dataspace MERGED | Toast notification | All users with permissions on that dataspace |
| Snapshot created | Info toast | Creator only |
| Env Sync completed | Info toast with download link | Initiator only |
