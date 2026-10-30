ALTER TABLE newsletters ADD COLUMN draft_dossier jsonb;
ALTER TABLE newsletters ADD COLUMN published_dossier jsonb;
ALTER TABLE newsletters ADD COLUMN publication_revision integer NOT NULL DEFAULT 0 CHECK(publication_revision>=0);
ALTER TABLE newsletters ADD COLUMN first_published_at timestamptz;
ALTER TABLE newsletters ADD COLUMN distribution varchar(24) NOT NULL DEFAULT 'FREE_PREVIEW' CHECK(distribution IN ('FREE_PREVIEW','PAID_SUBSCRIPTION'));
UPDATE newsletters SET first_published_at=published_at,publication_revision=1 WHERE published IS NOT NULL;
