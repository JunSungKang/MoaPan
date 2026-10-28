ALTER TABLE sources ADD COLUMN published_since date;
ALTER TABLE sources ADD COLUMN topic_terms jsonb NOT NULL DEFAULT '[]';
ALTER TABLE sources ADD CONSTRAINT source_topic_terms_array CHECK (jsonb_typeof(topic_terms)='array' AND jsonb_array_length(topic_terms)<=40);
CREATE TABLE source_access_gate (id varchar(30) PRIMARY KEY, next_at timestamptz NOT NULL);
INSERT INTO source_access_gate VALUES ('arxiv',now());
