#!/bin/bash
set -euo pipefail
: "${RAG_DB_PASSWORD:?}"
psql -v ON_ERROR_STOP=1 -v rag_pw="$RAG_DB_PASSWORD" --username "$POSTGRES_USER" --dbname postgres <<'EOSQL'
SELECT 'CREATE ROLE rag LOGIN' WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'rag') \gexec
ALTER ROLE rag PASSWORD :'rag_pw';
SELECT 'CREATE DATABASE ragdb OWNER rag' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'ragdb') \gexec
REVOKE CONNECT ON DATABASE ragdb FROM PUBLIC;
EOSQL
# vector is not a trusted extension, so the superuser creates it; rag owns ragdb and keeps CONNECT.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname ragdb -c 'CREATE EXTENSION IF NOT EXISTS vector'
# Isolation is one-way: releasemgmt is POSTGRES_USER (a superuser) and can still read ragdb; only rag is confined.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres -c "REVOKE CONNECT ON DATABASE \"$POSTGRES_DB\" FROM PUBLIC"
