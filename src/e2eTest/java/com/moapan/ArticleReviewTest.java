package com.moapan;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class ArticleReviewTest {
    @BeforeAll static void start()throws Exception{BrowserTest.start();}
    @AfterAll static void stop(){BrowserTest.stop();}
    static String articles(){
        return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(java.util.stream.IntStream.range(0,3).mapToObj(i->Map.of(
            "id","review-"+i,"title","합성 기사 / Synthetic review article "+i+" · 긴 제목으로 기사 구분과 가독성을 확인합니다",
            "summary_bullets","[\"Original summary "+i+"\"]","tag_ids","[\"tag-ai\"]","public",true,"summary_locked",false,"tags_locked",false)).toList());
    }
    static Page open(BrowserContext context){Page page=context.newPage();page.navigate(BrowserTest.origin+"/admin.html#articles");page.waitForLoadState(LoadState.NETWORKIDLE);return page;}

    @Test void oneArticleAtATimeIsReadableAtAllSizesAndLanguages()throws Exception{
        Path pictures=Path.of("build/reports/screenshots/article-review");Files.createDirectories(pictures);
        try(var context=BrowserTest.browser.newContext()){
            SiteDesignAuditTest.routes(context,"ADMIN");context.route("**/api/v1/admin/articles*",r->r.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody(articles())));
            var page=open(context);var errors=new ArrayList<String>();page.onPageError(errors::add);
            page.locator("#refresh-articles").click();page.waitForSelector(".article-review-sheet");
            assertEquals(1,page.locator(".article-review-sheet").count(),"Only the visited editor should be mounted");
            for(String language:List.of("ko","en"))for(int width:new int[]{390,768,1440})for(int index=0;index<3;index++){
                page.setViewportSize(width,1000);page.locator("[data-language-select]").selectOption(language);
                page.locator("#review-article-picker").selectOption(String.valueOf(index));
                assertEquals(1,page.locator(".article-review-sheet:visible").count());
                assertEquals((index+1)+" / 3",page.locator("#article-review-position").textContent());
                assertEquals(index==0,page.locator("#review-previous").isDisabled());assertEquals(index==2,page.locator("#review-next").isDisabled());
                assertTrue(page.locator(".article-review-sheet:visible h3").textContent().contains("article "+index));
                assertTrue((boolean)page.evaluate("document.documentElement.scrollWidth<=innerWidth"));
                assertEquals("double",page.locator(".article-review-sheet:visible").evaluate("e=>getComputedStyle(e).borderTopStyle"));
                page.screenshot(new Page.ScreenshotOptions().setPath(pictures.resolve(language+"-article-"+index+"-"+width+".png")).setFullPage(true));
            }
            assertTrue(errors.isEmpty(),errors.toString());
        }
    }

    @Test void summaryAndTranslationDraftsSurviveSwitchesAndSaveTheCorrectArticle(){
        try(var context=BrowserTest.browser.newContext()){
            SiteDesignAuditTest.routes(context,"ADMIN");var writes=new ArrayList<String>();var pending=new AtomicReference<Route>();var delayedRead=new AtomicReference<Route>();var translationReads=new AtomicInteger();
            context.route("**/api/v1/admin/articles**",r->{
                if(r.request().method().equals("PATCH")){writes.add(URI.create(r.request().url()).getPath()+" "+r.request().postData());pending.set(r);}
                else r.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody(articles()));
            });
            context.route("**/api/v1/admin/translations/review-*/*",r->{
                if(r.request().method().equals("PUT")){writes.add(r.request().url()+" "+r.request().postData());pending.set(r);}
                else if(translationReads.incrementAndGet()==2)delayedRead.set(r);
                else r.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody("{\"version\":1,\"contentHash\":\"synthetic-hash\",\"title\":\"Loaded translation\",\"summaryBullets\":[\"Loaded translated summary\"],\"locked\":false}"));
            });
            var page=open(context);page.locator("#refresh-articles").click();page.waitForSelector(".article-review-sheet");
            page.getByLabel("수동 요약 (한 줄 한 항목)").fill("Unsaved summary for article zero");
            page.locator(".article-review-sheet:visible > details > summary").click();page.getByText("번역 불러오기",new Page.GetByTextOptions().setExact(true)).click();
            page.waitForCondition(()->page.getByText("번역 저장",new Page.GetByTextOptions().setExact(true)).isEnabled());page.getByLabel("번역 제목").fill("Unsaved translation zero");page.getByLabel("번역 요약 (한 줄 한 항목)").fill("Unsaved translated summary zero");
            page.locator("#review-next").click();page.locator(".article-review-sheet:visible").getByLabel("수동 요약 (한 줄 한 항목)").fill("Unsaved summary one");page.locator("#review-previous").click();
            var zero=page.locator(".article-review-sheet:visible");
            assertEquals("Unsaved summary for article zero",zero.getByLabel("수동 요약 (한 줄 한 항목)").inputValue());assertEquals("Unsaved translation zero",zero.getByLabel("번역 제목").inputValue());
            page.locator("#sections-tab-advertising").click();page.locator("#sections-tab-articles").click();
            zero.getByText("요약·태그·공개 상태 저장",new Locator.GetByTextOptions().setExact(true)).click();page.waitForCondition(()->pending.get()!=null);
            assertTrue(page.locator("#refresh-articles").isDisabled());assertTrue(writes.getFirst().startsWith("/api/v1/admin/articles/review-0 "));assertTrue(writes.getFirst().contains("\"tagIds\":null"));
            pending.getAndSet(null).fulfill(new Route.FulfillOptions().setContentType("application/json").setBody("{}"));page.waitForCondition(()->page.locator("#refresh-articles").isEnabled());
            assertTrue(zero.locator(".article-review-dirty").isVisible(),"Translation is still unsaved");
            zero.getByText("번역 저장",new Locator.GetByTextOptions().setExact(true)).click();page.waitForCondition(()->pending.get()!=null);
            assertTrue(page.locator("#refresh-articles").isDisabled());assertTrue(zero.getByLabel("번역 언어").isDisabled());assertTrue(writes.getLast().contains("/translations/review-0/ko"));assertTrue(writes.getLast().contains("Unsaved translation zero"));
            pending.getAndSet(null).fulfill(new Route.FulfillOptions().setContentType("application/json").setBody("{}"));page.waitForCondition(()->page.locator("#refresh-articles").isEnabled());
            assertFalse(zero.locator(".article-review-dirty").isVisible());
            page.waitForCondition(()->delayedRead.get()!=null);
            zero.getByLabel("번역 제목").fill("New draft during delayed refresh");
            delayedRead.getAndSet(null).fulfill(new Route.FulfillOptions().setContentType("application/json").setBody("{\"version\":2,\"contentHash\":\"synthetic-hash\",\"title\":\"Committed translation\",\"summaryBullets\":[\"Committed summary\"],\"locked\":false}"));
            page.waitForCondition(()->zero.getByText("번역 저장",new Locator.GetByTextOptions().setExact(true)).isEnabled());
            assertEquals("New draft during delayed refresh",zero.getByLabel("번역 제목").inputValue());
            assertTrue(zero.locator(".article-review-dirty").isVisible());
            zero.getByText("번역 저장",new Locator.GetByTextOptions().setExact(true)).click();page.waitForCondition(()->pending.get()!=null);
            assertTrue(writes.getLast().contains("\"version\":2"));
            assertTrue(writes.getLast().contains("New draft during delayed refresh"));
            pending.getAndSet(null).fulfill(new Route.FulfillOptions().setContentType("application/json").setBody("{}"));
            page.waitForCondition(()->page.locator("#refresh-articles").isEnabled());
            page.locator("#review-next").click();assertEquals("Unsaved summary one",page.locator(".article-review-sheet:visible").getByLabel("수동 요약 (한 줄 한 항목)").inputValue());
        }
    }

    @Test void reloadCancellationErrorsAndEmptyResultsDoNotLoseEdits(){
        try(var context=BrowserTest.browser.newContext()){
            SiteDesignAuditTest.routes(context,"ADMIN");var calls=new AtomicInteger();var mode=new AtomicInteger();var pending=new AtomicReference<Route>();
            context.route("**/api/v1/admin/articles*",r->{calls.incrementAndGet();if(mode.get()==2){pending.set(r);return;}
                r.fulfill(new Route.FulfillOptions().setContentType("application/json").setStatus(mode.get()==1?503:200).setBody(mode.get()==1?"{}":mode.get()==3?"[]":articles()));});
            var page=open(context);page.locator("#refresh-articles").click();page.waitForSelector(".article-review-sheet");
            page.getByLabel("수동 요약 (한 줄 한 항목)").fill("Keep my synthetic draft");
            page.onceDialog(Dialog::dismiss);page.locator("#refresh-articles").click();assertEquals(1,calls.get());assertEquals("Keep my synthetic draft",page.getByLabel("수동 요약 (한 줄 한 항목)").inputValue());
            mode.set(1);page.onceDialog(Dialog::accept);page.locator("#refresh-articles").click();page.waitForFunction("document.querySelector('#article-review-status').textContent.includes('일시적으로')");
            assertEquals("Keep my synthetic draft",page.getByLabel("수동 요약 (한 줄 한 항목)").inputValue());
            mode.set(2);page.onceDialog(Dialog::accept);page.locator("#refresh-articles").click();page.waitForCondition(()->pending.get()!=null);
            assertTrue(page.locator("#refresh-articles").isDisabled());assertEquals(true,page.locator("#article-list").evaluate("e=>e.inert"));
            pending.getAndSet(null).fulfill(new Route.FulfillOptions().setContentType("application/json").setBody(articles()));page.waitForCondition(()->page.locator("#refresh-articles").isEnabled());
            assertEquals("Original summary 0",page.getByLabel("수동 요약 (한 줄 한 항목)").inputValue());
            mode.set(3);page.locator("#refresh-articles").click();page.waitForFunction("document.querySelector('#article-review-status').textContent.includes('기사가 없습니다')");
            assertEquals(0,page.locator(".article-review-sheet").count());assertFalse(page.locator("#article-review-toolbar").isVisible());
        }
    }
}
