# Plan 0003 — Admin UI & RBAC

Answers POC Success Criterion: Tenant lifecycle management (create + delete tenants through admin UI, with schema provisioning and migration happening synchronously).

## Dependencies

- [x] 0001-tenant-isolation (tenant schemas exist, `TenantContext` works, `JwtAuthenticationFilter` works)
- [x] `TenantMigrationService` exists (reused for admin-driven schema creation)

## Decisions

| # | Decision |
|---|----------|
| 1 | Add `role TEXT NOT NULL DEFAULT 'CLINIC_EMPLOYEE'` column to `public.users` via V3 shared migration |
| 2 | Make `tenant_id` nullable in `users` (admin users have `tenant_id = NULL`) |
| 3 | Standard Spring Security `authorities` claim: `["ROLE_ADMIN"]` or `["ROLE_CLINIC_EMPLOYEE"]` in JWT |
| 4 | `JwtAuthenticationFilter` skips `TenantContext.set()` when role is ADMIN — admin queries stay on `public` schema |
| 5 | `JwtAuthenticationFilter` populates `UsernamePasswordAuthenticationToken` with real `GrantedAuthority` objects (not empty list) |
| 6 | Single login page — `AuthController` redirects admins to `/admin`, clinic employees to `/dashboard` |
| 7 | `SecurityConfig` restricts `/admin/**` to ADMIN role via `@PreAuthorize` or `.hasRole("ADMIN")` |
| 8 | First admin bootstrapped via V3 migration: `INSERT INTO users VALUES (..., 'admin', NULL, 'ADMIN')` |
| 9 | `DevSeedRunner` updated to set `role = 'CLINIC_EMPLOYEE'` on seed users |
| 10 | `AdminTenantService` handles tenant create (insert row → CREATE SCHEMA → migrate) and delete (DROP SCHEMA CASCADE → delete users → delete tenant) |
| 11 | `AdminUserService` handles full user CRUD against `public.users` |
| 12 | Single `/admin` Thymeleaf page with two sections: tenant table + user table, inline forms for CRUD |
| 13 | `AdminController` uses POST/Redirect/GET pattern, delegates to services, no REST JSON endpoints |
| 14 | Admin DDL operations (schema create/drop) go through `migrationDataSource` — tenant row/user row operations go through primary `dataSource` |
| 15 | Tenant create is synchronous (all steps inline during HTTP request) |
| 16 | Tenant delete is hard delete — no soft delete, no confirmation beyond the button |
| 17 | Tenant editing is out of scope (`tenant_id` is the schema name — immutable) |
| 18 | Error handling out of scope — unhandled exceptions result in 500 |
| 19 | No CSS framework — same minimal inline styles as existing `dashboard.html` |

## Files to create

| File | Purpose |
|------|---------|
| `src/main/resources/db/migration/shared/V3__add_role.sql` | Add `role` column to `users`, make `tenant_id` nullable, insert default admin |
| `src/main/kotlin/ee/claimai/admin/AdminTenantService.kt` | Tenant create + delete logic, delegates to `TenantMigrationService` for schema/migrations and `TenantRepository` for rows |
| `src/main/kotlin/ee/claimai/admin/AdminUserService.kt` | User CRUD against `public.users` via `UserRepository` |
| `src/main/kotlin/ee/claimai/admin/AdminController.kt` | GET `/admin` + POST handlers for tenant/user CRUD, `@PreAuthorize("hasRole('ADMIN')")` |
| `src/main/resources/templates/admin.html` | Single-page admin dashboard with tenant table + user table, inline create/edit/delete forms |
| `src/test/kotlin/ee/claimai/integration/AdminAuthTest.kt` | E2E: admin login → redirect to /admin; clinic employee accessing /admin → 403 |
| `src/test/kotlin/ee/claimai/integration/AdminTenantTest.kt` | E2E: create tenant → verify schema exists + row in tenants table; delete tenant → verify schema gone + users cleaned up |
| `src/test/kotlin/ee/claimai/integration/AdminUserTest.kt` | E2E: full user CRUD via admin endpoints |
| `src/test/kotlin/ee/claimai/unit/JwtServiceAdminTest.kt` | Unit: JWT generation/validation with authorities claim for both roles |

## Files to modify

| File | Change |
|------|--------|
| `src/main/kotlin/ee/claimai/user/User.kt` | Add `role: String = "CLINIC_EMPLOYEE"` field; change `tenantId: String` to `tenantId: String?` |
| `src/main/kotlin/ee/claimai/user/UserRepository.kt` | Add `findByRole(role: String): List<User>`, `findByTenantId(tenantId: String): List<User>` |
| `src/main/kotlin/ee/claimai/security/JwtService.kt` | `generateToken` accepts `role: String`; include `authorities` claim; `validateAndExtract` returns `authorities` list |
| `src/main/kotlin/ee/claimai/security/JwtAuthenticationFilter.kt` | Parse `authorities` from JWT claims; set real `GrantedAuthority` on auth token; skip `TenantContext.set()` when admin |
| `src/main/kotlin/ee/claimai/auth/AuthController.kt` | Read `role` from user; role-based redirect (admin → /admin, employee → /dashboard); add `GET /admin` dashboard endpoint |
| `src/main/kotlin/ee/claimai/config/SecurityConfig.kt` | Restrict `/admin/**` to ADMIN role via HTTP security config |
| `src/main/kotlin/ee/claimai/config/DevSeedRunner.kt` | Add `role` column to seed INSERT statements; add default admin user seed |
| `src/main/resources/application.yml` | No changes expected, unless `/admin` needs a separate CSRF or session config entry |

## V3 Migration

```sql
-- Add role column with default
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS role TEXT NOT NULL DEFAULT 'CLINIC_EMPLOYEE';

-- Allow admin users to have no tenant
ALTER TABLE public.users ALTER COLUMN tenant_id DROP NOT NULL;

-- Insert default admin (idempotent: skip if exists)
INSERT INTO public.users (username, tenant_id, role)
VALUES ('admin', NULL, 'ADMIN')
ON CONFLICT (username) DO NOTHING;
```

## Acceptance Criteria & Verification

### AC1 — V3 migration applies correctly (Flyway)
- **Verify:** Start the app (dev or test profile). Flyway runs V3 without error. Connect to DB and confirm:
  - `public.users` has a `role` column with default `'CLINIC_EMPLOYEE'`
  - `tenant_id` column is nullable
  - Row with `username = 'admin'` exists with `role = 'ADMIN'` and `tenant_id IS NULL`
  - Existing seed users (`user_a`, `user_b`) still have `tenant_id` set and `role = 'CLINIC_EMPLOYEE'`
- **Means:** Manual verification in dev, or smoke test that `DevSeedRunner` completes without error after V3 exists

### AC2 — Admin JWT contains authorities claim (unit)
- **Test:** `JwtServiceAdminTest.kt`
  1. `generateToken("admin", null, "ADMIN")` — assert JWT payload contains `"authorities": ["ROLE_ADMIN"]` and no `tenant_id` claim (or `tenant_id: null`)
  2. `generateToken("user_a", "tenant_a", "CLINIC_EMPLOYEE")` — assert JWT payload contains `"authorities": ["ROLE_CLINIC_EMPLOYEE"]` and `"tenant_id": "tenant_a"`
  3. `validateAndExtract(adminJwt)` — assert returned map has `"authorities" -> listOf("ROLE_ADMIN")`
  4. Tokens for different roles produce different authority values
- **Means:** Run with `./gradlew test --tests "*JwtServiceAdminTest"`

### AC3 — Admin login redirects to /admin (integration)
- **Test:** `AdminAuthTest.kt`
  1. POST `/login` with `username=admin` — assert 302 redirect to `/admin`
  2. POST `/login` with `username=user_a` — assert 302 redirect to `/dashboard` (existing behavior preserved)
  3. GET `/admin` with admin JWT cookie — assert 200, body contains admin dashboard HTML
  4. GET `/admin` with clinic employee JWT cookie — assert 403 forbidden
  5. GET `/admin` without JWT — assert 401 unauthorized
- **Means:** `@SpringBootTest(webEnvironment = RANDOM_PORT)`, extends `PostgresTestBase`, uses `TestRestTemplate`. Run with `./gradlew test --tests "*AdminAuthTest"`

### AC4 — Admin bypasses TenantContext (integration)
- **Test:** `AdminAuthTest.kt` (same file)
  1. Login as admin, then GET `/admin` — in a `@BeforeEach` or separate test, use `JdbcTemplate` to query `SELECT current_setting('search_path')` from a debug endpoint or verify indirectly via: admin dashboard renders tenant list from `public.tenants` (not from a tenant schema)
  2. Login as `user_a`, then GET `/dashboard` — verify tenant-scoped data is accessible (existing `TenantIsolationTest` behavior)
- **Means:** Can be combined into AC3 test class

### AC5 — Admin creates tenant end-to-end (integration)
- **Test:** `AdminTenantTest.kt`
  1. Login as `admin`, POST to tenant create endpoint with `tenantId=test_clinic&name=Test Clinic`
  2. Assert redirect to `/admin`
  3. Query `public.tenants` via `JdbcTemplate` — assert row exists with `tenant_id = 'test_clinic'`
  4. Query `information_schema.schemata` — assert schema `test_clinic` exists
  5. Query `test_clinic.treatment_invoices` — assert table exists (migrations ran successfully)
  6. GET `/admin` — assert HTML body contains "Test Clinic" in the tenant table
- **Means:** `@SpringBootTest(webEnvironment = RANDOM_PORT)`, extends `PostgresTestBase`. Run with `./gradlew test --tests "*AdminTenantTest"`

### AC6 — Admin deletes tenant end-to-end (integration)
- **Test:** `AdminTenantTest.kt` (same file)
  1. Setup: create a tenant with at least one user assigned to it
  2. Login as admin, POST to tenant delete endpoint for that tenant
  3. Assert redirect to `/admin`
  4. Query `public.tenants` — assert row no longer exists
  5. Query `information_schema.schemata` — assert schema no longer exists
  6. Query `public.users` — assert users with that `tenant_id` are deleted
  7. GET `/admin` — assert HTML body does NOT contain the deleted tenant
- **Means:** Same test class as AC5

### AC7 — Admin CRUD for users (integration)
- **Test:** `AdminUserTest.kt`
  1. **Create:** Login as admin, POST to user create endpoint with `username=test_user&tenantId=tenant_a&role=CLINIC_EMPLOYEE` — assert redirect, query DB: user exists
  2. **Read:** GET `/admin` with admin JWT — assert HTML body contains `test_user` in the user table, with correct tenant and role
  3. **Edit:** POST to user edit endpoint changing username to `test_user_renamed` — assert redirect, query DB: username changed, tenant/role preserved
  4. **Delete:** POST to user delete endpoint — assert redirect, query DB: user no longer exists, GET `/admin` HTML body no longer contains the deleted user
  5. **Filter by tenant:** GET `/admin?tenantFilter=tenant_a` — assert HTML body shows only users belonging to `tenant_a`
  6. **Create admin user:** POST to user create with `role=ADMIN` and no tenant — assert user has `tenant_id = NULL` in DB
- **Means:** `@SpringBootTest(webEnvironment = RANDOM_PORT)`, extends `PostgresTestBase`. Run with `./gradlew test --tests "*AdminUserTest"`

### AC8 — Admin UI renders correctly (manual / component)
- **Verify:** Start dev server (`./gradlew bootRun` or `docker compose up`), open `http://localhost:8080/login`, log in as `admin`
  1. Page displays a **tenant table** with columns: tenant ID, name, active. Each row has a delete button.
  2. Page displays a **tenant create form** with tenant ID + name fields and a submit button.
  3. After creating a tenant, the page refreshes and the new tenant appears in the table.
  4. After deleting a tenant, the page refreshes and the tenant disappears.
  5. Page displays a **user table** with columns: username, role, tenant. Each row has edit and delete buttons.
  6. Page displays a **user create form** with username, tenant dropdown, role dropdown, and submit button.
  7. User table has a **tenant filter dropdown** — selecting a tenant filters the table to show only that tenant's users.
  8. Editing a user shows an inline form with prepopulated values.
- **Means:** Manual walkthrough in browser

### AC9 — DevSeedRunner works with role column (dev/docker startup)
- **Verify:** Start app with `dev` or `docker` profile
  1. `user_a` and `user_b` are created with `role = 'CLINIC_EMPLOYEE'`
  2. `admin` user exists (either from V3 migration or seed)
  3. `DevSeedRunner` does not throw SQL exceptions due to missing `role` column
- **Means:** `./gradlew bootRun --args='--spring.profiles.active=dev'` starts without errors, or verify in existing integration tests that use `@ActiveProfiles("dev")`

### AC10 — app_user cannot perform admin DDL (security)
- **Verify:** This is already covered by the existing `AppUserPrivilegesTest.kt`. The admin endpoints use `migrationDataSource` for DDL — verify in `AdminTenantService` that `migrationDataSource` is injected (not the primary `dataSource`). Manual check: if the service were to accidentally use the primary datasource (which connects as `app_user`), the `CREATE SCHEMA` would fail with permission denied.
- **Means:** Code review of `AdminTenantService` constructor injection + existing `AppUserPrivilegesTest` running confirms `app_user` cannot create schemas

### AC11 — Tenant delete cleans up all associated data (integration)
- **Test:** `AdminTenantTest.kt` (same file as AC5/AC6)
  1. Create a tenant → create a user for that tenant → insert sample invoice data in the tenant schema
  2. Delete the tenant
  3. Assert: tenant row gone, users gone, schema gone, all tenant tables gone
- **Means:** Combined into AC6 test

### AC12 — Admin user cannot access tenant-scoped dashboard (integration)
- **Test:** `AdminAuthTest.kt` (same file as AC3)
  1. Login as admin
  2. GET `/dashboard` — assert response is either 403 (blocked by role) or 200 but without tenant data (since TenantContext is null). The correct behavior is that admin should NOT see tenant data — either redirect to `/admin` or show empty dashboard.
  3. Decision: admin accessing `/dashboard` should redirect to `/admin` (prevent confusion)
- **Means:** Add a guard in `AuthController.dashboard()` that checks role and redirects admin to `/admin`

## Out of scope (deferred)

- Error handling (duplicate tenant ID, invalid schema name → 500)
- Flash messages / success feedback on CRUD operations
- Tenant editing (tenant ID is the schema name — immutable)
- Soft delete for tenants
- REST JSON API (service layer only, no `@RestController`)
- React frontend integration
- Pagination for tenant/user tables
- Password-based authentication
- Role hierarchy / many-to-many roles
