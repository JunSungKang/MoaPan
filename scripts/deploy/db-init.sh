#!/bin/sh
set -eu
# Official PostgreSQL entrypoint runs this only for a new data directory.
# Only generated hex secrets are accepted so SQL stdin cannot be injected.
case "$POSTGRES_APP_PASSWORD" in ''|*[!a-f0-9]*) echo 'Application DB password must be generated hex' >&2; exit 1;; esac
[ "${#POSTGRES_APP_PASSWORD}" -ge 64 ] || { echo 'Application DB secret too short' >&2; exit 1; }
{
  printf "CREATE ROLE moapan LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD '%s';\n" "$POSTGRES_APP_PASSWORD"
  printf '%s\n' 'GRANT CONNECT ON DATABASE moapan TO moapan;' 'GRANT USAGE ON SCHEMA public TO moapan;' 'ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO moapan;' 'ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE,SELECT ON SEQUENCES TO moapan;'
} | psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set ON_ERROR_STOP=1 >/dev/null
