package com.moapan.cache;

import com.moapan.advertising.application.AdvertisingSettings;
import com.moapan.shared.infrastructure.cache.SharedReadCaches;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

class AdvertisingCacheIntegrationTest {
    static CacheDatabase f;AdvertisingSettings service;
    @BeforeAll static void start(){f=new CacheDatabase();}
    @AfterAll static void stop(){f.close();}
    @BeforeEach void setup(){
        f.db.sql("UPDATE advertising_settings SET enabled=true,provider='BANNER',title='Synthetic banner',target_url='https://example.test'").update();
        service=new AdvertisingSettings(f.db,new SharedReadCaches(f.tm,"web"));
    }
    @Test void warmDisplayReusesOnlyPublicImmutableDto(){
        var before=service.display();f.reset();assertSame(before,service.display());assertEquals(1,f.readsContaining("read_cache_fingerprints"));assertEquals(0,f.readsContaining("from advertising_settings"));
        String json=tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(before);assertFalse(json.contains("publisher"));assertFalse(json.contains("slotId"));
    }
    @Test void changedDisplayAndDisableAreSeenAcrossInstances(){
        var other=new AdvertisingSettings(f.db,new SharedReadCaches(f.tm,"web"));service.display();other.display();
        f.db.sql("UPDATE advertising_settings SET title='Changed synthetic banner'").update();assertEquals("Changed synthetic banner",service.display().title());assertEquals(service.display(),other.display());
        f.db.sql("UPDATE advertising_settings SET enabled=false").update();assertFalse(service.display().enabled());assertEquals(service.display(),other.display());
    }
    @Test void privateIdentifiersDoNotInvalidateOrEnterDisplayCacheAndAdminReadStaysFresh(){
        var before=service.display();f.db.sql("UPDATE advertising_settings SET publisher_id='ca-pub-0000000000000000',slot_id='123',version=version+1").update();
        assertSame(before,service.display());assertEquals("ca-pub-0000000000000000",service.read().publisherId());
    }
    @Test void rolledBackChangeNeverReplacesSharedPublicValue(){
        var before=service.display();new TransactionTemplate(f.tm).executeWithoutResult(s->{
            f.db.sql("UPDATE advertising_settings SET title='Uncommitted synthetic banner'").update();assertEquals("Uncommitted synthetic banner",service.display().title());s.setRollbackOnly();
        });assertSame(before,service.display());
    }
}
