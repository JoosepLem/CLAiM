```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'lineColor': '#999999'}}}%%
C4Component
    title Component diagram for Web Application — Invoice Upload Pipeline

    Person(employee, "Medical Centre Employee", "Uploads a partner or treatment invoice PDF.")

    Container_Boundary(spring_boot, "Web Application") {
        Component(ui, "Invoice UI Controller", "Thymeleaf + Spring MVC", "Receives multipart file upload. Validates PDF extension, MIME type, and magic bytes. Returns parser feedback or errors to the view.")

        Component(invoice_svc, "Invoice Service", "Kotlin service", "Orchestrates the upload: validates file, routes to parser, encrypts isikukood and computes HMAC, persists invoice lines with key_version.")

        Component(parser_registry, "Parser Registry", "Map<String, InvoiceParser>", "Strategy pattern. Auto-wired by Spring. Returns the InvoiceParser matching the partner ID for the uploaded invoice format.")

        Component(encryption_svc, "Encryption Service", "Kotlin service, Caffeine cache", "Envelope encryption: AES-256-GCM encrypt + HMAC-SHA256 hash per tenant DEK. Caffeine cache with 30-min TTL for DEK lazy-loading from tenant_keys table.")

        Component(audit_svc, "Audit Service", "Kotlin service", "Logs CREATE events via SECURITY DEFINER PostgreSQL function after invoice lines are persisted.")
    }

    ContainerDb(postgres, "Database", "PostgreSQL on AWS RDS", "Per-tenant schemas: treatment_invoices, partner_invoices, tenant_keys.")

    Rel(employee, ui, "Uploads invoice PDF", "HTTPS (multipart)")
    Rel(ui, invoice_svc, "Passes validated file stream")
    Rel(invoice_svc, parser_registry, "Selects parser by partner ID")
    Rel(invoice_svc, encryption_svc, "Encrypts isikukood, computes HMAC hash")
    Rel(invoice_svc, audit_svc, "Logs CREATE event")
    Rel(invoice_svc, postgres, "Persists encrypted invoice lines", "SQL/TCP (SSL)")
    Rel(encryption_svc, postgres, "Reads and writes encrypted DEKs", "SQL/TCP (SSL)")
    Rel(audit_svc, postgres, "Calls log_audit_event()", "SQL/TCP (SSL)")
```
