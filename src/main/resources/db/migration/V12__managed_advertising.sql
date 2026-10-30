CREATE TABLE advertising_settings (
    singleton boolean PRIMARY KEY DEFAULT true CHECK(singleton),
    enabled boolean NOT NULL DEFAULT false,
    provider varchar(16) NOT NULL DEFAULT 'BANNER' CHECK(provider IN ('BANNER','ADSENSE')),
    title varchar(80) NOT NULL DEFAULT '',
    description varchar(160) NOT NULL DEFAULT '',
    target_url varchar(2048) NOT NULL DEFAULT '',
    image_path varchar(240) NOT NULL DEFAULT '',
    publisher_id varchar(32) NOT NULL DEFAULT '',
    slot_id varchar(20) NOT NULL DEFAULT '',
    version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
    CHECK(NOT enabled OR provider='BANNER')
);
INSERT INTO advertising_settings(singleton) VALUES(true);
