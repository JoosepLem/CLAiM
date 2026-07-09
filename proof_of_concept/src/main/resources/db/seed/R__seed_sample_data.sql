INSERT INTO public.tenants (tenant_id, name) VALUES
    ('tenant_a', 'Demo Clinic 1'),
    ('tenant_b', 'Demo Clinic 2')
ON CONFLICT (tenant_id) DO NOTHING;

INSERT INTO public.users (username, tenant_id) VALUES
    ('user_a', 'tenant_a'),
    ('user_b', 'tenant_b')
ON CONFLICT (username) DO NOTHING;

DO $$
DECLARE
    t RECORD;
BEGIN
    FOR t IN SELECT tenant_id FROM public.tenants LOOP
        EXECUTE format('CREATE SCHEMA IF NOT EXISTS %I', t.tenant_id);

        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.treatment_invoices (
                id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                invoice_number  TEXT,
                uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
                source_filename TEXT
            )
        ', t.tenant_id);

        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.treatment_invoice_lines (
                id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                invoice_id      BIGINT NOT NULL REFERENCES %I.treatment_invoices(id),
                isikukood       BYTEA NOT NULL,
                isikukood_hash  BYTEA NOT NULL,
                procedure_code  TEXT NOT NULL,
                amount          NUMERIC(12,2) NOT NULL,
                treatment_date  DATE NOT NULL
            )
        ', t.tenant_id, t.tenant_id);

        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%I_til_hash ON %I.treatment_invoice_lines(isikukood_hash)', t.tenant_id, t.tenant_id);

        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.partner_invoices (
                id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                invoice_number  TEXT,
                provider_name   TEXT NOT NULL,
                uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
                source_filename TEXT
            )
        ', t.tenant_id);

        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.partner_invoice_lines (
                id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                invoice_id      BIGINT NOT NULL REFERENCES %I.partner_invoices(id),
                isikukood       BYTEA NOT NULL,
                isikukood_hash  BYTEA NOT NULL,
                procedure_code  TEXT NOT NULL,
                amount          NUMERIC(12,2) NOT NULL,
                service_date    DATE NOT NULL
            )
        ', t.tenant_id, t.tenant_id);

        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%I_pil_hash ON %I.partner_invoice_lines(isikukood_hash)', t.tenant_id, t.tenant_id);
    END LOOP;
END $$;

INSERT INTO tenant_a.treatment_invoices (invoice_number, source_filename) VALUES
    ('TA-INV-001', 'ta_invoice_001.pdf'),
    ('TA-INV-002', 'ta_invoice_002.pdf'),
    ('TA-INV-003', 'ta_invoice_003.pdf');

INSERT INTO tenant_a.treatment_invoice_lines (invoice_id, isikukood, isikukood_hash, procedure_code, amount, treatment_date)
SELECT id, E'\\x010203'::bytea, E'\\x040506'::bytea, '3004', 150.00, '2025-01-15'::date
FROM tenant_a.treatment_invoices WHERE invoice_number = 'TA-INV-001'
UNION ALL
SELECT id, E'\\x070809'::bytea, E'\\x0a0b0c'::bytea, '3008', 200.00, '2025-01-16'::date
FROM tenant_a.treatment_invoices WHERE invoice_number = 'TA-INV-001'
UNION ALL
SELECT id, E'\\x0d0e0f'::bytea, E'\\x101112'::bytea, '3012', 350.00, '2025-02-01'::date
FROM tenant_a.treatment_invoices WHERE invoice_number = 'TA-INV-002';

INSERT INTO tenant_b.treatment_invoices (invoice_number, source_filename) VALUES
    ('TB-INV-001', 'tb_invoice_001.pdf'),
    ('TB-INV-002', 'tb_invoice_002.pdf');

INSERT INTO tenant_b.treatment_invoice_lines (invoice_id, isikukood, isikukood_hash, procedure_code, amount, treatment_date)
SELECT id, E'\\x111213'::bytea, E'\\x141516'::bytea, '3004', 500.00, '2025-03-10'::date
FROM tenant_b.treatment_invoices WHERE invoice_number = 'TB-INV-001'
UNION ALL
SELECT id, E'\\x171819'::bytea, E'\\x1a1b1c'::bytea, '3008', 600.00, '2025-03-11'::date
FROM tenant_b.treatment_invoices WHERE invoice_number = 'TB-INV-001'
UNION ALL
SELECT id, E'\\x1d1e1f'::bytea, E'\\x202122'::bytea, '3012', 750.00, '2025-03-12'::date
FROM tenant_b.treatment_invoices WHERE invoice_number = 'TB-INV-002';
