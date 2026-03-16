-- PostgreSQL initialization script
-- Creates one database per microservice.
-- Runs automatically on first container start (docker-entrypoint-initdb.d).

SELECT 'CREATE DATABASE profiles_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'profiles_db')\gexec
SELECT 'CREATE DATABASE swipes_db'   WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'swipes_db')\gexec
SELECT 'CREATE DATABASE consumer_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'consumer_db')\gexec
SELECT 'CREATE DATABASE match_db'    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'match_db')\gexec
SELECT 'CREATE DATABASE subscriptions_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'subscriptions_db')\gexec
SELECT 'CREATE DATABASE keycloak'    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloak')\gexec
