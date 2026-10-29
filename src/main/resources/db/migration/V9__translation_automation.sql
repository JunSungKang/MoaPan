ALTER TABLE article_translation_jobs DROP CONSTRAINT article_translation_jobs_attempts_check;
ALTER TABLE article_translation_jobs ADD CONSTRAINT article_translation_jobs_attempts_check CHECK(attempts>=0);
ALTER TABLE article_translation_jobs ADD COLUMN state varchar(20) NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','RUNNING','RETRY','DONE','ORIGINAL','CANCELLED'));
ALTER TABLE article_translation_jobs ADD COLUMN policy_version varchar(100) NOT NULL DEFAULT 'auto-v2';
ALTER TABLE article_translation_jobs ADD COLUMN progress jsonb NOT NULL DEFAULT '{}';
ALTER TABLE article_translation_jobs ADD COLUMN field_attempts jsonb NOT NULL DEFAULT '{}';
ALTER TABLE article_translation_jobs ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE article_translation_jobs ADD COLUMN infra_failures integer NOT NULL DEFAULT 0 CHECK(infra_failures>=0);
-- Re-evaluate existing translations once under the new rules; the prior published result is invalidated.
DELETE FROM article_translations;
UPDATE article_translation_jobs SET attempts=0,lease_until=NULL,lease_token=NULL,error_code=NULL,available_at=now();
CREATE INDEX translation_jobs_state_schedule ON article_translation_jobs(state,available_at);
CREATE TABLE translation_runtime (
 id integer PRIMARY KEY CHECK(id=1),
 state varchar(20) NOT NULL DEFAULT 'UNKNOWN',
 failures integer NOT NULL DEFAULT 0,
 next_probe_at timestamptz NOT NULL DEFAULT now(),
 checked_at timestamptz,
 error_code varchar(80)
);
INSERT INTO translation_runtime(id) VALUES(1);
