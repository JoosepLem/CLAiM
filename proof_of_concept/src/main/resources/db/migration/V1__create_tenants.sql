CREATE TABLE public.tenants (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id TEXT NOT NULL UNIQUE,
    name      TEXT NOT NULL
);
