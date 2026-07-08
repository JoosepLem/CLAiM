# Plan 0001 — Tenant Isolation + Schema Provisioning

Answers POC Success Criterion #1: Multi-tenant isolation.

## Decisions

| # | Decision |
|---|----------|
| 1 | `TenantSchema` class — centralized `qualify(table)` method reads from `TenantContext` |
| 2 | `TenantContext` — ThreadLocal holder, set by `OncePerRequestFilter`, cleared after request |
| 3 | V2 migration — creates `tenant_a` and `tenant_b` schemas, each with 4 tables |
| 4 | `isikukood` as `BYTEA` + `isikukood_hash BYTEA` from start (columns unused until encryption slice) |
| 5 | Two PG users: `app_migrator` (DDL/Flyway) + `app_user` (runtime, restricted) |
| 6 | Docker Compose init script creates `app_migrator` user |
| 7 | `spring.flyway.user`/`password` in `application.yml` — single `DataSource` bean for runtime |
| 8 | `SecurityConfig` hardened: `OncePerRequestFilter` validates JWT, sets cookie flags (`HttpOnly`, `SameSite=Strict`, `Secure` conditional) |
| 9 | `/login` + `/logout` public; everything else returns 401 on invalid/missing/expired JWT |
| 10 | Throwaway `GET /api/invoices` returns JSON scoped to tenant — exists only for e2e test |
| 11 | E2E test: `@SpringBootTest` + `TestRestTemplate`, insert data for both tenants, verify cross-tenant invisibility |

## Files to create

| File | Purpose |
|------|---------|
| `src/main/kotlin/ee/claimai/tenant/TenantContext.kt` | ThreadLocal tenant ID holder |
| `src/main/kotlin/ee/claimai/tenant/TenantSchema.kt` | Centralized `qualify(table)` |
| `src/main/kotlin/ee/claimai/security/JwtAuthenticationFilter.kt` | `OncePerRequestFilter` — validates JWT, sets TenantContext, 401 on failure |
| `src/main/kotlin/ee/claimai/invoice/InvoiceController.kt` | Throwaway `GET /api/invoices` for e2e test |
| `src/main/kotlin/ee/claimai/invoice/InvoiceRepository.kt` | JDBC repository querying tenant-scoped tables |
| `docker-entrypoint-initdb.d/01-create-users.sh` | Creates `app_migrator` user on container init |
| `src/test/kotlin/ee/claimai/integration/TenantIsolationTest.kt` | E2E isolation test |

## Files to modify

| File | Change |
|------|--------|
| `V2__create_tenant_template.sql` | Replace SQL comments with executable DDL: create `tenant_a` + `tenant_b` schemas, 4 tables each, grants to `app_user` |
| `application.yml` | Add `spring.flyway.user`/`password` for migrator credentials; add `app.security.secure-cookie` toggle |
| `SecurityConfig.kt` | Register `JwtAuthenticationFilter`, permit `/login` + `/logout`, protect everything else |
| `AuthController.kt` | Set `SameSite=Strict`, `HttpOnly`, conditional `Secure` on JWT cookie |
| `docker-compose.yml` | Add `app_migrator` user env var, mount init script |
| `build.gradle.kts` | Add `spring-boot-starter-test` and TestRestTemplate dependency if missing |

## Per-tenant tables (V2)

Each tenant schema (`tenant_a`, `tenant_b`) gets:

```sql
CREATE TABLE {tenant}.treatment_invoices (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_number TEXT,
    uploaded_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    source_filename TEXT
);

CREATE TABLE {tenant}.treatment_invoice_lines (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id     UUID NOT NULL REFERENCES {tenant}.treatment_invoices(id),
    isikukood      BYTEA NOT NULL,
    isikukood_hash BYTEA NOT NULL,
    procedure_code TEXT NOT NULL,
    amount         NUMERIC(12,2) NOT NULL,
    treatment_date DATE NOT NULL
);

CREATE INDEX idx_til_hash ON {tenant}.treatment_invoice_lines(isikukood_hash);

CREATE TABLE {tenant}.partner_invoices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_number  TEXT,
    provider_name   TEXT NOT NULL,
    uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    source_filename TEXT
);

CREATE TABLE {tenant}.partner_invoice_lines (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id     UUID NOT NULL REFERENCES {tenant}.partner_invoices(id),
    isikukood      BYTEA NOT NULL,
    isikukood_hash BYTEA NOT NULL,
    procedure_code TEXT NOT NULL,
    amount         NUMERIC(12,2) NOT NULL,
    service_date   DATE NOT NULL
);

CREATE INDEX idx_pil_hash ON {tenant}.partner_invoice_lines(isikukood_hash);
```

## Grants

```sql
GRANT USAGE ON SCHEMA {tenant} TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA {tenant} TO app_user;
```

## Acceptance criteria

- [ ] `docker compose up` provisions `app_migrator` + `app_user`, runs V1+V2, creates `tenant_a` and `tenant_b` with all tables
- [ ] `GET /login` returns 200 (public)
- [ ] `GET /dashboard` without JWT returns 401
- [ ] Login as `user_a` → JWT cookie set → `GET /api/invoices` returns only `tenant_a` data
- [ ] Login as `user_b` → `GET /api/invoices` returns only `tenant_b` data
- [ ] `user_a` cannot see `tenant_b` data and vice versa
- [ ] `app_user` cannot create schemas or tables (verified in test)
- [ ] JWT cookie has `HttpOnly=true`, `SameSite=Strict`

## Out of scope (deferred to future slices)

- Encryption (AES-256-GCM + HMAC key derivation)
- DEK caching (Caffeine)
- KMS integration
- PDF parsing and upload
- Reconciliation logic and storage
- Audit logging
- Key rotation
- Invoice detail page
