-- Public read-model fingerprints only. No source text or member data is retained here.
CREATE TABLE read_cache_fingerprints (
    domain varchar(40) NOT NULL CHECK (domain ~ '^[a-z_]{1,40}$'),
    subject uuid NOT NULL,
    fingerprint char(64) NOT NULL CHECK (fingerprint ~ '^[0-9a-f]{64}$'),
    PRIMARY KEY(domain,subject)
);

-- Domain migrations attach triggers to their exact output projections. This function is invoked
-- within the writer's transaction, never by an ordinary read to discover whether data changed.
CREATE FUNCTION set_read_cache_fingerprint(cache_domain text, cache_subject uuid, projection jsonb)
RETURNS void LANGUAGE sql AS $$
    INSERT INTO read_cache_fingerprints(domain,subject,fingerprint)
    VALUES(cache_domain,cache_subject,encode(sha256(convert_to(projection::text,'UTF8')),'hex'))
    ON CONFLICT(domain,subject) DO UPDATE SET fingerprint=EXCLUDED.fingerprint
    WHERE read_cache_fingerprints.fingerprint IS DISTINCT FROM EXCLUDED.fingerprint;
$$;
