#!/bin/sh
# Idempotent provision of moderation_db for both fresh and existing Postgres volumes.
set -eu

tries=0
max_tries=60
until psql --dbname=postgres -c 'SELECT 1' >/dev/null 2>&1; do
  tries=$((tries + 1))
  if [ "$tries" -ge "$max_tries" ]; then
    echo "postgres did not accept connections after ${max_tries} attempts" >&2
    exit 2
  fi
  sleep 2
done

psql -v ON_ERROR_STOP=1 --dbname=postgres <<SQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'moderation_app') THEN
    CREATE ROLE moderation_app LOGIN PASSWORD '${MODERATION_DB_PASSWORD}';
  END IF;
END
\$\$;

SELECT 'CREATE DATABASE moderation_db'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'moderation_db')\gexec
SQL

psql -v ON_ERROR_STOP=1 --dbname=moderation_db <<SQL
REVOKE ALL ON DATABASE moderation_db FROM PUBLIC;
GRANT CONNECT ON DATABASE moderation_db TO moderation_app;
GRANT USAGE, CREATE ON SCHEMA public TO moderation_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO moderation_app;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO moderation_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO moderation_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO moderation_app;
SQL
