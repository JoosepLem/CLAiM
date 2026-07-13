DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_user') THEN
        GRANT USAGE ON SCHEMA ${schema} TO app_user;

        ALTER DEFAULT PRIVILEGES IN SCHEMA ${schema}
            GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_user;

        ALTER DEFAULT PRIVILEGES IN SCHEMA ${schema}
            GRANT USAGE, SELECT ON SEQUENCES TO app_user;
    END IF;
END $$;
