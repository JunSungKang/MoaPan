CREATE OR REPLACE FUNCTION invalidate_classification() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 UPDATE article_classification_jobs SET state='PENDING',attempts=0,lease_token=NULL,lease_until=NULL,available_at=now(),error_code=NULL,content_hash=repeat('0',64),updated_at=now() WHERE article_id=NEW.id;
 IF NOT NEW.tags_locked THEN
  DELETE FROM article_tags WHERE article_id=NEW.id AND method='LLM' AND NOT locked;
 END IF;
 RETURN NEW;
END $$;
