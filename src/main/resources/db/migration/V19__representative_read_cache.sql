-- Administrator's common defaults, never a member-interest profile.
CREATE FUNCTION refresh_representative_read_cache() RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(727102);
    PERFORM set_read_cache_fingerprint('representative','00000000-0000-0000-0000-000000000000',
        jsonb_build_object('settings',(SELECT to_jsonb(r) FROM representative_interests r WHERE singleton),
            'active',(SELECT coalesce(jsonb_agg(id ORDER BY id),'[]') FROM tags WHERE active),
            'redirects',(SELECT coalesce(jsonb_agg(to_jsonb(r) ORDER BY old_id),'[]') FROM tag_redirects r)));
END $$;
CREATE FUNCTION representative_read_cache_changed() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN PERFORM refresh_representative_read_cache(); RETURN NULL; END $$;
CREATE TRIGGER representative_read_cache AFTER INSERT OR UPDATE OR DELETE ON representative_interests FOR EACH STATEMENT EXECUTE FUNCTION representative_read_cache_changed();
CREATE TRIGGER tags_representative_read_cache AFTER INSERT OR UPDATE OR DELETE ON tags FOR EACH STATEMENT EXECUTE FUNCTION representative_read_cache_changed();
CREATE TRIGGER redirects_representative_read_cache AFTER INSERT OR UPDATE OR DELETE ON tag_redirects FOR EACH STATEMENT EXECUTE FUNCTION representative_read_cache_changed();
SELECT refresh_representative_read_cache();
