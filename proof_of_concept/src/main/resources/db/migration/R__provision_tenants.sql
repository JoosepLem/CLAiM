DO $$
DECLARE
    t RECORD;
BEGIN
    FOR t IN SELECT tenant_id FROM public.tenants LOOP
        EXECUTE format('CREATE SCHEMA IF NOT EXISTS %I', t.tenant_id);

        EXECUTE format('
            CREATE TABLE %I.treatment_invoices (
                id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                invoice_number  TEXT,
                uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
                source_filename TEXT
            )
        ', t.tenant_id);

        EXECUTE format('
            CREATE TABLE %I.treatment_invoice_lines (
                id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                invoice_id      BIGINT NOT NULL REFERENCES %I.treatment_invoices(id),
                isikukood       BYTEA NOT NULL,
                isikukood_hash  BYTEA NOT NULL,
                procedure_code  TEXT NOT NULL,
                amount          NUMERIC(12,2) NOT NULL,
                treatment_date  DATE NOT NULL
            )
        ', t.tenant_id, t.tenant_id);

        EXECUTE format('CREATE INDEX ON %I.treatment_invoice_lines(isikukood_hash)', t.tenant_id);

        EXECUTE format('
            CREATE TABLE %I.partner_invoices (
                id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                invoice_number  TEXT,
                provider_name   TEXT NOT NULL,
                uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
                source_filename TEXT
            )
        ', t.tenant_id);

        EXECUTE format('
            CREATE TABLE %I.partner_invoice_lines (
                id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                invoice_id      BIGINT NOT NULL REFERENCES %I.partner_invoices(id),
                isikukood       BYTEA NOT NULL,
                isikukood_hash  BYTEA NOT NULL,
                procedure_code  TEXT NOT NULL,
                amount          NUMERIC(12,2) NOT NULL,
                service_date    DATE NOT NULL
            )
        ', t.tenant_id, t.tenant_id);

        EXECUTE format('CREATE INDEX ON %I.partner_invoice_lines(isikukood_hash)', t.tenant_id);
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_user') THEN
            EXECUTE format('GRANT USAGE ON SCHEMA %I TO app_user', t.tenant_id);
            EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA %I TO app_user', t.tenant_id);
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA %I GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_user', t.tenant_id);
        END IF;
    END LOOP;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_user') THEN
        GRANT USAGE ON SCHEMA public TO app_user;
        GRANT SELECT ON ALL TABLES IN SCHEMA public TO app_user;
    END IF;
END $$;
