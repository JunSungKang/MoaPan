package com.moapan;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic presentation fixtures; actual HTTP entitlement enforcement is in PostgreSQL integration tests. */
class SubscriptionBrowserTest {
    @BeforeAll static void start()throws Exception{BrowserTest.start();}
    @AfterAll static void stop(){BrowserTest.stop();}
    final JsonMapper json=JsonMapper.builder().build();
    private void viewer(BrowserContext context,String role,AtomicBoolean paid){context.route("**/api/v1/auth/session",r->r.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody(json.writeValueAsString(Map.of("id",BrowserTest.A,"username","synthetic_subscription","role",role,"canReadNewsletter",paid.get())))));}
    @Test void allMemberChromeHidesNewsletterForUnsubscribedIncludingEditors() {
        for(String role:List.of("USER","ADMIN"))try(var c=BrowserTest.browser.newContext()){
            SiteDesignAuditTest.routes(c,role);var paid=new AtomicBoolean(false);viewer(c,role,paid);var p=c.newPage();var calls=new AtomicInteger();p.onRequest(r->{if(r.url().contains("/api/v1/newsletters"))calls.incrementAndGet();});
            var pages=new ArrayList<>(List.of("index","about","privacy","terms","settings","account"));if(role.equals("ADMIN"))pages.addAll(List.of("admin","newsletter-editor"));
            for(String page:pages){p.navigate(BrowserTest.origin+"/"+page+".html");p.waitForLoadState(LoadState.NETWORKIDLE);assertEquals(0,p.locator("a[href='/newsletter.html']:visible").count(),page);}
            assertEquals(0,calls.get());paid.set(true);p.navigate(BrowserTest.origin+"/index.html");p.waitForLoadState(LoadState.NETWORKIDLE);assertTrue(p.locator(".content-menu a[href='/newsletter.html']").isVisible());assertTrue(calls.get()>0);
        }
    }
    @Test void adminCanGrantAndRevokeUsingUsernameAndKoreaTimeWithNoRawIdentifiers()throws Exception{
        try(var c=BrowserTest.browser.newContext()){
            SiteDesignAuditTest.routes(c,"ADMIN");viewer(c,"ADMIN",new AtomicBoolean(false));var version=new AtomicInteger();List<Map<String,Object>> mutations=new ArrayList<>();
            c.route("**/api/v1/admin/subscriptions/**",r->{
                if(r.request().url().endsWith("/search")){r.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody(json.writeValueAsString(Map.of("items",List.of(Map.of("id",BrowserTest.B,"username","synthetic_reader","role","USER","active",true,"subscriptionStatus",version.get()==0?"NONE":version.get()==1?"ACTIVE":"REVOKED","version",version.get())),"page",0,"hasNext",false))));}
                else{assertEquals("synthetic-csrf",r.request().headers().get("x-csrf-token"));mutations.add(json.readValue(r.request().postData(),Map.class));version.incrementAndGet();r.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody("{}"));}
            });
            var p=c.newPage();p.navigate(BrowserTest.origin+"/admin.html#subscriptions");p.getByLabel("아이디 검색",new Page.GetByLabelOptions().setExact(true)).fill("synthetic_reader");p.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,new Page.GetByRoleOptions().setName("계정 조회").setExact(true)).click();p.locator("#subscription-accounts button").click();
            p.locator("#subscription-form [name=startsAt]").fill("2026-11-01T09:00");p.locator("#subscription-form [name=expiresAt]").fill("2026-12-01T09:00");p.locator("#subscription-form [type=submit]").click();p.waitForFunction("document.querySelector('#subscription-status').textContent==='구독 이용 기간을 저장했습니다.'");
            assertEquals("2026-11-01T00:00:00.000Z",mutations.getFirst().get("startsAt"));assertEquals("2026-12-01T00:00:00.000Z",mutations.getFirst().get("expiresAt"));
            p.locator("#subscription-accounts button").click();p.locator("#subscription-revoke").click();p.waitForFunction("document.querySelector('#subscription-status').textContent==='구독 권한을 회수했습니다.'");assertEquals(1,((Number)mutations.getLast().get("version")).intValue());
            assertFalse(p.locator("#sections-panel-subscriptions").innerText().contains(BrowserTest.B));assertFalse(p.url().contains("synthetic_reader"));assertFalse((boolean)p.evaluate("JSON.stringify({...localStorage,...sessionStorage}).includes('synthetic_reader')"));
            for(int width:new int[]{390,768,1440}){p.setViewportSize(width,1000);assertTrue((boolean)p.evaluate("document.documentElement.scrollWidth<=innerWidth"));Path target=Path.of("build/reports/screenshots/subscription/admin-"+width+".png");Files.createDirectories(target.getParent());p.screenshot(new Page.ScreenshotOptions().setPath(target).setFullPage(true));}
            p.locator("[data-language-select]").selectOption("en");assertEquals("Subscription access",p.locator("#sections-panel-subscriptions h2").textContent());
        }
    }
    @Test void accountListLoadsOnFirstTabEntryAndChangesSortSizeAndPagesWithoutSearchClicks()throws Exception {
        try(var c=BrowserTest.browser.newContext()){
            SiteDesignAuditTest.routes(c,"ADMIN");viewer(c,"ADMIN",new AtomicBoolean(false));var calls=new ArrayList<Map<String,Object>>();
            c.route("**/api/v1/admin/subscriptions/search",r->{
                var input=json.readValue(r.request().postData(),Map.class);calls.add(input);assertFalse(r.request().url().contains("?"));assertEquals("synthetic-csrf",r.request().headers().get("x-csrf-token"));
                int size=((Number)input.get("size")).intValue(),page=((Number)input.get("page")).intValue();var rows=new ArrayList<Map<String,Object>>();
                for(int i=page*size;i<Math.min(105,(page+1)*size);i++)rows.add(Map.of("id",new UUID(0,i+1).toString(),"username","synthetic_reader_"+i,"role","USER","active",true,"subscriptionStatus","ACTIVE","version",1,"expiresAt","2026-12-01T00:00:00Z"));
                r.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody(json.writeValueAsString(Map.of("items",rows,"page",page,"hasNext",(page+1)*size<105))));
            });
            var p=c.newPage();List<String> errors=new ArrayList<>();p.onPageError(errors::add);p.navigate(BrowserTest.origin+"/admin.html#collection/list");p.waitForLoadState(LoadState.NETWORKIDLE);assertTrue(calls.isEmpty(),"No hidden-tab lookup");
            p.locator("#sections-tab-subscriptions").click();p.waitForFunction("document.querySelectorAll('#subscription-accounts tr').length===10");assertEquals("RECENT",calls.getFirst().get("sort"));assertEquals(10,((Number)calls.getFirst().get("size")).intValue());
            assertEquals(List.of("10","30","50","100"),p.locator("#subscription-search [name=size] option").evaluateAll("options=>options.map(o=>o.value)"));
            for(int size:List.of(30,50,100,10)){
                p.locator("#subscription-search [name=size]").selectOption(String.valueOf(size));p.waitForFunction("n=>document.querySelectorAll('#subscription-accounts tr').length===n",size);assertEquals(0,((Number)calls.getLast().get("page")).intValue());
            }
            p.locator("#subscription-next").click();p.waitForFunction("document.querySelector('#subscription-page').textContent==='2'");assertEquals(1,((Number)calls.getLast().get("page")).intValue());
            p.locator("#subscription-search [name=sort]").selectOption("EXPIRING");p.waitForFunction("document.querySelector('#subscription-page').textContent==='1' && document.querySelectorAll('#subscription-accounts tr').length===10");assertEquals("EXPIRING",calls.getLast().get("sort"));assertEquals(0,((Number)calls.getLast().get("page")).intValue());
            p.locator("#subscription-search [name=username]").fill("synthetic_private_search");p.locator("#subscription-search [type=submit]").click();p.waitForFunction("document.querySelector('#subscription-status').textContent==='계정을 선택해 이용 기간을 설정하세요.'");assertEquals("synthetic_private_search",calls.getLast().get("username"));assertFalse(p.url().contains("synthetic_private_search"));assertFalse((boolean)p.evaluate("JSON.stringify({...localStorage,...sessionStorage}).includes('synthetic_private_search')"));
            for(int width:new int[]{390,768,1440}){p.setViewportSize(width,1000);p.evaluate("window.scrollTo(0,0);new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)))");assertEquals(1,p.locator("header").count());assertTrue((boolean)p.evaluate("document.documentElement.scrollWidth<=innerWidth"));Path target=Path.of("build/reports/screenshots/subscription/list-"+width+".png");Files.createDirectories(target.getParent());p.screenshot(new Page.ScreenshotOptions().setPath(target).setFullPage(true));}
            p.locator("[data-language-select]").selectOption("en");assertEquals("Expiring soon",p.locator("#subscription-search [name=sort] option[value=EXPIRING]").textContent());assertEquals("10 accounts",p.locator("#subscription-search [name=size] option[value='10']").textContent());assertTrue(errors.isEmpty(),errors.toString());
        }
    }
    @Test void lostPermissionUsesErrorRouteAndErrorPageRemainsBilingual(){
        try(var c=BrowserTest.browser.newContext()){
            SiteDesignAuditTest.routes(c,"USER");viewer(c,"USER",new AtomicBoolean(false));var p=c.newPage();p.navigate(BrowserTest.origin+"/newsletter.html");p.waitForURL("**/404.html");p.waitForLoadState(LoadState.NETWORKIDLE);assertEquals("페이지를 찾을 수 없습니다",p.locator("h1").textContent());p.locator("[data-language-select]").selectOption("en");assertEquals("Page not found",p.locator("h1").textContent());assertTrue(p.title().startsWith("Page not found"));assertEquals(0,p.locator("#letter-body").count());
        }
    }
    @Test void alreadyLoadedNewsletterCleansUpOnAuthenticationLossWithoutReferenceErrors(){
        try(var c=BrowserTest.browser.newContext()){
            SiteDesignAuditTest.routes(c,"USER");viewer(c,"USER",new AtomicBoolean(true));var p=c.newPage();List<String> errors=new ArrayList<>();p.onPageError(errors::add);
            p.navigate(BrowserTest.origin+"/newsletter.html?id="+BrowserTest.A);p.waitForSelector("#letter-body:visible");
            p.evaluate("window.addEventListener('moapan-clear',()=>sessionStorage.setItem('synthetic-newsletter-cleared','1'));window.dispatchEvent(new Event('moapan-auth-lost'))");
            p.waitForURL("**/404.html");p.waitForLoadState(LoadState.NETWORKIDLE);assertEquals("1",p.evaluate("sessionStorage.getItem('synthetic-newsletter-cleared')"));assertEquals(0,p.locator("#letter-body").count());assertTrue(errors.isEmpty(),errors.toString());
        }
    }
    @Test void initialAnonymousSession204And401BothReach404WithoutScriptErrors(){
        for(int status:List.of(204,401))try(var c=BrowserTest.browser.newContext()){
            SiteDesignAuditTest.routes(c,"USER");c.route("**/api/v1/auth/session",r->r.fulfill(new Route.FulfillOptions().setStatus(status).setContentType("application/json").setBody(status==204?"":"{\"error\":\"authentication_required\"}")));var p=c.newPage();List<String> errors=new ArrayList<>();p.onPageError(errors::add);
            p.navigate(BrowserTest.origin+"/newsletter.html?id="+BrowserTest.A);p.waitForURL("**/404.html");p.waitForLoadState(LoadState.NETWORKIDLE);assertEquals(0,p.locator("#letter-body").count());assertTrue(errors.isEmpty(),errors.toString());
        }
    }
    @Test void everyReuseAndWorksheetButtonClearsLoadedContentAfterServer404(){
        for(String button:List.of("출처 포함 복사","재게재용 파일","회의용 요약","연구 우선순위표","실험·평가 양식"))try(var c=BrowserTest.browser.newContext()){
            SiteDesignAuditTest.routes(c,"USER");viewer(c,"USER",new AtomicBoolean(true));c.route("**/api/v1/newsletters/*/reuse",r->r.fulfill(new Route.FulfillOptions().setStatus(404).setContentType("application/json").setBody("{\"error\":\"not_found\"}")));c.route("**/api/v1/newsletters/*/worksheet",r->r.fulfill(new Route.FulfillOptions().setStatus(404).setContentType("application/json").setBody("{\"error\":\"not_found\"}")));
            var p=c.newPage();List<String> errors=new ArrayList<>();var downloads=new AtomicInteger();p.onPageError(errors::add);p.onDownload(d->downloads.incrementAndGet());p.navigate(BrowserTest.origin+"/newsletter.html?id="+BrowserTest.A);p.waitForSelector("#letter-body:visible");p.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,new Page.GetByRoleOptions().setName(button).setExact(true)).click();
            p.waitForURL("**/404.html");p.waitForLoadState(LoadState.NETWORKIDLE);assertEquals(0,p.locator("#letter-body").count());assertEquals(0,downloads.get());assertTrue(errors.isEmpty(),errors.toString());
        }
    }
    @Test void unavailableOptionalNewsletterNeverClearsPublicNewsOrPersonalization(){
        try(var c=BrowserTest.browser.newContext()){
            SiteDesignAuditTest.routes(c,"USER");viewer(c,"USER",new AtomicBoolean(true));c.route("**/api/v1/newsletters",r->r.fulfill(new Route.FulfillOptions().setStatus(404).setContentType("application/json").setBody("{\"error\":\"not_found\"}")));var p=c.newPage();List<String> errors=new ArrayList<>();p.onPageError(errors::add);
            p.navigate(BrowserTest.origin+"/index.html");p.waitForLoadState(LoadState.NETWORKIDLE);assertEquals(7,p.locator("#paper article").count());assertTrue(p.url().endsWith("/index.html"));assertEquals(0,p.locator("[data-newsletter-content]").count());assertEquals(0,p.locator(".content-menu a[href='/newsletter.html']:visible").count());assertTrue(p.locator("#account-menu a[href='/account.html']").isVisible());assertTrue(errors.isEmpty(),errors.toString());
        }
    }
}
