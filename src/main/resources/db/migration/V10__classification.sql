CREATE TABLE classification_config (id integer PRIMARY KEY CHECK(id=1), version bigint NOT NULL DEFAULT 0);
INSERT INTO classification_config(id) VALUES(1);
CREATE TABLE classification_topics (
 tag_id uuid PRIMARY KEY REFERENCES tags(id),
 definition varchar(200) NOT NULL CHECK(length(definition)>0)
);
INSERT INTO tags(id,slug,name,category) VALUES
 (gen_random_uuid(),'llm','LLM 모델·성능','technology'),
 (gen_random_uuid(),'rag','RAG','technology'),
 (gen_random_uuid(),'database','데이터베이스','technology'),
 (gen_random_uuid(),'search','검색 기술·속도','technology') ON CONFLICT(slug) DO NOTHING;
INSERT INTO classification_topics(tag_id,definition) SELECT id,CASE slug
 WHEN 'security' THEN 'Software vulnerabilities, exploits, access control, privacy attacks and defenses. Not model hallucination alone.'
 WHEN 'llm' THEN 'LLM capabilities, reasoning, training, fine-tuning, inference efficiency or agent evaluation. Mere LLM use, RAG or server vulnerabilities do not qualify.'
 WHEN 'rag' THEN 'Retrieval-augmented generation, grounded answers, GraphRAG or RAG-specific defenses. Do not automatically add llm/search.'
 WHEN 'database' THEN 'Database engines, transactions, consistency, SQL/SPARQL query execution or database indexes. Mere data storage does not qualify.'
 WHEN 'search' THEN 'Information retrieval, document/vector retrieval, ranking, retrieval latency or recall. Not beam search, generic recommendation or RAG without retrieval improvements.' END
 FROM tags WHERE slug IN ('security','llm','rag','database','search') AND active;
CREATE TABLE article_classification_jobs (
 article_id uuid PRIMARY KEY REFERENCES articles(id),
 content_hash char(64) NOT NULL,
 policy_hash char(64) NOT NULL,
 state varchar(20) NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','RUNNING','RETRY','DONE','UNCLASSIFIED','FAILED','CANCELLED')),
 attempts integer NOT NULL DEFAULT 0 CHECK(attempts>=0),
 available_at timestamptz NOT NULL DEFAULT now(),
 lease_token uuid, lease_until timestamptz,
 error_code varchar(80), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX classification_queue ON article_classification_jobs(state,available_at);
CREATE TABLE classification_runtime (
 id integer PRIMARY KEY CHECK(id=1), lease_token uuid, lease_until timestamptz,
 checked_at timestamptz, error_code varchar(80)
);
INSERT INTO classification_runtime(id) VALUES(1);
CREATE FUNCTION invalidate_classification() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 UPDATE article_classification_jobs SET state='PENDING',attempts=0,lease_token=NULL,lease_until=NULL,available_at=now(),error_code=NULL,content_hash=repeat('0',64),updated_at=now() WHERE article_id=NEW.id;
 DELETE FROM article_tags WHERE article_id=NEW.id AND method='LLM' AND NOT locked;
 RETURN NEW;
END $$;
CREATE TRIGGER article_classification_changed AFTER UPDATE OF title,summary_bullets ON articles
 FOR EACH ROW WHEN (OLD.title IS DISTINCT FROM NEW.title OR OLD.summary_bullets IS DISTINCT FROM NEW.summary_bullets) EXECUTE FUNCTION invalidate_classification();
