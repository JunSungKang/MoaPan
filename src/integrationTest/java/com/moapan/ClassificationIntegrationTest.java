package com.moapan;
import com.moapan.classification.application.TopicCatalog;
import com.moapan.classification.application.ClassificationWorker;
import com.moapan.classification.infrastructure.LocalClassifier;
import com.moapan.classification.api.ClassificationController;
import com.moapan.catalog.infrastructure.CatalogRepository;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
@SpringBootTest(properties={"spring.flyway.enabled=true","moapan.secret=synthetic-only-test-secret-32-characters","moapan.public-base-url=http://localhost","server.servlet.session.cookie.secure=false"}) @AutoConfigureMockMvc
class ClassificationIntegrationTest {
    static final PostgreSQLContainer<?> pg=new PostgreSQLContainer<>(org.testcontainers.utility.DockerImageName.parse("postgres@sha256:edb0c08dc33befd0262ab3b5cf1c4f3f8cfd8b09e88c05b45adee29d6761ddb6").asCompatibleSubstituteFor("postgres"));
    static {pg.start();org.flywaydb.core.Flyway.configure().dataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword()).load().migrate();}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",pg::getJdbcUrl);r.add("spring.datasource.username",pg::getUsername);r.add("spring.datasource.password",pg::getPassword);}
    @AfterAll static void stop(){pg.stop();}
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
    @Autowired CatalogRepository repo;@Autowired ClassificationWorker worker;@Autowired TopicCatalog catalog;@Autowired MockMvc mvc;
    UUID article,source;
    @BeforeEach void fixture(){
        repo.db().sql("INSERT INTO users(id,username,password_hash,role) VALUES ('00000000-0000-0000-0000-000000000099','synthetic_classifier_admin','unused-fixture-hash','ADMIN'),('00000000-0000-0000-0000-000000000098','synthetic_classifier_user','unused-fixture-hash','USER') ON CONFLICT DO NOTHING").update();
        repo.db().sql("DELETE FROM article_classification_jobs").update();repo.db().sql("UPDATE classification_runtime SET lease_token=NULL,lease_until=NULL").update();repo.db().sql("UPDATE articles SET public=false").update();
        UUID site=UUID.randomUUID();source=UUID.randomUUID();article=UUID.randomUUID();
        repo.db().sql("INSERT INTO sites(id,name,homepage_url,normalized_homepage_url) VALUES(:id,'Fixture',:url,:url)").param("id",site).param("url","https://"+site+".example.test").update();
        repo.db().sql("INSERT INTO sources(id,site_id,label,type,endpoint_url,normalized_endpoint_url,rights_status,allow_display,allow_summary) VALUES(:id,:site,'Fixture','RSS','https://example.test/feed','https://example.test/feed','APPROVED',true,true)").param("id",source).param("site",site).update();
        repo.db().sql("INSERT INTO articles(id,source_id,source_guid,canonical_url,title,summary_bullets,summary_mode,language,input_hash) VALUES(:id,:source,'fixture','https://example.test/article','Synthetic security advisory','[\"A synthetic access control issue.\"]','EXTRACTIVE_ORIGINAL_LANGUAGE','en',repeat('a',64))").param("id",article).param("source",source).update();
    }
    @Test void onlyOneConcurrentClaimAndManualTagsPreserved()throws Exception{
        ClassificationWorker.Job claimed=null;int count=0;try(var pool=Executors.newFixedThreadPool(4)){var calls=new ArrayList<Future<ClassificationWorker.Job>>();for(int i=0;i<4;i++)calls.add(pool.submit(worker::claim));for(var f:calls){var j=f.get();if(j!=null){count++;claimed=j;}}}assertEquals(1,count);assertNotNull(claimed);
        UUID manual=repo.db().sql("SELECT id FROM tags WHERE slug='ai'").query(UUID.class).single();repo.db().sql("INSERT INTO article_tags VALUES(:id,:tag,1,'MANUAL',true)").param("id",article).param("tag",manual).update();
        UUID security=catalog.snapshot(false).topics().stream().filter(t->t.slug().equals("security")).findFirst().orElseThrow().id();assertTrue(worker.finish(claimed,Set.of(security)));assertEquals(2,repo.db().sql("SELECT count(*) FROM article_tags WHERE article_id=:id").param("id",article).query(Integer.class).single());assertNull(worker.claim());
    }
    @Test void changedContentAndRevokedRightsRejectLateResult(){
        var job=worker.claim();repo.db().sql("UPDATE articles SET title='Changed title' WHERE id=:id").param("id",article).update();assertFalse(worker.finish(job,Set.of()));
        var next=worker.claim();assertNotNull(next);assertNotEquals(job.hash(),next.hash());repo.db().sql("UPDATE sources SET allow_display=false WHERE id=:id").param("id",source).update();assertFalse(worker.finish(next,Set.of()));assertNull(worker.claim());
    }
    @Test void articleLockAndPolicyChangesRejectLateResults(){
        var job=worker.claim();repo.db().sql("UPDATE articles SET tags_locked=true WHERE id=:id").param("id",article).update();assertFalse(worker.finish(job,Set.of()));assertNull(worker.claim());
        repo.db().sql("UPDATE articles SET tags_locked=false WHERE id=:id").param("id",article).update();job=worker.claim();repo.db().sql("UPDATE classification_config SET version=version+1").update();assertFalse(worker.finish(job,Set.of()));assertNotNull(worker.claim());
    }
    @Test void expiredLeaseCannotApplyAndRetriesStop(){
        var old=worker.claim();repo.db().sql("UPDATE article_classification_jobs SET lease_until=now()-interval '1 second'").update();repo.db().sql("UPDATE classification_runtime SET lease_until=now()-interval '1 second'").update();assertFalse(worker.finish(old,Set.of()));assertNull(worker.claim());
        repo.db().sql("UPDATE article_classification_jobs SET available_at=now()").update();var next=worker.claim();assertNotNull(next);assertNotEquals(old.token(),next.token());worker.fail(next,"classification_request_failed");repo.db().sql("UPDATE article_classification_jobs SET available_at=now()").update();next=worker.claim();worker.fail(next,"classification_request_failed");assertEquals("FAILED",repo.db().sql("SELECT state FROM article_classification_jobs WHERE article_id=:id").param("id",article).query(String.class).single());assertNull(worker.claim());
    }
    @Test void lockedAutomaticTagsSurviveSummaryChanges(){
        var job=worker.claim();UUID tag=job.topics().topics().getFirst().id();assertTrue(worker.finish(job,Set.of(tag)));
        repo.db().sql("UPDATE articles SET tags_locked=true,summary_bullets='[\"Changed synthetic summary\"]' WHERE id=:id").param("id",article).update();
        assertEquals(1,repo.db().sql("SELECT count(*) FROM article_tags WHERE article_id=:id AND tag_id=:tag").param("id",article).param("tag",tag).query(Integer.class).single());assertNull(worker.claim());
    }
    @Test void preservedManualTagsConsumeTheSixTopicBudget(){
        var job=worker.claim();var selected=new HashSet<>(job.topics().topics().stream().map(com.moapan.classification.domain.TopicTaxonomy.Topic::id).toList());
        var manual=repo.db().sql("SELECT id FROM tags WHERE slug IN ('ai','postgresql')").query(UUID.class).list();assertEquals(2,manual.size());
        for(UUID tag:manual)repo.db().sql("INSERT INTO article_tags VALUES(:id,:tag,1,'MANUAL',true)").param("id",article).param("tag",tag).update();
        assertTrue(worker.finish(job,selected));assertEquals(6,repo.db().sql("SELECT count(*) FROM article_tags WHERE article_id=:id").param("id",article).query(Integer.class).single());
        assertEquals(2,repo.db().sql("SELECT count(*) FROM article_tags WHERE article_id=:id AND method='MANUAL'").param("id",article).query(Integer.class).single());
    }
    @Test void summaryPermissionRevocationPreventsInputAndLateOutput(){
        var job=worker.claim();repo.db().sql("UPDATE sources SET allow_summary=false WHERE id=:id").param("id",source).update();assertFalse(worker.finish(job,Set.of()));assertNull(worker.claim());
    }
    private jakarta.servlet.http.Cookie login(String role)throws Exception{
        String user=role.equals("ADMIN")?"synthetic_classifier_admin":"synthetic_classifier_user";
        repo.db().sql("UPDATE users SET password_hash=:hash WHERE username=:name").param("hash",encoder.encode("synthetic-password")).param("name",user).update();
        var initial=mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse().getCookie("SESSION");
        return mvc.perform(post("/api/v1/auth/login").cookie(initial).with(csrf()).contentType("application/json").content("{\"username\":\""+user+"\",\"password\":\"synthetic-password\"}")).andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
    }
    @Test void adminApiRequiresAuthentication()throws Exception{mvc.perform(get("/api/v1/admin/classification")).andExpect(status().isUnauthorized());}
    @Test void adminApiRejectsUser()throws Exception{mvc.perform(get("/api/v1/admin/classification").cookie(login("USER"))).andExpect(status().isForbidden());}
    @Test void taxonomyVersionAndCsrf()throws Exception{
        var cookie=login("ADMIN");
        mvc.perform(get("/api/v1/admin/classification").cookie(cookie)).andExpect(status().isOk());var snapshot=catalog.snapshot(false);var t=snapshot.topics().getFirst();String body="{\"version\":"+snapshot.version()+",\"topics\":[{\"tagId\":\""+t.id()+"\",\"definition\":\"Synthetic test definition\"}]}";
        mvc.perform(put("/api/v1/admin/classification/topics").cookie(cookie).contentType("application/json").content(body)).andExpect(status().isForbidden());
        try{mvc.perform(put("/api/v1/admin/classification/topics").cookie(cookie).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk());mvc.perform(put("/api/v1/admin/classification/topics").cookie(cookie).with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());}
        finally{var now=catalog.snapshot(false);mvc.perform(put("/api/v1/admin/classification/topics").cookie(cookie).with(csrf()).contentType("application/json").content(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(new TopicCatalog.Edit(now.version(),snapshot.topics().stream().map(x->new TopicCatalog.Entry(x.id(),x.definition())).toList())))).andExpect(status().isOk());}
    }
}
