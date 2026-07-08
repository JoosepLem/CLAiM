# CLAiM

Reconciliation platform for Estonian medical centres — matches partner invoices against Tervisekassa reimbursement records to find uncovered services.

## Language

### Core domain

**Treatment Invoice**: A patient reimbursement invoice (Tervisekassa records). Contains the services a medical centre performed and expects to be reimbursed for.
_Avoid_: Arve (ambiguous in Estonian), patient invoice

**Partner Invoice**: An invoice from a partner provider (lab, hospital) for services rendered. Compared against Treatment Invoices during reconciliation.
_Avoid_: Lab invoice, hospital invoice, provider invoice

**Isikukood**: Estonian personal identification code. The key field used for matching records across invoices. Encrypted at rest, queried via HMAC hash.
_Avoid_: National ID, patient ID, SSN

**Procedure Code**: A service code identifying a specific medical procedure. Used alongside isikukood as a match criterion in reconciliation.

### Multi-tenancy

**Tenant**: A medical centre (clinic). Data is fully isolated per tenant via PostgreSQL schemas.
_Avoid_: Clinic, organisation, account

**Tenant Schema**: A PostgreSQL schema named after the tenant ID. Contains all tenant-scoped tables.

**app_user**: The runtime database user. Has DML privileges on tenant schemas but no DDL.
**app_migrator**: The migration database user. Owns schemas, runs Flyway, creates audit functions.

### Reconciliation

**Reconciliation**: The process of matching partner invoice lines against treatment invoice lines by isikukood + procedure code.

**Discrepancy**: A match result showing difference between partner and treatment amounts, or an unmatched line.

### Encryption

**Envelope Encryption**: Per-tenant DEK encrypted with AWS KMS master key. DEKs are cached in-memory (Caffeine).

**DEK**: Data Encryption Key — per-tenant symmetric key used to encrypt isikukood via AES-256-GCM.
**KEK**: Key Encryption Key — the AWS KMS master key that encrypts DEKs.

**isikukood_hash**: HMAC-SHA256 of the isikukood, stored alongside the encrypted value for exact-match lookups. Derived from the tenant's DEK, so different tenants produce different hashes for the same isikukood.
