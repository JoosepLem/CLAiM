ALTER TABLE treatment_invoice_lines ADD COLUMN IF NOT EXISTS key_version INT NOT NULL DEFAULT 1;
ALTER TABLE partner_invoice_lines ADD COLUMN IF NOT EXISTS key_version INT NOT NULL DEFAULT 1;
