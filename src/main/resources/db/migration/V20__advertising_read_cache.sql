-- Publisher/slot identifiers and edit versions are not public display payloads.
CREATE FUNCTION advertising_read_cache_changed() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        DELETE FROM read_cache_fingerprints WHERE domain='advertising' AND subject='00000000-0000-0000-0000-000000000000';
    ELSE
        PERFORM set_read_cache_fingerprint('advertising','00000000-0000-0000-0000-000000000000',
            jsonb_build_array(NEW.enabled,NEW.provider,NEW.title,NEW.description,NEW.target_url,NEW.image_path));
    END IF;
    RETURN NULL;
END $$;
CREATE TRIGGER advertising_read_cache AFTER INSERT OR UPDATE OR DELETE ON advertising_settings FOR EACH ROW EXECUTE FUNCTION advertising_read_cache_changed();
SELECT set_read_cache_fingerprint('advertising','00000000-0000-0000-0000-000000000000',
    jsonb_build_array(enabled,provider,title,description,target_url,image_path)) FROM advertising_settings WHERE singleton;
