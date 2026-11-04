package com.moapan;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP/Spring/PostgreSQL, disposable DB, Chromium cache enabled (no request interception). */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.flyway.enabled=true","moapan.secret=synthetic-performance-only-secret-32-characters","moapan.public-base-url=http://localhost","server.servlet.session.cookie.secure=false"})
class WebPerformanceTest {
    static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>(DockerImageName.parse("postgres@sha256:edb0c08dc33befd0262ab3b5cf1c4f3f8cfd8b09e88c05b45adee29d6761ddb6").asCompatibleSubstituteFor("postgres"));
    static { postgres.start(); org.flywaydb.core.Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).cleanDisabled(true).load().migrate(); }
    @DynamicPropertySource static void db(DynamicPropertyRegistry r){r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);}
    @LocalServerPort int port;
    @AfterAll static void stop(){postgres.stop();}
    static final String STAGE=System.getenv().getOrDefault("PERFORMANCE_STAGE","after");
    static final String COUNTERS="""
        (()=>{window.__work={textVisits:0,formatters:0};const original=document.createTreeWalker.bind(document);
        document.createTreeWalker=(...args)=>{const w=original(...args),next=w.nextNode.bind(w);w.nextNode=()=>{const n=next();if(n)window.__work.textVisits++;return n;};return w;};
        const Formatter=Intl.DateTimeFormat;Intl.DateTimeFormat=new Proxy(Formatter,{construct(target,args){window.__work.formatters++;return Reflect.construct(target,args);}});})();
        """;
    @Test void recordColdWarmAndIncrementalWork()throws Exception{
        var measurements=new LinkedHashMap<String,Object>();
        try(var playwright=Playwright.create();var browser=playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true).setChannel("chromium").setArgs(List.of("--disable-gpu")))){
            for(String name:List.of("login","index")){
                var samples=new ArrayList<Object>();
                for(int i=0;i<3;i++)try(var context=browser.newContext()){
                    context.addInitScript(COUNTERS);var page=context.newPage();
                    page.navigate("http://localhost:"+port+"/"+name+".html");ready(page,name);
                    Object cold=timing(page);page.reload();ready(page,name);Object warm=timing(page);
                    samples.add(Map.of("cold",cold,"warm",warm));
                    if(!STAGE.equals("baseline")){
                        var c=(Map<?,?>)cold;var w=(Map<?,?>)warm;
                        assertTrue(((Number)w.get("transferBytes")).doubleValue()<((Number)c.get("transferBytes")).doubleValue()/2,"Static HTTP revalidation must reduce repeat transfer");
                        assertFalse(((List<?>)c.get("modules")).stream().anyMatch(x->x.toString().contains("i18n-admin-navigation")||x.toString().contains("i18n-pages")),"Ordinary pages must not download admin/policy dictionaries");
                    }
                    if(name.equals("index")&&i==0)measurements.put("incrementalWork",incremental(page));
                }
                measurements.put(name,samples);
            }
        }
        measurements.put("stage",STAGE);measurements.put("samplesPerPage",3);measurements.put("scope","Local real Spring HTTP, uncompressed assets, disposable empty PostgreSQL; cold/warm same Chromium context. No route interception, production traffic, or tracking.");
        Path directory=Path.of("build/reports/performance");Files.createDirectories(directory);Files.writeString(directory.resolve(STAGE+".json"),tools.jackson.databind.json.JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(measurements));
    }
    @Test void delayedGuestNewsDoesNotMoveTheFirstViewportOrLogErrors()throws Exception{
        var results=new ArrayList<Object>();
        try(var playwright=Playwright.create();var browser=playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true).setChannel("chromium").setArgs(List.of("--disable-gpu")))){
            for(int width:new int[]{390,768,1440})try(var context=browser.newContext(new Browser.NewContextOptions().setViewportSize(width,900))){
                context.addInitScript("window.__shifts=[];new PerformanceObserver(list=>{for(const e of list.getEntries())if(!e.hadRecentInput)window.__shifts.push(e.value);}).observe({type:'layout-shift',buffered:true});");
                context.route("**/api/v1/public/news/edition",r->{try{Thread.sleep(1200);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}r.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody(BrowserTest.edition()));});
                var page=context.newPage();var errors=new ArrayList<String>();page.onPageError(errors::add);page.onConsoleMessage(m->{if(m.type().equals("error"))errors.add(m.text());});
                page.navigate("http://localhost:"+port+"/index.html");page.waitForSelector("#paper article");page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
                double cls=((Number)page.evaluate("window.__shifts.reduce((a,b)=>a+b,0)")).doubleValue();assertTrue(cls<=0.02,"First viewport CLS "+width+": "+cls);assertTrue(errors.isEmpty(),errors.toString());
                assertTrue((Boolean)page.evaluate("document.documentElement.scrollWidth<=innerWidth"));assertEquals(7,page.locator("#paper article").count());assertEquals(0,page.locator("a[href='/newsletter.html']:visible").count());
                page.locator("#open-search").click();assertTrue(page.locator("#search-dialog").isVisible());page.keyboard().press("Escape");assertFalse(page.locator("#search-dialog").isVisible());
                results.add(Map.of("width",width,"cls",cls,"consoleErrors",errors.size()));
            }
        }
        Path out=Path.of("build/reports/performance/pagespeed-layout.json");Files.createDirectories(out.getParent());Files.writeString(out,tools.jackson.databind.json.JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(results));
    }
    @Test void enforcedTrustedTypesBlocksSinksWhileNewsletterExportStillWorks(){
        try(var playwright=Playwright.create();var browser=playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true).setChannel("chromium").setArgs(List.of("--disable-gpu")));var context=browser.newContext()){
            var page=context.newPage();page.navigate("http://localhost:"+port+"/index.html");ready(page,"index");
            assertEquals(true,page.evaluate("()=>{try{new DOMParser().parseFromString('<p>blocked</p>','text/html');return false;}catch(e){return e instanceof TypeError;}}"));
            String html=(String)page.evaluate("async data=>(await import('/assets/js/newsletter/publication-output.js')).htmlNewsletter(data,'2026-001',location.origin+'/newsletter.html')",BrowserTest.productIssue());
            assertTrue(html.startsWith("<!doctype html>"));assertTrue(html.contains("<article lang=\"und\">"));assertTrue(html.contains("<footer>"));assertFalse(html.contains("<script"));
        }
    }
    static void ready(Page p,String name){p.waitForSelector("[data-language-select]");if(name.equals("index"))p.waitForSelector("#paper > *");p.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);}
    static Object timing(Page p){return p.evaluate("""
        ()=>{const assets=performance.getEntriesByType('resource').filter(r=>new URL(r.name).pathname.startsWith('/assets/'));return {transferBytes:assets.reduce((n,r)=>n+r.transferSize,0),assetRequests:assets.length,jsBodyBytes:assets.filter(r=>r.name.endsWith('.js')).reduce((n,r)=>n+r.decodedBodySize,0),modules:assets.filter(r=>r.name.endsWith('.js')).map(r=>new URL(r.name).pathname),loadMs:performance.getEntriesByType('navigation')[0].loadEventEnd};}
        """);}
    static Object incremental(Page p){Object result=p.evaluate("""
        async()=>{const common=await import('/assets/js/shared/common.js');const stage=document.createElement('div');for(let i=0;i<2000;i++){const p=document.createElement('p');p.textContent='합성 성능 측정 '+i;stage.append(p);}const target=document.createElement('span');stage.append(target);document.body.append(stage);await new Promise(r=>setTimeout(r,0));
        window.__work.textVisits=0;window.__work.formatters=0;const start=performance.now();for(let i=0;i<20;i++){target.textContent=i%2?'로그인':'회원가입';await new Promise(r=>setTimeout(r,0));}const mutationMs=performance.now()-start;const formattingStart=performance.now();for(let i=0;i<1000;i++)common.date('2026-10-28T01:02:03Z');const answer={...window.__work,mutationMs,formattingMs:performance.now()-formattingStart};stage.remove();return answer;}
        """);if(!STAGE.equals("baseline")){var data=(Map<?,?>)result;assertTrue(((Number)data.get("textVisits")).intValue()<200,"Small DOM mutations must not traverse unrelated article text");assertTrue(((Number)data.get("formatters")).intValue()<=2,"Repeated dates must share bounded formatters");}return result;}
}
