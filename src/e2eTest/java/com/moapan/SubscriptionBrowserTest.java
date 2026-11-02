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
    @Test void lostPermissionUsesErrorRouteAndErrorPageRemainsBilingual(){
        try(var c=BrowserTest.browser.newContext()){
            SiteDesignAuditTest.routes(c,"USER");viewer(c,"USER",new AtomicBoolean(false));var p=c.newPage();p.navigate(BrowserTest.origin+"/newsletter.html");p.waitForURL("**/404.html");p.waitForLoadState(LoadState.NETWORKIDLE);assertEquals("페이지를 찾을 수 없습니다",p.locator("h1").textContent());p.locator("[data-language-select]").selectOption("en");assertEquals("Page not found",p.locator("h1").textContent());assertTrue(p.title().startsWith("Page not found"));assertEquals(0,p.locator("#letter-body").count());
        }
    }
}
