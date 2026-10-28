package com.moapan;
import com.microsoft.playwright.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic UI fixtures only. PostgreSQL/authentication is exercised by integrationTest. */
class BrowserTest {
    static HttpServer server;static Playwright playwright;static Browser browser;static String origin;
    static final String A="10000000-0000-0000-0000-000000000001",B="10000000-0000-0000-0000-000000000002";
    static String active=A;
    @BeforeAll static void start()throws Exception{
        server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
        server.createContext("/",exchange->{String path=exchange.getRequestURI().getPath();String response=null;String type="application/json";
            if(path.equals("/api/v1/account"))response="{\"id\":\""+active+"\",\"username\":\"synthetic_user\",\"role\":\"USER\"}";
            else if(path.equals("/api/v1/tags"))response="{\"tags\":[{\"id\":\"ai\",\"slug\":\"ai\",\"name\":\"인공지능\",\"category\":\"기술\"},{\"id\":\"space\",\"slug\":\"space\",\"name\":\"우주\",\"category\":\"과학\"}],\"aliases\":[{\"alias\":\"인공지능\",\"tagId\":\"ai\"},{\"alias\":\"AI\",\"tagId\":\"ai\"}],\"redirects\":[]}";
            else if(path.equals("/api/v1/sources"))response="[]";
            else if(path.equals("/api/v1/news/edition"))response=edition();
            else if(path.equals("/api/v1/auth/csrf"))response="{\"token\":\"synthetic-csrf\",\"headerName\":\"X-CSRF-TOKEN\"}";
            if(response!=null){byte[]bytes=response.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type",type);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();return;}
            if(path.equals("/"))path="/index.html";try(var in=BrowserTest.class.getResourceAsStream("/static"+path)){if(in==null){exchange.sendResponseHeaders(404,-1);}else{type=path.endsWith(".js")?"text/javascript":path.endsWith(".css")?"text/css":"text/html";exchange.getResponseHeaders().set("Content-Type",type+"; charset=utf-8");byte[]bytes=in.readAllBytes();exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);}}exchange.close();
        });server.start();origin="http://localhost:"+server.getAddress().getPort();playwright=Playwright.create();browser=playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true).setChannel("chromium").setArgs(List.of("--disable-gpu")));
    }
    static String edition(){StringBuilder items=new StringBuilder();for(int i=0;i<15;i++){if(i>0)items.append(',');items.append("{\"id\":\"synthetic-").append(i).append("\",\"title\":\"[합성 검증 기사] 긴 한글 제목과 인공지능 우주 소식이 여러 단의 신문 지면에서 자연스럽게 표시되는지 확인합니다 ").append(i).append("\",\"summaryBullets\":[\"실제 뉴스가 아닌 화면 검증용 합성 문장입니다. 외부 전송 없이 현재 후보를 정렬합니다.\"],\"source\":{\"id\":\"source-").append(i%5).append("\",\"site\":\"합성 출처\",\"name\":\"Synthetic\",\"language\":\"ko\"},\"publishedAt\":null,\"collectedAt\":\"2026-10-28T00:00:00Z\",\"tags\":[{\"id\":\"ai\",\"slug\":\"ai\",\"name\":\"인공지능\",\"category\":\"기술\",\"confidence\":1}],\"originalUrl\":\"https://example.org/synthetic/").append(i).append("\",\"summaryMode\":\"EXTRACTIVE_ORIGINAL_LANGUAGE\"}");}return "{\"editionId\":\"synthetic-edition\",\"editionAt\":\"2026-10-28T00:00:00Z\",\"periodDays\":7,\"candidateCount\":15,\"items\":["+items+"],\"nextCursor\":null}";}
    @AfterAll static void stop(){if(browser!=null)browser.close();if(playwright!=null)playwright.close();if(server!=null)server.stop(0);}
    @Test void responsivePaperHasNoOverflowAndNoTracking()throws Exception{
        Path dir=Path.of("build/reports/screenshots");Files.createDirectories(dir);
        try(var context=browser.newContext()){Page p=context.newPage();List<String>outbound=new ArrayList<>();p.onRequest(r->{if(!r.url().startsWith(origin))outbound.add(r.url());});
            for(int width:new int[]{390,768,1440}){p.setViewportSize(width,1000);p.navigate(origin+"/index.html");p.waitForSelector("#paper article");assertEquals(15,p.locator("#paper article").count());assertTrue((boolean)p.evaluate("document.documentElement.scrollWidth <= window.innerWidth"));p.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("newspaper-"+width+".png")).setFullPage(true));}
            p.locator("#search").fill("synthetic-search-private-phrase");assertEquals(0,p.locator("#paper article").count());assertTrue(outbound.isEmpty());assertEquals(0,p.locator("script:not([type=module])").count());p.emulateMedia(new Page.EmulateMediaOptions().setMedia(com.microsoft.playwright.options.Media.PRINT));assertFalse(p.locator("#more").isVisible());
        }
    }
    @Test void simpleAuthFormsRequireConsentAndShowReadableTerms()throws Exception{
        Path dir=Path.of("build/reports/screenshots");Files.createDirectories(dir);
        try(var context=browser.newContext()){
            Page p=context.newPage();List<String> bodies=new ArrayList<>();
            p.route("**/api/v1/auth/signup",r->{bodies.add(r.request().postData());r.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody("{}"));});
            for(int width:new int[]{390,768,1440})for(String mode:List.of("login","signup")){
                p.setViewportSize(width,900);p.navigate(origin+"/"+mode+".html");
                assertEquals(2,p.locator("input:not([type=checkbox])").count());assertTrue((boolean)p.evaluate("document.documentElement.scrollWidth <= innerWidth"));
                p.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve(mode+"-"+width+".png")).setFullPage(true));
            }
            p.locator("#username").fill("synthetic_terms");p.locator("#password").fill("synthetic password 12345");
            assertFalse(p.locator("input[type=checkbox]").isChecked());p.locator("button[type=submit]").click();assertTrue(bodies.isEmpty());
            Page terms=p.waitForPopup(()->p.locator(".agreement a").click());terms.waitForLoadState();assertTrue(terms.locator("main").textContent().contains("2026-10-28.2"));assertTrue(terms.locator("main").textContent().contains("복구할 수 없습니다"));terms.close();
            assertEquals("synthetic_terms",p.locator("#username").inputValue());p.locator("input[type=checkbox]").check();p.locator("button[type=submit]").click();p.waitForURL("**/login.html");
            assertEquals(1,bodies.size());assertTrue(bodies.getFirst().contains("\"termsAccepted\":true"));assertTrue(bodies.getFirst().contains("\"termsVersion\":\"2026-10-28.2\""));
        }
    }
    @Test void loginWarningCountdownAndUnlockAreVisible()throws Exception{
        try(var context=browser.newContext()){
            Page p=context.newPage();var calls=new java.util.concurrent.atomic.AtomicInteger();
            p.route("**/api/v1/auth/login",r->{int n=calls.incrementAndGet();r.fulfill(new Route.FulfillOptions().setStatus(n==1?401:429).setContentType("application/json").setHeaders(Map.of("Retry-After","125")).setBody(n==1?"{\"error\":\"invalid_credentials\",\"attemptsRemaining\":5}":"{\"error\":\"login_limited\"}"));});
            p.setViewportSize(390,900);p.navigate(origin+"/login.html");p.evaluate("window.testNow=Date.now();Date.now=()=>window.testNow");
            p.locator("#username").fill("synthetic_countdown");p.locator("#password").fill("synthetic wrong password");p.locator("button[type=submit]").click();
            p.waitForFunction("document.querySelector('[role=status]').textContent.includes('5회 남았습니다')");assertFalse(p.locator("#login-limit").isVisible());
            p.locator("button[type=submit]").click();p.waitForFunction("document.querySelector('#login-countdown').textContent==='02:05'");assertTrue(p.locator("button[type=submit]").isDisabled());
            p.evaluate("window.testNow+=2000");p.waitForFunction("document.querySelector('#login-countdown').textContent==='02:03'");assertEquals(2,calls.get());
            Files.createDirectories(Path.of("build/reports/screenshots"));p.screenshot(new Page.ScreenshotOptions().setPath(Path.of("build/reports/screenshots/login-countdown-390.png")).setFullPage(true));
            p.evaluate("window.testNow+=123000");p.waitForFunction("!document.querySelector('button[type=submit]').disabled");assertFalse(p.locator("#login-limit").isVisible());assertTrue(p.locator("[role=status]").textContent().contains("다시 로그인할 수 있습니다"));assertEquals(2,calls.get());
            p.locator("button[type=submit]").click();p.waitForFunction("document.querySelector('#login-countdown').textContent==='02:05'");p.locator("#username").fill("another_account");assertFalse(p.locator("#login-limit").isVisible());assertFalse(p.locator("button[type=submit]").isDisabled());
        }
    }
    @Test void interestSentenceStaysLocalAndAccountsAreSeparated(){
        try(var context=browser.newContext()){Page p=context.newPage();List<String>network=new ArrayList<>();p.onRequest(r->network.add(r.url()+" "+Objects.toString(r.postData(),"")));active=A;p.navigate(origin+"/settings.html");p.waitForSelector("#tag-settings input");p.locator("#interest-sentence").fill("인공지능 synthetic-interest-private-phrase");p.locator("#extract").click();p.locator("#confirm-tags").click();p.waitForFunction("document.querySelector('#interest-sentence').value === ''");
            assertEquals(3,((Number)p.evaluate("async id => (await import('/assets/js/preferences.js')).loadPreferences(id).then(s=>s.weights.ai)",A)).intValue());
            assertEquals(0,((Number)p.evaluate("async id => Object.keys((await (await import('/assets/js/preferences.js')).loadPreferences(id)).weights).length",B)).intValue());
            assertTrue(network.stream().noneMatch(v->v.contains("synthetic-interest-private-phrase")));assertTrue(network.stream().noneMatch(v->v.contains("synthetic-search-private-phrase")));
            p.locator("#clear-local").click();p.waitForFunction("document.querySelector('[role=status]').textContent.includes('削除') || document.querySelector('[role=status]').textContent.includes('삭제')");
        }
    }
    @Test void actualModulesRankDeterministicallyAndRespectExclusions(){
        try(var context=browser.newContext()){Page p=context.newPage();p.navigate(origin+"/about.html");Object result=p.evaluate("""
            async () => {
              const {rank}=await import('/assets/js/ranking.js');
              const {extractTags,canonicalize}=await import('/assets/js/preferences.js');
              const now=Date.parse('2026-10-28T00:00:00Z');
              const candidates=[{id:'a',source:{id:'s1'},publishedAt:new Date(now-3600000).toISOString(),tags:[{id:'ai',confidence:1}]},{id:'b',source:{id:'s2'},publishedAt:new Date(now).toISOString(),tags:[{id:'space',confidence:1}]}];
              const base={enabled:true,weights:{ai:5},excludedTags:[],excludedSources:[]};
              return {a:rank(candidates,base,now)[0].id,b:rank(candidates,{...base,weights:{space:5}},now)[0].id,excluded:rank(candidates,{...base,excludedTags:['ai']},now).map(a=>a.id),noTags:rank(candidates,{...base,weights:{}},now)[0].id,partial:extractTags('rain said',[{alias:'ai',tagId:'ai'}]),merged:canonicalize(base,[{oldId:'ai',canonicalId:'new'}]).weights};
            }
            """);@SuppressWarnings("unchecked")var map=(Map<String,Object>)result;assertEquals("a",map.get("a"));assertEquals("b",map.get("b"));assertEquals(List.of("b"),map.get("excluded"));assertEquals("b",map.get("noTags"));assertEquals(List.of(),map.get("partial"));assertEquals(Map.of("new",5),map.get("merged"));}
    }
    @Test void loadingEmptyErrorAndKeyboardStatesAreUsable(){
        try(var context=browser.newContext()){
            Page p=context.newPage();var pending=new java.util.concurrent.atomic.AtomicReference<Route>();
            p.route("**/api/v1/news/edition",pending::set);
            p.navigate(origin+"/index.html",new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.COMMIT));
            p.waitForFunction("document.querySelector('[role=status]').textContent.includes('불러오는 중')");assertNotNull(pending.get());
            pending.get().fulfill(new Route.FulfillOptions().setContentType("application/json").setBody("{\"editionId\":\"empty-fixture\",\"editionAt\":\"2026-10-28T00:00:00Z\",\"periodDays\":7,\"candidateCount\":0,\"items\":[],\"nextCursor\":null}"));
            p.waitForSelector("#paper p");assertEquals(0,p.locator("#paper article").count());assertTrue(p.locator("#paper").textContent().contains("찾지 못했습니다"));
            p.keyboard().press("Tab");assertEquals("#main",p.evaluate("document.activeElement.getAttribute('href')"));
            p.unroute("**/api/v1/news/edition");p.route("**/api/v1/news/edition",r->r.fulfill(new Route.FulfillOptions().setStatus(503).setContentType("application/json").setBody("{\"error\":\"temporarily_unavailable\"}")));
            p.reload();p.waitForFunction("document.querySelector('[role=status]').textContent.includes('일시적으로')");assertFalse(p.locator("#more").isVisible());
        }
    }

}
