CREATE TABLE users (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username  TEXT NOT NULL UNIQUE,
    tenant_id TEXT NOT NULL REFERENCES tenants(tenant_id)
);
