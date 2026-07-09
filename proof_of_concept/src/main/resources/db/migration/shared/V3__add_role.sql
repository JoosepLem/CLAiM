ALTER TABLE public.users ADD COLUMN IF NOT EXISTS role TEXT NOT NULL DEFAULT 'CLINIC_EMPLOYEE';

ALTER TABLE public.users ALTER COLUMN tenant_id DROP NOT NULL;

INSERT INTO public.users (username, tenant_id, role)
VALUES ('admin', NULL, 'ADMIN')
ON CONFLICT (username) DO NOTHING;
