#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE SCHEMA IF NOT EXISTS public;

    -- gen_random_uuid(), used for comfy_workflow.id, is core from PostgreSQL 13 onward, so on the
    -- pinned image this is redundant. It is created anyway so the migration still applies if the
    -- image is ever pinned back to an older major version.
    CREATE EXTENSION IF NOT EXISTS pgcrypto;

    -- POSTGRES_USER is created as superuser and database owner by the image entrypoint before this
    -- script runs, so there is deliberately no CREATE ROLE here -- it would abort with
    -- "role already exists" under ON_ERROR_STOP=1. These grants are explicit rather than implied
    -- because PostgreSQL 15 removed the default CREATE grant on schema public.
    GRANT ALL PRIVILEGES ON DATABASE "$POSTGRES_DB" TO "$POSTGRES_USER";
    GRANT ALL PRIVILEGES ON SCHEMA public TO "$POSTGRES_USER";
    ALTER SCHEMA public OWNER TO "$POSTGRES_USER";
EOSQL
