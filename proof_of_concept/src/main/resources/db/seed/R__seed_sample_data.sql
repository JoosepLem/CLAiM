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

INSERT INTO tenant_a.partner_invoices (invoice_number, provider_name, source_filename) VALUES
    ('PA-INV-001', 'Synlab Eesti', 'pa_invoice_001.pdf');

INSERT INTO tenant_a.partner_invoice_lines (invoice_id, isikukood, isikukood_hash, procedure_code, amount, service_date)
SELECT id, E'\\x010203'::bytea, E'\\x040506'::bytea, '3004', 160.00, '2025-01-15'::date
FROM tenant_a.partner_invoices WHERE invoice_number = 'PA-INV-001';

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

INSERT INTO tenant_b.partner_invoices (invoice_number, provider_name, source_filename) VALUES
    ('PB-INV-001', 'Medicum', 'pb_invoice_001.pdf');

INSERT INTO tenant_b.partner_invoice_lines (invoice_id, isikukood, isikukood_hash, procedure_code, amount, service_date)
SELECT id, E'\\x111213'::bytea, E'\\x141516'::bytea, '3004', 520.00, '2025-03-10'::date
FROM tenant_b.partner_invoices WHERE invoice_number = 'PB-INV-001';
