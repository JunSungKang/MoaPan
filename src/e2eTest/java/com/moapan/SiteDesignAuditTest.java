package com.moapan;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.Media;
import org.junit.jupiter.api.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** All pages use synthetic API fixtures; no production accounts or article content. */
class SiteDesignAuditTest {
    static final List<String> PAGES=List.of("index","about","privacy","terms","login","signup","settings","account","admin","newsletter","newsletter-editor");
    @BeforeAll static void start()throws Exception{BrowserTest.start();}
    @AfterAll static void stop(){BrowserTest.stop();}
    static void routes(BrowserContext context,String role){
        var json=tools.jackson.databind.json.JsonMapper.builder().build();
        context.route("**/api/v1/**",r->{
            String path=URI.create(r.request().url()).getPath(),page=r.request().frame().url();
            String body=null;int status=200;
            if(path.equals("/api/v1/auth/session")){
                if(role.equals("GUEST")||page.contains("/login.html")||page.contains("/signup.html")){body="";status=204;}
                else body=json.writeValueAsString(Map.of("id",BrowserTest.A,"username","synthetic_layout_user","role",role,"canReadNewsletter",true));
            }else if(path.equals("/api/v1/newsletters"))body=json.writeValueAsString(List.of(Map.of("id",BrowserTest.A,"title","합성 뉴스레터 · Synthetic newsletter","introduction","화면 검증용 원고입니다.","topic","AI","published_at","2026-10-30T08:00:00Z")));
            else if(path.equals("/api/v1/newsletters/"+BrowserTest.A))body=json.writeValueAsString(BrowserTest.productIssue());
            else if(path.equals("/api/v1/admin/interests"))body="{\"version\":0,\"weights\":{\"ai\":3}}";
            else if(path.equals("/api/v1/admin/classification"))body="{\"catalog\":{\"version\":0,\"topics\":[]},\"counts\":[],\"runtime\":{},\"recent\":[]}";
            else if(path.equals("/api/v1/admin/classification/topics"))body="{\"version\":0,\"topics\":[]}";
            else if(path.startsWith("/api/v1/admin/")&&!path.equals("/api/v1/admin/advertisements"))body="[]";
            if(body==null)r.fallback();else r.fulfill(new Route.FulfillOptions().setStatus(status).setContentType("application/json").setBody(body));
        });
    }
    @Test void allPagesAndDetailShareChromeWithoutOverlappingLinks()throws Exception{
        var evidence=new ArrayList<Map<String,Object>>();
        Path pictures=Path.of("build/reports/screenshots/site-consistency");Files.createDirectories(pictures);
        try(var context=BrowserTest.browser.newContext()){
            routes(context,"ADMIN");Page p=context.newPage();List<String> errors=new ArrayList<>();p.onPageError(errors::add);
            List<String> pages=new ArrayList<>(PAGES);pages.add("newsletter.html?id="+BrowserTest.A);
            for(String lang:List.of("ko","en"))for(int width:new int[]{390,768,1440})for(String name:pages){
                String path=name.contains(".html")?name:name+".html";String label=lang+"-"+name.split("\\.")[0]+(name.contains("?")?"-detail":"")+"-"+width;
                p.setViewportSize(width,1000);p.navigate(BrowserTest.origin+"/"+path);p.waitForLoadState(LoadState.NETWORKIDLE);
                p.locator("[data-language-select]").selectOption(lang);p.waitForFunction("document.documentElement.lang=== '"+lang+"'");
                assertTrue((boolean)p.evaluate("document.documentElement.scrollWidth<=innerWidth"),label+" horizontal overflow");
                assertEquals(1,p.locator("h1:visible").count(),label+" needs one visible page heading");
                if(!List.of("login","signup").contains(name)){
                    var links=p.locator(".content-menu a:visible");assertEquals(2,links.count(),label);
                    var first=links.nth(0).boundingBox();var second=links.nth(1).boundingBox();
                    assertTrue(second.x-(first.x+first.width)>=12,label+" links must have separate hit areas");
                    var style=(List<?>)links.first().evaluate("e=>{const s=getComputedStyle(e.parentElement);return[s.fontFamily,s.fontSize,s.lineHeight,s.display]}");
                    assertEquals(List.of("14px","21px","flex"),style.subList(1,4),label);
                    assertTrue(style.get(0).toString().startsWith("system-ui"),label);
                    if(name.equals("index")){
                        assertEquals(1,p.locator(".content-menu a[aria-current=page]").count());
                        assertTrue((boolean)p.locator("#open-search").evaluate("e=>{const text=[...e.childNodes].find(n=>n.nodeType===Node.TEXT_NODE&&n.textContent.trim());const r=document.createRange();r.selectNodeContents(text);return r.getClientRects().length===1}"),label+" search label must stay on one line");
                    }
                    evidence.add(Map.of("page",path,"language",lang,"width",width,"navigationGap",second.x-first.x-first.width,"font",style,"articles",p.locator("#paper article").count(),"headings",1,"overflow",false));
                }else evidence.add(Map.of("page",path,"language",lang,"width",width,"headings",1,"overflow",false));
                assertEquals(List.of("login","signup").contains(name)?2:3,p.locator("footer a").count(),label);
                var footer=p.locator("footer").evaluate("e=>{const s=getComputedStyle(e);return[s.fontFamily,s.fontSize]}");
                assertTrue(((List<?>)footer).get(0).toString().startsWith("system-ui"),label+" footer font");
                assertEquals("13px",((List<?>)footer).get(1),label+" footer size");
                p.screenshot(new Page.ScreenshotOptions().setPath(pictures.resolve(label+".png")));
            }
            assertTrue(errors.isEmpty(),"Unexpected JavaScript errors: "+errors);
        }
        Files.writeString(Path.of("build/reports/site-design-matrix.json"),tools.jackson.databind.json.JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
    }
    @Test void navigationKeepsGuestBoundariesKeyboardAndPrintBehavior(){
        for(String role:List.of("GUEST","USER","ADMIN"))try(var context=BrowserTest.browser.newContext()){
            routes(context,role);Page p=context.newPage();
            for(String name:List.of("index","about","terms","privacy")){
                p.navigate(BrowserTest.origin+"/"+name+".html");p.waitForLoadState(LoadState.NETWORKIDLE);
                assertEquals(role.equals("GUEST")?1:2,p.locator(".content-menu a:visible").count(),name+" "+role);
                assertEquals(role.equals("ADMIN")?1:0,p.locator("#account-menu a[href='/newsletter-editor.html']:visible").count());
            }
            if(!role.equals("GUEST")){
                p.locator(".content-menu a").first().focus();p.keyboard().press("Tab");
                assertTrue((boolean)p.locator(".content-menu a").last().evaluate("e=>e===document.activeElement && getComputedStyle(e).outlineStyle!=='none'"));
            }
            p.emulateMedia(new Page.EmulateMediaOptions().setMedia(Media.PRINT));assertFalse(p.locator(".content-menu").isVisible());
        }
    }
}
