-- Only translations of the already stored title/summary; never source article bodies.
CREATE TABLE article_translations (
    article_id uuid NOT NULL REFERENCES articles(id),
    language varchar(2) NOT NULL CHECK (language IN ('ko','en')),
    content_hash char(64) NOT NULL,
    title varchar(1000) NOT NULL,
    summary_bullets jsonb NOT NULL CHECK (jsonb_typeof(summary_bullets)='array' AND jsonb_array_length(summary_bullets)<=4),
    provider varchar(160) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(article_id,language)
);

CREATE TABLE article_translation_jobs (
    article_id uuid PRIMARY KEY REFERENCES articles(id),
    attempts integer NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 3),
    available_at timestamptz NOT NULL DEFAULT now(),
    lease_until timestamptz,
    lease_token uuid,
    error_code varchar(80)
);

CREATE FUNCTION invalidate_article_translations() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    DELETE FROM article_translations WHERE article_id=NEW.id;
    DELETE FROM article_translation_jobs WHERE article_id=NEW.id;
    RETURN NEW;
END;
$$;
CREATE TRIGGER article_translation_content_changed
AFTER UPDATE OF title,summary_bullets ON articles
FOR EACH ROW WHEN (OLD.title IS DISTINCT FROM NEW.title OR OLD.summary_bullets IS DISTINCT FROM NEW.summary_bullets)
EXECUTE FUNCTION invalidate_article_translations();
