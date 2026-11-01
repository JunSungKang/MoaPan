package com.moapan.classification.application;

import com.moapan.shared.infrastructure.cache.SharedReadCaches;
import com.moapan.shared.infrastructure.cache.VersionedReadCache;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Shared public dictionary only. No member interests or submitted sentences enter this cache. */
@Service
public final class TagDictionary {
    private static final tools.jackson.databind.json.JsonMapper JSON=tools.jackson.databind.json.JsonMapper.builder().build();
    private final JdbcClient db;
    private final VersionedReadCache<Map<String,Object>> cache;
    public TagDictionary(JdbcClient db, SharedReadCaches caches) {
        this.db=db;
        cache=caches.create(SharedReadCaches.Region.DICTIONARY,v->3*JSON.writeValueAsString(v).length());
    }
    public Map<String,Object> read() {
        return cache.read("dictionary",()->db.sql("SELECT fingerprint FROM read_cache_fingerprints WHERE domain='taxonomy' AND subject='00000000-0000-0000-0000-000000000000'").query(String.class).optional().orElse(null),this::load);
    }
    private Map<String,Object> load() {
        return Map.of("tags",freeze(db.sql("SELECT id,slug,name,category FROM tags WHERE active ORDER BY name,id").query().listOfRows()),
            "aliases",freeze(db.sql("SELECT a.alias,a.tag_id AS \"tagId\" FROM tag_aliases a JOIN tags t ON t.id=a.tag_id WHERE t.active ORDER BY a.alias").query().listOfRows()),
            "redirects",freeze(db.sql("SELECT old_id AS \"oldId\",canonical_id AS \"canonicalId\" FROM tag_redirects ORDER BY old_id").query().listOfRows()));
    }
    private static List<Map<String,Object>> freeze(List<Map<String,Object>> rows) {return rows.stream().map(Map::copyOf).toList();}
}
