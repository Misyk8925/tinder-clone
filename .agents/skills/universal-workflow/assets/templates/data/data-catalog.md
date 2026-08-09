# Data catalog — <feature-slug>

Status: DRAFT | APPROVED <date>
Migration(s): `<path to the real migration file(s) in the repo>` (source of truth — SQL runs, this doesn't)

## Table: <table_name>

Purpose: <why this table exists>
Owner: <team/service>
Tenant boundary: <row-level (tenant_id column) | schema-per-tenant | database-per-tenant>

| Column | Type | Meaning | PII? | Retention |
|--------|------|---------|------|-----------|
| id | uuid | | no | |
| tenant_id | uuid | | no | |

**Relationships worth knowing** (beyond the FK itself):
- <e.g. "total is denormalized from line_items and must be recalculated on any line-item change">

**Indexes**

| Index | Columns | Query it serves |
|-------|---------|------------------|
