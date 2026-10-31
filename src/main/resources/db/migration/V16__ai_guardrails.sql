ALTER TABLE translation_runtime ADD COLUMN lease_token uuid;
ALTER TABLE translation_runtime ADD COLUMN lease_until timestamptz;
ALTER TABLE article_translation_jobs ADD COLUMN started_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE article_classification_jobs ADD COLUMN started_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE article_translations ADD COLUMN locked boolean NOT NULL DEFAULT false;
ALTER TABLE article_translations ADD COLUMN version bigint NOT NULL DEFAULT 0;
CREATE TABLE ai_source_budgets (
 source_id uuid NOT NULL REFERENCES sources(id),
 model varchar(20) NOT NULL CHECK(model IN ('translation','classification')),
 day date NOT NULL,
 calls integer NOT NULL DEFAULT 0 CHECK(calls BETWEEN 0 AND 500),
 characters integer NOT NULL DEFAULT 0 CHECK(characters BETWEEN 0 AND 300000),
 seconds integer NOT NULL DEFAULT 0 CHECK(seconds BETWEEN 0 AND 7200),
 PRIMARY KEY(source_id,model,day)
);
CREATE INDEX ai_source_budget_retention ON ai_source_budgets(day);
