CREATE TABLE account_subscriptions (
    account_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    status varchar(8) NOT NULL CHECK (status IN ('ACTIVE','REVOKED')),
    starts_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    version bigint NOT NULL CHECK (version > 0),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (expires_at > starts_at)
);
-- No backfill: an existing or newly registered account has no subscription by default.
