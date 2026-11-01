package com.moapan;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AdminUsabilityTest {
    static final String TOPIC="21000000-0000-0000-0000-000000000001",OTHER="21000000-0000-0000-0000-000000000002",NEW="21000000-0000-0000-0000-000000000003",JOB="31000000-0000-0000-0000-000000000001";
    static final tools.jackson.databind.json.JsonMapper JSON=tools.jackson.databind.json.JsonMapper.builder().build();
    @BeforeAll static void start()throws Exception{BrowserTest.start();}
    @AfterAll static void stop(){BrowserTest.stop();}
    static Map<String,Object> tag(String id,String name){return new LinkedHashMap<>(Map.of("id",id,"name",name,"category","기술","slug","synthetic-topic"));}
    static Map<String,Object> article(String id,String title,String collected){return Map.of("id",id,"title",title,"collected_at",collected,"site_name","합성 출처","summary_bullets","[\"Synthetic summary\"]","tag_ids",JSON.writeValueAsString(List.of(TOPIC)),"public",true);}
    static void dictionary(BrowserContext context){context.route("**/api/v1/tags",route->route.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody(JSON.writeValueAsString(Map.of("tags",List.of(tag(TOPIC,"인공지능"),tag(OTHER,"검색")),"aliases",List.of(),"redirects",List.of())))));}
    static void screenshots(Page page,String name)throws Exception{
        Path directory=Path.of("build/reports/screenshots/admin-usability");Files.createDirectories(directory);
        for(String language:List.of("ko","en"))for(int width:new int[]{390,768,1440}){
            page.setViewportSize(width,1000);page.locator("[data-language-select]").selectOption(language);
            assertTrue((boolean)page.evaluate("document.documentElement.scrollWidth<=innerWidth"));
            assertEquals(1,page.locator("h1").count());page.mouse().move(0,0);page.evaluate("window.scrollTo(0,0)");
            page.evaluate("()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)))");
            page.screenshot(new Page.ScreenshotOptions().setPath(directory.resolve(language+"-"+name+"-"+width+".png")).setFullPage(true));
        }
        page.locator("[data-language-select]").selectOption("ko");
    }

    @Test void collectedDateAndOrderAreRequestedBeforeShowingNamedTopicChoices()throws Exception{
        try(var context=BrowserTest.browser.newContext()){
            SiteDesignAuditTest.routes(context,"ADMIN");dictionary(context);var requests=new ArrayList<String>();var writes=new ArrayList<String>();
            context.route("**/api/v1/admin/articles**",route->{
                if(route.request().method().equals("PATCH")){writes.add(route.request().postData());route.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody("{}"));return;}
                String url=route.request().url();requests.add(url);var older=article("article-older","A synthetic title","2026-10-30T00:00:00Z");var newer=article("article-newer","Z synthetic title","2026-10-30T01:00:00Z");
                route.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody(JSON.writeValueAsString(url.contains("sort=collected_asc")?List.of(older,newer):List.of(newer,older))));
            });
            var page=context.newPage();page.navigate(BrowserTest.origin+"/admin.html#articles");page.locator("#refresh-articles").click();page.waitForSelector(".article-review-sheet");
            assertTrue(page.locator(".article-review-sheet:visible h3").textContent().startsWith("Z synthetic"));
            page.locator("#article-review-filters [name=collectedFrom]").fill("2026-10-30");page.locator("#article-review-filters [name=collectedTo]").fill("2026-10-30");
            page.locator("#article-review-filters [name=sort]").selectOption("collected_asc");page.locator("#refresh-articles").click();page.waitForCondition(()->requests.size()==2&&page.locator("#refresh-articles").isEnabled());
            assertTrue(requests.getLast().contains("collectedFrom=2026-10-30"));assertTrue(requests.getLast().contains("collectedTo=2026-10-30"));assertTrue(requests.getLast().contains("sort=collected_asc"));
            assertTrue(page.locator("#review-article-picker option").first().textContent().contains("2026-10-30 09:00"));
            page.locator("#review-article-picker").selectOption("0");page.waitForSelector(".article-review-sheet:visible .topic-checklist input[value='"+TOPIC+"']");
            var current=page.locator(".article-review-sheet:visible");current.locator(".topic-checklist input[value='"+TOPIC+"']").uncheck();current.locator(".topic-checklist input[value='"+OTHER+"']").check();
            current.locator("button[type=submit]").first().click();page.waitForCondition(()->writes.size()==1);assertTrue(writes.getFirst().contains(OTHER));assertFalse(writes.getFirst().contains(TOPIC));
            assertEquals(0,page.locator("input[aria-label='수동 태그 UUID 목록']").count());assertFalse(current.innerText().contains(TOPIC));
            screenshots(page,"collected-date");
        }
    }

    @Test void topicCatalogSupportsNamedCreateEditExpressionsAndMergeWithoutTypedIds()throws Exception{
        try(var context=BrowserTest.browser.newContext()){
            SiteDesignAuditTest.routes(context,"ADMIN");var tags=new ArrayList<Map<String,Object>>(List.of(tag(TOPIC,"인공지능"),tag(OTHER,"검색")));var aliases=new ArrayList<Map<String,Object>>();var redirects=new ArrayList<Map<String,Object>>();var writes=new ArrayList<String>();
            context.route("**/api/v1/tags",route->route.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody(JSON.writeValueAsString(Map.of("tags",tags,"aliases",aliases,"redirects",redirects)))));
            context.route("**/api/v1/admin/tags**",route->{
                String path=URI.create(route.request().url()).getPath(),body=route.request().postData();writes.add(path+" "+body);var data=JSON.readTree(body);String response="{}";
                if(path.endsWith("/merge")){tags.removeIf(tag->tag.get("id").equals(NEW));redirects.add(Map.of("oldId",NEW,"canonicalId",TOPIC));}
                else if(path.endsWith("/aliases"))aliases.add(Map.of("alias",data.get("alias").asText(),"tagId",NEW));
                else if(path.endsWith("/"+NEW)){tags.stream().filter(tag->tag.get("id").equals(NEW)).findFirst().orElseThrow().put("name",data.get("name").asText());}
                else {tags.add(tag(NEW,data.get("name").asText()));response=JSON.writeValueAsString(Map.of("id",NEW));}
                route.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody(response));
            });
            var page=context.newPage();page.navigate(BrowserTest.origin+"/admin.html#topics/dictionary");page.waitForCondition(()->page.locator("#topic-edit-choice").isEnabled());
            page.locator("#tag-form [name=name]").fill("새 연구 주제");page.locator("#tag-form [name=category]").fill("기술");page.locator("#tag-form [type=submit]").click();
            page.waitForCondition(()->writes.size()==1&&page.locator("#tag-form [type=submit]").isEnabled());assertFalse(writes.getFirst().contains("slug"));
            assertEquals(NEW,page.locator("#topic-edit-choice").inputValue());page.locator("#tag-form [name=name]").fill("수정한 연구 주제");page.locator("#tag-form [type=submit]").click();
            page.waitForCondition(()->writes.size()==2&&page.locator("#tag-form [type=submit]").isEnabled());assertTrue(writes.getLast().contains("/tags/"+NEW));
            page.locator("#alias-form [name=tagId]").selectOption(NEW);page.locator("#alias-form [name=alias]").fill("Research equivalent");page.locator("#alias-form button").click();
            page.waitForCondition(()->writes.size()==3&&page.locator("#alias-form button").isEnabled());assertTrue(page.locator("#topic-catalog-list").innerText().contains("Research equivalent"));
            screenshots(page,"topic-catalog");
            page.locator("#merge-form [name=oldId]").selectOption(NEW);assertEquals(0,page.locator("#merge-form [name=canonicalId] option[value='"+NEW+"']").count());
            page.locator("#merge-form [name=canonicalId]").selectOption(TOPIC);page.onceDialog(Dialog::accept);page.locator("#merge-form button").click();
            page.waitForCondition(()->writes.size()==4&&page.locator("#merge-form button").isEnabled());assertTrue(writes.getLast().contains(TOPIC));
            assertEquals(0,page.locator("input[name=tagId],input[name=oldId],input[name=canonicalId],input[name=slug]").count());
            assertFalse(page.locator("#topics-panel-dictionary").innerText().contains(TOPIC));
        }
    }

    @Test void collectionJobsArePickedBySourceAndTime()throws Exception{
        for(String name:SiteDesignAuditTest.PAGES)try(var input=ArticleReviewTest.class.getResourceAsStream("/static/"+name+".html")){assertNotNull(input);String html=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);assertFalse(java.util.regex.Pattern.compile("<input\\b[^>]*(?:name=[\"\'](?:tagId|oldId|canonicalId|slug|siteId|sourceId|articleId|jobId|userId)[\"\']|id=[\"\'](?:job|tag|site|source|article|user)-id[\"\'])",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(html).find(),name+" requires an internal ID to be typed");}
        try(var context=BrowserTest.browser.newContext()){
            SiteDesignAuditTest.routes(context,"ADMIN");
            context.route("**/api/v1/admin/jobs**",route->{String body=route.request().url().endsWith("/"+JOB)?"{\"id\":\""+JOB+"\",\"status\":\"SUCCEEDED\",\"attempts\":1}":JSON.writeValueAsString(List.of(Map.of("id",JOB,"site_name","합성 기관","source_label","공식 연구 소식","status","SUCCEEDED","created_at","2026-10-30T01:00:00Z")));route.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody(body));});
            var page=context.newPage();page.navigate(BrowserTest.origin+"/admin.html#collection/sources");page.getByText("수집 작업 조회",new Page.GetByTextOptions().setExact(true)).click();page.locator("#refresh-jobs").click();
            page.waitForCondition(()->page.locator("#job-id option").count()==2);var option=page.locator("#job-id option").last();
            assertTrue(option.textContent().contains("공식 연구 소식"));assertFalse(option.textContent().contains(JOB));
            page.locator("#job-id").selectOption(JOB);page.locator("#job-query button").click();page.waitForFunction("document.querySelector('#source-result').textContent.includes('완료')");
            assertFalse(page.locator("#source-result").textContent().contains(JOB));assertEquals(0,page.locator("input#job-id").count());
        }
    }

    @Test void classificationRefreshFetchesExternalTopicsAndBlocksUnresolvedSelections()throws Exception{
        try(var context=BrowserTest.browser.newContext()){
            SiteDesignAuditTest.routes(context,"ADMIN");var external=new java.util.concurrent.atomic.AtomicBoolean();var missing=new java.util.concurrent.atomic.AtomicBoolean();
            var reads=new java.util.concurrent.atomic.AtomicInteger();var writes=new ArrayList<String>();
            context.route("**/api/v1/tags",route->{reads.incrementAndGet();var tags=new ArrayList<Map<String,Object>>(List.of(tag(TOPIC,"인공지능"),tag(OTHER,"검색")));if(external.get()&&!missing.get())tags.add(tag(NEW,"다른 관리자가 추가한 주제"));
                route.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody(JSON.writeValueAsString(Map.of("tags",tags,"aliases",List.of(),"redirects",List.of()))));});
            context.route("**/api/v1/admin/classification**",route->{
                if(route.request().method().equals("PUT")){writes.add(route.request().postData());route.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody("{}"));return;}
                var topics=new ArrayList<Map<String,Object>>(List.of(Map.of("id",TOPIC,"definition","Synthetic model evaluation")));if(external.get())topics.add(Map.of("id",NEW,"definition","External topic definition"));
                route.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody(JSON.writeValueAsString(Map.of("catalog",Map.of("version",external.get()?1:0,"topics",topics),"counts",List.of(),"runtime",Map.of(),"recent",List.of()))));
            });
            var page=context.newPage();page.navigate(BrowserTest.origin+"/admin.html#topics/classification");page.waitForSelector("#classify-"+TOPIC);int initialReads=reads.get();
            external.set(true);page.locator("#refresh-classification").click();page.waitForSelector("#classify-"+NEW);assertTrue(reads.get()>initialReads);assertTrue(page.locator("#classify-"+NEW).isChecked());
            screenshots(page,"classification");
            page.locator("#classification-form [type=submit]").click();page.waitForCondition(()->writes.size()==1&&page.locator("#classification-form [type=submit]").isEnabled());
            assertTrue(writes.getFirst().contains(NEW));assertTrue(writes.getFirst().contains(TOPIC));assertTrue(writes.getFirst().contains("\"version\":1"));
            missing.set(true);page.locator("#refresh-classification").click();page.waitForCondition(()->page.locator("#refresh-classification").isEnabled());
            assertTrue(page.locator("#classification-form [type=submit]").isDisabled());assertTrue(page.locator("#classification-message").textContent().contains("다시 불러온"));
            assertEquals(3,page.locator("#classification-topics textarea").count(),"Previous fields must remain instead of silently removing the unresolved topic");
            page.locator("#classification-form").evaluate("e=>e.requestSubmit()");assertEquals(1,writes.size());
        }
    }

    @Test void failedInitialCatalogLoadBlocksMutationsAndCanRecover(){
        try(var context=BrowserTest.browser.newContext()){
            SiteDesignAuditTest.routes(context,"ADMIN");var unavailable=new java.util.concurrent.atomic.AtomicBoolean(true);
            context.route("**/api/v1/tags",route->{
                if(unavailable.get())route.fulfill(new Route.FulfillOptions().setStatus(503).setContentType("application/json").setBody("{\"error\":\"temporarily_unavailable\"}"));
                else route.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody(JSON.writeValueAsString(Map.of("tags",List.of(tag(TOPIC,"인공지능")),"aliases",List.of(),"redirects",List.of()))));
            });
            var page=context.newPage();page.navigate(BrowserTest.origin+"/admin.html#topics/dictionary");
            page.waitForCondition(()->page.locator("#refresh-topic-catalog").isEnabled());
            assertTrue(page.locator("#tag-form [type=submit]").isDisabled());assertTrue(page.locator("#topic-edit-choice").isDisabled());assertTrue(page.locator("#new-topic").isDisabled());
            unavailable.set(false);page.locator("#refresh-topic-catalog").click();
            page.waitForCondition(()->page.locator("#topic-edit-choice").isEnabled());assertTrue(page.locator("#tag-form [type=submit]").isEnabled());
            assertTrue(page.locator("#topic-catalog-list").textContent().contains("인공지능"));
        }
    }
}
