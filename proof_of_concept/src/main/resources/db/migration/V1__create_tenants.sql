CREATE TABLE public.tenants (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id TEXT NOT NULL UNIQUE,
    name      TEXT NOT NULL
);

INSERT INTO public.tenants (tenant_id, name) VALUES
    ('tenant_a', 'Demo Clinic 1'),
    ('tenant_b', 'Demo Clinic 2');
