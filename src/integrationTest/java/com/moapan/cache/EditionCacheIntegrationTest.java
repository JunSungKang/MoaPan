package com.moapan.cache;

import com.moapan.access.infrastructure.Limits;
import com.moapan.classification.application.TopicCatalog;
import com.moapan.classification.domain.TopicTaxonomy;
import com.moapan.edition.application.*;
import com.moapan.shared.api.ApiError;
import com.moapan.shared.infrastructure.cache.SharedReadCaches;
import com.moapan.translation.infrastructure.ArticleTranslations;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EditionCacheIntegrationTest {
    static CacheDatabase f;
    UUID site,source,user;NewsService news;ArticleTranslations translations;Limits limits;
    private static final String POLICY="f".repeat(64);
    @BeforeAll static void start(){f=new CacheDatabase();}
    @AfterAll static void stop(){f.close();}
    @BeforeEach void setup(){
        f.db.sql("DELETE FROM news_editions").update();f.db.sql("UPDATE articles SET public=false").update();
        site=UUID.randomUUID();source=UUID.randomUUID();user=UUID.randomUUID();
        f.db.sql("INSERT INTO sites(id,name,homepage_url,normalized_homepage_url) VALUES(:id,'Synthetic site',:url,:url)").param("id",site).param("url","https://"+site+".example.test").update();
        f.db.sql("INSERT INTO sources(id,site_id,label,type,endpoint_url,normalized_endpoint_url,rights_status,allow_metadata,allow_display,allow_summary,allow_translation) VALUES(:id,:site,'Synthetic source','RSS','https://example.test/feed','https://example.test/feed','APPROVED',true,true,true,true)").param("id",source).param("site",site).update();
        f.db.sql("INSERT INTO users(id,username,password_hash) VALUES(:id,:name,'unused-fixture-hash')").param("id",user).param("name","synthetic_"+user.toString().replace("-","")).update();
        addArticles(45);translations=new ArticleTranslations(f.db,f.tm);limits=new Limits(f.db,f.tm,"synthetic-cache-test-secret-with-at-least-32-characters");news=service();
    }
    private NewsService service(){
        // Classification is another domain; preserve its policy contract without binding its constructor.
        var topics=mock(TopicCatalog.class);when(topics.snapshot(false)).thenReturn(new TopicTaxonomy.Snapshot(0,List.of(),POLICY));
        return new NewsService(f.db,limits,f.tm,Clock.systemUTC(),translations,topics,new EditionReadModels(f.db,new SharedReadCaches(f.tm,"web")));
    }
    private void addArticles(int count){f.db.sql("INSERT INTO articles(id,source_id,source_guid,canonical_url,title,summary_bullets,summary_mode,language,input_hash) SELECT gen_random_uuid(),:source,CAST(gen_random_uuid() AS text),'https://example.test/'||gen_random_uuid(),'Synthetic research '||n,'[\"Synthetic evaluation results show a useful method.\"]','EXTRACTIVE_ORIGINAL_LANGUAGE','en',repeat('a',64) FROM generate_series(1,:count) n").param("source",source).param("count",count).update();}
    @Test void existingEditionActuallyReusesImmutableCardsWithoutReadingPayloadAgain(){
        var first=news.publicEdition();assertEquals(42,first.items().size());assertEquals(45,first.candidateCount());f.reset();var again=news.publicEdition();
        assertSame(first.items(),again.items());assertEquals(0,f.readsContaining("select a.id,a.title,a.author"));assertEquals(0,f.readsContaining("from article_tags at"));assertEquals(0,f.readsContaining("select t.* from article_translations"));assertEquals(0,f.readsContaining("select position,article_id from news_edition_items"));
        assertThrows(UnsupportedOperationException.class,()->again.items().clear());assertThrows(UnsupportedOperationException.class,()->again.items().getFirst().summaryBullets().clear());assertThrows(UnsupportedOperationException.class,()->again.items().getFirst().tags().clear());
    }
    @Test void hiddenArticleAndRevokedSourceCannotBeReturnedFromWarmCache(){
        var first=news.publicEdition();UUID hidden=first.items().getFirst().id();f.db.sql("UPDATE articles SET public=false WHERE id=:id").param("id",hidden).update();
        var next=news.publicEdition();assertEquals(44,next.candidateCount());assertEquals(42,next.items().size());assertTrue(next.items().stream().noneMatch(a->a.id().equals(hidden)));
        f.db.sql("UPDATE sources SET rights_status='DISABLED' WHERE id=:id").param("id",source).update();assertTrue(news.publicEdition().items().isEmpty());assertEquals(0,news.publicEdition().candidateCount());
    }
    @Test void correctionsTranslationAndSummaryPermissionsInvalidateAcrossInstances(){
        var other=service();var first=news.publicEdition().items().getFirst();other.publicEdition();
        String hash=ArticleTranslations.contentHash(first.title(),first.summaryBullets());
        assertTrue(translations.store(first.id(),hash,"ko",new ArticleTranslations.Text("합성 번역 제목",List.of("합성 번역 요약")),com.moapan.translation.application.TranslationWorker.PROVIDER));
        assertEquals("합성 번역 제목",news.publicEdition().items().getFirst().translations().get("ko").title());assertEquals(news.publicEdition().items(),other.publicEdition().items());
        f.db.sql("UPDATE sources SET allow_translation=false WHERE id=:id").param("id",source).update();assertTrue(news.publicEdition().items().getFirst().translations().isEmpty());
        f.db.sql("UPDATE sources SET allow_summary=false WHERE id=:id").param("id",source).update();var restricted=news.publicEdition().items().getFirst();assertTrue(restricted.summaryBullets().isEmpty());assertEquals("INSUFFICIENT",restricted.summaryMode());assertEquals(news.publicEdition().items(),other.publicEdition().items());
        f.db.sql("UPDATE sources SET allow_summary=true,allow_translation=true WHERE id=:id").param("id",source).update();
        f.db.sql("UPDATE articles SET title='Corrected synthetic title' WHERE id=:id").param("id",first.id()).update();var corrected=news.publicEdition().items().stream().filter(a->a.id().equals(first.id())).findFirst().orElseThrow();assertEquals("Corrected synthetic title",corrected.title());assertTrue(corrected.translations().isEmpty());
    }
    @Test void sourceAndSiteRenameAndPublicSourceCatalogAreRefreshed(){
        var before=news.publicEdition();var sourceList=news.sources();assertSame(sourceList,news.sources());
        f.db.sql("UPDATE sources SET label='Renamed source' WHERE id=:id").param("id",source).update();f.db.sql("UPDATE sites SET name='Renamed site' WHERE id=:id").param("id",site).update();
        var changed=news.publicEdition().items().getFirst().source();assertEquals("Renamed source",changed.name());assertEquals("Renamed site",changed.site());assertNotSame(before.items(),news.publicEdition().items());assertNotSame(sourceList,news.sources());
    }
    @Test void tagEditsAndClassificationStateNeverLeaveStaleDisplayedTopics(){
        UUID article=news.publicEdition().items().getFirst().id();UUID tag=f.db.sql("SELECT id FROM tags WHERE active ORDER BY id LIMIT 1").query(UUID.class).single();
        f.db.sql("INSERT INTO article_tags VALUES(:a,:t,1,'LLM',false)").param("a",article).param("t",tag).update();
        f.db.sql("INSERT INTO article_classification_jobs(article_id,content_hash,policy_hash,state) VALUES(:a,repeat('a',64),:policy,'PENDING')").param("a",article).param("policy",POLICY).update();assertTrue(news.publicEdition().items().getFirst().tags().isEmpty());
        f.db.sql("UPDATE article_classification_jobs SET state='DONE' WHERE article_id=:a").param("a",article).update();assertEquals(1,news.publicEdition().items().getFirst().tags().size());
        f.db.sql("UPDATE tags SET name='Renamed synthetic topic' WHERE id=:id").param("id",tag).update();assertEquals("Renamed synthetic topic",news.publicEdition().items().getFirst().tags().getFirst().name());
        f.db.sql("UPDATE article_classification_jobs SET policy_hash=repeat('e',64) WHERE article_id=:a").param("a",article).update();assertTrue(news.publicEdition().items().getFirst().tags().isEmpty());
    }
    @Test void rolledBackChangesAndUnrelatedBookkeepingDoNotReplaceCommittedCards(){
        var first=news.publicEdition();UUID article=first.items().getFirst().id();
        new TransactionTemplate(f.tm).executeWithoutResult(s->{f.db.sql("UPDATE articles SET title='Uncommitted synthetic title' WHERE id=:id").param("id",article).update();assertEquals("Uncommitted synthetic title",news.publicEdition().items().getFirst().title());s.setRollbackOnly();});
        assertSame(first.items(),news.publicEdition().items());
        f.db.sql("UPDATE articles SET updated_at=now(),summary_locked=NOT summary_locked WHERE id=:id").param("id",article).update();assertSame(first.items(),news.publicEdition().items());
        f.db.sql("UPDATE sources SET etag='synthetic-bookkeeping',next_run_at=now() WHERE id=:id").param("id",source).update();assertSame(first.items(),news.publicEdition().items());
    }
    @Test void warmReadsStillChargeReturnedItemsAndAccountBoundCursorsRemainProtected(){
        var first=news.first(user,7);var repeat=news.first(user,7);assertSame(first.items(),repeat.items());assertNotNull(first.nextCursor());
        assertEquals(14,f.db.sql("SELECT used FROM api_quotas WHERE user_id=:id AND bucket LIKE 'day:%'").param("id",user).query(Integer.class).single());
        UUID second=UUID.randomUUID();f.db.sql("INSERT INTO users(id,username,password_hash) VALUES(:id,:name,'unused')").param("id",second).param("name","synthetic_"+second.toString().replace("-","")).update();
        var error=assertThrows(ApiError.class,()->news.next(second,first.nextCursor()));assertEquals(400,error.statusCode());
        assertEquals(7,news.next(user,first.nextCursor()).items().size());
    }
    @Test void membershipChangesAndEditionRemovalCannotServeOldPositions(){
        var first=news.publicEdition();UUID article=first.items().getFirst().id();
        f.db.sql("DELETE FROM news_edition_items WHERE edition_id=:id").param("id",first.editionId()).update();assertTrue(news.publicEdition().items().isEmpty());
        f.db.sql("INSERT INTO news_edition_items VALUES(:edition,0,:article)").param("edition",first.editionId()).param("article",article).update();assertEquals(1,news.publicEdition().items().size());
        f.db.sql("DELETE FROM news_editions WHERE id=:id").param("id",first.editionId()).update();assertNotEquals(first.editionId(),news.publicEdition().editionId());
    }
    @Test void candidateCeilingAndFirstRequestPayloadLimitStayUnchanged(){
        addArticles(310);var page=news.publicEdition();assertEquals(300,page.candidateCount());assertEquals(42,page.items().size());
    }
    @Test void unchangedCardsAreReusedAcrossSuccessiveEditionIds(){
        var first=news.publicEdition();
        f.db.sql("UPDATE news_editions SET created_at=now()-interval '16 minutes' WHERE id=:id").param("id",first.editionId()).update();f.reset();
        var next=news.publicEdition();assertNotEquals(first.editionId(),next.editionId());assertSame(first.items(),next.items());assertEquals(0,f.readsContaining("select a.id,a.title,a.author"));
    }
    @Test void equivalentTimestampProjectionDoesNotChangeWithWriterTimezone(){
        UUID article=news.publicEdition().items().getFirst().id();String before=f.db.sql("SELECT fingerprint FROM read_cache_fingerprints WHERE domain='article_card' AND subject=:id").param("id",article).query(String.class).single();
        new TransactionTemplate(f.tm).executeWithoutResult(s->{f.db.sql("SET LOCAL TIME ZONE 'Asia/Seoul'").update();f.db.sql("UPDATE articles SET title=title WHERE id=:id").param("id",article).update();});
        assertEquals(before,f.db.sql("SELECT fingerprint FROM read_cache_fingerprints WHERE domain='article_card' AND subject=:id").param("id",article).query(String.class).single());
    }
    @Test void classificationJobUpdateAndArticleEditHaveNoParentChildLockInversion()throws Exception{
        UUID article=news.publicEdition().items().getFirst().id();
        f.db.sql("INSERT INTO article_classification_jobs(article_id,content_hash,policy_hash,state) VALUES(:id,repeat('a',64),:policy,'DONE')").param("id",article).param("policy",POLICY).update();
        var parentLocked=new java.util.concurrent.CountDownLatch(1);var jobLocked=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var editor=pool.submit(()->new TransactionTemplate(f.tm).executeWithoutResult(s->{
                f.db.sql("SELECT id FROM articles WHERE id=:id FOR UPDATE").param("id",article).query(UUID.class).single();parentLocked.countDown();await(jobLocked);
                f.db.sql("UPDATE articles SET title='Concurrent edited title' WHERE id=:id").param("id",article).update();
            }));
            var classifier=pool.submit(()->new TransactionTemplate(f.tm).executeWithoutResult(s->{
                await(parentLocked);f.db.sql("SELECT article_id FROM article_classification_jobs WHERE article_id=:id FOR UPDATE").param("id",article).query(UUID.class).single();jobLocked.countDown();
                f.db.sql("UPDATE article_classification_jobs SET state='RUNNING' WHERE article_id=:id").param("id",article).update();
            }));
            editor.get(10,java.util.concurrent.TimeUnit.SECONDS);classifier.get(10,java.util.concurrent.TimeUnit.SECONDS);
        }
        assertEquals("Concurrent edited title",news.publicEdition().items().getFirst().title());
        assertEquals("PENDING",f.db.sql("SELECT state FROM article_classification_jobs WHERE article_id=:id").param("id",article).query(String.class).single());
    }
    private static void await(java.util.concurrent.CountDownLatch latch){try{if(!latch.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Synthetic latch timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
}
