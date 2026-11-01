package com.moapan.cache;

import com.moapan.edition.application.RepresentativeInterests;
import com.moapan.shared.infrastructure.cache.SharedReadCaches;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

class InterestsCacheIntegrationTest {
    static CacheDatabase f;
    RepresentativeInterests service;
    @BeforeAll static void start(){f=new CacheDatabase();}
    @AfterAll static void stop(){f.close();}
    @BeforeEach void setup(){service=new RepresentativeInterests(f.db,new SharedReadCaches(f.tm,"web"));}
    private List<UUID> tags(){return f.db.sql("SELECT id FROM tags WHERE active ORDER BY id LIMIT 2").query(UUID.class).list();}
    private void weights(UUID id){f.db.sql("UPDATE representative_interests SET version=version+1,weights=jsonb_build_object(CAST(:id AS text),3)").param("id",id.toString()).update();}
    @Test void existingDefaultsReadUsesOneHashProbeAndImmutableSettings(){
        weights(tags().getFirst());var first=service.read();f.reset();assertSame(first,service.read());
        assertEquals(1,f.readsContaining("read_cache_fingerprints"));assertEquals(0,f.readsContaining("from representative_interests"));assertEquals(0,f.readsContaining("from tags"));
        assertThrows(UnsupportedOperationException.class,()->first.weights().clear());
    }
    @Test void defaultsChangeIsVisibleAcrossInstancesImmediately(){
        var ids=tags();weights(ids.getFirst());var other=new RepresentativeInterests(f.db,new SharedReadCaches(f.tm,"web"));
        var before=service.read();other.read();weights(ids.getLast());assertNotEquals(before,service.read());assertEquals(service.read(),other.read());assertEquals(Map.of(ids.getLast(),3),service.read().weights());
    }
    @Test void inactiveAndRedirectedTagsAreRecomputed(){
        var ids=tags();weights(ids.getFirst());service.read();
        f.db.sql("INSERT INTO tag_redirects VALUES(:old,:target) ON CONFLICT(old_id) DO UPDATE SET canonical_id=EXCLUDED.canonical_id").param("old",ids.getFirst()).param("target",ids.getLast()).update();
        f.db.sql("UPDATE tags SET active=false WHERE id=:id").param("id",ids.getFirst()).update();
        assertEquals(Map.of(ids.getLast(),3),service.read().weights());
        f.db.sql("UPDATE tags SET active=true WHERE id=:id").param("id",ids.getFirst()).update();
    }
    @Test void rollbackAndSameContentHashDoNotPublishPrivateSettings(){
        var ids=tags();weights(ids.getFirst());var before=service.read();
        new TransactionTemplate(f.tm).executeWithoutResult(s->{weights(ids.getLast());assertNotEquals(before,service.read());s.setRollbackOnly();});
        assertSame(before,service.read());f.db.sql("UPDATE representative_interests SET weights=weights").update();assertSame(before,service.read());
    }
}
