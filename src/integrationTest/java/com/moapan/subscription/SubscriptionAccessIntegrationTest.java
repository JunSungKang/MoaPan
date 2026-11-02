package com.moapan.subscription;

import com.moapan.subscription.application.SubscriptionManagement;
import com.moapan.newsletter.NewsletterService;
import com.moapan.newsletter.domain.PublicationResearch.*;
import com.moapan.shared.api.ApiError;
import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"moapan.mail.enabled=true","moapan.mail.host=localhost","moapan.mail.from=no-reply@example.test","moapan.mail.data-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=","spring.flyway.enabled=true","moapan.secret=synthetic-only-test-secret-32-characters","moapan.public-base-url=http://localhost","server.servlet.session.cookie.secure=false"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@org.springframework.context.annotation.Import(com.moapan.identity.EmailTestSupport.Config.class)
class SubscriptionAccessIntegrationTest {
    @Autowired com.moapan.identity.application.EmailOutbox emailOutbox;
    @Autowired com.moapan.identity.EmailTestSupport.Mailbox emailMailbox;
    static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>(org.testcontainers.utility.DockerImageName.parse("postgres@sha256:edb0c08dc33befd0262ab3b5cf1c4f3f8cfd8b09e88c05b45adee29d6761ddb6").asCompatibleSubstituteFor("postgres"));
    static {postgres.start();org.flywaydb.core.Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).cleanDisabled(true).load().migrate();}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);}
    @AfterAll static void stop(){postgres.stop();}
    @Autowired MockMvc mvc;@Autowired JdbcClient db;@Autowired NewsletterService letters;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.moapan.subscription.infrastructure.DatabaseEntitlements provider;
    private final JsonMapper json=JsonMapper.builder().build();
    private record Account(UUID id,String username,Cookie session) {}
    Account admin,member;
    @BeforeEach void setup()throws Exception{admin=create("ADMIN");member=create("USER");}
    private Account create(String role)throws Exception{
        String name="subscription_"+UUID.randomUUID().toString().replace("-","");
        var signup=com.moapan.identity.EmailTestSupport.signup(mvc,emailOutbox,emailMailbox,name,"synthetic subscription password");assertFalse(json.readTree(signup.getResponse().getContentAsString()).path("canReadNewsletter").asBoolean());
        UUID id=db.sql("SELECT id FROM users WHERE username=:name").param("name",name).query(UUID.class).single();db.sql("UPDATE users SET role=:role WHERE id=:id").param("role",role).param("id",id).update();
        var initial=mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse().getCookie("SESSION");
        var cookie=mvc.perform(post("/api/v1/auth/login").cookie(initial).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("username",name,"password","synthetic subscription password","rememberMe",true)))).andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
        assertNotNull(cookie);return new Account(id,name,cookie);
    }
    private String grantBody(long version){return json.writeValueAsString(Map.of("version",version,"startsAt",Instant.now().minusSeconds(60).toString(),"expiresAt",Instant.now().plusSeconds(3600).toString()));}
    private void grant(Account target)throws Exception{mvc.perform(put("/api/v1/admin/subscriptions/"+target.id()).cookie(admin.session()).with(csrf()).contentType("application/json").content(grantBody(0))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));}
    private void denied(Cookie session)throws Exception {
        for(String path:List.of("/newsletter.html","/api/v1/newsletters","/api/v1/newsletters/"+UUID.randomUUID(),"/api/v1/newsletters/"+UUID.randomUUID()+"/reuse","/api/v1/newsletters/"+UUID.randomUUID()+"/worksheet")){
            var request=get(path).header("If-None-Match","*").header("If-Modified-Since","Sun, 01 Jan 2090 00:00:00 GMT");if(session!=null)request.cookie(session);
            mvc.perform(request).andExpect(status().isNotFound()).andExpect(content().json("{\"error\":\"not_found\"}")).andExpect(header().string("Cache-Control","no-store")).andExpect(header().doesNotExist("Location")).andExpect(header().doesNotExist("ETag"));
            var head=head(path);if(session!=null)head.cookie(session);mvc.perform(head).andExpect(status().isNotFound()).andExpect(content().string(""));
        }
    }
    @Test void guestOrdinaryMemberAndAdminWithoutGrantAllReceiveConcealed404()throws Exception{denied(null);denied(member.session());denied(admin.session());mvc.perform(get("/admin.html").cookie(admin.session())).andExpect(status().isOk());}
    @Test void denialHtmlMatchesRealMissingPageAndDirectErrorRouteHasReal404()throws Exception{
        String real=mvc.perform(get("/nonexistent-subscription-page.html").accept("text/html")).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        for(String path:List.of("/newsletter.html","/404.html"))mvc.perform(get(path).cookie(member.session()).accept("text/html")).andExpect(status().isNotFound()).andExpect(content().string(real)).andExpect(header().string("Cache-Control","no-store"));
        assertTrue(real.contains("<h1>"));assertFalse(real.contains("benefit_required"));
    }
    @Test void grantRenewAndRevokeApplyWithoutReloginToRememberedSession()throws Exception{
        grant(member);mvc.perform(get("/newsletter.html").cookie(member.session())).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/api/v1/auth/session").cookie(member.session())).andExpect(jsonPath("$.canReadNewsletter").value(true));
        mvc.perform(put("/api/v1/admin/subscriptions/"+member.id()).cookie(admin.session()).with(csrf()).contentType("application/json").content(grantBody(1))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        mvc.perform(post("/api/v1/admin/subscriptions/"+member.id()+"/revoke").cookie(admin.session()).with(csrf()).contentType("application/json").content("{\"version\":2}")).andExpect(status().isOk()).andExpect(jsonPath("$.subscriptionStatus").value("REVOKED"));
        denied(member.session());mvc.perform(get("/api/v1/account").cookie(member.session())).andExpect(status().isOk()).andExpect(jsonPath("$.canReadNewsletter").value(false));
        assertEquals(3,db.sql("SELECT count(*) FROM admin_actions WHERE target_id=:id AND action LIKE 'subscription_%'").param("id",member.id()).query(Integer.class).single());
    }
    @Test void futureExpiredDisabledAndDeletedAccountsCannotRead()throws Exception{
        grant(member);db.sql("UPDATE account_subscriptions SET starts_at=now()+interval '1 hour',expires_at=now()+interval '2 hours' WHERE account_id=:id").param("id",member.id()).update();denied(member.session());
        db.sql("UPDATE account_subscriptions SET starts_at=now()-interval '2 hours',expires_at=now()-interval '1 hour' WHERE account_id=:id").param("id",member.id()).update();denied(member.session());
        db.sql("UPDATE account_subscriptions SET expires_at=now()+interval '1 hour' WHERE account_id=:id").param("id",member.id()).update();db.sql("UPDATE users SET active=false WHERE id=:id").param("id",member.id()).update();denied(member.session());
        db.sql("DELETE FROM users WHERE id=:id").param("id",member.id()).update();assertEquals(0,db.sql("SELECT count(*) FROM account_subscriptions WHERE account_id=:id").param("id",member.id()).query(Integer.class).single());
    }
    @Test void revocationStoresOneServerInstantAndRepeatedRequestsCannotMoveIt()throws Exception {
        grant(member);var original=subscriptionSearch(Map.of("username",member.username())).path("items").get(0);Instant before=Instant.now();
        var revoked=revokeAccount(member,1);Instant ended=Instant.parse(revoked.path("expiresAt").asText());
        assertFalse(ended.isBefore(before));assertFalse(ended.isAfter(Instant.now()));assertEquals(original.path("startsAt"),revoked.path("startsAt"));assertEquals(2,revoked.path("version").asLong());
        var changed=db.sql("SELECT updated_at FROM account_subscriptions WHERE account_id=:id").param("id",member.id()).query(OffsetDateTime.class).single().toInstant();
        var audited=db.sql("SELECT created_at FROM admin_actions WHERE target_id=:id AND action='subscription_revoke'").param("id",member.id()).query(OffsetDateTime.class).single().toInstant();
        assertEquals(ended,changed);assertEquals(ended,audited);assertEquals(revoked.path("expiresAt"),subscriptionSearch(Map.of("username",member.username())).path("items").get(0).path("expiresAt"));
        assertEquals(revoked,revokeAccount(member,2));assertEquals(1,db.sql("SELECT count(*) FROM admin_actions WHERE target_id=:id AND action='subscription_revoke'").param("id",member.id()).query(Integer.class).single());
        mvc.perform(post("/api/v1/admin/subscriptions/"+member.id()+"/revoke").cookie(admin.session()).with(csrf()).contentType("application/json").content("{\"version\":1}")).andExpect(status().isConflict());denied(member.session());
        mvc.perform(put("/api/v1/admin/subscriptions/"+member.id()).cookie(admin.session()).with(csrf()).contentType("application/json").content(grantBody(2))).andExpect(status().isOk()).andExpect(jsonPath("$.subscriptionStatus").value("ACTIVE")).andExpect(jsonPath("$.version").value(3));
        mvc.perform(get("/newsletter.html").cookie(member.session())).andExpect(status().isOk());
    }
    @Test void cancellingScheduledSubscriptionKeepsItsPlannedStartAndClosesItNow()throws Exception {
        var start=Instant.now().plusSeconds(86400);var end=start.plusSeconds(86400);
        mvc.perform(put("/api/v1/admin/subscriptions/"+member.id()).cookie(admin.session()).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("version",0,"startsAt",start.toString(),"expiresAt",end.toString())))).andExpect(status().isOk()).andExpect(jsonPath("$.subscriptionStatus").value("SCHEDULED"));
        var original=subscriptionSearch(Map.of("username",member.username())).path("items").get(0);var revoked=revokeAccount(member,1);
        assertEquals("REVOKED",revoked.path("subscriptionStatus").asText());assertEquals(original.path("startsAt"),revoked.path("startsAt"));assertTrue(Instant.parse(revoked.path("expiresAt").asText()).isBefore(start));denied(member.session());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.sql("UPDATE account_subscriptions SET status='ACTIVE' WHERE account_id=:id").param("id",member.id()).update());
    }
    @Test void expiredSubscriptionCanBeRevokedAndEndsAtRecordedRevocationTime()throws Exception {
        grant(member);db.sql("UPDATE account_subscriptions SET starts_at=now()-interval '2 days',expires_at=now()-interval '1 day' WHERE account_id=:id").param("id",member.id()).update();
        Instant before=Instant.now();var revoked=revokeAccount(member,1);assertEquals("REVOKED",revoked.path("subscriptionStatus").asText());assertFalse(Instant.parse(revoked.path("expiresAt").asText()).isBefore(before));denied(member.session());
    }
    private tools.jackson.databind.JsonNode revokeAccount(Account target,long version)throws Exception {
        String result=mvc.perform(post("/api/v1/admin/subscriptions/"+target.id()+"/revoke").cookie(admin.session()).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("version",version)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();return json.readTree(result);
    }
    @Test void adminRoleCsrfCurrentVersionAndPeriodAreEnforced()throws Exception{
        String url="/api/v1/admin/subscriptions/"+member.id();
        mvc.perform(put(url).cookie(member.session()).with(csrf()).contentType("application/json").content(grantBody(0))).andExpect(status().isForbidden());
        mvc.perform(put(url).cookie(admin.session()).contentType("application/json").content(grantBody(0))).andExpect(status().isForbidden());
        mvc.perform(put(url).cookie(admin.session()).with(csrf().useInvalidToken()).contentType("application/json").content(grantBody(0))).andExpect(status().isForbidden());
        for(String input:List.of("{}","{\"version\":-1}",grantBody(0).replace("}",",\"role\":\"ADMIN\"}"),json.writeValueAsString(Map.of("version",0,"startsAt","2026-01-02T00:00:00Z","expiresAt","2026-01-01T00:00:00Z"))))mvc.perform(put(url).cookie(admin.session()).with(csrf()).contentType("application/json").content(input)).andExpect(status().isBadRequest());
        grant(member);mvc.perform(put(url).cookie(admin.session()).with(csrf()).contentType("application/json").content(grantBody(0))).andExpect(status().isConflict());
    }
    @Test void concurrentInitialGrantsCannotOverwriteAndOnlyWinnerIsAudited()throws Exception{
        String body=grantBody(0);try(var executor=Executors.newFixedThreadPool(2)){
            var results=executor.invokeAll(List.<Callable<Integer>>of(()->mvc.perform(put("/api/v1/admin/subscriptions/"+member.id()).cookie(admin.session()).with(csrf()).contentType("application/json").content(body)).andReturn().getResponse().getStatus(),()->mvc.perform(put("/api/v1/admin/subscriptions/"+member.id()).cookie(admin.session()).with(csrf()).contentType("application/json").content(body)).andReturn().getResponse().getStatus()));
            assertEquals(List.of(200,409),results.stream().map(r->{try{return r.get();}catch(Exception e){throw new AssertionError(e);}}).sorted().toList());
        }assertEquals(1,db.sql("SELECT count(*) FROM admin_actions WHERE target_id=:id AND action='subscription_grant'").param("id",member.id()).query(Integer.class).single());
    }
    @Test void searchIsBoundedAndOnlyReturnsMinimalAccountFields()throws Exception{
        mvc.perform(post("/api/v1/admin/subscriptions/search").cookie(member.session()).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        var r=mvc.perform(post("/api/v1/admin/subscriptions/search").cookie(admin.session()).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("username",member.username().toUpperCase(Locale.ROOT),"page",0,"size",1)))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].username").value(member.username())).andReturn().getResponse().getContentAsString();
        assertFalse(r.contains("password"));assertFalse(r.contains("termsAccepted"));
        for(String body:List.of("{\"size\":-1}","{\"size\":101}","{\"page\":-1}"))mvc.perform(post("/api/v1/admin/subscriptions/search").cookie(admin.session()).with(csrf()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
    }
    @Test void subscriptionSearchDefaultsToTenRecentGrantsAndPaginatesEverySupportedDisplaySize()throws Exception {
        String prefix="listing_"+UUID.randomUUID().toString().replace("-","");
        for(int i=0;i<105;i++){
            UUID id=UUID.randomUUID();
            db.sql("INSERT INTO users(id,username,password_hash) VALUES(:id,:name,'synthetic-unused-hash')").param("id",id).param("name",prefix+String.format(Locale.ROOT,"_%03d",i)).update();
            if(i<103)db.sql("INSERT INTO account_subscriptions(account_id,status,starts_at,expires_at,version,updated_at) VALUES(:id,'ACTIVE',now()-interval '1 day',now()+interval '10 days',1,now()-make_interval(mins=>:age))").param("id",id).param("age",i).update();
        }
        var first=subscriptionSearch(Map.of("username",prefix));assertEquals(10,first.path("items").size());assertTrue(first.path("hasNext").asBoolean());assertEquals(prefix+"_000",first.path("items").get(0).path("username").asText());
        for(int size:List.of(10,30,50,100)){
            var seen=new LinkedHashSet<String>();
            for(int page=0;;page++){
                var result=subscriptionSearch(Map.of("username",prefix,"sort","RECENT","size",size,"page",page));assertEquals(page,result.path("page").asInt());assertTrue(result.path("items").size()<=size);
                for(var item:result.path("items"))assertTrue(seen.add(item.path("username").asText()),"No repeated account between pages");
                if(!result.path("hasNext").asBoolean())break;assertTrue(page<12);
            }
            assertEquals(105,seen.size());assertEquals(prefix+"_104",seen.getLast());
        }
        for(String sort:List.of("","username","RECENT; DROP TABLE users","EXPIRING DESC"))mvc.perform(post("/api/v1/admin/subscriptions/search").cookie(admin.session()).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("sort",sort)))).andExpect(status().isBadRequest());
    }
    @Test void expiringSortPrioritizesUsableSubscriptionsAndKeepsOtherAccountsManageable()throws Exception {
        String prefix="expiry_"+UUID.randomUUID().toString().replace("-","");
        var now=Instant.now();
        for(String kind:List.of("near","later","future","expired","revoked","disabled","none","near_tie")){
            UUID id=UUID.randomUUID();db.sql("INSERT INTO users(id,username,password_hash,active) VALUES(:id,:name,'synthetic-unused-hash',:active)").param("id",id).param("name",prefix+"_"+kind).param("active",!kind.equals("disabled")).update();
            if(kind.equals("none"))continue;
            db.sql("INSERT INTO account_subscriptions(account_id,status,starts_at,expires_at,version,updated_at) VALUES(:id,:status,:start,:end,1,TIMESTAMPTZ '2026-01-01 00:00:00Z')")
                    .param("id",id).param("status",kind.equals("revoked")?"REVOKED":"ACTIVE").param("start",now.plusSeconds((kind.equals("future")?1:-24)*3600).atOffset(ZoneOffset.UTC)).param("end",now.plusSeconds((kind.equals("expired")?-1:kind.equals("later")?24:2)*3600).atOffset(ZoneOffset.UTC)).update();
        }
        var result=subscriptionSearch(Map.of("username",prefix,"sort","EXPIRING","size",100));var names=new ArrayList<String>();for(var row:result.path("items"))names.add(row.path("username").asText());
        assertEquals(List.of(prefix+"_near",prefix+"_near_tie",prefix+"_later"),names.subList(0,3));assertEquals(8,names.size());
        var search=subscriptionSearch(Map.of("username",prefix+"_none","sort","EXPIRING"));assertEquals("NONE",search.path("items").get(0).path("subscriptionStatus").asText());
    }
    private tools.jackson.databind.JsonNode subscriptionSearch(Map<String,Object> body)throws Exception {
        var result=mvc.perform(post("/api/v1/admin/subscriptions/search").cookie(admin.session()).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();return json.readTree(result);
    }
    @Test void signupCannotInjectSubscriptionAndDeletionRemovesOwnGrant()throws Exception{
        mvc.perform(post("/api/v1/auth/signup").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("username","injected_"+UUID.randomUUID(),"password","synthetic subscription password","termsAccepted",true,"termsVersion","terms-1","canReadNewsletter",true)))).andExpect(status().isBadRequest());
        grant(member);mvc.perform(delete("/api/v1/account").cookie(member.session()).with(csrf()).contentType("application/json").content("{\"currentPassword\":\"synthetic subscription password\"}")).andExpect(status().isOk());
        assertEquals(0,db.sql("SELECT count(*) FROM account_subscriptions WHERE account_id=:id").param("id",member.id()).query(Integer.class).single());denied(member.session());
    }
    @Test void revocationCannotUseWarmPublishedContentAndDoesNotInventPayment()throws Exception{
        UUID id=UUID.randomUUID();String manuscript=json.writeValueAsString(Map.of("title","Synthetic subscription issue","introduction","Synthetic editorial only","topic","Research","blocks",List.of(Map.of("type","paragraph","text","Synthetic own manuscript","url","","caption",""))));
        db.sql("INSERT INTO newsletters(id,draft,published,published_at,first_published_at,publication_revision) VALUES(:id,CAST(:body AS jsonb),CAST(:body AS jsonb),now(),now(),1)").param("id",id).param("body",manuscript).update();
        grant(member);for(int i=0;i<2;i++){
            mvc.perform(get("/api/v1/newsletters/"+id).cookie(member.session())).andExpect(status().isOk()).andExpect(jsonPath("$.distribution").value("FREE_PREVIEW"));
            mvc.perform(get("/api/v1/newsletters").cookie(member.session())).andExpect(status().isOk());
        }
        mvc.perform(post("/api/v1/admin/subscriptions/"+member.id()+"/revoke").cookie(admin.session()).with(csrf()).contentType("application/json").content("{\"version\":1}")).andExpect(status().isOk());
        for(String suffix:List.of("","/reuse","/worksheet"))mvc.perform(get("/api/v1/newsletters/"+id+suffix).cookie(member.session())).andExpect(status().isNotFound()).andExpect(content().json("{\"error\":\"not_found\"}"));
        var context=org.springframework.security.core.context.SecurityContextHolder.getContext();context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(member.id().toString(),null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
        try{assertEquals(404,assertThrows(ApiError.class,()->letters.detail(id)).statusCode());}finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    }
    @Test void entitlementDatabaseFailureIs503AndNeverGrantsOrPretendsMissing()throws Exception{
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic outage")).when(provider).grants(org.mockito.ArgumentMatchers.eq(member.id()),org.mockito.ArgumentMatchers.any(Instant.class));
        mvc.perform(get("/api/v1/newsletters").cookie(member.session())).andExpect(status().isServiceUnavailable()).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("Retry-After","60"));
    }
    @Test void encodedUnreservedPathsCannotBypassConcealmentOrApiContentType()throws Exception{
        for(String path:List.of("/%6eewsletter.html","/api/v1/%6eewsletters","/%61pi/v1/newsletters")){
            for(Cookie cookie:List.of(member.session(),admin.session()))mvc.perform(get(java.net.URI.create(path)).cookie(cookie).accept("text/html")).andExpect(status().isNotFound()).andExpect(header().string("Cache-Control","no-store")).andExpect(header().doesNotExist("Location"));
            mvc.perform(get(java.net.URI.create(path))).andExpect(status().isNotFound()).andExpect(content().json("{\"error\":\"not_found\"}"));
            if(!path.endsWith(".html"))mvc.perform(get(java.net.URI.create(path)).cookie(member.session()).accept("text/html")).andExpect(content().contentType("application/json"));
        }
    }
}
