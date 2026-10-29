CREATE TABLE representative_interests (
    singleton boolean PRIMARY KEY DEFAULT true CHECK(singleton),
    weights jsonb NOT NULL CHECK(jsonb_typeof(weights)='object'),
    version bigint NOT NULL DEFAULT 0 CHECK(version>=0)
);
INSERT INTO representative_interests(singleton,weights)
SELECT true,COALESCE(jsonb_object_agg(id::text,3),'{}'::jsonb) FROM tags WHERE active;
