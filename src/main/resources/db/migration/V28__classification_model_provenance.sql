-- A taxonomy hash alone cannot establish which model produced a legacy result.
-- NULL is deliberately untrusted until an exact current hash or reviewed CLI maps it.
ALTER TABLE article_classification_jobs ADD COLUMN model_policy varchar(160);
CREATE INDEX classification_unstamped_policy ON article_classification_jobs(policy_hash)
    WHERE model_policy IS NULL;

CREATE OR REPLACE FUNCTION article_classification_cache_changed() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        DELETE FROM read_cache_fingerprints WHERE domain='article_classification' AND subject=OLD.article_id;
    ELSE
        IF TG_OP='UPDATE' AND OLD.article_id IS DISTINCT FROM NEW.article_id THEN
            DELETE FROM read_cache_fingerprints WHERE domain='article_classification' AND subject=OLD.article_id;
        END IF;
        PERFORM set_read_cache_fingerprint('article_classification',NEW.article_id,
            jsonb_build_array(NEW.state,NEW.policy_hash,NEW.model_policy));
    END IF;
    RETURN NULL;
END $$;
DROP TRIGGER classification_result_read_cache ON article_classification_jobs;
CREATE TRIGGER classification_result_read_cache
    AFTER INSERT OR UPDATE OF article_id,state,policy_hash,model_policy OR DELETE
    ON article_classification_jobs FOR EACH ROW EXECUTE FUNCTION article_classification_cache_changed();
SELECT set_read_cache_fingerprint('article_classification',article_id,
    jsonb_build_array(state,policy_hash,model_policy)) FROM article_classification_jobs ORDER BY article_id;
