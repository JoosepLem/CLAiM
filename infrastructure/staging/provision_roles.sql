-- Run once as RDS master to bootstrap app_migrator and app_user.
-- Idempotent — safe to re-run.
--
-- Usage:
--   PGPASSWORD=<master-password> psql "sslmode=require host=<rds> port=5432 user=claim dbname=claim" -f provision_roles.sql
--   Enter migrator and app_user passwords at the prompts.

\set QUIET on
\set ON_ERROR_STOP on

\prompt 'Enter app_migrator password: ' migrator_password
\prompt 'Enter app_user password:     ' app_user_password

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_migrator') THEN
        EXECUTE 'CREATE ROLE app_migrator WITH LOGIN CREATEDB CREATEROLE PASSWORD ' || quote_literal(:'migrator_password');
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_user') THEN
        EXECUTE 'CREATE ROLE app_user WITH LOGIN PASSWORD ' || quote_literal(:'app_user_password');
    END IF;
END $$;

GRANT ALL PRIVILEGES ON DATABASE claim TO app_migrator;
GRANT ALL PRIVILEGES ON SCHEMA public TO app_migrator;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO app_migrator;
GRANT CONNECT ON DATABASE claim TO app_user;
