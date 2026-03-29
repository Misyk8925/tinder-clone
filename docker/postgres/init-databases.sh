#!/bin/bash
# Initialize databases and least-privilege users for all services.
# This script runs once when the postgres container is first created.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE ROLE profiles_app      LOGIN PASSWORD '$PROFILES_DB_PASSWORD';
    CREATE ROLE match_app         LOGIN PASSWORD '$MATCH_DB_PASSWORD';
    CREATE ROLE consumer_app      LOGIN PASSWORD '$CONSUMER_DB_PASSWORD';
    CREATE ROLE subscriptions_app LOGIN PASSWORD '$SUBSCRIPTIONS_DB_PASSWORD';
    CREATE ROLE swipes_app        LOGIN PASSWORD '$SWIPES_DB_PASSWORD';

    CREATE DATABASE profiles_db;
    CREATE DATABASE match_db;
    CREATE DATABASE consumer_db;
    CREATE DATABASE subscriptions_db;
    CREATE DATABASE swipes_db;
EOSQL

# profiles_db — PostGIS + migrations + grants
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "profiles_db" <<-EOSQL
    CREATE EXTENSION IF NOT EXISTS postgis;
EOSQL
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "profiles_db" \
    -f /docker-entrypoint-initdb.d/migration/V1_profiles.sql
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "profiles_db" <<-EOSQL
    ALTER TABLE preferences         OWNER TO profiles_app;
    ALTER TABLE location            OWNER TO profiles_app;
    ALTER TABLE profiles            OWNER TO profiles_app;
    ALTER TABLE photo               OWNER TO profiles_app;
    ALTER TABLE profile_hobbies     OWNER TO profiles_app;
    ALTER TABLE profile_event_outbox OWNER TO profiles_app;
    ALTER TABLE profile_cache_model OWNER TO profiles_app;
    ALTER SEQUENCE IF EXISTS preferences_id_seq         OWNER TO profiles_app;
    ALTER SEQUENCE IF EXISTS location_id_seq            OWNER TO profiles_app;
    ALTER SEQUENCE IF EXISTS profiles_id_seq            OWNER TO profiles_app;
    ALTER SEQUENCE IF EXISTS photo_id_seq               OWNER TO profiles_app;
    ALTER SEQUENCE IF EXISTS profile_event_outbox_id_seq OWNER TO profiles_app;
    ALTER SEQUENCE IF EXISTS profile_cache_model_id_seq OWNER TO profiles_app;
    GRANT CONNECT ON DATABASE profiles_db TO profiles_app;
    GRANT USAGE, CREATE ON SCHEMA public TO profiles_app;
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO profiles_app;
    GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO profiles_app;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO profiles_app;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO profiles_app;
EOSQL

# match_db
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "match_db" \
    -f /docker-entrypoint-initdb.d/migration/V1_match.sql
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "match_db" <<-EOSQL
    REVOKE ALL ON DATABASE match_db FROM PUBLIC;
    GRANT CONNECT ON DATABASE match_db TO match_app;
    GRANT USAGE, CREATE ON SCHEMA public TO match_app;
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO match_app;
    GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO match_app;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO match_app;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO match_app;
EOSQL

# consumer_db
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "consumer_db" \
    -f /docker-entrypoint-initdb.d/migration/V1_consumer.sql
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "consumer_db" <<-EOSQL
    REVOKE ALL ON DATABASE consumer_db FROM PUBLIC;
    GRANT CONNECT ON DATABASE consumer_db TO consumer_app;
    GRANT USAGE, CREATE ON SCHEMA public TO consumer_app;
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO consumer_app;
    GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO consumer_app;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO consumer_app;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO consumer_app;
EOSQL

# subscriptions_db
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "subscriptions_db" \
    -f /docker-entrypoint-initdb.d/migration/V1_subscriptions.sql
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "subscriptions_db" <<-EOSQL
    REVOKE ALL ON DATABASE subscriptions_db FROM PUBLIC;
    GRANT CONNECT ON DATABASE subscriptions_db TO subscriptions_app;
    GRANT USAGE, CREATE ON SCHEMA public TO subscriptions_app;
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO subscriptions_app;
    GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO subscriptions_app;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO subscriptions_app;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO subscriptions_app;
EOSQL

# swipes_db
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "swipes_db" \
    -f /docker-entrypoint-initdb.d/migration/V1_swipes.sql
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "swipes_db" <<-EOSQL
    REVOKE ALL ON DATABASE swipes_db FROM PUBLIC;
    GRANT CONNECT ON DATABASE swipes_db TO swipes_app;
    GRANT USAGE, CREATE ON SCHEMA public TO swipes_app;
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO swipes_app;
    GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO swipes_app;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO swipes_app;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO swipes_app;
EOSQL
