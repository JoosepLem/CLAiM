DO $$
DECLARE
    t RECORD;
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_user') THEN
        FOR t IN SELECT tenant_id FROM public.tenants LOOP
            EXECUTE format('GRANT USAGE ON SCHEMA %I TO app_user', t.tenant_id);
            EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA %I TO app_user', t.tenant_id);
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA %I GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_user', t.tenant_id);
        END LOOP;

        GRANT USAGE ON SCHEMA public TO app_user;
        GRANT SELECT ON ALL TABLES IN SCHEMA public TO app_user;
    END IF;
END $$;
