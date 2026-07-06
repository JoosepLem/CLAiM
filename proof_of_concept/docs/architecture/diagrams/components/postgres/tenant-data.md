```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'lineColor': '#999999'}}}%%
C4Component
    title Component diagram for Database — Tenant Data & Invoice Storage

    Container(spring_boot, "Web Application", "Spring Boot + Kotlin", "Connects as single app_user over SSL.")

    Container_Boundary(postgres, "Database") {
        Container_Boundary(public_schema, "public schema") {
            Component(users_table, "users", "Table", "Maps username to tenant_id (schema name). Read-only for app_user. Used during login to resolve tenant and set search_path.")
        }

        Container_Boundary(tenant_schema, "tenant schema (one per clinic)", "Schema name = tenant_id from JWT. search_path routes all tenant queries here.") {
            Component(treatment_table, "treatment_invoices", "Table", "Encrypted isikukood (BYTEA, AES-256-GCM), isikukood_hash (BYTEA, HMAC-SHA256), procedure_code, amount, treatment_date, key_version.")
            Component(partner_table, "partner_invoices", "Table", "Encrypted isikukood, isikukood_hash, procedure_code, amount, service_date, provider_name, key_version.")
            Component(keys_table, "tenant_keys", "Table", "Holds per-tenant DEKs: tenant_id, key_version, encrypted_dek (BYTEA, wrapped via KMS), created_at.")
        }
    }

    Rel(spring_boot, users_table, "SELECT username -> tenant_id\non login", "SQL/TCP (SSL)")
    Rel(spring_boot, treatment_table, "INSERT, SELECT by isikukood_hash", "SQL/TCP (SSL)")
    Rel(spring_boot, partner_table, "INSERT, SELECT by isikukood_hash", "SQL/TCP (SSL)")
    Rel(spring_boot, keys_table, "SELECT encrypted DEK\non cache miss", "SQL/TCP (SSL)")
    Rel(treatment_table, keys_table, "key_version references")
    Rel(partner_table, keys_table, "key_version references")
```
