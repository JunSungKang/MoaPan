CREATE FUNCTION refresh_publication_read_cache(publication_key uuid) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(727105);
    IF NOT EXISTS(SELECT 1 FROM newsletters WHERE id=publication_key AND published IS NOT NULL) THEN
        DELETE FROM read_cache_fingerprints WHERE domain='publication' AND subject=publication_key;
    ELSE
        PERFORM set_read_cache_fingerprint('publication',n.id,
            jsonb_build_array(n.issue_number,n.published,n.published_dossier,n.published_at AT TIME ZONE 'UTC',n.first_published_at AT TIME ZONE 'UTC',n.publication_revision,n.distribution))
            FROM newsletters n WHERE n.id=publication_key;
    END IF;
    PERFORM set_read_cache_fingerprint('publication_catalog','00000000-0000-0000-0000-000000000000',
        (SELECT coalesce(jsonb_agg(jsonb_build_array(n.id,f.fingerprint) ORDER BY n.published_at DESC,n.id),'[]')
         FROM (SELECT id,published_at FROM newsletters WHERE published IS NOT NULL ORDER BY published_at DESC,id LIMIT 100) n
         JOIN read_cache_fingerprints f ON f.domain='publication' AND f.subject=n.id));
END $$;
CREATE FUNCTION publication_read_cache_changed() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN PERFORM refresh_publication_read_cache(OLD.id);
    ELSE PERFORM refresh_publication_read_cache(NEW.id);END IF;
    RETURN NULL;
END $$;
CREATE TRIGGER publication_read_cache AFTER INSERT OR UPDATE OF issue_number,published,published_dossier,published_at,first_published_at,publication_revision,distribution OR DELETE ON newsletters FOR EACH ROW EXECUTE FUNCTION publication_read_cache_changed();
SELECT refresh_publication_read_cache(id) FROM newsletters ORDER BY id;
SELECT set_read_cache_fingerprint('publication_catalog','00000000-0000-0000-0000-000000000000','[]'::jsonb)
WHERE NOT EXISTS(SELECT 1 FROM read_cache_fingerprints WHERE domain='publication_catalog' AND subject='00000000-0000-0000-0000-000000000000');
