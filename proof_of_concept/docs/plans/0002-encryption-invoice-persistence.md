# Plan 0002 — Encryption Stack + Invoice Persistence

Answers POC Success Criteria #3 (KMS performance) and #4 (HMAC+AES-256-GCM viability).

## Dependencies

- [x] 0001-tenant-isolation (tenant schemas exist, TenantContext works)

## Decisions

| # | Decision |
|---|----------|
| 1 | LocalStack for KMS in local dev (docker-compose service, port 4566) |
| 2 | V3 Flyway migration: `tenant_keys` table + `key_version INT NOT NULL` on invoice line tables |
| 3 | Lazy DEK creation on first write — encryption service generates DEK, wraps with KMS, stores in `tenant_keys`, caches |
| 4 | HKDF from DEK → AES-256-GCM encryption key + HMAC-SHA256 key (purpose-separated) |
| 5 | No PDF parsers — synthetic JSON payloads on upload endpoint |
| 6 | Java AWS SDK `software.amazon.awssdk:kms` for KMS integration |
| 7 | `key_version INT NOT NULL DEFAULT 1` on `treatment_invoice_lines` and `partner_invoice_lines` |
| 8 | Single `EncryptionService` — `encrypt(tenantId, plaintext)` returns `(ciphertext, hmac)`, `decrypt(tenantId, ciphertext, keyVersion)` returns `plaintext` |
| 9 | `POST /api/invoices` accepts full invoice JSON (header + lines); `GET /api/invoices` returns decrypted isikukood |
| 10 | Caffeine DEK cache: tenant ID → `SecretKey`, 30-min expiry, lazy load, single-flight |
| 11 | Dashboard UI deferred — no Thymeleaf changes |
| 12 | Performance measured via structured log traces on endpoints (KMS fetch, cached DEK, no-encrypt baseline) |

## Files to create

| File | Purpose |
|------|---------|
| `src/main/kotlin/ee/claimai/encryption/EncryptionService.kt` | AES-256-GCM encrypt/decrypt + HKDF key derivation |
| `src/main/kotlin/ee/claimai/encryption/KeyManagementService.kt` | DEK generation, KMS wrap/unwrap, DEK persistence to `tenant_keys` |
| `src/main/kotlin/ee/claimai/encryption/DekCache.kt` | Caffeine cache, lazy-loaded from `KeyManagementService` |
| `src/main/kotlin/ee/claimai/encryption/KmsConfig.kt` | KMS client configuration (LocalStack endpoint) |
| `src/main/kotlin/ee/claimai/invoice/PartnerInvoiceRepository.kt` | JDBC CRUD for `partner_invoices` + `partner_invoice_lines` (tenant-scoped) |
| `src/main/kotlin/ee/claimai/invoice/TreatmentInvoiceRepository.kt` | JDBC CRUD for `treatment_invoices` + `treatment_invoice_lines` (tenant-scoped) |
| `src/main/kotlin/ee/claimai/invoice/InvoiceService.kt` | Orchestrates encryption → repository write; read → decrypt |
| `src/main/kotlin/ee/claimai/invoice/InvoiceController.kt` | `POST /api/invoices` + `GET /api/invoices` REST endpoints |
| `src/main/kotlin/ee/claimai/invoice/dto/InvoiceUploadRequest.kt` | Request DTO for upload |
| `src/main/kotlin/ee/claimai/invoice/dto/InvoiceResponse.kt` | Response DTO with decrypted isikukood |
| `src/main/resources/db/migration/V3__add_encryption_tables.sql` | `tenant_keys` table + `key_version` columns |
| `src/test/kotlin/ee/claimai/integration/EncryptionRoundTripTest.kt` | E2E: upload → read → verify decrypt correctness |
| `src/test/kotlin/ee/claimai/integration/TenantEncryptionIsolationTest.kt` | E2E: tenant A data encrypted with A's DEK, tenant B can't decrypt |
| `src/test/kotlin/ee/claimai/unit/EncryptionServiceTest.kt` | Unit: AES-256-GCM round-trip + HKDF determinism |
| `src/test/kotlin/ee/claimai/unit/HmacMatchingTest.kt` | Unit: same isikukood → same hmak_hash; different isikukood → different hash |

## Files to modify

| File | Change |
|------|--------|
| `build.gradle.kts` | Add `software.amazon.awssdk:kms`, `caffeine`, localstack test dep |
| `application.yml` | Add KMS endpoint override for local profile |
| `docker-compose.yml` | Add LocalStack service (KMS only) |
| `V2__create_tenant_template.sql` | (No change — `isikukood BYTEA`, `isikukood_hash BYTEA` already present) |

## V3 Migration

```sql
-- Per-tenant: DEK storage
CREATE TABLE {tenant}.tenant_keys (
    tenant_id     TEXT NOT NULL,
    key_version   INT NOT NULL,
    encrypted_dek BYTEA NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, key_version)
);

-- Add key_version to invoice line tables
ALTER TABLE {tenant}.treatment_invoice_lines ADD COLUMN key_version INT NOT NULL DEFAULT 1;
ALTER TABLE {tenant}.partner_invoice_lines ADD COLUMN key_version INT NOT NULL DEFAULT 1;
```

## Acceptance Criteria (via test cases)

### TC1 — DEK lazy creation (unit)
- **Given** a tenant with no entry in `tenant_keys`
- **When** `EncryptionService.encrypt(tenantId, "47101010033")` is called
- **Then** a new DEK is generated, wrapped via KMS, stored in `tenant_keys`, cached in Caffeine, and `encrypt()` returns `(ciphertext, hmac)`

### TC2 — AES-256-GCM round-trip (unit)
- **Given** a valid DEK
- **When** `encrypt("47101010033")` produces `ciphertext`, then `decrypt(ciphertext, version=1)` is called
- **Then** the result equals `"47101010033"`

### TC3 — Same isikukood produces different ciphertexts (unit)
- **Given** a valid DEK
- **When** `encrypt("47101010033")` is called twice
- **Then** the two ciphertexts are different (GCM uses random nonce), but both decrypt to `"47101010033"`

### TC4 — HMAC determinism (unit)
- **Given** a valid DEK
- **When** `hmac("47101010033")` is called twice with the same HKDF-derived HMAC key
- **Then** both HMAC hashes are identical

### TC5 — HMAC non-collision (unit)
- **Given** a valid DEK
- **When** `hmac("47101010033")` and `hmac("38001020044")` are computed
- **Then** the two hashes are different

### TC6 — Wrong DEK cannot decrypt (unit)
- **Given** two different DEKs (version 1 and version 2)
- **When** data is encrypted with DEK v1, and decryption is attempted with DEK v2
- **Then** decryption fails (GCM authentication tag mismatch)

### TC7 — E2E invoice upload + read (integration, @SpringBootTest)
- **Given** a valid JWT for `user_a` (tenant_a), and LocalStack KMS running
- **When** `POST /api/invoices` with a partner invoice containing 2 line items
- **Then** response is 201. `GET /api/invoices` returns the invoice with decrypted isikukood matching input. Database rows show `BYTEA` ciphertexts (not plaintext).

### TC8 — E2E tenant encryption isolation (integration)
- **Given** invoices uploaded for both `tenant_a` and `tenant_b`
- **When** authenticated as `user_a`, `GET /api/invoices`
- **Then** only `tenant_a`'s invoices are returned, with correct decryption. Tenant A's DEK cannot decrypt tenant B's ciphertexts.

### TC9 — KMS latency measurement (integration)
- **Given** a cold cache (DEK not yet loaded)
- **When** the first `encrypt()` call is made for a tenant
- **Then** structured log contains `kms_unwrap_duration_ms >= 0` (captures KMS call latency). Subsequent calls log `dek_cache_hit=true` with no KMS call.

### TC10 — Invalid JWT rejected (integration)
- **Given** no JWT cookie or expired/tampered JWT
- **When** `POST /api/invoices` or `GET /api/invoices` is called
- **Then** response is 401

### TC11 — Invalid JSON rejected (integration)
- **Given** a valid JWT
- **When** `POST /api/invoices` with malformed JSON body
- **Then** response is 400 with error message

## Out of scope (deferred to future slices)

- PDF parsing and upload
- Reconciliation logic and storage
- Audit logging
- Key rotation (admin endpoint + batch re-encryption)
- Dashboard/invoice-detail Thymeleaf pages
