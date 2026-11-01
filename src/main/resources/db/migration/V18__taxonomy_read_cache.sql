CREATE FUNCTION refresh_taxonomy_read_cache() RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    -- Serialize rare catalog edits before reading the projection. No article content is read.
    PERFORM pg_advisory_xact_lock(727101);
    PERFORM set_read_cache_fingerprint('taxonomy','00000000-0000-0000-0000-000000000000',
        jsonb_build_object(
            'tags',(SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY id),'[]') FROM tags t),
            'aliases',(SELECT coalesce(jsonb_agg(to_jsonb(a) ORDER BY alias),'[]') FROM tag_aliases a),
            'redirects',(SELECT coalesce(jsonb_agg(to_jsonb(r) ORDER BY old_id),'[]') FROM tag_redirects r),
            'topics',(SELECT coalesce(jsonb_agg(to_jsonb(c) ORDER BY tag_id),'[]') FROM classification_topics c),
            'version',(SELECT version FROM classification_config WHERE id=1)));
END $$;
CREATE FUNCTION taxonomy_read_cache_changed() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN PERFORM refresh_taxonomy_read_cache(); RETURN NULL; END $$;
CREATE TRIGGER tags_read_cache AFTER INSERT OR UPDATE OR DELETE ON tags FOR EACH STATEMENT EXECUTE FUNCTION taxonomy_read_cache_changed();
CREATE TRIGGER aliases_read_cache AFTER INSERT OR UPDATE OR DELETE ON tag_aliases FOR EACH STATEMENT EXECUTE FUNCTION taxonomy_read_cache_changed();
CREATE TRIGGER redirects_read_cache AFTER INSERT OR UPDATE OR DELETE ON tag_redirects FOR EACH STATEMENT EXECUTE FUNCTION taxonomy_read_cache_changed();
CREATE TRIGGER topics_read_cache AFTER INSERT OR UPDATE OR DELETE ON classification_topics FOR EACH STATEMENT EXECUTE FUNCTION taxonomy_read_cache_changed();
CREATE TRIGGER classification_read_cache AFTER INSERT OR UPDATE OR DELETE ON classification_config FOR EACH STATEMENT EXECUTE FUNCTION taxonomy_read_cache_changed();
SELECT refresh_taxonomy_read_cache();
