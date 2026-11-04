package com.moapan.cache;

import com.moapan.newsletter.*;
import com.moapan.newsletter.application.PublicationEvidence;
import com.moapan.newsletter.domain.PublicationResearch.*;
import com.moapan.shared.api.ApiError;
import com.moapan.shared.infrastructure.cache.SharedReadCaches;
import com.moapan.subscription.application.ContentAccess;
import com.moapan.subscription.domain.EntitlementPolicy.Benefit;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicationCacheIntegrationTest {
    static CacheDatabase f;
    UUID id,article,source;NewsletterService service;ContentAccess access;
    final JsonMapper json=JsonMapper.builder().build();
    @BeforeAll static void start(){f=new CacheDatabase();}
    @AfterAll static void stop(){f.close();}
    @BeforeEach void setup(){
        f.db.sql("DELETE FROM newsletters").update();UUID site=UUID.randomUUID();source=UUID.randomUUID();article=UUID.randomUUID();id=UUID.randomUUID();
        f.db.sql("INSERT INTO sites(id,name,homepage_url,normalized_homepage_url) VALUES(:id,'Synthetic publication',:url,:url)").param("id",site).param("url","https://"+site+".example.test").update();
        f.db.sql("INSERT INTO sources(id,site_id,label,type,endpoint_url,normalized_endpoint_url,rights_status,allow_metadata,allow_display,allow_summary) VALUES(:id,:site,'Synthetic source','RSS','https://example.test/feed','https://example.test/feed','APPROVED',true,true,true)").param("id",source).param("site",site).update();
        f.db.sql("INSERT INTO articles(id,source_id,source_guid,canonical_url,title,summary_bullets,summary_mode,language,input_hash) VALUES(:id,:source,'synthetic','https://example.test/paper','Synthetic evidence','[\"Synthetic evidence summary.\"]','EXTRACTIVE_ORIGINAL_LANGUAGE','en',repeat('a',64))").param("id",article).param("source",source).update();
        var forecast=new Forecast("Question","Observation","Conditional outlook","4 weeks","Experiment","Compare outcome","Stop condition",List.of(article));
        var dossier=new Dossier(com.moapan.newsletter.domain.PublicationResearch.week(Instant.now()).toString(),Instant.now().toString(),List.of(article),List.of(forecast),true,true,"");
        var manuscript=new com.moapan.newsletter.domain.Manuscript("Synthetic publication","Introduction","Research",List.of(new com.moapan.newsletter.domain.Manuscript.Block("paragraph","Synthetic research outlook.","","")));
        f.db.sql("INSERT INTO newsletters(id,issue_number,draft,published,draft_dossier,published_dossier,published_at,first_published_at,publication_revision,version) VALUES(:id,1,CAST(:body AS jsonb),CAST(:body AS jsonb),CAST(:dossier AS jsonb),CAST(:dossier AS jsonb),now(),now(),1,1)")
                .param("id",id).param("body",json.writeValueAsString(manuscript)).param("dossier",json.writeValueAsString(dossier)).update();
        access=mock(ContentAccess.class);service=service();
    }
    private NewsletterService service(){return new NewsletterService(f.db,new PublicationEvidence(f.db,Clock.systemUTC()),access,Clock.systemUTC(),new PublicationReadModels(f.db,new SharedReadCaches(f.tm,"web")),new com.moapan.newsletter.application.ManuscriptValidation());}
    @SuppressWarnings("unchecked") private Map<String,Object> detail(){return (Map<String,Object>)service.detail(id);}
    @Test void warmPublishedReadsEliminateContentQueriesButStillCheckAccessAndEvidence(){
        var before=detail();service.list();f.reset();var after=detail();service.list();
        assertSame(before.get("manuscript"),after.get("manuscript"));assertEquals(0,f.readsContaining("published::text content"));assertEquals(0,f.readsContaining("published->>'title'"));
        assertEquals(2,f.readsContaining("visible from articles"));verify(access,times(4)).require(Benefit.READ_NEWSLETTER);
    }
    @Test void nestedPublishedModelsCannotBeMutatedThroughReturnedObjects(){
        var out=detail();@SuppressWarnings("unchecked")var body=(Map<String,Object>)out.get("manuscript");
        assertThrows(UnsupportedOperationException.class,()->body.put("title","Corrupt"));assertThrows(UnsupportedOperationException.class,()->((List<?>)body.get("blocks")).clear());
        var dossier=(Dossier)out.get("dossier");assertThrows(UnsupportedOperationException.class,()->dossier.articleIds().clear());assertThrows(UnsupportedOperationException.class,()->dossier.forecasts().getFirst().evidenceIds().clear());
        out.put("id",UUID.randomUUID());assertEquals(id,detail().get("id"));
    }
    @Test void manuscriptsMatchingCurrentProductionSizeAreActuallyCachedWithinFixedBudget(){
        var paragraphs=new ArrayList<com.moapan.newsletter.domain.Manuscript.Block>();for(int i=0;i<21;i++)paragraphs.add(new com.moapan.newsletter.domain.Manuscript.Block("paragraph","x".repeat(10000),"",""));
        var body=new com.moapan.newsletter.domain.Manuscript("Large synthetic publication","Introduction","Research",paragraphs);
        f.db.sql("UPDATE newsletters SET published=CAST(:body AS jsonb),publication_revision=publication_revision+1 WHERE id=:id").param("id",id).param("body",json.writeValueAsString(body)).update();
        var first=detail();f.reset();assertSame(first.get("manuscript"),detail().get("manuscript"));assertEquals(0,f.readsContaining("published::text content"));
    }
    @Test void revisionsInvalidateAcrossInstancesAndDraftOnlyEditsDoNot(){
        var other=service();var before=detail();other.detail(id);
        f.db.sql("UPDATE newsletters SET draft=jsonb_set(draft,'{title}','\"Changed draft\"'),version=version+1 WHERE id=:id").param("id",id).update();assertSame(before.get("manuscript"),detail().get("manuscript"));
        f.db.sql("UPDATE newsletters SET published=jsonb_set(published,'{title}','\"Corrected publication\"'),publication_revision=publication_revision+1 WHERE id=:id").param("id",id).update();
        assertNotSame(before.get("manuscript"),detail().get("manuscript"));assertEquals(detail(),other.detail(id));
    }
    @Test void withdrawalDeletionAndEvidenceRevocationCannotBeBypassedByWarmCache(){
        detail();service.list();f.db.sql("UPDATE articles SET public=false WHERE id=:id").param("id",article).update();assertEquals(404,assertThrows(ApiError.class,this::detail).statusCode());assertTrue(((List<?>)service.list()).isEmpty());
        f.db.sql("UPDATE articles SET public=true WHERE id=:id").param("id",article).update();detail();
        f.db.sql("UPDATE sources SET allow_metadata=false WHERE id=:id").param("id",source).update();assertEquals(404,assertThrows(ApiError.class,this::detail).statusCode());
        f.db.sql("UPDATE sources SET allow_metadata=true WHERE id=:id").param("id",source).update();detail();
        f.db.sql("UPDATE newsletters SET published=NULL WHERE id=:id").param("id",id).update();assertEquals(404,assertThrows(ApiError.class,this::detail).statusCode());assertTrue(((List<?>)service.list()).isEmpty());
        f.db.sql("UPDATE newsletters SET published=draft WHERE id=:id").param("id",id).update();detail();f.db.sql("DELETE FROM newsletters WHERE id=:id").param("id",id).update();assertEquals(404,assertThrows(ApiError.class,this::detail).statusCode());
    }
    @Test void entitlementIsCheckedBeforeEveryWarmRead(){
        detail();f.reset();doThrow(new ApiError(403,"synthetic_access_revoked")).when(access).require(Benefit.READ_NEWSLETTER);
        assertEquals(403,assertThrows(ApiError.class,this::detail).statusCode());assertEquals(0,f.readsContaining("read_cache_fingerprints"));
    }
    @Test void rolledBackPublishedChangesCannotPoisonSharedParsedContent(){
        var before=detail();new TransactionTemplate(f.tm).executeWithoutResult(s->{
            f.db.sql("UPDATE newsletters SET published=jsonb_set(published,'{title}','\"Uncommitted publication\"') WHERE id=:id").param("id",id).update();assertNotEquals(before.get("manuscript"),detail().get("manuscript"));s.setRollbackOnly();
        });assertSame(before.get("manuscript"),detail().get("manuscript"));
    }
}
