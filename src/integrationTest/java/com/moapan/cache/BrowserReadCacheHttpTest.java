package com.moapan.cache;

import com.moapan.classification.application.TagDictionary;
import com.moapan.edition.application.RepresentativeInterests;
import com.moapan.translation.infrastructure.ArticleTranslations;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"moapan.secret=synthetic-http-cache-secret-at-least-32-characters","moapan.public-base-url=http://localhost","server.servlet.session.cookie.secure=false"})
@AutoConfigureMockMvc @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS) @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BrowserReadCacheHttpTest {
    static final CacheDatabase f=new CacheDatabase();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",f::jdbcUrl);r.add("spring.datasource.username",f::username);r.add("spring.datasource.password",f::password);}
    @Autowired MockMvc mvc;@Autowired ConfigurableApplicationContext context;
    @MockitoSpyBean ArticleTranslations translations;
    @MockitoSpyBean TagDictionary dictionary;
    @MockitoSpyBean RepresentativeInterests interests;
    @BeforeAll void registerDatabaseCleanupAfterSpringResources(){
        // DirtiesContext must close repositories/pool before stopping their synthetic database.
        var factory=(org.springframework.beans.factory.support.DefaultListableBeanFactory)context.getBeanFactory();
        assertTrue(context.containsBean("dataSource"));
        factory.registerSingleton("httpCacheDatabase",f);
        factory.registerDisposableBean("httpCacheDatabase",f::close);
        factory.registerDependentBean("httpCacheDatabase","dataSource");
    }
    @BeforeEach void fixture(){
        f.db.sql("DELETE FROM news_editions").update();f.db.sql("UPDATE articles SET public=false").update();
        UUID site=UUID.randomUUID(),source=UUID.randomUUID();
        f.db.sql("INSERT INTO sites(id,name,homepage_url,normalized_homepage_url) VALUES(:id,'HTTP cache fixture',:url,:url)").param("id",site).param("url","https://"+site+".example.test").update();
        f.db.sql("INSERT INTO sources(id,site_id,label,type,endpoint_url,normalized_endpoint_url,rights_status,allow_display,allow_summary) VALUES(:id,:site,'HTTP cache source','RSS','https://example.test/feed','https://example.test/feed','APPROVED',true,true)").param("id",source).param("site",site).update();
        f.db.sql("INSERT INTO articles(id,source_id,source_guid,canonical_url,title,summary_bullets,summary_mode,language,input_hash) VALUES(:id,:source,'fixture','https://example.test/article','Synthetic HTTP news','[\"Synthetic research summary.\"]','EXTRACTIVE_ORIGINAL_LANGUAGE','en',repeat('a',64))").param("id",UUID.randomUUID()).param("source",source).update();
    }
    @Test void realHttpFlowReusesPayloadButStillChecksCurrentRightsAndPrivateRouteAccess()throws Exception{
        mvc.perform(get("/api/v1/public/news/edition")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/v1/public/news/edition")).andExpect(status().isOk());verify(translations,times(1)).read(anyList());
        mvc.perform(get("/api/v1/news/edition")).andExpect(status().isUnauthorized());mvc.perform(get("/api/v1/admin/sites")).andExpect(status().isUnauthorized());
        f.db.sql("UPDATE sources SET allow_summary=false WHERE label='HTTP cache source'").update();
        mvc.perform(get("/api/v1/public/news/edition")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].summaryBullets.length()").value(0));verify(translations,times(2)).read(anyList());
        mvc.perform(get("/api/v1/public/news/edition")).andExpect(status().isOk());verify(translations,times(2)).read(anyList());
    }
    @Test void existingHttpTagAndRepresentativeDefaultsRoutesReachActualCaches()throws Exception{
        var dictionaries=new ArrayList<Object>();var defaults=new ArrayList<Object>();
        doAnswer(i->{Object value=i.callRealMethod();dictionaries.add(value);return value;}).when(dictionary).read();
        doAnswer(i->{Object value=i.callRealMethod();defaults.add(value);return value;}).when(interests).read();
        for(int i=0;i<2;i++){mvc.perform(get("/api/v1/public/tags")).andExpect(status().isOk());mvc.perform(get("/api/v1/public/interests")).andExpect(status().isOk());}
        assertEquals(2,dictionaries.size());assertSame(dictionaries.getFirst(),dictionaries.getLast());assertEquals(2,defaults.size());assertSame(defaults.getFirst(),defaults.getLast());
    }
}
