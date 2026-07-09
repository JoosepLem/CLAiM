# CLAiM Proof of Concept — Scope & Requirements

## Purpose

This document defines the scope, clarified requirements, and goals for the CLAiM proof of concept. A separate architecture document will detail the technical design.

---

## POC Success Criteria

The POC is complete when all of the following questions have a satisfactory answer:

1. **Multi-tenant isolation** — How is it ensured at the application level that clinic schema boundaries are not violated (e.g., a query to `clinic_A.table` cannot see `clinic_B` data)?
2. **Audit log placement** — Where does the audit log physically live? Per-tenant schema vs. cross-tenant log schema?
3. **KMS performance impact** — How much does the AWS key vault slow down each request? What is the latency difference between loading a key from KMS on every request vs. caching?
4. **HMAC + AES-256-GCM viability** — Does the HMAC + AES-256-GCM implementation work correctly at CLAiM's data volumes? Correctness via round-trip testing and HMAC-based matching on synthetic data; performance reasonableness.

---

## Test Setup

- **Number of test tenants:** 2
- **Tenant isolation verification:** Explicit — log in as Tenant A, confirm Tenant B's data is not visible, and vice versa.
- **Performance measurement:** Benchmark endpoint-level performance on endpoints that encrypt and decrypt data, using log traces for latency measurement. Compare three scenarios: first call (lazy-loading DEK from KMS into cache), cached DEK, and no encryption at all (baseline).

---

## Clarified Requirements

### 1. Multi-Tenant Routing — Schema per Tenant

**Decision:** JWT claim-based tenant resolution.

- User logs in → JWT contains tenant ID (schema name)
- Spring `HandlerInterceptor` extracts tenant from JWT, sets a `TenantContext`
- A single database user (`app_user`) connects to PostgreSQL and routes queries by setting `search_path` to the tenant's schema
- DB user permissions: `CONNECT` on database, `USAGE` on all tenant schemas, `SELECT/INSERT/UPDATE/DELETE` on tenant tables
- **No** `CREATE SCHEMA`, `DROP`, `ALTER`, `TRUNCATE` for the app user

**Alternative approach (noted, not tested unless approach above is unsatisfactory):**
- Separate database user per clinic, each with a default `search_path` to their schema, connection pool holds per-tenant connections

**Schema provisioning:** A separate migration user (`app_migrator`) handles all DDL via Flyway/Liquibase during CI/CD or app startup. The app user cannot create or modify schemas.

**User-to-tenant mapping:** A `users` table in the `public` schema maps `username → tenant_id` (schema name). The app user has read-only access to `public.users`.

---

### 2. Encryption at Rest and in Transit (Database)

**Decision:** AWS RDS for PostgreSQL.

- **At rest:** RDS encryption enabled, backed by AWS KMS (one-time configuration)
- **In transit:** SSL enforced via RDS parameter group (`rds.force_ssl=1`), JDBC connects with `sslmode=require`
- No application code beyond JDBC URL configuration needed

---

### 3. Reading and Saving Only Necessary Data from Treatment Invoices

**Four tables per tenant schema:**

`treatment_invoices` — header-level, one row per uploaded treatment invoice:

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| invoice_number | TEXT | Nullable, if present in the source |
| uploaded_at | TIMESTAMPTZ | When the invoice was uploaded |
| source_filename | TEXT | Original PDF filename |

`treatment_invoice_lines` — line-level, one row per line item. Used by the reconciliation engine:

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| invoice_id | UUID | FK to treatment_invoices |
| isikukood | BYTEA (encrypted) | AES-256-GCM encrypted with tenant DEK |
| isikukood_hash | BYTEA | HMAC-SHA256 for exact-match lookups (indexed) |
| procedure_code | TEXT | |
| amount | NUMERIC | |
| treatment_date | DATE | |

`partner_invoices` — header-level, one row per uploaded partner invoice:

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| invoice_number | TEXT | Nullable |
| provider_name | TEXT | |
| uploaded_at | TIMESTAMPTZ | |
| source_filename | TEXT | |

`partner_invoice_lines` — line-level, one row per line item. Used by the reconciliation engine:

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| invoice_id | UUID | FK to partner_invoices |
| isikukood | BYTEA (encrypted) | Encrypted on parse, before persistence |
| isikukood_hash | BYTEA | HMAC-SHA256 (indexed) |
| procedure_code | TEXT | |
| amount | NUMERIC | |
| service_date | DATE | |

The reconciliation engine matches `treatment_invoice_lines` against `partner_invoice_lines` by isikukood_hash + procedure_code.

**No** diagnosis codes, patient names, or personally identifiable data other than the isikukood. Only exact-match queries on isikukood (via HMAC hash column with index). No partial/prefix search needed.

Uploaded PDFs are parsed and discarded — only the extracted relational data is persisted. No blob/documents storage.

---

### 4. Column-Level Encryption of Isikukood

**Approach:** Envelope encryption with DEK per tenant + master key in AWS KMS.

- Each tenant has a data encryption key (DEK), stored encrypted in the tenant's schema (`tenant_keys` table)
- The DEK is encrypted with a master key in AWS KMS
- Isikukood is encrypted using AES-256-GCM with the tenant's DEK
- A parallel HMAC-SHA256 hash of the isikukood is stored for exact-match lookups (indexed column)
- HMAC key is also derived from the tenant's DEK, ensuring different tenants produce different hashes for the same isikukood
- Deterministic encryption is NOT used — only the HMAC hash enables queries

**Parser separation:** The PDF parser extracts the raw isikukood and returns it in a DTO. The service layer handles all encryption and HMAC computation. The parser has zero crypto knowledge.

---

### 5. Performance of Decryption on Queries (Envelope Encryption)

**Measurement scope:**

- Endpoint-level latency on first call (lazy-loading DEK from KMS into Caffeine cache)
- Endpoint-level latency with cached DEK (encryption/decryption enabled, DEK in memory)
- Endpoint-level latency with no encryption at all (baseline)
- Compare all three to measure KMS fetch overhead and column-level encryption overhead
- Measured via log traces / structured logging, not a separate benchmark harness

---

### 6. DEK Caching in Application Memory

**Decision:** Caffeine cache with lazy load and 30-minute expiry.

- Cache key: tenant ID
- Cache value: plaintext `SecretKey` (DEK)
- Loaded on first request that needs encryption/decryption for that tenant (cache loader fetches from DB, unwraps via KMS)
- `expireAfterAccess(30, TimeUnit.MINUTES)` — if a tenant is idle for 30 minutes, the DEK drops from memory
- Fits sparse usage pattern (1-2 times/month per tenant)
- Caffeine provides thread-safe single-flight loading (no duplicate KMS calls under concurrent requests)
- Statistics available for POC performance analysis

---

### 7. Append-Only Audit Log per Tenant

**Decision:** Detailed audit log with hash chain for tamper evidence.

**Schema (per tenant):**

| Column | Type | Description |
|---|---|---|
| id | BIGSERIAL | Primary key |
| event_time | TIMESTAMPTZ | Default `now()` |
| username | TEXT | Authenticated user |
| action | TEXT | `READ`, `CREATE`, `UPDATE`, `DELETE` |
| endpoint | TEXT | e.g., `GET /api/treatment-invoices/123` |
| record_type | TEXT | `treatment_invoice`, `partner_invoice` |
| record_id | TEXT | Row UUID |
| fields_accessed | TEXT[] | e.g., `['isikukood', 'procedure_code']` |
| purpose | TEXT | `reconciliation`, `upload`, `review` |
| ip_address | INET | Client IP |
| user_agent | TEXT | |
| success | BOOLEAN | Was access granted? |
| details | JSONB | Extra context |
| chain_hash | BYTEA | SHA-256(previous_chain_hash \|\| current_entry_data) |

**Audit log placement:** Per-tenant `audit_log` table in each tenant schema. A `SECURITY DEFINER` function (`log_audit_event(...)`) owned by the migration user handles inserts. The app user has zero `INSERT` on the audit table directly and can only call the function. Hash chain is computed inside the function.

---

### 8. App User Cannot Edit or Delete Audit Log

**Decision:** PostgreSQL `SECURITY DEFINER` function.

- App user has zero `INSERT`, `UPDATE`, `DELETE`, `TRUNCATE` on the audit log table
- App user has only `EXECUTE` on the `log_audit_event(...)` function
- The function is owned by the migration user and runs with their privileges
- Hash chain computation happens inside the function (database-level)
- This prevents both the app code and a DBA from inserting fake entries, as the hash chain would break on verification

---

### 9. Key Rotation for Tenants

**Decision:** Hybrid approach — lazy reads with key versioning, plus optional batch re-encryption.

**Two keys in play:**

| Key | Where | Rotation |
|---|---|---|
| Master key (KEK) | AWS KMS | Automatic yearly rotation via KMS |
| Tenant DEK | `tenant_keys` table in tenant schema | Manual via admin endpoint |

**DEK rotation mechanism:**

- Each encrypted row stores a `key_version` column referencing which DEK version encrypted it
- `tenant_keys` table holds multiple versions per tenant: `(tenant_id, key_version, encrypted_dek, created_at)`
- On read: check `key_version`, fetch the correct DEK from `tenant_keys`, decrypt
- On write: always use the latest active DEK version
- Rotation: generate new DEK, encrypt with KMS master key, store as new version. Old rows remain encrypted with old DEK version.
- Optional admin endpoint triggers batch re-encryption of old rows with the new DEK
- After all rows are re-encrypted, old DEK version can be retired

---

### 10. Uploading and Reading Partner Invoices

**Decision:** Strategy pattern for PDF parsers.

- Each partner format gets its own `@Component` implementing `InvoiceParser` interface:

```kotlin
interface InvoiceParser {
    val partnerId: String
    fun parse(inputStream: InputStream): ParsedInvoice
}
```

- A `ParserRegistry` holds a `Map<String, InvoiceParser>` (auto-wired by Spring)
- The reconciliation engine calls `parserRegistry.getParser(partnerId).parse(pdf)`
- Adding a new format = one new class, zero changes to existing code
- Parsers are built later (outside this POC scope document), up to 30 formats expected eventually

---

### 11. Encryption of Isikukood on Reading from Partner Invoice

**Decision:** Parser extracts raw isikukood → service layer handles encryption + HMAC.

- The PDF parser class extracts the raw isikukood from the PDF and returns it in a `ParsedInvoice` DTO
- The service layer (`InvoiceService`) receives the DTO, computes `encrypt(isikukood)` and `hmac(isikukood)`, and persists both columns
- The raw isikukood exists only in JVM memory during the request lifecycle
- Parser has no knowledge of encryption

---

### 12. Authentication — HTTPS + JWT

**Decision:** Single JWT, 6-hour expiry, no refresh token, no sliding expiration.

- Login page: free-text username input (Thymeleaf server-rendered)
- Backend validates username against `public.users` table
- If found: issues a JWT containing the user's tenant ID (schema name), set in a secure cookie
- Cookie attributes:
  - `HttpOnly` — not accessible from JavaScript
  - `Secure` — only sent over HTTPS
  - `SameSite=Strict` — no cross-site requests
- JWT expiry: 6 hours
- After expiry: user must log in again with username
- No password — username-only auth is POC-scoped only

---

### 13. UI Page Structure

**Decision:** Three Thymeleaf server-rendered pages, all scoped to the user's tenant via JWT.

**1. Login page** (`/login`):
- Single free-text username input field
- No password — username-only auth for POC
- On success: JWT set in secure cookie, redirect to overview
- On failure: error message, retry

**2. Overview page** (`/dashboard`):
- Two visually distinct sections:
  - **Partner invoices** — uploaded partner service invoices, each row shows upload date, provider, invoice number, reconciliation status, and match summary counts (matched / mismatch / unmatched)
  - **Treatment invoices** — uploaded patient reimbursement invoices, each row shows upload date, invoice number
- **Upload form** embedded on the page: multipart file picker for PDF, partner selector dropdown, submit button. After upload, invoice appears in the relevant section.
- **Bulk reconciliation button** — triggers reconciliation across all partner invoices that have a treatment invoice for the same month
- Each partner invoice row links to its detail page

**3. Invoice detail page** (`/invoices/{id}`):
- Displays reconciliation results for a single partner invoice
- Sortable table with columns: partner invoice line details, matched treatment invoice line details, amount (both), difference, status
- Status badges: matched (green), amount mismatch (yellow), unmatched (red)
- Back link to overview

**Reconciliation triggers:**
- **Automatic** on partner invoice upload, if a treatment invoice exists for the same month
- **Manual** via bulk button on the overview page

**No** export functionality for the POC.

---

### 14. Reconciliation Logic

**Match criteria:** Isikukood + procedure code.

For each line on a partner invoice, find a corresponding treatment invoice line with the same isikukood (via HMAC hash match) and same procedure code.

**Amount handling:** Amount is not a match criterion, but is compared and displayed. Lines matching on isikukood + procedure code but with different amounts are flagged as "matched with amount discrepancy."

**Results categories:**

| Status | Meaning |
|---|---|
| Matched | Same isikukood + procedure code, same amount |
| Amount mismatch | Same isikukood + procedure code, different amounts |
| Unmatched | No corresponding treatment invoice line for the partner line |

---

### 15. Reconciliation Results Display

**Decision:** Shown on the invoice detail page (`/invoices/{id}`), Thymeleaf server-rendered, scoped to the user's tenant.

- Sortable table with columns: partner invoice line, matched treatment invoice line, amount (both), difference, status
- Status badges: matched (green), amount mismatch (yellow), unmatched (red)
- Back link to overview

---

### 16. Reconciliation Storage

**Decision:** Reconciliation runs and results are stored in per-tenant schemas for historical review without re-running matching.

**`reconciliation_runs`** — one row per reconciliation execution:

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| triggered_by | TEXT | Username who triggered the run |
| run_at | TIMESTAMPTZ | When the run was started |
| status | TEXT | `running`, `completed`, `failed` |
| total_matched | INTEGER | |
| total_amount_mismatch | INTEGER | |
| total_unmatched | INTEGER | |

**`reconciliation_results`** — one row per partner line comparison. Contains no personal data:

| Field | Type | Notes |
|---|---|---|
| id | UUID | Primary key |
| run_id | UUID | FK to reconciliation_runs |
| partner_invoice_id | UUID | FK to partner_invoices — for per-invoice display in the UI |
| partner_invoice_line_id | UUID | FK to partner_invoice_lines |
| treatment_invoice_line_id | UUID | FK to treatment_invoice_lines, nullable if unmatched |
| match_status | TEXT | `matched`, `amount_mismatch`, `unmatched` |
| partner_amount | NUMERIC | |
| treatment_amount | NUMERIC | Nullable if unmatched |
| amount_difference | NUMERIC | Nullable if unmatched |

**Audit interaction:** `reconciliation_results` contains only line IDs and amounts — no personal data. However, the reconciliation engine accesses `treatment_invoice_lines` and `partner_invoice_lines` (which contain encrypted isikukood) during matching. Each access generates a READ audit event via the audit service, preserving the audit trail for personal data access separately from result storage.

---

### 17. Secure Invoice Upload

**Decision:** Web UI file upload with validation.

- File picker embedded on the overview page (Thymeleaf form, multipart POST)
- Endpoint requires valid JWT (tenant-scoped)
- Validation:
  - File extension: `.pdf` only
  - MIME type: `application/pdf`
  - Magic bytes: file must start with `%PDF-`
  - Maximum file size: 10 MB
- After parsing, the PDF is discarded — no long-term storage of uploaded documents

Error handling is rudimental for the POC: invalid/corrupt PDFs return an error message and are skipped.

---

### 18. General Notes

- **Technology stack:** Spring Boot (Kotlin), PostgreSQL on AWS RDS, Thymeleaf server-rendered UI, Gradle (Kotlin DSL), Docker Compose for local PostgreSQL
- **Frontend:** Thymeleaf for the POC; may adopt a frontend framework later
- **Test data:** Generated separately (synthetic). Exact PDF parsers built later.
- **Security scope:** POC-level — HTTPS, JWT, column-level encryption, HMAC searchable hashes, append-only audit log with hash chain
- **Production readiness:** Not required for POC. Demonstrates technical feasibility of the chosen patterns.

---

### 19. Admin UI & RBAC (Add-On)

**Purpose:**

Close the loop on tenant management by adding a minimal admin dashboard with role-based access control. This enables the POC to test the full tenant lifecycle — provisioning, user assignment, and decommissioning — through a UI rather than SQL or app restarts.

### POC Success Criterion

- **Tenant lifecycle management** — Can a tenant be created and deleted through the admin UI, with schema provisioning and migration happening synchronously and correctly?

---

#### 19.1 User Roles

**Decision:** Add a `role` column to the `public.users` table.

- `ADMIN` — Manages tenants and users. Has no tenant affiliation (`tenant_id = NULL`). Bypasses tenant-scoped data access (no `search_path` set).
- `CLINIC_EMPLOYEE` — Existing tenant-scoped user. Access limited to their tenant's schema via `search_path`.

**Migration:** A new shared migration (`V3__add_role.sql`) adds `role TEXT NOT NULL DEFAULT 'CLINIC_EMPLOYEE'` to `public.users`.

---

#### 19.2 JWT Claims

**Decision:** Use the standard `authorities` claim as a string array.

- Admin JWT: `"authorities": ["ROLE_ADMIN"]`
- Clinic employee JWT: `"authorities": ["ROLE_CLINIC_EMPLOYEE"]`

No `tenant_id` claim for admin users (or set to JSON `null`). The `JwtAuthenticationFilter` uses the `authorities` claim to populate Spring Security `GrantedAuthority` objects.

---

#### 19.3 Authentication Flow

**Decision:** Single login page serves both roles.

- Login validates username against `public.users`, reads `role` and `tenant_id`
- JWT is issued with `authorities` claim
- Post-login redirect: admins → `/admin`, clinic employees → `/dashboard`
- `@PreAuthorize("hasRole('ADMIN')")` guards all `/admin/**` endpoints

**TenantContext bypass:** When `role = ADMIN`, the `JwtAuthenticationFilter` does not set a `TenantContext`. Admin queries operate on the `public` schema only.

---

#### 19.4 First Admin Bootstrap

**Decision:** Flyway migration inserts a default admin user.

The `V3__add_role.sql` migration also inserts:
```sql
INSERT INTO public.users (username, tenant_id, role)
VALUES ('admin', NULL, 'ADMIN');
```

This ensures an admin is available immediately at startup. Works identically in dev, docker, staging, and production profiles.

---

#### 19.5 Admin UI — Layout

**Decision:** A single Thymeleaf page at `/admin` with two sections.

- **Tenant section:** Table listing all tenants (tenant ID, name, active status). Inline form to create a new tenant (tenant ID + name). Delete button per row.
- **User section:** Table listing all users (username, role, tenant). Filterable by tenant. Inline form to create a new user (username, tenant dropdown, role dropdown). Edit and delete buttons per row.

Minimal styling — same approach as the existing login and dashboard pages. No CSS framework.

---

#### 19.6 Tenant CRUD

**Decision:** Create and delete only. No editing (tenant ID is immutable — it is the schema name). Read is the tenant table on `/admin`.

**Create flow (synchronous):**
1. Validate tenant ID format (`[a-z][a-z0-9_]{0,62}`)
2. INSERT into `public.tenants`
3. `CREATE SCHEMA IF NOT EXISTS tenant_{id}`
4. Run tenant migrations on the new schema via `TenantMigrationService.migrateTenant(schema)`
5. All steps run inline during the HTTP request

**Delete flow (synchronous, hard delete):**
1. `DROP SCHEMA IF EXISTS tenant_{id} CASCADE`
2. DELETE from `public.users` WHERE `tenant_id = ?`
3. DELETE from `public.tenants` WHERE `tenant_id = ?`

No soft delete, no confirmation beyond the delete button. Page refreshes and the tenant is gone.

---

#### 19.7 User CRUD

**Decision:** Full create, read, update, and delete for users.

- **Create:** Username, tenant assignment (dropdown of active tenants), role (ADMIN or CLINIC_EMPLOYEE via dropdown). No password — username-only auth consistent with existing POC auth.
- **Read:** User table on `/admin`, filterable by tenant via a dropdown. All users visible (across all tenants and admin users).
- **Update:** Change username, tenant assignment, or role.
- **Delete:** Remove the user row. No cascade concerns — users own no data.

---

#### 19.8 Service Layer

**Decision:** Thin REST service layer behind Thymeleaf controllers.

- `AdminTenantService` — Tenant create/delete logic, delegates to `TenantMigrationService` for schema + migration concerns and `TenantRepository` for row operations.
- `AdminUserService` — User CRUD against `public.users` via a `UserRepository`.

Controllers (`AdminController`) handle form submissions with POST/Redirect/GET and delegate to services. No `@RestController` JSON endpoints for the POC, but services are structured so REST endpoints can be added later without logic changes.

---

#### 19.9 DataSource for Admin Operations

Admin endpoints that perform DDL (schema creation, schema drop) write through the migration datasource (`app_migrator`). The runtime datasource (`app_user`) cannot create or drop schemas. `TenantMigrationService` already uses the migration datasource via programmatic Flyway — admin flows reuse this.

---

#### 19.10 Error Handling

**Decision:** Out of scope for the POC. Unhandled exceptions (duplicate tenant ID, invalid schema name, DB errors) result in HTTP 500 responses. No flash messages, inline validation, or custom error pages.

---

#### 19.11 Changes to Existing Requirements

- **Requirement 1 (Multi-Tenant Routing):** `JwtAuthenticationFilter` conditionally skips setting `TenantContext` when the user's role is `ADMIN`.
- **Requirement 12 (Authentication):** `AuthController` inspects the user's role after successful authentication and redirects accordingly (`/admin` for `ADMIN`, `/dashboard` for `CLINIC_EMPLOYEE`).
