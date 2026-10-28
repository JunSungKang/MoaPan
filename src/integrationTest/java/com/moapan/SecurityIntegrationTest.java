package com.moapan;
import com.moapan.auth.*;
import com.moapan.security.Limits;
import com.moapan.site.*;
import com.moapan.jobs.Worker;
import com.moapan.source.SafeFetch;
import com.moapan.news.NewsService;
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
    @Autowired com.moapan.news.EditorialService editorial;
    static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>(org.testcontainers.utility.DockerImageName.parse("postgres@sha256:edb0c08dc33befd0262ab3b5cf1c4f3f8cfd8b09e88c05b45adee29d6761ddb6").asCompatibleSubstituteFor("postgres"));
    static { postgres.start(); org.flywaydb.core.Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).cleanDisabled(true).load().migrate(); }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);}
    @AfterAll static void stop(){postgres.stop();}
    @Autowired MockMvc mvc;@Autowired Accounts accounts;@Autowired Limits limits;@Autowired CatalogRepository repo;@Autowired CatalogService catalog;@Autowired Worker worker;@Autowired NewsService news;
    @Autowired org.springframework.session.SessionRepository<?> sessions;
    private static final String PASSWORD="synthetic password 12345";
    private void signup(String user)throws Exception{mvc.perform(post("/api/v1/auth/signup").with(csrf()).contentType("application/json").content("{\"username\":\""+user+"\",\"termsAccepted\":true,\"termsVersion\":\"2026-10-28.2\",\"password\":\""+PASSWORD+"\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("USER")).andExpect(jsonPath("$.passwordHash").doesNotExist());}
    private Map<String,Object> claimReady(){
        for(int i=0;i<100;i++){var job=worker.claim();if(job!=null)return job;try{Thread.sleep(20);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
        throw new AssertionError("Queued job not ready within 2 seconds: "+repo.db().sql("SELECT status,available_at,now() db_now FROM content_jobs WHERE status='QUEUED'").query().listOfRows());
    }
    @Test @Order(1) void unauthenticatedAndCsrf()throws Exception{
        mvc.perform(get("/api/v1/news/edition").header("Referer","http://localhost").header("User-Agent","browser")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/signup").contentType("application/json").content("{\"username\":\"badone\",\"password\":\""+PASSWORD+"\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/signup").with(csrf().useInvalidToken()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.token").isString());
    }
    @Test @Order(2) void signupRejectsExtraFieldsAndStoresOnlyHash()throws Exception{
        mvc.perform(post("/api/v1/auth/signup").with(csrf()).contentType("application/json").content("{\"username\":\"injected\",\"termsAccepted\":true,\"termsVersion\":\"2026-10-28.2\",\"password\":\""+PASSWORD+"\",\"role\":\"ADMIN\"}")).andExpect(status().isBadRequest());
        signup("synthetic_a");Account a=accounts.findByUsername("synthetic_a").orElseThrow();assertTrue(a.passwordHash.startsWith("$argon2id$"));assertFalse(a.passwordHash.contains(PASSWORD));
    }
    @Test @Order(2) void signupRequiresCurrentExplicitConsent()throws Exception{
        Instant before=Instant.now();
        for(String fields:List.of("",",\"termsAccepted\":false,\"termsVersion\":\"2026-10-28.2\"",",\"termsAccepted\":true,\"termsVersion\":\"old\"",",\"termsAccepted\":true")){
            String body="{\"username\":\"consent_test\",\"password\":\""+PASSWORD+"\""+fields+"}";
            mvc.perform(post("/api/v1/auth/signup").with(csrf()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
            assertTrue(accounts.findByUsername("consent_test").isEmpty());
        }
        signup("consent_test");Account a=accounts.findByUsername("consent_test").orElseThrow();
        assertEquals(AuthController.TERMS_VERSION,a.termsVersion);assertFalse(a.termsAcceptedAt.isBefore(before));assertFalse(a.termsAcceptedAt.isAfter(Instant.now()));
        Account legacy=accounts.saveAndFlush(new Account("legacy_consent",a.passwordHash));assertNull(accounts.findById(legacy.id).orElseThrow().termsAcceptedAt);assertNull(legacy.termsVersion);
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
        for(String invalid:List.of("😀".repeat(14),"😀".repeat(129))) {
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
        var draft=new CatalogService.SourceInput("Preview","RSS","https://preview.example.org/feed","en",900,false,"UNREVIEWED",false,false,false,false,false,false,false,false,null,null,"",Map.of(),null,null);
        @SuppressWarnings("unchecked")var source=(Map<String,Object>)catalog.createSource((UUID)site.get("id"),draft,actor);UUID id=(UUID)source.get("id");assertThrows(ApiError.class,()->catalog.preview(id,false,actor));
        var approved=new CatalogService.SourceInput("Preview","RSS","https://preview.example.org/feed","en",900,false,"RESTRICTED",true,true,true,false,false,false,false,false,"https://preview.example.org/terms",java.time.LocalDate.of(2026,10,28),"Synthetic terms fixture",Map.of(),null,0L);
        catalog.updateSource(id,approved,actor);catalog.preview(id,false,actor);assertEquals("NOT_TESTED",repo.source(id,false).get("validation_status"));catalog.preview(id,true,actor);assertEquals("PASSED",repo.source(id,false).get("validation_status"));assertEquals(0,repo.db().sql("SELECT count(*) FROM articles WHERE source_id=:id").param("id",id).query(Integer.class).single());assertEquals(0,repo.db().sql("SELECT count(*) FROM content_jobs WHERE source_id=:id").param("id",id).query(Integer.class).single());
        var changed=new CatalogService.SourceInput("Preview","RSS","https://changed.example.org/feed","en",900,false,"RESTRICTED",true,true,true,false,false,false,false,false,"https://preview.example.org/terms",java.time.LocalDate.of(2026,10,28),"Synthetic",Map.of(),null,1L);catalog.updateSource(id,changed,actor);assertEquals("UNREVIEWED",repo.source(id,false).get("rights_status"));assertEquals("NOT_TESTED",repo.source(id,false).get("validation_status"));assertEquals(false,repo.source(id,false).get("enabled"));
    }
    @Test @Order(14) @WithMockUser(roles="ADMIN") void tagEditingAndCanonicalMergePreserveLockedLinks(){
        UUID actor=accounts.findByUsername("synthetic_a").orElseThrow().id;
        @SuppressWarnings("unchecked")var created=(Map<String,Object>)editorial.tag(new com.moapan.news.EditorialService.TagInput("ai-unified","Synthetic AI","기술"),actor);UUID target=(UUID)created.get("id"),old=UUID.fromString("00000000-0000-0000-0000-000000000001");editorial.updateTag(target,new com.moapan.news.EditorialService.TagInput("ai-unified","수정 합성 태그","기술"),actor);editorial.merge(old,target,actor);assertEquals(target,repo.db().sql("SELECT canonical_id FROM tag_redirects WHERE old_id=:id").param("id",old).query(UUID.class).single());assertEquals(0,repo.db().sql("SELECT count(*) FROM article_tags WHERE tag_id=:id").param("id",old).query(Integer.class).single());assertEquals(target,repo.db().sql("SELECT tag_id FROM tag_aliases WHERE alias='ai'").query(UUID.class).single());
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

    @Test @Order(99) void databaseOutageDoesNotBypassSessionsOrQuota()throws Exception {
        var cookie=login("synthetic_admin");postgres.stop();
        mvc.perform(get("/api/v1/news/edition").cookie(cookie)).andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After","60")).andExpect(jsonPath("$.error").value("temporarily_unavailable"));
    }

    @Test @Order(98) void securityCounterCapacityIsBounded(){
        repo.db().sql("INSERT INTO auth_rate_limits(bucket,window_start,used,expires_at) SELECT 'capacity-fixture-'||n,now(),1,now()+interval '15 minutes' FROM generate_series(1,10000)n").update();
        try{assertEquals(503,assertThrows(ApiError.class,()->limits.auth("new-capacity-fixture",1,60)).statusCode());}
        finally{repo.db().sql("DELETE FROM auth_rate_limits WHERE bucket LIKE 'capacity-fixture-%'").update();}
    }

}
