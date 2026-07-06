# CLAiM — Architecture Documentation

## Overview

**CLAiM** is a web application that helps family medicine centres in Estonia prevent financial loss by comparing patient reimbursement invoices (submitted to the Estonian Health Insurance Fund / Tervisekassa) against partner service invoices (from healthcare service providers like labs, specialists, pharmacies). The system automatically matches invoices and highlights discrepancies for a medical centre employee to resolve.

## Purpose

Ensure every expense on a partner service invoice is also present on the corresponding patient reimbursement invoice — so the medical centre is never paying for a service that is not claimed for compensation.

## Users

| Role | Description |
|---|---|
| **Medical Centre Employee** | Primary user. Uploads invoices (PDF upload or CSV import), reviews automated matches and highlighted discrepancies. |
| **Application Administrator** | Manages user accounts — add, remove, or edit users. Limited UI focused solely on user management. |

## External System Dependencies

| System | Integration | Protocol |
|---|---|---|
| **Estonian eID OCSP Service** | Certificate revocation checking during ID-card authentication (web-eid) | OCSP/HTTPS |
| **Web-eID** | Browser-based ID-card authentication via web-eid.js and web-eid authtoken validation in Spring backend | HTTPS (client→server), OCSP (server→OCSP) |

No integration with Tervisekassa (Estonian Health Insurance Fund) in version 1. Discrepancies are resolved by the employee in a separate, unrelated portal.

## Containers (v1)

| Container | Technology | Role |
|---|---|---|
| Frontend Server | Node.js | Serves the React SPA and static assets |
| Single-Page Application | React | Browser-based UI for invoice upload, comparison review, and user management |
| Backend API | Spring Boot + Kotlin | Business logic, invoice matching engine, authentication, file processing |
| Database | PostgreSQL | Stores user accounts, invoice data, match results, and audit logs (GDPR compliance) |
| Encrypted Document Store | S3-compatible | Stores encrypted uploaded invoice files (PDF, CSV) |

## Ingest & Workflow

1. **Upload** — Medical centre employee uploads partner invoices and patient invoices via PDF upload or CSV import.
2. **Matching** — The backend automatically matches line items between the two invoice sets.
3. **Review** — Matching results are displayed in the UI with discrepancies highlighted. The employee reviews and notes issues.
4. **Resolution** — The employee resolves discrepancies manually in an external, unrelated portal (outside CLAiM scope in v1).

## Authentication

- Two-factor authentication via Estonian ID-card using the **web-eid** solution (web-eid.js on frontend, web-eid-authtoken-validation-java on backend).
- Requires a physical ID-card reader connected to the user's machine.
- OCSP certificate revocation checking is performed by the backend against the Estonian eID OCSP infrastructure.

## Diagrams

- [System Context](diagrams/system-context/system-context.mmd)
