CREATE TABLE public.users (
    username TEXT PRIMARY KEY,
    tenant_id TEXT NOT NULL
);

INSERT INTO public.users (username, tenant_id) VALUES
    ('user_a', 'tenant_a'),
    ('user_b', 'tenant_b');
