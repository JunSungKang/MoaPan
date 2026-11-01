package com.moapan;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Management layout uses synthetic fixtures and never changes the running service. */
class AdminNavigationTest {
    @BeforeAll static void start() throws Exception { BrowserTest.start(); }
    @AfterAll static void stop() { BrowserTest.stop(); }

    @Test void allSectionsAndSubsectionsFitBothLanguages() throws Exception {
        Path pictures=Path.of("build/reports/screenshots/admin-navigation");Files.createDirectories(pictures);
        var evidence=new ArrayList<Map<String,Object>>();
        try(var context=BrowserTest.browser.newContext()) {
            SiteDesignAuditTest.routes(context,"ADMIN");
            Page page=context.newPage();var errors=new ArrayList<String>();page.onPageError(errors::add);
            for(String language:List.of("ko","en"))for(int width:new int[]{390,768,1440}) {
                page.setViewportSize(width,1000);page.navigate(BrowserTest.origin+"/admin.html");page.waitForLoadState(LoadState.NETWORKIDLE);
                page.locator("[data-language-select]").selectOption(language);
                for(String section:List.of("collection","articles","topics","interests","advertising","processing")) {
                    page.locator("#sections-tab-"+section).click();
                    checkAndCapture(page,pictures,evidence,language,width,section);
                }
                page.locator("#sections-tab-collection").click();
                for(String section:List.of("site","sources")) {
                    page.locator("#collection-tab-"+section).click();
                    if(section.equals("sources"))page.locator("#new-source").click();
                    checkAndCapture(page,pictures,evidence,language,width,"collection-"+section);
                }
                page.locator("#sections-tab-topics").click();page.locator("#topics-tab-dictionary").click();
                checkAndCapture(page,pictures,evidence,language,width,"topics-dictionary");
            }
            assertTrue(errors.isEmpty(),"JavaScript errors: "+errors);
        }
        assertEquals(54,evidence.size());
        Files.writeString(Path.of("build/reports/admin-navigation-matrix.json"),tools.jackson.databind.json.JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
    }

    static void checkAndCapture(Page page,Path pictures,List<Map<String,Object>> evidence,String language,int width,String section) {
        assertEquals(1,page.locator(".admin-workspace:visible").count(),section);
        assertEquals(1,page.locator(".admin-tabs-primary [aria-selected=true]").count());
        assertTrue((boolean)page.evaluate("document.documentElement.scrollWidth<=innerWidth"),section+" "+width+" overflow");
        assertTrue((boolean)page.locator(".admin-tabs-primary [aria-selected=true]").evaluate("e=>{const b=e.getBoundingClientRect();return b.width>=44&&b.height>=44}"));
        if(section.startsWith("collection")||section.startsWith("topics"))assertEquals(1,page.locator(".admin-subsection:visible").count());
        assertEquals(1,page.locator("h1:visible").count());
        if(language.equals("en"))assertEquals("Article review",page.locator("#sections-tab-articles").textContent());
        page.screenshot(new Page.ScreenshotOptions().setPath(pictures.resolve(language+"-"+section+"-"+width+".png")));
        evidence.add(Map.of("language",language,"width",width,"section",section,"visibleWorkspaces",1,"overflow",false));
    }

    @Test void switchingKeyboardDeepLinksAndHistoryPreserveDrafts() {
        try(var context=BrowserTest.browser.newContext()) {
            SiteDesignAuditTest.routes(context,"ADMIN");var page=context.newPage();
            page.navigate(BrowserTest.origin+"/admin.html#collection/site");page.waitForSelector("#site-form");
            page.locator("#site-form [name=name]").fill("Unsaved synthetic site");
            page.locator("#sections-tab-advertising").click();page.locator("#advertising-form [name=title]").fill("Unsaved synthetic banner");
            page.locator("#sections-tab-collection").click();
            assertTrue(page.locator("#collection-panel-site").isVisible());
            assertEquals("Unsaved synthetic site",page.locator("#site-form [name=name]").inputValue());
            page.goBack();assertTrue(page.locator("#sections-panel-advertising").isVisible());
            assertEquals("Unsaved synthetic banner",page.locator("#advertising-form [name=title]").inputValue());
            page.goForward();assertTrue(page.locator("#collection-panel-site").isVisible());
            page.locator("#sections-tab-collection").focus();page.keyboard().press("ArrowRight");
            assertTrue(page.locator("#sections-panel-articles").isVisible());
            assertTrue((boolean)page.locator("#sections-tab-articles").evaluate("e=>e===document.activeElement"));
            page.keyboard().press("End");assertTrue(page.locator("#sections-panel-processing").isVisible());
            page.keyboard().press("Home");assertTrue(page.locator("#sections-panel-collection").isVisible());
            page.locator("#collection-tab-site").focus();page.keyboard().press("ArrowRight");
            assertTrue(page.locator("#collection-panel-sources").isVisible());
            page.locator("#new-source").click();page.locator("#source-form [name=label]").fill("Unsaved synthetic feed");
            page.locator("#sections-tab-articles").click();page.locator("#sections-tab-collection").click();
            assertEquals("Unsaved synthetic feed",page.locator("#source-form [name=label]").inputValue());
            page.navigate(BrowserTest.origin+"/admin.html#topics/dictionary");page.waitForSelector("#topics-panel-dictionary");
            page.navigate(BrowserTest.origin+"/admin.html#unrecognized/arbitrary");page.waitForSelector("#collection-panel-list");
            page.locator("#new-site").click();assertTrue(page.locator("#collection-panel-site").isVisible());
            assertTrue((boolean)page.locator("#site-form [name=name]").evaluate("e=>e===document.activeElement"));
        }
    }

    @Test void delayedSiteDetailDoesNotTakeOverAnotherSection() {
        try(var context=BrowserTest.browser.newContext()) {
            SiteDesignAuditTest.routes(context,"ADMIN");
            var pending=new java.util.concurrent.atomic.AtomicReference<Route>();
            context.route("**/api/v1/admin/sites**",route->{
                String path=java.net.URI.create(route.request().url()).getPath();
                if(path.endsWith("/synthetic-site")) {pending.set(route);return;}
                String body=path.endsWith("/sources")?"[]":"[{\"id\":\"synthetic-site\",\"name\":\"Synthetic site\",\"homepage_url\":\"https://example.test\"}]";
                route.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody(body));
            });
            var page=context.newPage();page.navigate(BrowserTest.origin+"/admin.html");
            page.locator("#sites tr").first().getByText("상세·수정").click();page.waitForCondition(()->pending.get()!=null);
            page.locator("#sections-tab-advertising").click();
            pending.get().fulfill(new Route.FulfillOptions().setContentType("application/json").setBody("{\"id\":\"synthetic-site\",\"name\":\"Synthetic site\",\"version\":0,\"homepage_url\":\"https://example.test\"}"));
            page.waitForFunction("document.querySelector('#site-form [name=name]').value==='Synthetic site'");
            assertTrue(page.locator("#sections-panel-advertising").isVisible());
            assertEquals("#advertising",(String)page.evaluate("location.hash"));
            page.locator("#sections-tab-collection").click();page.locator("#collection-tab-site").click();
            assertEquals("Synthetic site",page.locator("#site-form [name=name]").inputValue());
        }
    }

    @Test void sourceActionResultsRemainVisibleWithJobLookupClosed() {
        try(var context=BrowserTest.browser.newContext()) {
            SiteDesignAuditTest.routes(context,"ADMIN");
            context.route("**/api/v1/admin/sites**",route->{
                String path=java.net.URI.create(route.request().url()).getPath();
                String body=path.endsWith("/sources")?"[{\"id\":\"synthetic-feed\",\"label\":\"Synthetic feed\",\"type\":\"RSS\"}]"
                    :path.endsWith("/synthetic-site")?"{\"id\":\"synthetic-site\",\"name\":\"Synthetic site\",\"version\":0}"
                    :"[{\"id\":\"synthetic-site\",\"name\":\"Synthetic site\",\"homepage_url\":\"https://example.test\"}]";
                route.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody(body));
            });
            context.route("**/api/v1/admin/sources/synthetic-feed/*",route->{
                boolean collect=route.request().url().endsWith("/collect");
                route.fulfill(new Route.FulfillOptions().setContentType("application/json").setStatus(collect?202:200).setBody(collect?"{\"id\":\"synthetic-job-id\",\"status\":\"QUEUED\"}":"{\"preview\":true}"));
            });
            Page page=context.newPage();page.navigate(BrowserTest.origin+"/admin.html");
            page.locator("#sites tr").first().getByText("상세·수정").click();page.locator("#manage-sources").click();
            assertFalse((boolean)page.locator("#job-query").evaluate("e=>e.closest('details').open"));
            page.locator("#source-list").getByText("미리보기",new Locator.GetByTextOptions().setExact(true)).click();
            page.waitForFunction("document.querySelector('#source-result').textContent.includes('preview')");
            assertTrue(page.locator("#source-result").isVisible(),"Preview result must be visible without expanding the job lookup");
            page.locator("#source-list").getByText("지금 수집",new Locator.GetByTextOptions().setExact(true)).click();
            page.waitForFunction("document.querySelector('#source-result').textContent.includes('synthetic-job-id')");
            assertTrue(page.locator("#source-result").isVisible());
            assertTrue(page.locator("#source-result").textContent().contains("수집 성공이 아닙니다"));
            assertEquals("synthetic-job-id",page.locator("#job-id").inputValue());
            assertFalse((boolean)page.locator("#job-query").evaluate("e=>e.closest('details').open"));
        }
    }

    @Test void tabsDoNotGrantManagementToMembersOrGuests() {
        for(String role:List.of("USER","GUEST"))try(var context=BrowserTest.browser.newContext()) {
            SiteDesignAuditTest.routes(context,role);var requests=new ArrayList<String>();
            context.onRequest(request->{if(request.url().contains("/api/v1/admin/"))requests.add(request.url());});
            Page page=context.newPage();page.navigate(BrowserTest.origin+"/admin.html#advertising");
            page.waitForURL(role.equals("GUEST")?"**/login.html":"**/index.html");
            assertEquals(0,page.locator(".admin-tabs:visible").count());assertTrue(requests.isEmpty());
        }
    }
}
