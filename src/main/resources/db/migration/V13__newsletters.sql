CREATE SEQUENCE newsletter_issue_seq;
CREATE TABLE newsletters(id uuid PRIMARY KEY, issue_number bigint UNIQUE, draft jsonb NOT NULL, published jsonb, version bigint NOT NULL DEFAULT 0, published_at timestamptz, updated_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX newsletters_published ON newsletters(published_at DESC) WHERE published IS NOT NULL;
