CREATE FUNCTION source_rights_scope(s sources) RETURNS text LANGUAGE sql IMMUTABLE STRICT AS $$
 SELECT encode(sha256(convert_to(jsonb_build_array(s.endpoint_url,s.type,s.rights_status,s.allow_metadata,s.allow_display,s.allow_summary,s.allow_translation,s.terms_url,s.terms_checked_on)::text,'UTF8')),'hex')
$$;
CREATE TABLE source_rights_reviews (
    id uuid PRIMARY KEY,
    source_id uuid NOT NULL REFERENCES sources(id),
    source_version bigint NOT NULL,
    scope_hash char(64) NOT NULL,
    endpoint_url varchar(2048) NOT NULL,
    terms_url varchar(2048) NOT NULL,
    reviewed_on date NOT NULL,
    next_review_on date NOT NULL CHECK(next_review_on>=reviewed_on),
    commercial_use varchar(16) NOT NULL CHECK(commercial_use IN ('PERMITTED','RESTRICTED','UNCLEAR')),
    basis varchar(2000) NOT NULL,
    permissions jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX source_rights_reviews_latest ON source_rights_reviews(source_id,created_at DESC,id);
CREATE TRIGGER source_rights_review_immutable BEFORE UPDATE OR DELETE ON source_rights_reviews
FOR EACH ROW EXECUTE FUNCTION immutable_editorial_record();
