-- Hash only existing, permitted public metadata/summary projections, at write time.
-- Per-article rows avoid a global hot article-generation row or a full-article hash scan on reads.
CREATE FUNCTION refresh_article_card_fingerprint(article_key uuid) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    PERFORM id FROM articles WHERE id=article_key FOR NO KEY UPDATE;
    IF NOT FOUND THEN
        DELETE FROM read_cache_fingerprints WHERE domain='article_card' AND subject=article_key;
        RETURN;
    END IF;
    PERFORM set_read_cache_fingerprint('article_card',a.id,jsonb_build_object(
        'article',jsonb_build_array(a.id,a.source_id,a.title,a.author,a.summary_bullets,a.summary_mode,a.published_at AT TIME ZONE 'UTC',a.collected_at AT TIME ZONE 'UTC',a.canonical_url,a.public),
        'tags',(SELECT coalesce(jsonb_agg(jsonb_build_array(tag_id,confidence,method,locked) ORDER BY tag_id),'[]') FROM article_tags WHERE article_id=a.id),
        'translations',(SELECT coalesce(jsonb_agg(jsonb_build_array(language,content_hash,title,summary_bullets,provider,locked) ORDER BY language),'[]') FROM article_translations WHERE article_id=a.id)))
        FROM articles a WHERE a.id=article_key;
END $$;
CREATE FUNCTION article_card_changed() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN PERFORM refresh_article_card_fingerprint(OLD.id);
    ELSE PERFORM refresh_article_card_fingerprint(NEW.id); END IF;
    RETURN NULL;
END $$;
CREATE FUNCTION article_card_child_changed() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN PERFORM refresh_article_card_fingerprint(OLD.article_id);
    ELSE
        PERFORM refresh_article_card_fingerprint(NEW.article_id);
        IF TG_OP='UPDATE' AND OLD.article_id<>NEW.article_id THEN PERFORM refresh_article_card_fingerprint(OLD.article_id); END IF;
    END IF;
    RETURN NULL;
END $$;
CREATE TRIGGER article_read_cache AFTER INSERT OR UPDATE OF source_id,title,author,summary_bullets,summary_mode,published_at,collected_at,canonical_url,public OR DELETE ON articles FOR EACH ROW EXECUTE FUNCTION article_card_changed();
CREATE TRIGGER article_tags_read_cache AFTER INSERT OR UPDATE OR DELETE ON article_tags FOR EACH ROW EXECUTE FUNCTION article_card_child_changed();
CREATE TRIGGER translations_read_cache AFTER INSERT OR UPDATE OF language,content_hash,title,summary_bullets,provider,locked OR DELETE ON article_translations FOR EACH ROW EXECUTE FUNCTION article_card_child_changed();
-- Job claim/failure paths lock jobs before articles. Never acquire an article lock here:
-- article edits take the opposite order when invalidating jobs. A separate fingerprint
-- records the single job row atomically without introducing a parent/child lock cycle.
CREATE FUNCTION article_classification_cache_changed() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        DELETE FROM read_cache_fingerprints WHERE domain='article_classification' AND subject=OLD.article_id;
    ELSE
        IF TG_OP='UPDATE' AND OLD.article_id<>NEW.article_id THEN
            DELETE FROM read_cache_fingerprints WHERE domain='article_classification' AND subject=OLD.article_id;
        END IF;
        PERFORM set_read_cache_fingerprint('article_classification',NEW.article_id,jsonb_build_array(NEW.state,NEW.policy_hash));
    END IF;
    RETURN NULL;
END $$;
CREATE TRIGGER classification_result_read_cache AFTER INSERT OR UPDATE OF article_id,state,policy_hash OR DELETE ON article_classification_jobs FOR EACH ROW EXECUTE FUNCTION article_classification_cache_changed();

CREATE FUNCTION refresh_news_source_catalog() RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(727103);
    PERFORM set_read_cache_fingerprint('news_sources','00000000-0000-0000-0000-000000000000',
        (SELECT coalesce(jsonb_agg(jsonb_build_array(s.id,s.label,s.language,p.name,s.terms_url) ORDER BY p.name,s.label,s.id),'[]')
         FROM sources s JOIN sites p ON p.id=s.site_id WHERE s.allow_display AND s.rights_status IN ('APPROVED','RESTRICTED')));
END $$;
CREATE FUNCTION news_source_catalog_changed() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN PERFORM refresh_news_source_catalog();RETURN NULL;END $$;
CREATE FUNCTION news_source_changed() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN DELETE FROM read_cache_fingerprints WHERE domain='news_source' AND subject=OLD.id;
    ELSE PERFORM set_read_cache_fingerprint('news_source',NEW.id,jsonb_build_array(NEW.site_id,NEW.label,NEW.language,NEW.allow_display,NEW.allow_summary,NEW.allow_translation,NEW.rights_status));END IF;
    RETURN NULL;
END $$;
CREATE FUNCTION news_site_changed() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN DELETE FROM read_cache_fingerprints WHERE domain='news_site' AND subject=OLD.id;
    ELSE PERFORM set_read_cache_fingerprint('news_site',NEW.id,to_jsonb(NEW.name));END IF;
    RETURN NULL;
END $$;
CREATE TRIGGER news_source_read_cache AFTER INSERT OR UPDATE OF site_id,label,language,allow_display,allow_summary,allow_translation,rights_status OR DELETE ON sources FOR EACH ROW EXECUTE FUNCTION news_source_changed();
CREATE TRIGGER news_site_read_cache AFTER INSERT OR UPDATE OF name OR DELETE ON sites FOR EACH ROW EXECUTE FUNCTION news_site_changed();
CREATE TRIGGER news_sources_catalog AFTER INSERT OR UPDATE OF site_id,label,language,allow_display,rights_status,terms_url OR DELETE ON sources FOR EACH STATEMENT EXECUTE FUNCTION news_source_catalog_changed();
CREATE TRIGGER news_sites_catalog AFTER INSERT OR UPDATE OF name OR DELETE ON sites FOR EACH STATEMENT EXECUTE FUNCTION news_source_catalog_changed();

CREATE FUNCTION refresh_news_taxonomy_fingerprint() RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(727104);
    PERFORM set_read_cache_fingerprint('news_taxonomy','00000000-0000-0000-0000-000000000000',
        jsonb_build_object('tags',(SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY id),'[]') FROM tags t),
            'topics',(SELECT coalesce(jsonb_agg(to_jsonb(c) ORDER BY tag_id),'[]') FROM classification_topics c),
            'version',(SELECT version FROM classification_config WHERE id=1)));
END $$;
CREATE FUNCTION news_taxonomy_changed() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN PERFORM refresh_news_taxonomy_fingerprint();RETURN NULL;END $$;
-- Prefix order keeps shared catalog-lock acquisition consistent with the taxonomy domain.
CREATE TRIGGER news_tags_cache AFTER INSERT OR UPDATE OR DELETE ON tags FOR EACH STATEMENT EXECUTE FUNCTION news_taxonomy_changed();
CREATE TRIGGER news_topics_cache AFTER INSERT OR UPDATE OR DELETE ON classification_topics FOR EACH STATEMENT EXECUTE FUNCTION news_taxonomy_changed();
CREATE TRIGGER classification_news_cache AFTER INSERT OR UPDATE OR DELETE ON classification_config FOR EACH STATEMENT EXECUTE FUNCTION news_taxonomy_changed();

CREATE FUNCTION refresh_edition_membership_fingerprint(edition_key uuid) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS(SELECT 1 FROM news_editions WHERE id=edition_key) THEN
        DELETE FROM read_cache_fingerprints WHERE domain='edition_membership' AND subject=edition_key;
    ELSE
        PERFORM set_read_cache_fingerprint('edition_membership',edition_key,
            (SELECT coalesce(jsonb_agg(jsonb_build_array(position,article_id) ORDER BY position),'[]') FROM news_edition_items WHERE edition_id=edition_key));
    END IF;
END $$;
CREATE FUNCTION edition_parent_cache_changed() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN PERFORM refresh_edition_membership_fingerprint(OLD.id);
    ELSE PERFORM refresh_edition_membership_fingerprint(NEW.id);END IF;
    RETURN NULL;
END $$;
CREATE FUNCTION edition_members_inserted() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE key uuid;
BEGIN FOR key IN SELECT DISTINCT edition_id FROM new_members LOOP PERFORM refresh_edition_membership_fingerprint(key);END LOOP;RETURN NULL;END $$;
CREATE FUNCTION edition_members_deleted() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE key uuid;
BEGIN FOR key IN SELECT DISTINCT edition_id FROM old_members LOOP PERFORM refresh_edition_membership_fingerprint(key);END LOOP;RETURN NULL;END $$;
CREATE FUNCTION edition_members_updated() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE key uuid;
BEGIN FOR key IN SELECT edition_id FROM new_members UNION SELECT edition_id FROM old_members LOOP PERFORM refresh_edition_membership_fingerprint(key);END LOOP;RETURN NULL;END $$;
CREATE TRIGGER edition_parent_cache AFTER INSERT OR DELETE ON news_editions FOR EACH ROW EXECUTE FUNCTION edition_parent_cache_changed();
CREATE TRIGGER edition_members_insert_cache AFTER INSERT ON news_edition_items REFERENCING NEW TABLE AS new_members FOR EACH STATEMENT EXECUTE FUNCTION edition_members_inserted();
CREATE TRIGGER edition_members_delete_cache AFTER DELETE ON news_edition_items REFERENCING OLD TABLE AS old_members FOR EACH STATEMENT EXECUTE FUNCTION edition_members_deleted();
CREATE TRIGGER edition_members_update_cache AFTER UPDATE ON news_edition_items REFERENCING NEW TABLE AS new_members OLD TABLE AS old_members FOR EACH STATEMENT EXECUTE FUNCTION edition_members_updated();

SELECT refresh_article_card_fingerprint(id) FROM articles ORDER BY id;
SELECT set_read_cache_fingerprint('article_classification',article_id,jsonb_build_array(state,policy_hash)) FROM article_classification_jobs ORDER BY article_id;
SELECT set_read_cache_fingerprint('news_source',id,jsonb_build_array(site_id,label,language,allow_display,allow_summary,allow_translation,rights_status)) FROM sources ORDER BY id;
SELECT set_read_cache_fingerprint('news_site',id,to_jsonb(name)) FROM sites ORDER BY id;
SELECT refresh_news_source_catalog();
SELECT refresh_news_taxonomy_fingerprint();
SELECT refresh_edition_membership_fingerprint(id) FROM news_editions ORDER BY id;
