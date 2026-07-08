/*
 * Tenant schema template.
 * Each tenant gets a schema named with their tenant_id.
 * These DDL statements are commented out — they serve as documentation
 * for the schema provisioning that will be implemented in a later milestone.
 */

-- CREATE SCHEMA tenant_template;

-- CREATE TABLE tenant_template.treatment_invoices (
--     id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
--     invoice_number TEXT NOT NULL,
--     uploaded_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
--     source_filename TEXT NOT NULL
-- );

-- CREATE TABLE tenant_template.treatment_invoice_lines (
--     id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
--     invoice_id     UUID NOT NULL REFERENCES tenant_template.treatment_invoices(id),
--     isikukood      BYTEA NOT NULL,
--     isikukood_hash BYTEA NOT NULL,
--     procedure_code TEXT NOT NULL,
--     amount         NUMERIC(12,2) NOT NULL,
--     treatment_date DATE NOT NULL,
--     key_version    INT NOT NULL
-- );

-- CREATE INDEX idx_til_hash ON tenant_template.treatment_invoice_lines(isikukood_hash);

-- CREATE TABLE tenant_template.partner_invoices (
--     id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
--     invoice_number  TEXT NOT NULL,
--     provider_name   TEXT NOT NULL,
--     uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
--     source_filename TEXT NOT NULL
-- );

-- CREATE TABLE tenant_template.partner_invoice_lines (
--     id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
--     invoice_id     UUID NOT NULL REFERENCES tenant_template.partner_invoices(id),
--     isikukood      BYTEA NOT NULL,
--     isikukood_hash BYTEA NOT NULL,
--     procedure_code TEXT NOT NULL,
--     amount         NUMERIC(12,2) NOT NULL,
--     service_date   DATE NOT NULL,
--     key_version    INT NOT NULL
-- );

-- CREATE INDEX idx_pil_hash ON tenant_template.partner_invoice_lines(isikukood_hash);

-- CREATE TABLE tenant_template.tenant_keys (
--     tenant_id     TEXT NOT NULL,
--     key_version   INT NOT NULL,
--     encrypted_dek BYTEA NOT NULL,
--     created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
--     PRIMARY KEY (tenant_id, key_version)
-- );

-- CREATE TABLE tenant_template.reconciliation_runs (
--     id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
--     triggered_by      TEXT NOT NULL,
--     run_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
--     status            TEXT NOT NULL DEFAULT 'RUNNING',
--     total_matched     INT NOT NULL DEFAULT 0,
--     total_amount_mismatch INT NOT NULL DEFAULT 0,
--     total_unmatched   INT NOT NULL DEFAULT 0
-- );

-- CREATE TABLE tenant_template.reconciliation_results (
--     id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
--     run_id                     UUID NOT NULL REFERENCES tenant_template.reconciliation_runs(id),
--     partner_invoice_id         UUID NOT NULL REFERENCES tenant_template.partner_invoices(id),
--     partner_invoice_line_id    UUID NOT NULL REFERENCES tenant_template.partner_invoice_lines(id),
--     treatment_invoice_line_id  UUID REFERENCES tenant_template.treatment_invoice_lines(id),
--     match_status               TEXT NOT NULL,
--     treatment_amount           NUMERIC(12,2),
--     partner_amount             NUMERIC(12,2),
--     amount_difference          NUMERIC(12,2)
-- );

-- CREATE TABLE tenant_template.audit_events (
--     id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
--     event_time      TIMESTAMPTZ NOT NULL DEFAULT now(),
--     username        TEXT NOT NULL,
--     action          TEXT NOT NULL,
--     endpoint        TEXT NOT NULL,
--     record_type     TEXT,
--     record_id       TEXT,
--     fields_accessed TEXT[],
--     purpose         TEXT NOT NULL,
--     ip_address      TEXT,
--     user_agent      TEXT,
--     success         BOOLEAN NOT NULL DEFAULT TRUE,
--     details         JSONB DEFAULT '{}',
--     chain_hash      BYTEA NOT NULL
-- );
