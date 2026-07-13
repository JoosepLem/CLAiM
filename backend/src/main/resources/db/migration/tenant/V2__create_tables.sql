CREATE TABLE treatment_invoices (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_number  TEXT,
    uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    source_filename TEXT
);

CREATE TABLE treatment_invoice_lines (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_id      BIGINT NOT NULL REFERENCES treatment_invoices(id),
    isikukood       BYTEA NOT NULL,
    isikukood_hash  BYTEA NOT NULL,
    procedure_code  TEXT NOT NULL,
    amount          NUMERIC(12,2) NOT NULL,
    treatment_date  DATE NOT NULL
);

CREATE INDEX idx_til_hash ON treatment_invoice_lines(isikukood_hash);

CREATE TABLE partner_invoices (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_number  TEXT,
    provider_name   TEXT NOT NULL,
    uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    source_filename TEXT
);

CREATE TABLE partner_invoice_lines (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_id      BIGINT NOT NULL REFERENCES partner_invoices(id),
    isikukood       BYTEA NOT NULL,
    isikukood_hash  BYTEA NOT NULL,
    procedure_code  TEXT NOT NULL,
    amount          NUMERIC(12,2) NOT NULL,
    service_date    DATE NOT NULL
);

CREATE INDEX idx_pil_hash ON partner_invoice_lines(isikukood_hash);
