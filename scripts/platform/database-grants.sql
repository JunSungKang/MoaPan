-- Run as the owner of this database/schema after Flyway. Common operations creates
-- roles and the database; no SUPERUSER, CREATEROLE, CREATEDB or cluster grant needed.
-- psql --set=runtime_role=damgox2_sid --file scripts/platform/database-grants.sql
GRANT CONNECT ON DATABASE damgox2 TO :"runtime_role";
GRANT USAGE ON SCHEMA public TO :"runtime_role";
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO :"runtime_role";
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO :"runtime_role";
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"runtime_role";
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO :"runtime_role";
REVOKE INSERT, UPDATE, DELETE ON flyway_schema_history FROM :"runtime_role";

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE CREATE ON SCHEMA public FROM :"runtime_role";
