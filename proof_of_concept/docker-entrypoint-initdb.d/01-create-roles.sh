#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    DO \$\$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_migrator') THEN
            CREATE ROLE app_migrator WITH LOGIN CREATEDB CREATEROLE PASSWORD 'app_migrator_pass';
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_user') THEN
            CREATE ROLE app_user WITH LOGIN PASSWORD 'app_user_pass';
        END IF;
    END
    \$\$;
    GRANT ALL PRIVILEGES ON DATABASE $POSTGRES_DB TO app_migrator;
    GRANT CREATE, USAGE ON SCHEMA public TO app_migrator;
    GRANT CONNECT ON DATABASE $POSTGRES_DB TO app_user;
EOSQL
