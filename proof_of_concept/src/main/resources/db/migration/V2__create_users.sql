CREATE TABLE public.users (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username  TEXT NOT NULL UNIQUE,
    tenant_id TEXT NOT NULL REFERENCES public.tenants(tenant_id)
);
