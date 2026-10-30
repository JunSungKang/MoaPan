package com.moapan;
import com.moapan.shared.api.ApiError;
import com.moapan.identity.api.AuthController;
import com.moapan.identity.infrastructure.Account;
import com.moapan.identity.infrastructure.Accounts;
import com.moapan.access.infrastructure.Limits;
import com.moapan.catalog.api.AdminController;
import com.moapan.catalog.application.CatalogService;
import com.moapan.catalog.infrastructure.CatalogRepository;
import com.moapan.collection.application.Worker;
import com.moapan.collection.infrastructure.SafeFetch;
import com.moapan.edition.application.NewsService;
import java.util.*;
import java.util.concurrent.*;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.flyway.enabled=true","moapan.secret=synthetic-only-test-secret-32-characters","moapan.public-base-url=http://localhost","server.servlet.session.cookie.secure=false"})
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(SecurityIntegrationTest.FixtureConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SecurityIntegrationTest {
    @org.springframework.boot.test.context.TestConfiguration
    static class FixtureConfig {
        static final java.util.concurrent.atomic.AtomicInteger failureStatus=new java.util.concurrent.atomic.AtomicInteger(429);
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        SafeFetch fixtureFetcher(){return new SafeFetch(){@Override public Result read(Map<String,Object> source){if(source.get("endpoint_url").equals("https://failure.example.org/feed"))throw new FetchFailure(failureStatus.get(),failureStatus.get()==429?"120":null);if(source.get("endpoint_url").equals("https://preview.example.org/feed"))return new Result(List.of(new Item("preview-fixture","Synthetic preview","https://preview.example.org/story","Synthetic preview only, never published or stored by preview.",null)),null,null,false);return super.read(source);}};}
    }
    @Autowired org.springframework.transaction.PlatformTransactionManager tm;
    @Autowired com.moapan.article.application.EditorialService editorial;
    static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>(org.testcontainers.utility.DockerImageName.parse("postgres@sha256:edb0c08dc33befd0262ab3b5cf1c4f3f8cfd8b09e88c05b45adee29d6761ddb6").asCompatibleSubstituteFor("postgres"));
    static { postgres.start(); org.flywaydb.core.Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).cleanDisabled(true).load().migrate(); }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);}
    @AfterAll static void stop(){postgres.stop();}
    @Autowired com.moapan.translation.infrastructure.ArticleTranslations translations;
    @Autowired MockMvc mvc;@Autowired Accounts accounts;@Autowired Limits limits;@Autowired CatalogRepository repo;@Autowired CatalogService catalog;@Autowired Worker worker;@Autowired NewsService news;
    @Autowired org.springframework.session.SessionRepository<?> sessions;
    private static final String PASSWORD="synthetic password 12345";
    private void signup(String user)throws Exception{mvc.perform(post("/api/v1/auth/signup").with(csrf()).contentType("application/json").content("{\"username\":\""+user+"\",\"termsAccepted\":true,\"termsVersion\":\"terms-1\",\"password\":\""+PASSWORD+"\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("USER")).andExpect(jsonPath("$.passwordHash").doesNotExist());}
    private Map<String,Object> claimReady(){
        for(int i=0;i<100;i++){var job=worker.claim();if(job!=null)return job;try{Thread.sleep(20);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
        throw new AssertionError("Queued job not ready within 2 seconds: "+repo.db().sql("SELECT status,available_at,now() db_now FROM content_jobs WHERE status='QUEUED'").query().listOfRows());
    }
    @Test @Order(1) void unauthenticatedAndCsrf()throws Exception{
        mvc.perform(get("/api/v1/private.html")).andExpect(status().isUnauthorized()).andExpect(content().contentType("application/json"));
        mvc.perform(get("/api/v1/news/edition").header("Referer","http://localhost").header("User-Agent","browser")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/signup").contentType("application/json").content("{\"username\":\"badone\",\"password\":\""+PASSWORD+"\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/signup").with(csrf().useInvalidToken()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.token").isString());
    }
    @Test @Order(2) void signupRejectsExtraFieldsAndStoresOnlyHash()throws Exception{
        mvc.perform(post("/api/v1/auth/signup").with(csrf()).contentType("application/json").content("{\"username\":\"injected\",\"termsAccepted\":true,\"termsVersion\":\"terms-1\",\"password\":\""+PASSWORD+"\",\"role\":\"ADMIN\"}")).andExpect(status().isBadRequest());
        signup("synthetic_a");Account a=accounts.findByUsername("synthetic_a").orElseThrow();assertTrue(a.passwordHash.startsWith("$argon2id$"));assertFalse(a.passwordHash.contains(PASSWORD));
    }
    @Test @Order(2) void signupRequiresCurrentExplicitConsent()throws Exception{
        Instant before=Instant.now();
        for(String fields:List.of("",",\"termsAccepted\":false,\"termsVersion\":\"terms-1\"",",\"termsAccepted\":true,\"termsVersion\":\"old\"",",\"termsAccepted\":true")){
            String body="{\"username\":\"consent_test\",\"password\":\""+PASSWORD+"\""+fields+"}";
            mvc.perform(post("/api/v1/auth/signup").with(csrf()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
            assertTrue(accounts.findByUsername("consent_test").isEmpty());
        }
        signup("consent_test");Account a=accounts.findByUsername("consent_test").orElseThrow();
        assertEquals(AuthController.TERMS_VERSION,a.termsVersion);assertFalse(a.termsAcceptedAt.isBefore(before));assertFalse(a.termsAcceptedAt.isAfter(Instant.now()));
        Account legacy=accounts.saveAndFlush(new Account("legacy_consent",a.passwordHash));assertNull(accounts.findById(legacy.id).orElseThrow().termsAcceptedAt);assertNull(legacy.termsVersion);
    }
    @Test @Order(2) void loginWarningsAndRetryAfterFollowStoredExpiry()throws Exception{
        signup("countdown_user");
        String body="{\"username\":\"countdown_user\",\"password\":\"synthetic wrong password\"}";
        String bucket="login:"+limits.fingerprint("countdown_user");
        for(int i=1;i<=10;i++){
            var result=mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content(body));
            if(i<10){result.andExpect(status().isUnauthorized());if(i<5)result.andExpect(jsonPath("$.attemptsRemaining").doesNotExist());else result.andExpect(jsonPath("$.attemptsRemaining").value(10-i));}
            else{var response=result.andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error").value("login_limited")).andReturn().getResponse();int retry=Integer.parseInt(response.getHeader("Retry-After"));assertTrue(retry>850&&retry<=900);}
        }
        repo.db().sql("UPDATE auth_rate_limits SET expires_at=now()+interval '125 seconds' WHERE bucket=:b").param("b",bucket).update();
        Instant expires=repo.db().sql("SELECT expires_at FROM auth_rate_limits WHERE bucket=:b").param("b",bucket).query((rs,n)->rs.getTimestamp(1).toInstant()).single();
        String correct="{\"username\":\"countdown_user\",\"password\":\""+PASSWORD+"\"}";
        var blocked=mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content(correct)).andExpect(status().isTooManyRequests()).andReturn().getResponse();
        int retry=Integer.parseInt(blocked.getHeader("Retry-After"));assertTrue(retry>=120&&retry<=125);
        assertEquals(expires,repo.db().sql("SELECT expires_at FROM auth_rate_limits WHERE bucket=:b").param("b",bucket).query((rs,n)->rs.getTimestamp(1).toInstant()).single());
        repo.db().sql("UPDATE auth_rate_limits SET expires_at=now()-interval '1 second' WHERE bucket=:b").param("b",bucket).update();
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content(correct)).andExpect(status().isOk());
    }
    @Test @Order(2) void accountLengthBoundariesApplyToSignupLoginAndChange()throws Exception{
        for(String invalid:List.of("","u".repeat(129)))mvc.perform(post("/api/v1/auth/signup").with(csrf()).contentType("application/json").content("{\"username\":\""+invalid+"\",\"password\":\"abcdef\",\"termsAccepted\":true,\"termsVersion\":\""+AuthController.TERMS_VERSION+"\"}")).andExpect(status().isBadRequest());
        for(String invalid:List.of("abcde","😀".repeat(5)))mvc.perform(post("/api/v1/auth/signup").with(csrf()).contentType("application/json").content("{\"username\":\"length_bad\",\"password\":\""+invalid+"\",\"termsAccepted\":true,\"termsVersion\":\""+AuthController.TERMS_VERSION+"\"}")).andExpect(status().isBadRequest());
        assertTrue(accounts.findByUsername("length_bad").isEmpty());
        for(String username:List.of("z","u".repeat(128))){
            String password=" aB1! ";
            mvc.perform(post("/api/v1/auth/signup").with(csrf()).contentType("application/json").content("{\"username\":\""+username+"\",\"password\":\""+password+"\",\"termsAccepted\":true,\"termsVersion\":\""+AuthController.TERMS_VERSION+"\"}")).andExpect(status().isOk());
            var first=mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content("{\"username\":\""+username+"\",\"password\":\""+password+"\"}")).andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
            mvc.perform(patch("/api/v1/account/password").cookie(first).with(csrf()).contentType("application/json").content("{\"currentPassword\":\""+password+"\",\"newPassword\":\"abcde\"}")).andExpect(status().isBadRequest());
            String changed="가나다라마바";
            mvc.perform(patch("/api/v1/account/password").cookie(first).with(csrf()).contentType("application/json").content("{\"currentPassword\":\""+password+"\",\"newPassword\":\""+changed+"\"}")).andExpect(status().isOk());
            var second=mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content("{\"username\":\""+username+"\",\"password\":\""+changed+"\"}")).andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
            mvc.perform(delete("/api/v1/account").cookie(second).with(csrf()).contentType("application/json").content("{\"currentPassword\":\""+changed+"\"}")).andExpect(status().isOk());
        }
    }
    private jakarta.servlet.http.Cookie login(String user)throws Exception {
        var initial=mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
        assertNotNull(initial);
        var result=mvc.perform(post("/api/v1/auth/login").cookie(initial).with(csrf()).contentType("application/json").content("{\"username\":\""+user+"\",\"password\":\""+PASSWORD+"\"}")).andExpect(status().isOk()).andReturn();
        var authenticated=result.getResponse().getCookie("SESSION");assertNotNull(authenticated);assertNotEquals(initial.getValue(),authenticated.getValue());return authenticated;
    }
    @Test @Order(3) void loginRotatesSessionAndSanitizesPrincipal()throws Exception{
        var cookie=login("synthetic_a");
        String sessionId=new String(Base64.getDecoder().decode(cookie.getValue()),java.nio.charset.StandardCharsets.UTF_8);
        var session=sessions.findById(sessionId);assertNotNull(session);
        var context=(org.springframework.security.core.context.SecurityContext)session.getAttribute("SPRING_SECURITY_CONTEXT");
        assertNull(context.getAuthentication().getCredentials());assertEquals(accounts.findByUsername("synthetic_a").orElseThrow().id.toString(),context.getAuthentication().getPrincipal());
        mvc.perform(get("/api/v1/account").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/sites").cookie(cookie)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/news/edition?size=-1").cookie(cookie)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/news/edition?limit=10000").cookie(cookie)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/logout").cookie(cookie).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/account").cookie(cookie)).andExpect(status().isUnauthorized());
    }
    @Test @Order(4) void disabledSessionRejected()throws Exception{
        signup("synthetic_disabled");var cookie=login("synthetic_disabled");var user=accounts.findByUsername("synthetic_disabled").orElseThrow();user.active=false;accounts.save(user);mvc.perform(get("/api/v1/account").cookie(cookie)).andExpect(status().isUnauthorized());
    }
    @Test @Order(5) void quotaConcurrencyAndNoReloginReset()throws Exception{
        UUID user=accounts.findByUsername("synthetic_a").orElseThrow().id;
        Limits secondInstance=new Limits(repo.db(),tm,"synthetic-only-test-secret-32-characters");
        List<UUID> leases=new ArrayList<>();for(int i=0;i<3;i++)leases.add(limits.beginNews(user));assertThrows(ApiError.class,()->limits.beginNews(user));leases.forEach(limits::endNews);
        try(var executor=Executors.newFixedThreadPool(8)){List<Future<Boolean>>results=new ArrayList<>();for(int i=0;i<80;i++){Limits instance=i%2==0?limits:secondInstance;results.add(executor.submit(()->{try{instance.items(user,100);return true;}catch(ApiError e){return false;}}));}int ok=0;for(var future:results)if(future.get())ok++;assertEquals(60,ok);}
        assertThrows(ApiError.class,()->limits.items(user,1));assertEquals(6000,repo.db().sql("SELECT used FROM api_quotas WHERE user_id=:u AND bucket LIKE 'day:%'").param("u",user).query(Integer.class).single());
    }
    @Test @Order(6) @WithMockUser(roles="ADMIN") void siteDraftConflictArchiveAndWorkerStaleness(){
        UUID actor=accounts.findByUsername("synthetic_a").orElseThrow().id;
        @SuppressWarnings("unchecked")var created=(Map<String,Object>)catalog.createSite(new CatalogService.SiteInput("Synthetic fixture","https://example.org/","","",null),actor);UUID site=(UUID)created.get("id");
        assertThrows(ApiError.class,()->catalog.state(site,"ACTIVE",actor));catalog.updateSite(site,new CatalogService.SiteInput("Renamed fixture","https://example.org/","","",0L),actor);assertThrows(ApiError.class,()->catalog.updateSite(site,new CatalogService.SiteInput("Conflict","https://example.org/","","",0L),actor));
        UUID source=UUID.randomUUID();repo.db().sql("INSERT INTO sources(id,site_id,label,type,endpoint_url,normalized_endpoint_url,enabled,rights_status,allow_metadata,allow_display,allow_summary,validation_status) VALUES(:id,:site,'Synthetic','RSS','https://example.org/feed','https://example.org/feed',true,'APPROVED',true,true,true,'PASSED')").param("id",source).param("site",site).update();catalog.state(site,"ACTIVE",actor);catalog.collect(source,actor);var job=claimReady();assertNotNull(job);var snapshot=repo.source(source,false);catalog.state(site,"PAUSED",actor);assertThrows(ApiError.class,()->worker.apply(job,snapshot,new SafeFetch.Result(List.of(new SafeFetch.Item("synthetic","Synthetic news title","https://example.org/news","Synthetic only description, not real news.",Instant.now())),null,null,false)));assertEquals(0,repo.db().sql("SELECT count(*) FROM articles WHERE source_id=:id").param("id",source).query(Integer.class).single());
        catalog.state(site,"ARCHIVED",actor);catalog.state(site,"PAUSED",actor);assertEquals(false,repo.source(source,false).get("enabled"));assertEquals("UNREVIEWED",repo.source(source,false).get("rights_status"));
    }
    @Test @Order(7) void databaseHasNoProfileOrTrackingTables(){var names=repo.db().sql("SELECT table_name FROM information_schema.tables WHERE table_schema='public'").query(String.class).list();for(String name:List.of("user_profiles","user_interests","reading_history","click_events","search_history","user_embeddings"))assertFalse(names.contains(name));}
    @Test @Order(8) void signedCursorIsAccountBoundAndHiddenContentIsRechecked()throws Exception {
        signup("synthetic_pages");signup("synthetic_other");UUID user=accounts.findByUsername("synthetic_pages").orElseThrow().id,other=accounts.findByUsername("synthetic_other").orElseThrow().id;
        UUID site=UUID.randomUUID(),source=UUID.randomUUID();
        repo.db().sql("INSERT INTO sites(id,name,homepage_url,normalized_homepage_url,status) VALUES(:id,'Synthetic page fixture','https://pages.example.org/','https://pages.example.org/','ACTIVE')").param("id",site).update();
        repo.db().sql("INSERT INTO sources(id,site_id,label,type,endpoint_url,normalized_endpoint_url,enabled,rights_status,allow_metadata,allow_display,validation_status) VALUES(:id,:site,'Synthetic','RSS','https://pages.example.org/feed','https://pages.example.org/feed',true,'APPROVED',true,true,'PASSED')").param("id",source).param("site",site).update();
        for(int i=0;i<31;i++)repo.db().sql("INSERT INTO articles(id,source_id,source_guid,canonical_url,title,summary_mode,language,input_hash,collected_at) VALUES(:id,:source,:guid,:url,'Synthetic page fixture','INSUFFICIENT','en',:hash,now()-make_interval(secs=>:age))").param("id",UUID.randomUUID()).param("source",source).param("guid","fixture-"+i).param("url","https://pages.example.org/"+i).param("hash","a".repeat(64)).param("age",i).update();
        var first=news.first(user,30);assertEquals(30,first.items().size());assertEquals(31,first.candidateCount());assertNotNull(first.nextCursor());
        assertEquals(1,news.next(user,first.nextCursor()).items().size());
        assertThrows(ApiError.class,()->news.next(other,first.nextCursor()));
        assertThrows(ApiError.class,()->news.next(user,first.nextCursor()+"x"));
        String expired=user+"|"+first.editionId()+"|30|1|30";String signed=Base64.getUrlEncoder().withoutPadding().encodeToString(expired.getBytes(java.nio.charset.StandardCharsets.UTF_8))+"."+limits.fingerprint("cursor:"+expired);
        assertThrows(ApiError.class,()->news.next(user,signed));
        UUID hidden=first.items().getFirst().id();
        repo.db().sql("UPDATE articles SET summary_bullets='[\"Synthetic permitted summary\"]',summary_mode='MANUAL' WHERE id=:id").param("id",hidden).update();
        repo.db().sql("UPDATE sources SET allow_summary=true WHERE id=:id").param("id",source).update();assertFalse(news.first(user,30).items().getFirst().summaryBullets().isEmpty());
        repo.db().sql("UPDATE sources SET allow_summary=false WHERE id=:id").param("id",source).update();assertTrue(news.first(user,30).items().getFirst().summaryBullets().isEmpty());
        repo.db().sql("UPDATE articles SET public=false WHERE id=:id").param("id",hidden).update();var refreshed=news.first(user,30);assertEquals(first.editionId(),refreshed.editionId());assertTrue(refreshed.items().stream().noneMatch(a->a.id().equals(hidden)));assertEquals(30,refreshed.candidateCount());
        repo.db().sql("UPDATE sources SET allow_display=false WHERE id=:id").param("id",source).update();assertEquals(0,news.first(user,30).items().size());
    }
    @Test @Order(8) @WithMockUser(roles="ADMIN") void collectionDoesNotPersistOriginalInputEvenInRevisions(){
        UUID actor=accounts.findByUsername("synthetic_a").orElseThrow().id,site=UUID.randomUUID(),source=UUID.randomUUID();
        repo.db().sql("INSERT INTO sites(id,name,homepage_url,normalized_homepage_url,status) VALUES(:id,'Retention fixture','https://retention.example.org/','https://retention.example.org/','ACTIVE')").param("id",site).update();
        repo.db().sql("INSERT INTO sources(id,site_id,label,type,endpoint_url,normalized_endpoint_url,enabled,rights_status,allow_metadata,allow_display,allow_summary,validation_status) VALUES(:id,:site,'Retention fixture','RSS','https://retention.example.org/feed','https://retention.example.org/feed',true,'APPROVED',true,true,true,'PASSED')").param("id",source).param("site",site).update();
        String marker="SYNTHETIC_ORIGINAL_ONLY_DO_NOT_RETAIN",summary="We propose an efficient retrieval algorithm for database queries.";
        String firstHash=null;
        for(int revision=0;revision<2;revision++){
            String original=summary+" "+marker+" "+revision+". "+"Unselected background material. ".repeat(30);
            catalog.collect(source,actor);var job=claimReady();
            worker.apply(job,repo.source(source,false),new SafeFetch.Result(List.of(new SafeFetch.Item("retention-guid","Synthetic retention fixture","https://retention.example.org/story",original,Instant.parse("2026-10-10T00:00:00Z"))),null,null,false));
            var row=repo.db().sql("SELECT summary_bullets::text AS summary,canonical_url,input_hash FROM articles WHERE source_id=:source").param("source",source).query().singleRow();
            assertEquals("[\""+summary+"\"]",row.get("summary"));assertEquals("https://retention.example.org/story",row.get("canonical_url"));
            String hash=row.get("input_hash").toString();assertEquals(64,hash.length());if(firstHash==null)firstHash=hash;else assertNotEquals(firstHash,hash);
            for(String table:List.of("articles","article_revisions","content_jobs","fetch_runs")){
                assertFalse(repo.db().sql("SELECT EXISTS(SELECT 1 FROM "+table+" r WHERE to_jsonb(r)::text LIKE :marker)").param("marker","%"+marker+"%").query(Boolean.class).single(),table+" retained source-only text");
            }
        }
        assertEquals(1,repo.db().sql("SELECT count(*) FROM article_revisions r JOIN articles a ON a.id=r.article_id WHERE a.source_id=:source").param("source",source).query(Integer.class).single());
        var columns=repo.db().sql("SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name IN ('articles','article_revisions','content_jobs','fetch_runs')").query(String.class).list();
        for(String forbidden:List.of("body","content","description","abstract","raw","raw_body","original_text","payload"))assertFalse(columns.contains(forbidden));
    }

    @Test @Order(8) void translationsRequirePermissionCurrentContentAndRetainOnlyTranslatedCards(){
        UUID site=UUID.randomUUID(),source=UUID.randomUUID(),id=UUID.randomUUID();
        repo.db().sql("INSERT INTO sites(id,name,homepage_url,normalized_homepage_url,status) VALUES(:id,'Translation fixture','https://translation.example.org','https://translation.example.org','ACTIVE')").param("id",site).update();
        repo.db().sql("INSERT INTO sources(id,site_id,label,type,endpoint_url,normalized_endpoint_url,rights_status,allow_display,allow_summary,allow_translation) VALUES(:id,:site,'Translation fixture','RSS','https://translation.example.org/rss','https://translation.example.org/rss','APPROVED',true,true,false)").param("id",source).param("site",site).update();
        repo.db().sql("INSERT INTO articles(id,source_id,source_guid,canonical_url,title,summary_bullets,summary_mode,language,input_hash) VALUES(:id,:source,'translate-fixture','https://translation.example.org/article','Synthetic retrieval','[\"A synthetic retrieval algorithm.\"]','EXTRACTIVE_ORIGINAL_LANGUAGE','en',:hash)").param("id",id).param("source",source).param("hash","e".repeat(64)).update();
        String hash=com.moapan.translation.infrastructure.ArticleTranslations.contentHash("Synthetic retrieval",List.of("A synthetic retrieval algorithm."));
        var text=new com.moapan.translation.infrastructure.ArticleTranslations.Text("합성 검색",List.of("합성 검색 알고리즘입니다."));
        assertFalse(translations.store(id,hash,"ko",text,"synthetic-test"));
        repo.db().sql("UPDATE sources SET allow_translation=true WHERE id=:id").param("id",source).update();
        assertFalse(translations.store(id,"wrong-hash","ko",text,"synthetic-test"));
        assertTrue(translations.store(id,hash,"ko",text,"synthetic-test"));
        repo.db().sql("UPDATE news_editions SET expires_at=now()-interval '1 second'").update();
        var article=news.publicEdition().items().stream().filter(a->a.id().equals(id)).findFirst().orElseThrow();
        assertEquals("Synthetic retrieval",article.title());assertEquals(text,article.translations().get("ko"));
        var searchService=new com.moapan.edition.application.NewsSearch(repo.db(),news,limits,tm);
        assertEquals(id,searchService.search(new com.moapan.edition.application.NewsSearch.Input("합성 검색","",null,0),null).items().getFirst().id());
        repo.db().sql("UPDATE sources SET allow_translation=false WHERE id=:id").param("id",source).update();
        assertTrue(translations.read(List.of(new com.moapan.translation.infrastructure.ArticleTranslations.Original(article.id(),article.title(),article.summaryBullets()))).isEmpty());
        assertEquals(0,searchService.search(new com.moapan.edition.application.NewsSearch.Input("합성 검색","",null,0),null).total());
        repo.db().sql("UPDATE sources SET allow_translation=true WHERE id=:id").param("id",source).update();
        repo.db().sql("UPDATE articles SET title='Changed synthetic retrieval' WHERE id=:id").param("id",id).update();
        assertEquals(0,repo.db().sql("SELECT count(*) FROM article_translations WHERE article_id=:id").param("id",id).query(Integer.class).single());
        assertFalse(translations.store(id,hash,"ko",text,"synthetic-test"));
        var engine=new com.moapan.translation.infrastructure.LocalTranslator("",""){@Override public String translate(String input,String language,int attempt,String reason){return "합성 번역";}};
        var translationWorker=new com.moapan.translation.application.TranslationWorker(repo.db(),engine,translations,tm);
        var first=translationWorker.claim();assertEquals(id,first.get("article_id"));assertNull(translationWorker.claim());
        repo.db().sql("UPDATE article_translation_jobs SET lease_until=now()-interval '1 second' WHERE article_id=:id").param("id",id).update();
        assertTrue(translationWorker.processOne());
        assertTrue(translationWorker.processOne());
        assertEquals(1,repo.db().sql("SELECT count(*) FROM article_translations WHERE article_id=:id").param("id",id).query(Integer.class).single());
        assertNull(translationWorker.claim());
        repo.db().sql("UPDATE articles SET title='Another synthetic change' WHERE id=:id").param("id",id).update();
        assertEquals(0,repo.db().sql("SELECT count(*) FROM article_translation_jobs WHERE article_id=:id").param("id",id).query(Integer.class).single());
        var failing=new com.moapan.translation.infrastructure.LocalTranslator("",""){@Override public String translate(String input,String language,int attempt,String reason){throw new IllegalStateException("synthetic failure");}};
        var retryWorker=new com.moapan.translation.application.TranslationWorker(repo.db(),failing,translations,tm);
        for(int attempt=0;attempt<3;attempt++){assertTrue(retryWorker.processOne());repo.db().sql("UPDATE article_translation_jobs SET available_at=now() WHERE article_id=:id").param("id",id).update();}
        assertFalse(retryWorker.processOne());
        assertEquals("translation_invalid",repo.db().sql("SELECT error_code FROM article_translation_jobs WHERE article_id=:id").param("id",id).query(String.class).single());
        assertEquals("ORIGINAL",repo.db().sql("SELECT state FROM article_translation_jobs WHERE article_id=:id").param("id",id).query(String.class).single());
        repo.db().sql("UPDATE articles SET title='Infrastructure fixture' WHERE id=:id").param("id",id).update();
        var unavailable=new com.moapan.translation.infrastructure.LocalTranslator("",""){
            @Override public String translate(String input,String language,int attempt,String reason){throw new com.moapan.translation.domain.TranslationFailure("translation_transport",true);}
            @Override public boolean healthy(){return false;}
        };
        var infrastructureWorker=new com.moapan.translation.application.TranslationWorker(repo.db(),unavailable,translations,tm);
        for(int i=0;i<4;i++){assertTrue(infrastructureWorker.processOne());repo.db().sql("UPDATE article_translation_jobs SET available_at=now() WHERE article_id=:id").param("id",id).update();}
        assertEquals("RETRY",repo.db().sql("SELECT state FROM article_translation_jobs WHERE article_id=:id").param("id",id).query(String.class).single());
        assertEquals("{}",repo.db().sql("SELECT field_attempts::text FROM article_translation_jobs WHERE article_id=:id").param("id",id).query(String.class).single());
        assertEquals(4,repo.db().sql("SELECT infra_failures FROM article_translation_jobs WHERE article_id=:id").param("id",id).query(Integer.class).single());
        assertFalse(infrastructureWorker.runtimeReady());
        var recovered=new com.moapan.translation.application.TranslationWorker(repo.db(),engine,translations,tm);
        var leased=recovered.claim();assertNotNull(leased);
        assertTrue(recovered.heartbeat(id,(UUID)leased.get("token")));
        assertFalse(recovered.heartbeat(id,UUID.randomUUID()));
        repo.db().sql("UPDATE sources SET allow_translation=false WHERE id=:id").param("id",source).update();
        assertNull(recovered.claim());
        assertEquals("CANCELLED",repo.db().sql("SELECT state FROM article_translation_jobs WHERE article_id=:id").param("id",id).query(String.class).single());
        repo.db().sql("UPDATE sources SET allow_translation=true WHERE id=:id").param("id",source).update();
        assertTrue(recovered.processOne());assertTrue(recovered.processOne());
        assertEquals("DONE",repo.db().sql("SELECT state FROM article_translation_jobs WHERE article_id=:id").param("id",id).query(String.class).single());
        repo.db().sql("UPDATE articles SET public=false WHERE id=:id").param("id",id).update();
        repo.db().sql("UPDATE news_editions SET expires_at=now()-interval '1 second'").update();
    }

    @Test @Order(9) @WithMockUser(roles="ADMIN") void duplicateFetchAndChangedInputPreserveManualLocks() {
        UUID actor=accounts.findByUsername("synthetic_a").orElseThrow().id,site=UUID.randomUUID(),source=UUID.randomUUID();
        repo.db().sql("INSERT INTO sites(id,name,homepage_url,normalized_homepage_url,status) VALUES(:id,'Worker synthetic','https://worker.example.org/','https://worker.example.org/','ACTIVE')").param("id",site).update();
        repo.db().sql("INSERT INTO sources(id,site_id,label,type,endpoint_url,normalized_endpoint_url,enabled,rights_status,allow_metadata,allow_display,allow_summary,validation_status) VALUES(:id,:site,'Synthetic','RSS','https://worker.example.org/feed','https://worker.example.org/feed',true,'APPROVED',true,true,true,'PASSED')").param("id",source).param("site",site).update();
        var item=new SafeFetch.Item("synthetic-guid","AI synthetic fixture","https://worker.example.org/story","AI is discussed in this entirely synthetic fixture sentence.",Instant.now());
        for(int i=0;i<2;i++){catalog.collect(source,actor);var job=claimReady();worker.apply(job,repo.source(source,false),new SafeFetch.Result(List.of(item),null,null,false));}
        assertEquals(1,repo.db().sql("SELECT count(*) FROM articles WHERE source_id=:id").param("id",source).query(Integer.class).single());
        UUID article=repo.db().sql("SELECT id FROM articles WHERE source_id=:id").param("id",source).query(UUID.class).single();repo.db().sql("UPDATE articles SET summary_locked=true,tags_locked=true,summary_bullets='[\"Manual synthetic summary\"]' WHERE id=:id").param("id",article).update();
        catalog.collect(source,actor);worker.apply(claimReady(),repo.source(source,false),new SafeFetch.Result(List.of(new SafeFetch.Item(item.guid(),"Changed synthetic title",item.url(),"Completely changed synthetic description for regression verification.",null)),null,null,false));
        assertEquals("[\"Manual synthetic summary\"]",repo.db().sql("SELECT summary_bullets::text FROM articles WHERE id=:id").param("id",article).query(String.class).single());assertEquals(1,repo.db().sql("SELECT count(*) FROM article_revisions WHERE article_id=:id").param("id",article).query(Integer.class).single());
        catalog.collect(source,actor);var lease=claimReady();repo.db().sql("INSERT INTO fetch_runs(id,source_id,result) VALUES(gen_random_uuid(),:source,'RUNNING')").param("source",source).update();repo.db().sql("UPDATE content_jobs SET lease_until=now()-interval '1 second' WHERE id=:id").param("id",lease.get("id")).update();worker.recover();assertEquals(1,repo.db().sql("SELECT count(*) FROM fetch_runs WHERE source_id=:id AND error_code='worker_lease_expired' AND finished_at IS NOT NULL").param("id",source).query(Integer.class).single());assertEquals("QUEUED",repo.db().sql("SELECT status FROM content_jobs WHERE id=:id").param("id",lease.get("id")).query(String.class).single());
    }

    @Test @Order(10) void passwordChangeAndDeletionInvalidateActualJdbcSessions()throws Exception {
        signup("synthetic_lifecycle");var first=login("synthetic_lifecycle");var second=login("synthetic_lifecycle");
        mvc.perform(patch("/api/v1/account/password").cookie(first).with(csrf()).contentType("application/json").content("{\"currentPassword\":\""+PASSWORD+"\",\"newPassword\":\"changed synthetic password 12345\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/account").cookie(first)).andExpect(status().isUnauthorized());mvc.perform(get("/api/v1/account").cookie(second)).andExpect(status().isUnauthorized());
        var result=mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content("{\"username\":\"synthetic_lifecycle\",\"password\":\"changed synthetic password 12345\"}")).andExpect(status().isOk()).andReturn();var cookie=result.getResponse().getCookie("SESSION");assertNotNull(cookie);
        mvc.perform(delete("/api/v1/account").cookie(cookie).with(csrf()).contentType("application/json").content("{\"currentPassword\":\"changed synthetic password 12345\"}")).andExpect(status().isOk());assertTrue(accounts.findByUsername("synthetic_lifecycle").isEmpty());mvc.perform(get("/api/v1/account").cookie(cookie)).andExpect(status().isUnauthorized());
    }
    @Test @Order(10) void supplementaryUnicodePasswordUsesCodePointLimit()throws Exception {
        signup("synthetic_unicode");var cookie=login("synthetic_unicode");
        for(String invalid:List.of("😀".repeat(5),"😀".repeat(129))) {
            mvc.perform(patch("/api/v1/account/password").cookie(cookie).with(csrf()).contentType("application/json").content("{\"currentPassword\":\""+PASSWORD+"\",\"newPassword\":\""+invalid+"\"}")).andExpect(status().isBadRequest());
        }
        String changed="😀".repeat(128);
        mvc.perform(patch("/api/v1/account/password").cookie(cookie).with(csrf()).contentType("application/json").content("{\"currentPassword\":\""+PASSWORD+"\",\"newPassword\":\""+changed+"\"}")).andExpect(status().isOk());
        var result=mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content("{\"username\":\"synthetic_unicode\",\"password\":\""+changed+"\"}")).andExpect(status().isOk()).andReturn();
        mvc.perform(delete("/api/v1/account").cookie(result.getResponse().getCookie("SESSION")).with(csrf()).contentType("application/json").content("{\"currentPassword\":\""+changed+"\"}")).andExpect(status().isOk());
    }
    @Test @Order(11) void administratorCanCreateDraftSourceWithSafeDefaults()throws Exception {
        signup("synthetic_admin");var a=accounts.findByUsername("synthetic_admin").orElseThrow();a.role="ADMIN";accounts.save(a);var cookie=login("synthetic_admin");
        var created=mvc.perform(post("/api/v1/admin/sites").cookie(cookie).with(csrf()).contentType("application/json").content("{\"name\":\"HTTP synthetic site\",\"homepageUrl\":\"https://http.example.org/\"}")).andExpect(status().isOk()).andReturn();
        String id=tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.getResponse().getContentAsString()).path("id").asString();
        mvc.perform(post("/api/v1/admin/sites/"+id+"/sources").cookie(cookie).with(csrf()).contentType("application/json").content("{\"label\":\"Draft HTTP source\",\"type\":\"RSS\",\"endpointUrl\":\"https://http.example.org/feed\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.rights_status").value("UNREVIEWED")).andExpect(jsonPath("$.validation_status").value("NOT_TESTED"));
        mvc.perform(post("/api/v1/admin/sites/"+id+"/activate").cookie(cookie).with(csrf())).andExpect(status().isConflict());
    }

    @Test @Order(12) void idleAndAbsoluteExpiryAreEnforced()throws Exception {
        signup("synthetic_expiry");var absolute=login("synthetic_expiry");String id=new String(Base64.getDecoder().decode(absolute.getValue()),java.nio.charset.StandardCharsets.UTF_8);
        repo.db().sql("UPDATE spring_session SET creation_time=:created WHERE session_id=:id").param("created",System.currentTimeMillis()-43_200_001L).param("id",id).update();mvc.perform(get("/api/v1/account").cookie(absolute)).andExpect(status().isUnauthorized());
        var idle=login("synthetic_expiry");String idleId=new String(Base64.getDecoder().decode(idle.getValue()),java.nio.charset.StandardCharsets.UTF_8);repo.db().sql("UPDATE spring_session SET creation_time=:access,last_access_time=:access,expiry_time=:expiry WHERE session_id=:id").param("access",System.currentTimeMillis()-1_801_000L).param("expiry",System.currentTimeMillis()-1_000L).param("id",idleId).update();mvc.perform(get("/api/v1/account").cookie(idle)).andExpect(status().isUnauthorized());
    }
    @Test @Order(13) @WithMockUser(roles="ADMIN") void previewDoesNotPublishAndEndpointChangeResetsApproval(){
        UUID actor=accounts.findByUsername("synthetic_a").orElseThrow().id;
        @SuppressWarnings("unchecked")var site=(Map<String,Object>)catalog.createSite(new CatalogService.SiteInput("Preview fixture","https://preview.example.org/","","",null),actor);
        var draft=new CatalogService.SourceInput("Preview","RSS","https://preview.example.org/feed","en",900,false,"UNREVIEWED",false,false,false,false,false,false,false,false,null,null,"",Map.of(),null,null,List.of(),null);
        @SuppressWarnings("unchecked")var source=(Map<String,Object>)catalog.createSource((UUID)site.get("id"),draft,actor);UUID id=(UUID)source.get("id");assertThrows(ApiError.class,()->catalog.preview(id,false,actor));
        var approved=new CatalogService.SourceInput("Preview","RSS","https://preview.example.org/feed","en",900,false,"RESTRICTED",true,true,true,false,false,false,false,false,"https://preview.example.org/terms",java.time.LocalDate.of(2026,10,28),"Synthetic terms fixture",Map.of(),null,null,List.of(),0L);
        catalog.updateSource(id,approved,actor);catalog.preview(id,false,actor);assertEquals("NOT_TESTED",repo.source(id,false).get("validation_status"));catalog.preview(id,true,actor);assertEquals("PASSED",repo.source(id,false).get("validation_status"));assertEquals(0,repo.db().sql("SELECT count(*) FROM articles WHERE source_id=:id").param("id",id).query(Integer.class).single());assertEquals(0,repo.db().sql("SELECT count(*) FROM content_jobs WHERE source_id=:id").param("id",id).query(Integer.class).single());
        var changed=new CatalogService.SourceInput("Preview","RSS","https://changed.example.org/feed","en",900,false,"RESTRICTED",true,true,true,false,false,false,false,false,"https://preview.example.org/terms",java.time.LocalDate.of(2026,10,28),"Synthetic",Map.of(),null,null,List.of(),1L);catalog.updateSource(id,changed,actor);assertEquals("UNREVIEWED",repo.source(id,false).get("rights_status"));assertEquals("NOT_TESTED",repo.source(id,false).get("validation_status"));assertEquals(false,repo.source(id,false).get("enabled"));
    }
    @Test @Order(14) @WithMockUser(roles="ADMIN") void tagEditingAndCanonicalMergePreserveLockedLinks(){
        UUID actor=accounts.findByUsername("synthetic_a").orElseThrow().id;
        @SuppressWarnings("unchecked")var created=(Map<String,Object>)editorial.tag(new com.moapan.article.application.EditorialService.TagInput("ai-unified","Synthetic AI","기술"),actor);UUID target=(UUID)created.get("id"),old=UUID.fromString("00000000-0000-0000-0000-000000000001");editorial.updateTag(target,new com.moapan.article.application.EditorialService.TagInput("ai-unified","수정 합성 태그","기술"),actor);editorial.merge(old,target,actor);assertEquals(target,repo.db().sql("SELECT canonical_id FROM tag_redirects WHERE old_id=:id").param("id",old).query(UUID.class).single());assertEquals(0,repo.db().sql("SELECT count(*) FROM article_tags WHERE tag_id=:id").param("id",old).query(Integer.class).single());assertEquals(target,repo.db().sql("SELECT tag_id FROM tag_aliases WHERE alias='ai'").query(UUID.class).single());
    }

    @Test @Order(15) @WithMockUser(roles="ADMIN") void retryAfterFailureAndTtlCleanupAreEnforced(){
        UUID actor=accounts.findByUsername("synthetic_a").orElseThrow().id,site=UUID.randomUUID(),source=UUID.randomUUID();
        repo.db().sql("UPDATE content_jobs SET status='CANCELLED',finished_at=now() WHERE status IN ('QUEUED','RUNNING')").update();
        repo.db().sql("INSERT INTO sites(id,name,homepage_url,normalized_homepage_url,status) VALUES(:id,'Failure fixture','https://failure.example.org/','https://failure.example.org/','ACTIVE')").param("id",site).update();
        repo.db().sql("INSERT INTO sources(id,site_id,label,type,endpoint_url,normalized_endpoint_url,enabled,rights_status,allow_metadata,allow_display,validation_status) VALUES(:id,:site,'Failure fixture','RSS','https://failure.example.org/feed','https://failure.example.org/feed',true,'APPROVED',true,true,'PASSED')").param("id",source).param("site",site).update();
        var accepted=catalog.collect(source,actor);assertEquals(accepted,catalog.collect(source,actor));worker.processOne();
        assertEquals("QUEUED",repo.db().sql("SELECT status FROM content_jobs WHERE source_id=:id AND status='QUEUED'").param("id",source).query(String.class).single());
        assertTrue(repo.db().sql("SELECT retry_after>now()+interval '110 seconds' FROM sources WHERE id=:id").param("id",source).query(Boolean.class).single());assertNull(worker.claim());
        repo.db().sql("UPDATE content_jobs SET attempts=2,available_at=now() WHERE source_id=:id").param("id",source).update();worker.processOne();assertEquals("FAILED",repo.db().sql("SELECT status FROM content_jobs WHERE source_id=:id").param("id",source).query(String.class).single());
        for(int failure:List.of(503,0)){
            FixtureConfig.failureStatus.set(failure);repo.db().sql("UPDATE sources SET retry_after=NULL WHERE id=:id").param("id",source).update();catalog.collect(source,actor);worker.processOne();
            assertTrue(repo.db().sql("SELECT retry_after>now()+interval '50 seconds' FROM sources WHERE id=:id").param("id",source).query(Boolean.class).single());
            repo.db().sql("UPDATE content_jobs SET status='CANCELLED',finished_at=now() WHERE source_id=:id AND status='QUEUED'").param("id",source).update();repo.db().sql("UPDATE sources SET retry_after=NULL WHERE id=:id").param("id",source).update();
        }
        FixtureConfig.failureStatus.set(429);assertThrows(ApiError.class,()->catalog.preview(source,true,actor));assertEquals("FAILED",repo.source(source,false).get("validation_status"));
        repo.db().sql("INSERT INTO auth_rate_limits(bucket,window_start,used,expires_at) VALUES('synthetic-expired',now()-interval '1 day',1,now()-interval '1 second')").update();worker.cleanup();assertEquals(0,repo.db().sql("SELECT count(*) FROM auth_rate_limits WHERE bucket='synthetic-expired'").query(Integer.class).single());
    }


    @Test @Order(80) void publicEditionIsBoundedAndProtectedPagesStayPrivate()throws Exception{
        for(String page:List.of("index","settings","account","admin"))mvc.perform(get("/"+page+".html")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login.html"));
        mvc.perform(get("/api/v1/account")).andExpect(status().isUnauthorized());
        UUID site=UUID.randomUUID(),source=UUID.randomUUID();
        repo.db().sql("INSERT INTO sites(id,name,homepage_url,normalized_homepage_url,status) VALUES(:id,'Public synthetic','https://public.example.org/','https://public.example.org/','ACTIVE')").param("id",site).update();
        repo.db().sql("INSERT INTO sources(id,site_id,label,type,endpoint_url,normalized_endpoint_url,rights_status,allow_display) VALUES(:id,:site,'Public synthetic','RSS','https://public.example.org/feed','https://public.example.org/feed','APPROVED',true)").param("id",source).param("site",site).update();
        for(int i=0;i<55;i++)repo.db().sql("INSERT INTO articles(id,source_id,source_guid,canonical_url,title,summary_mode,language,input_hash,collected_at) VALUES(:id,:source,:guid,:url,'Public synthetic','INSUFFICIENT','en',:hash,now())").param("id",UUID.randomUUID()).param("source",source).param("guid","public-"+i).param("url","https://public.example.org/"+i).param("hash","b".repeat(64)).update();
        repo.db().sql("UPDATE news_editions SET expires_at=now()-interval '1 second'").update();
        mvc.perform(get("/api/v1/public/news/edition")).andExpect(status().isUnauthorized());signup("synthetic_gate");var gateCookie=login("synthetic_gate");String response=mvc.perform(get("/api/v1/public/news/edition").cookie(gateCookie)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.items.length()").value(42)).andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("admin_note"));assertFalse(response.contains("password"));
        var page=news.publicEdition();UUID hidden=page.items().getFirst().id();repo.db().sql("UPDATE articles SET public=false WHERE id=:id").param("id",hidden).update();assertTrue(news.publicEdition().items().stream().noneMatch(a->a.id().equals(hidden)));
        for(String query:List.of("?size=300","?cursor=x","?limit=-1"))mvc.perform(get("/api/v1/public/news/edition"+query).cookie(gateCookie)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/public/news/page?cursor=x")).andExpect(status().isUnauthorized());
        UUID gateUser=accounts.findByUsername("synthetic_gate").orElseThrow().id;repo.db().sql("UPDATE api_quotas SET used=60 WHERE user_id=:u AND bucket LIKE 'minute:%'").param("u",gateUser).update();
        mvc.perform(get("/api/v1/public/news/edition").cookie(gateCookie)).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        repo.db().sql("DELETE FROM auth_rate_limits WHERE bucket='public_news'").update();
    }
    @Test @Order(80) void serverSearchIsBoundedLiteralPrivateAndRightsAware()throws Exception{
        UUID site=UUID.randomUUID(),source=UUID.randomUUID();
        repo.db().sql("INSERT INTO sites(id,name,homepage_url,normalized_homepage_url,status) VALUES(:id,'Search fixture','https://search.example.org','https://search.example.org','ACTIVE')").param("id",site).update();
        repo.db().sql("INSERT INTO sources(id,site_id,label,type,endpoint_url,normalized_endpoint_url,rights_status,allow_display,allow_summary) VALUES(:id,:site,'Search fixture','RSS','https://search.example.org/rss','https://search.example.org/rss','APPROVED',true,true)").param("id",source).param("site",site).update();
        for(int i=0;i<150;i++)repo.db().sql("INSERT INTO articles(id,source_id,source_guid,canonical_url,title,summary_bullets,summary_mode,language,input_hash,collected_at) VALUES(:id,:source,:guid,:url,:title,'[\"synthetic-summary-only-query\"]','EXTRACTIVE_ORIGINAL_LANGUAGE','en',:hash,now()-interval '10 days'-make_interval(secs=>:age))").param("id",UUID.randomUUID()).param("source",source).param("guid","search-"+i).param("url","https://search.example.org/"+i).param("title","synthetic-search-fixture-"+i).param("hash","c".repeat(64)).param("age",i).update();
        signup("synthetic_search_gate");var searchCookie=login("synthetic_search_gate");String endpoint="/api/v1/public/news/search",body="{\"search\":\"synthetic-search-fixture\",\"category\":\"\",\"day\":null,\"page\":0}";
        mvc.perform(post(endpoint).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/news/search").with(csrf().asHeader()).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(endpoint).with(csrf().asHeader()).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(endpoint).cookie(searchCookie).with(csrf().asHeader()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.items.length()").value(7)).andExpect(jsonPath("$.total").value(140)).andExpect(jsonPath("$.pages").value(20)).andExpect(jsonPath("$.items[0].title").value("synthetic-search-fixture-0"));
        mvc.perform(post(endpoint).cookie(searchCookie).with(csrf().asHeader()).contentType("application/json").content(body.replace("\"page\":0","\"page\":19"))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].title").value("synthetic-search-fixture-133"));
        for(int page:List.of(-1,20,1000))mvc.perform(post(endpoint).cookie(searchCookie).with(csrf().asHeader()).contentType("application/json").content(body.replace("\"page\":0","\"page\":"+page))).andExpect(status().isBadRequest());
        mvc.perform(post(endpoint+"?offset=7").cookie(searchCookie).with(csrf().asHeader()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        mvc.perform(post(endpoint).cookie(searchCookie).with(csrf().asHeader()).contentType("application/json").content(body.replace("synthetic-search-fixture","%_"))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(post(endpoint).cookie(searchCookie).with(csrf().asHeader()).contentType("application/json").content(body.replace("\"page\":0","\"page\":0,\"limit\":999"))).andExpect(status().isBadRequest());
        mvc.perform(post(endpoint).cookie(searchCookie).with(csrf().asHeader()).contentType("application/json").content(body.replace("synthetic-search-fixture","x".repeat(101)))).andExpect(status().isBadRequest());
        repo.db().sql("UPDATE sources SET allow_summary=false WHERE id=:id").param("id",source).update();
        mvc.perform(post(endpoint).cookie(searchCookie).with(csrf().asHeader()).contentType("application/json").content(body.replace("synthetic-search-fixture","synthetic-summary-only-query"))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(post(endpoint).cookie(searchCookie).with(csrf().asHeader()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].summaryBullets.length()").value(0));
        repo.db().sql("UPDATE sources SET allow_display=false WHERE id=:id").param("id",source).update();
        mvc.perform(post(endpoint).cookie(searchCookie).with(csrf().asHeader()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        assertEquals(0,repo.db().sql("SELECT count(*) FROM auth_rate_limits WHERE bucket LIKE '%synthetic-search%'").query(Integer.class).single());
        repo.db().sql("UPDATE auth_rate_limits SET used=120 WHERE bucket='news_search'").update();
        mvc.perform(post(endpoint).cookie(searchCookie).with(csrf().asHeader()).contentType("application/json").content(body)).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        repo.db().sql("DELETE FROM auth_rate_limits WHERE bucket='news_search'").update();
    }

    @Test @Order(81) void representativeInterestsRequireAdminCsrfAndCurrentVersion()throws Exception{
        signup("representative_owner");var owner=accounts.findByUsername("representative_owner").orElseThrow();var userCookie=login(owner.username);
        String tag="00000000-0000-0000-0000-000000000004";
        String initial=mvc.perform(get("/api/v1/public/interests")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long version=tools.jackson.databind.json.JsonMapper.builder().build().readTree(initial).get("version").asLong();
        String body="{\"version\":"+version+",\"weights\":{\""+tag+"\":5}}";
        mvc.perform(put("/api/v1/admin/interests").with(csrf()).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/admin/interests").cookie(userCookie).with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(get("/admin.html").cookie(userCookie)).andExpect(redirectedUrl("/index.html"));
        owner.role="ADMIN";accounts.saveAndFlush(owner);var admin=login(owner.username);
        mvc.perform(put("/api/v1/admin/interests").cookie(admin).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/interests").cookie(admin).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(version+1));
        mvc.perform(put("/api/v1/admin/interests").cookie(admin).with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/public/interests")).andExpect(jsonPath("$.weights['"+tag+"']").value(5));
        for(String weights:List.of("{}","{\""+tag+"\":6}","{\""+UUID.randomUUID()+"\":3}"))mvc.perform(put("/api/v1/admin/interests").cookie(admin).with(csrf()).contentType("application/json").content("{\"version\":"+(version+1)+",\"weights\":"+weights+"}")).andExpect(status().isBadRequest());
        assertEquals(1,repo.db().sql("SELECT count(*) FROM admin_actions WHERE action='representative_interests' AND admin_id=:id").param("id",owner.id).query(Integer.class).single());
        mvc.perform(get("/settings.html").cookie(admin)).andExpect(status().isOk());
    }

    @Test @Order(99) void databaseOutageDoesNotBypassSessionsOrQuota()throws Exception {
        var cookie=login("synthetic_admin");postgres.stop();
        mvc.perform(get("/api/v1/news/edition").cookie(cookie)).andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After","60")).andExpect(jsonPath("$.error").value("temporarily_unavailable"));
    }

    @Test @Order(98) void securityCounterCapacityIsBounded(){
        repo.db().sql("INSERT INTO auth_rate_limits(bucket,window_start,used,expires_at) SELECT 'capacity-fixture-'||n,now(),1,now()+interval '15 minutes' FROM generate_series(1,10000)n").update();
        try{assertEquals(503,assertThrows(ApiError.class,()->limits.auth("new-capacity-fixture",1,60)).statusCode());}
        finally{repo.db().sql("DELETE FROM auth_rate_limits WHERE bucket LIKE 'capacity-fixture-%'").update();}
    }

    @Test @Order(30) @WithMockUser(roles="ADMIN") void collectionScopeIsEnforcedBeforeDatabaseWrite(){
        UUID actor=UUID.randomUUID(),site=UUID.randomUUID(),source=UUID.randomUUID();
        repo.db().sql("INSERT INTO sites(id,name,homepage_url,normalized_homepage_url,status) VALUES(:id,'Scope fixture','https://scope.example.org/','https://scope.example.org/','ACTIVE')").param("id",site).update();
        repo.db().sql("INSERT INTO sources(id,site_id,label,type,endpoint_url,normalized_endpoint_url,enabled,rights_status,allow_metadata,allow_display,validation_status,published_since,topic_terms) VALUES(:id,:site,'Scope','ATOM','https://scope.example.org/feed','https://scope.example.org/feed',true,'APPROVED',true,true,'PASSED','2026-10-10','[\"RAG\"]')").param("id",source).param("site",site).update();
        catalog.collect(source,actor);var job=claimReady();
        var result=worker.apply(job,repo.source(source,false),new SafeFetch.Result(List.of(
            new SafeFetch.Item("ok","RAG fixture","https://scope.example.org/ok","",Instant.parse("2026-10-10T00:00:00Z")),
            new SafeFetch.Item("old","RAG old fixture","https://scope.example.org/old","",Instant.parse("2026-10-09T00:00:00Z")),
            new SafeFetch.Item("unknown","RAG undated fixture","https://scope.example.org/unknown","",null),
            new SafeFetch.Item("wrong","Astronomy fixture","https://scope.example.org/wrong","",Instant.parse("2026-10-10T00:00:00Z"))
        ),null,null,false));assertEquals(1,result.created());assertEquals(1,repo.db().sql("SELECT count(*) FROM articles WHERE source_id=:id").param("id",source).query(Integer.class).single());
    }

    @Autowired com.moapan.collection.infrastructure.ArxivGate arxivGate;
    @Test @Order(31) void arxivAdmissionIsSharedAndCoolsDown(){
        repo.db().sql("UPDATE source_access_gate SET next_at=now()-interval '1 second'").update();
        try(var lease=arxivGate.enter()){assertThrows(SafeFetch.FetchFailure.class,()->arxivGate.enter());}
        assertThrows(SafeFetch.FetchFailure.class,()->arxivGate.enter());
        repo.db().sql("UPDATE source_access_gate SET next_at=now()-interval '1 second'").update();
        try(var lease=arxivGate.enter()){assertNotNull(lease);}
    }


    @Test @Order(32) void olderFeedExcerptsRefreshWithoutOverwritingManualSummary(){
        UUID source=repo.db().sql("SELECT id FROM sources WHERE allow_summary LIMIT 1").query(UUID.class).single();
        UUID id=UUID.randomUUID(),locked=UUID.randomUUID();
        for(UUID article:List.of(id,locked))repo.db().sql("INSERT INTO articles(id,source_id,source_guid,canonical_url,title,summary_bullets,summary_mode,language,input_hash,collected_at,summary_locked) VALUES(:id,:source,:guid,:url,'Synthetic old research','[\"Subscribe to the latest newsletter for all updates.\",\"We propose an algorithm for efficient retrieval.\"]','EXTRACTIVE_ORIGINAL_LANGUAGE','en',:hash,'1970-01-01',:locked)")
            .param("id",article).param("source",source).param("guid",article.toString()).param("url","https://example.org/"+article).param("hash","c".repeat(64)).param("locked",article.equals(locked)).update();
        worker.refreshStoredSummary();
        var row=repo.db().sql("SELECT summary_bullets::text bullets,processing_version FROM articles WHERE id=:id").param("id",id).query().singleRow();
        assertEquals("research-v2-stored",row.get("processing_version"));assertFalse(row.get("bullets").toString().contains("newsletter"));assertTrue(row.get("bullets").toString().contains("algorithm"));
        assertEquals("extractive-v1",repo.db().sql("SELECT processing_version FROM articles WHERE id=:id").param("id",locked).query(String.class).single());
    }

    @Test @Order(30) @WithMockUser(roles="ADMIN") void editorialSummaryOnlyPreservesTagsAndExplicitEmptyRemovesThem(){
        UUID actor=accounts.findByUsername("synthetic_a").orElseThrow().id;
        UUID article=repo.db().sql("SELECT id FROM articles LIMIT 1").query(UUID.class).single();
        UUID tag=repo.db().sql("SELECT id FROM tags WHERE active LIMIT 1").query(UUID.class).single();
        repo.db().sql("DELETE FROM article_tags WHERE article_id=:id").param("id",article).update();
        repo.db().sql("INSERT INTO article_tags VALUES(:id,:tag,0.8,'RULE',false)").param("id",article).param("tag",tag).update();
        editorial.edit(article,new com.moapan.article.application.EditorialService.Edit(List.of("Synthetic edited summary"),true,false,true,null),actor);
        assertEquals(tag,repo.db().sql("SELECT tag_id FROM article_tags WHERE article_id=:id").param("id",article).query(UUID.class).single());
        assertEquals("RULE",repo.db().sql("SELECT method FROM article_tags WHERE article_id=:id").param("id",article).query(String.class).single());
        editorial.edit(article,new com.moapan.article.application.EditorialService.Edit(List.of("Synthetic edited summary"),true,false,true,List.of()),actor);
        assertEquals(0,repo.db().sql("SELECT count(*) FROM article_tags WHERE article_id=:id").param("id",article).query(Integer.class).single());
    }
    @Test @Order(14) void rememberedBrowserSessionEndsOnLogoutAndOtherBrowserSurvives()throws Exception{
        signup("synthetic_remember");var normal=login("synthetic_remember");assertEquals(-1,normal.getMaxAge());
        var initial=mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse().getCookie("SESSION");
        var remembered=mvc.perform(post("/api/v1/auth/login").cookie(initial).with(csrf()).contentType("application/json").content("{\"username\":\"synthetic_remember\",\"password\":\""+PASSWORD+"\",\"rememberMe\":true}")).andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
        assertNotEquals(initial.getValue(),remembered.getValue());assertTrue(remembered.getMaxAge()>2500000);assertTrue(remembered.isHttpOnly());assertEquals("Strict",remembered.getAttribute("SameSite"));
        mvc.perform(get("/api/v1/account").cookie(remembered)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/logout").cookie(remembered).with(csrf())).andExpect(status().isOk()).andExpect(cookie().maxAge("SESSION",0));
        mvc.perform(get("/api/v1/account").cookie(remembered)).andExpect(status().isUnauthorized());mvc.perform(get("/index.html").cookie(remembered)).andExpect(redirectedUrl("/login.html"));
        mvc.perform(get("/api/v1/account").cookie(normal)).andExpect(status().isOk());
    }
    @Test @Order(14) @SuppressWarnings({"rawtypes","unchecked"}) void rememberedSessionHasAbsoluteExpiryAndPasswordChangeRevokesIt()throws Exception{
        signup("synthetic_remember_expiry");
        var cookie=mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content("{\"username\":\"synthetic_remember_expiry\",\"password\":\""+PASSWORD+"\",\"rememberMe\":true}")).andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
        String id=new String(Base64.getDecoder().decode(cookie.getValue()),java.nio.charset.StandardCharsets.UTF_8);var saved=sessions.findById(id);assertEquals(2592000,saved.getMaxInactiveInterval().getSeconds());saved.setAttribute(com.moapan.access.infrastructure.RememberedSession.UNTIL,System.currentTimeMillis()-1000);((org.springframework.session.SessionRepository)sessions).save(saved);
        mvc.perform(get("/api/v1/account").cookie(cookie)).andExpect(status().isUnauthorized());
        var again=mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content("{\"username\":\"synthetic_remember_expiry\",\"password\":\""+PASSWORD+"\",\"rememberMe\":true}")).andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
        var other=login("synthetic_remember_expiry");mvc.perform(patch("/api/v1/account/password").cookie(other).with(csrf()).contentType("application/json").content("{\"currentPassword\":\""+PASSWORD+"\",\"newPassword\":\"synthetic changed password\"}")).andExpect(status().isOk());mvc.perform(get("/api/v1/account").cookie(again)).andExpect(status().isUnauthorized());
        var last=mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content("{\"username\":\"synthetic_remember_expiry\",\"password\":\"synthetic changed password\",\"rememberMe\":true}")).andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");mvc.perform(delete("/api/v1/account").cookie(last).with(csrf()).contentType("application/json").content("{\"currentPassword\":\"synthetic changed password\"}")).andExpect(status().isOk());mvc.perform(get("/api/v1/account").cookie(last)).andExpect(status().isUnauthorized());
    }
    @Test @Order(82) void discoveryIsPublicAndSensitivePagesStayOutOfIndex()throws Exception{
        mvc.perform(get("/robots.txt")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Sitemap: http://localhost/sitemap.xml")));
        var xml=mvc.perform(get("/sitemap.xml")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(xml.contains("http://localhost/about.html"));for(String name:List.of("index","admin","account","settings","login","signup"))assertFalse(xml.contains(name+".html"));
        mvc.perform(get("/about.html")).andExpect(header().string("Link","<http://localhost/about.html>; rel=\"canonical\""));
        mvc.perform(get("/login.html")).andExpect(header().string("X-Robots-Tag","noindex, follow"));
        mvc.perform(get("/api/v1/account")).andExpect(status().isUnauthorized()).andExpect(header().string("X-Robots-Tag","noindex, follow"));
    }
    @Test @Order(15) void oversizedJsonIsRejectedBeforeCsrfAndController()throws Exception{
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{\"padding\":\""+"x".repeat(17000)+"\"}"))
            .andExpect(status().isPayloadTooLarge()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.error").value("request_too_large"));
    }
}
