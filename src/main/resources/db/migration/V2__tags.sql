INSERT INTO tags(id,slug,name,category) VALUES
('00000000-0000-0000-0000-000000000001','ai','인공지능','기술'),
('00000000-0000-0000-0000-000000000002','kubernetes','쿠버네티스','기술'),
('00000000-0000-0000-0000-000000000003','postgresql','PostgreSQL','기술'),
('00000000-0000-0000-0000-000000000004','space','우주','과학'),
('00000000-0000-0000-0000-000000000005','climate','기후','과학'),
('00000000-0000-0000-0000-000000000006','security','보안','기술');
INSERT INTO tag_aliases(alias,tag_id) SELECT alias,t.id FROM (VALUES ('ai','ai'),('artificial intelligence','ai'),('인공지능','ai'),('k8s','kubernetes'),('kubernetes','kubernetes'),('쿠버네티스','kubernetes'),('postgresql','postgresql'),('포스트그레스','postgresql'),('space','space'),('우주','space'),('nasa','space'),('climate','climate'),('기후','climate'),('security','security'),('보안','security')) AS a(alias,slug) JOIN tags t ON t.slug=a.slug;
