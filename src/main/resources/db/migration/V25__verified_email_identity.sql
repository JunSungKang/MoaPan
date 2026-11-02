ALTER TABLE users ADD COLUMN email_cipher bytea;
ALTER TABLE users ADD COLUMN email_key varchar(64) UNIQUE;
ALTER TABLE users ADD COLUMN email_verified_at timestamptz;
ALTER TABLE users ADD COLUMN email_policy_version varchar(32);
ALTER TABLE users ADD COLUMN email_policy_accepted_at timestamptz;
ALTER TABLE users ADD CONSTRAINT users_verified_email_check CHECK (
    (email_cipher IS NULL AND email_key IS NULL AND email_verified_at IS NULL AND email_policy_version IS NULL AND email_policy_accepted_at IS NULL) OR
    (email_cipher IS NOT NULL AND email_key IS NOT NULL AND email_verified_at IS NOT NULL AND email_policy_version IS NOT NULL AND email_policy_accepted_at IS NOT NULL)
);
-- Existing accounts remain usable; an authenticated owner enrolls their own email.
CREATE TABLE email_challenges (
    id uuid PRIMARY KEY,
    purpose varchar(8) NOT NULL CHECK (purpose IN ('SIGNUP','BIND','RESET')),
    account_id uuid REFERENCES users(id) ON DELETE CASCADE,
    auth_version bigint,
    subject_key varchar(64) NOT NULL,
    session_key varchar(64) NOT NULL,
    email_key varchar(64) NOT NULL,
    email_cipher bytea NOT NULL,
    code_hash varchar(64) NOT NULL,
    attempts smallint NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX email_challenges_expiry ON email_challenges(expires_at);
CREATE INDEX email_challenges_account ON email_challenges(account_id);
CREATE TABLE email_outbox (
    id uuid PRIMARY KEY,
    account_id uuid REFERENCES users(id) ON DELETE CASCADE,
    challenge_id uuid REFERENCES email_challenges(id) ON DELETE CASCADE,
    envelope_cipher bytea,
    status varchar(8) NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED','RUNNING','SENT','FAILED')),
    attempts smallint NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 3),
    available_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    lease_until timestamptz,
    lease_token uuid,
    error_code varchar(32),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX email_outbox_queue ON email_outbox(status,available_at);
CREATE INDEX email_outbox_expiry ON email_outbox(expires_at);
