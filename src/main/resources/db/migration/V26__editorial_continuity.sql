-- Editorial history contains our manuscripts and permitted metadata, never source bodies.
CREATE TABLE newsletter_revisions (
    newsletter_id uuid NOT NULL,
    revision integer NOT NULL CHECK (revision > 0),
    issue_number bigint,
    manuscript jsonb NOT NULL,
    dossier jsonb,
    evidence jsonb NOT NULL DEFAULT '[]',
    published_at timestamptz NOT NULL,
    first_published_at timestamptz NOT NULL,
    distribution varchar(24) NOT NULL CHECK(distribution IN ('FREE_PREVIEW','PAID_SUBSCRIPTION')),
    capture_kind varchar(24) NOT NULL CHECK(capture_kind IN ('EXISTING_VERSION','PUBLICATION')),
    recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY(newsletter_id,revision)
);
-- Retain the available version only. Earlier lost revisions must never be invented.
INSERT INTO newsletter_revisions(newsletter_id,revision,issue_number,manuscript,dossier,evidence,published_at,first_published_at,distribution,capture_kind)
SELECT n.id,n.publication_revision,n.issue_number,n.published,n.published_dossier,
       COALESCE((SELECT jsonb_agg(jsonb_build_object('id',a.id,'title',a.title,'url',a.canonical_url,'collectedAt',to_char(a.collected_at AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),'visible',a.public AND s.allow_metadata AND s.allow_display AND s.rights_status IN ('APPROVED','RESTRICTED')) ORDER BY a.id)
                 FROM articles a JOIN sources s ON s.id=a.source_id WHERE n.published_dossier->'articleIds' @> to_jsonb(a.id::text)), '[]'::jsonb),
       n.published_at,COALESCE(n.first_published_at,n.published_at),n.distribution,'EXISTING_VERSION'
FROM newsletters n WHERE n.published IS NOT NULL AND n.publication_revision>0;
CREATE FUNCTION immutable_editorial_record() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Editorial records are append-only' USING ERRCODE='55000'; END $$;
CREATE TRIGGER newsletter_revision_immutable BEFORE UPDATE OR DELETE ON newsletter_revisions
FOR EACH ROW EXECUTE FUNCTION immutable_editorial_record();
-- No cascading FK: removing an issue from the service must not erase its original outlook.
