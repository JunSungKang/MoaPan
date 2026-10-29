-- Existing accounts have no inferred or backdated consent.
ALTER TABLE users ADD COLUMN terms_version varchar(32);
ALTER TABLE users ADD COLUMN terms_accepted_at timestamptz;
ALTER TABLE users ADD CONSTRAINT users_terms_pair CHECK ((terms_version IS NULL) = (terms_accepted_at IS NULL));
