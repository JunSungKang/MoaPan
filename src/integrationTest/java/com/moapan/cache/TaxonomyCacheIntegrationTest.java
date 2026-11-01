package com.moapan.cache;

import com.moapan.classification.application.*;
import com.moapan.shared.infrastructure.cache.SharedReadCaches;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

class TaxonomyCacheIntegrationTest {
    static CacheDatabase fixture;
    TagDictionary dictionary;TopicCatalog topics;
    @BeforeAll static void start(){fixture=new CacheDatabase();}
    @AfterAll static void stop(){fixture.close();}
    @BeforeEach void services(){var caches=new SharedReadCaches(fixture.tm,"web");dictionary=new TagDictionary(fixture.db,caches);topics=new TopicCatalog(fixture.db,caches);}
    private String stamp(){return fixture.db.sql("SELECT fingerprint FROM read_cache_fingerprints WHERE domain='taxonomy'").query(String.class).single();}
    @Test void realDictionaryReadsReuseImmutablePayloadAndOnlyProbeSavedHash(){
        var first=dictionary.read();fixture.reset();assertSame(first,dictionary.read());
        assertEquals(1,fixture.readsContaining("read_cache_fingerprints"));assertEquals(0,fixture.readsContaining("from tags"));assertEquals(0,fixture.readsContaining("from tag_aliases"));
        @SuppressWarnings("unchecked")var rows=(List<Map<String,Object>>)first.get("tags");
        assertThrows(UnsupportedOperationException.class,()->rows.add(Map.of()));assertThrows(UnsupportedOperationException.class,()->rows.getFirst().put("name","corrupt"));
    }
    @Test void classificationSnapshotReusesDataButLockingReadKeepsDatabaseLocks(){
        var first=topics.snapshot(false);fixture.reset();assertSame(first,topics.snapshot(false));assertEquals(0,fixture.readsContaining("from classification_config"));
        assertThrows(UnsupportedOperationException.class,()->first.topics().clear());
        new TransactionTemplate(fixture.tm).executeWithoutResult(s->{assertEquals(first,topics.snapshot(true));});
        assertEquals(1,fixture.readsContaining("from classification_config"));assertEquals(1,fixture.readsContaining("for share of t,c"));
    }
    @Test void renamesDefinitionsAndVersionChangesInvalidateBeforeSixtyMinutesAcrossInstances(){
        var other=new TopicCatalog(fixture.db,new SharedReadCaches(fixture.tm,"web"));
        var before=topics.snapshot(false);other.snapshot(false);UUID id=before.topics().getFirst().id();
        fixture.db.sql("UPDATE tags SET name='Synthetic renamed topic' WHERE id=:id").param("id",id).update();
        var changed=topics.snapshot(false);assertNotEquals(before.hash(),changed.hash());assertEquals(changed,other.snapshot(false));
        fixture.db.sql("UPDATE classification_topics SET definition='Synthetic changed definition' WHERE tag_id=:id").param("id",id).update();
        var next=topics.snapshot(false);assertNotEquals(changed.hash(),next.hash());
        fixture.db.sql("UPDATE classification_config SET version=version+1").update();assertNotEquals(next.hash(),topics.snapshot(false).hash());
    }
    @Test void aliasAndRedirectChangesInvalidatePublicDictionary(){
        var before=dictionary.read();String hash=stamp();var ids=fixture.db.sql("SELECT id FROM tags WHERE active ORDER BY id LIMIT 2").query(UUID.class).list();
        fixture.db.sql("INSERT INTO tag_aliases VALUES(:alias,:id)").param("alias","synthetic_alias_"+UUID.randomUUID()).param("id",ids.getFirst()).update();
        assertNotEquals(hash,stamp());assertNotSame(before,dictionary.read());var aliased=dictionary.read();
        fixture.db.sql("INSERT INTO tag_redirects VALUES(:old,:target) ON CONFLICT(old_id) DO UPDATE SET canonical_id=EXCLUDED.canonical_id").param("old",ids.getFirst()).param("target",ids.getLast()).update();
        assertNotSame(aliased,dictionary.read());
    }
    @Test void writerBypassAndRollbackNeverPublishPrivateTaxonomy(){
        var before=topics.snapshot(false);String hash=stamp();UUID id=before.topics().getFirst().id();
        new TransactionTemplate(fixture.tm).executeWithoutResult(s->{
            fixture.db.sql("UPDATE tags SET name='Uncommitted synthetic topic' WHERE id=:id").param("id",id).update();
            assertNotEquals(hash,stamp());assertNotEquals(before.hash(),topics.snapshot(false).hash());s.setRollbackOnly();
        });
        assertEquals(hash,stamp());assertSame(before,topics.snapshot(false));
    }
    @Test void concurrentEditsLeaveFingerprintMatchingBothCommittedRows()throws Exception{
        try(var pool=Executors.newFixedThreadPool(2)){
            var calls=new ArrayList<Future<?>>();for(int i=0;i<2;i++){UUID id=UUID.randomUUID();calls.add(pool.submit(()->new TransactionTemplate(fixture.tm).executeWithoutResult(s->fixture.db.sql("INSERT INTO tags VALUES(:id,:slug,'Synthetic concurrent tag','technology',true)").param("id",id).param("slug","synthetic_"+id.toString().replace("-","")).update())));}
            for(var call:calls)call.get(10,TimeUnit.SECONDS);
        }
        String before=stamp();fixture.db.sql("SELECT refresh_taxonomy_read_cache()").query((r,n)->1).list();assertEquals(before,stamp());
    }
}
