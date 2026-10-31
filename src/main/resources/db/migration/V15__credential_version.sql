ALTER TABLE users ADD COLUMN auth_version bigint NOT NULL DEFAULT 0 CHECK(auth_version>=0);
