# CLAiM Proof of Concept — Architecture Documentation

## Overview

This directory contains the C4 model architecture diagrams for the CLAiM proof of concept. The POC validates technical feasibility of the chosen patterns before full production implementation.

The POC technology stack differs from the production architecture:
- **Server-rendered UI** (Thymeleaf) instead of React SPA
- **No** Node.js frontend server, **no** S3 document store
- Focus on multi-tenant isolation, column-level encryption, and reconciliation

## Users

| Role | Description |
|---|---|
| **Medical Centre Employee** | Uploads partner and patient reimbursement invoices, reviews automated reconciliation results. |

Note: The POC uses username-only authentication (no ID-card). User management is simplified — no dedicated Application Administrator role.

## External System Dependencies

| System | Integration | Protocol |
|---|---|---|
| **AWS KMS** | Master encryption key management for envelope encryption of per-tenant DEKs | HTTPS (AWS SDK) |

## Containers

| Container | Technology | Role |
|---|---|---|
| Web Application | Spring Boot + Kotlin, Thymeleaf | Three server-rendered pages: login (`/login`), overview with embedded upload (`/dashboard`), and invoice detail with reconciliation results (`/invoices/{id}`). Handles JWT auth, PDF parsing, column-level encryption, reconciliation, and audit logging. |
| Database | PostgreSQL on AWS RDS | Multi-tenant (schema per tenant). Invoice headers, encrypted line items, `tenant_keys`, `reconciliation_runs` and `reconciliation_results` (no personal data), user-to-tenant mapping, and append-only audit log with hash chain integrity. |

## Key Architectural Decisions

- **Multi-tenant isolation**: JWT claim → tenant ID → PostgreSQL `search_path` per schema. Single `app_user` with restricted permissions.
- **Envelope encryption**: Per-tenant DEK encrypted with AWS KMS master key. AES-256-GCM for isikukood, HMAC-SHA256 for exact-match lookups.
- **DEK caching**: Caffeine in-memory cache with 30-minute expiry. Lazy-loaded on first request. Thread-safe single-flight loading.
- **Audit log integrity**: Hash chain computed inside a `SECURITY DEFINER` PostgreSQL function. App user has only `EXECUTE` permission — no direct DML on audit tables.
- **PDF parsing**: Strategy pattern (`InvoiceParser` interface per partner format). Parser extracts raw data; service layer handles encryption.
- **Authentication**: Username-only JWT with 6-hour expiry. Tenant ID embedded in JWT claim. HttpOnly + Secure + SameSite=Strict cookie.

## Diagrams

- [System Context](diagrams/system-context/system-context.md)
- [Containers](diagrams/containers/containers.md)
- Components — Web Application
  - [Auth & Tenant Resolution](diagrams/components/spring-boot-app/auth-and-tenant.md)
  - [Invoice Upload Pipeline](diagrams/components/spring-boot-app/invoice-upload.md)
  - [Reconciliation](diagrams/components/spring-boot-app/reconciliation.md)
- Components — Database
  - [Tenant Data & Invoice Storage](diagrams/components/postgres/tenant-data.md)
  - [Audit Logging](diagrams/components/postgres/audit-log.md)
- [Deployment](diagrams/deployment/deployment-poc.md)

## Testing

- [Testing Architecture](testing-architecture.md)

## References

- [POC Scope Document](../POC_SCOPE.md)
