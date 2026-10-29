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
            else if((path.equals("/api/v1/tags")||path.equals("/api/v1/public/tags")))response="{\"tags\":[{\"id\":\"ai\",\"slug\":\"ai\",\"name\":\"인공지능\",\"category\":\"기술\"},{\"id\":\"space\",\"slug\":\"space\",\"name\":\"우주\",\"category\":\"과학\"}],\"aliases\":[{\"alias\":\"인공지능\",\"tagId\":\"ai\"},{\"alias\":\"AI\",\"tagId\":\"ai\"}],\"redirects\":[]}";
            else if(path.equals("/api/v1/public/interests"))response="{\"version\":0,\"weights\":{\"space\":3}}";
            else if(path.equals("/api/v1/sources"))response="[]";
            else if((path.equals("/api/v1/news/edition")||path.equals("/api/v1/public/news/edition")))response=edition();
            else if(path.endsWith("/news/search"))response="{\"items\":[],\"page\":0,\"pages\":0,\"total\":0}";
            else if(path.equals("/api/v1/auth/csrf"))response="{\"token\":\"synthetic-csrf\",\"headerName\":\"X-CSRF-TOKEN\"}";
            if(response!=null){byte[]bytes=response.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type",type);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();return;}
            if(path.equals("/"))path="/index.html";try(var in=BrowserTest.class.getResourceAsStream("/static"+path)){if(in==null){exchange.sendResponseHeaders(404,-1);}else{type=path.endsWith(".js")?"text/javascript":path.endsWith(".css")?"text/css":"text/html";exchange.getResponseHeaders().set("Content-Type",type+"; charset=utf-8");byte[]bytes=in.readAllBytes();exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);}}exchange.close();
        });server.start();origin="http://localhost:"+server.getAddress().getPort();playwright=Playwright.create();browser=playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true).setChannel("chromium").setArgs(List.of("--disable-gpu")));
    }
    static String edition(){StringBuilder items=new StringBuilder();for(int i=0;i<15;i++){if(i>0)items.append(',');items.append("{\"id\":\"synthetic-").append(i).append("\",\"title\":\"[합성 검증 기사] 긴 한글 제목과 인공지능 우주 소식이 여러 단의 신문 지면에서 자연스럽게 표시되는지 확인합니다 ").append(i).append("\",\"summaryBullets\":[\"실제 뉴스가 아닌 화면 검증용 합성 문장입니다. 외부 전송 없이 현재 후보를 정렬합니다.\"],\"source\":{\"id\":\"source-").append(i%5).append("\",\"site\":\"합성 출처\",\"name\":\"Synthetic\",\"language\":\"ko\"},\"publishedAt\":null,\"collectedAt\":\"2026-10-28T00:00:00Z\",\"tags\":[{\"id\":\"ai\",\"slug\":\"ai\",\"name\":\"인공지능\",\"category\":\"기술\",\"confidence\":1}],\"originalUrl\":\"https://example.org/synthetic/").append(i).append("\",\"summaryMode\":\"EXTRACTIVE_ORIGINAL_LANGUAGE\"}");}return "{\"editionId\":\"synthetic-edition\",\"editionAt\":\"2026-10-28T00:00:00Z\",\"periodDays\":7,\"candidateCount\":15,\"items\":["+items+"],\"nextCursor\":null}";}
    @AfterAll static void stop(){if(browser!=null)browser.close();if(playwright!=null)playwright.close();if(server!=null)server.stop(0);}

    @Test void serverSearchUsesPostOnlyOnSubmitAndLoadsOneSlideAtATime(){
        try(var context=browser.newContext()){
            List<String> bodies=new ArrayList<>();
            context.route("**/api/v1/news/search",r->{
                assertEquals("POST",r.request().method());assertFalse(r.request().url().contains("?"));
                String body=r.request().postData();bodies.add(body);
                int page=body.contains("\"page\":1")?1:0;
                String items=edition().substring(edition().indexOf("["),edition().lastIndexOf("]")+1);
                var mapper=tools.jackson.databind.json.JsonMapper.builder().build();
                var list=mapper.readTree(items);
                var output=new ArrayList<Object>();for(int i=0;i<7;i++)output.add(list.get(i));
                r.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody("{\"items\":"+mapper.writeValueAsString(output)+",\"page\":"+page+",\"pages\":20,\"total\":140}"));
            });
            Page p=context.newPage();p.navigate(origin+"/index.html");p.waitForSelector("#paper article");
            p.locator("#open-search").click();p.locator("#search").fill("synthetic-transient-query");assertTrue(bodies.isEmpty());
            p.locator("#search-form [type=submit]").click();p.waitForFunction("document.querySelector('#slide-position').textContent==='1 / 20'");
            assertEquals(1,bodies.size());assertEquals(7,p.locator("#paper article").count());
            p.locator("#next-slide").click();p.waitForFunction("document.querySelector('#slide-position').textContent==='2 / 20'");assertEquals(2,bodies.size());
            assertFalse((boolean)p.evaluate("JSON.stringify({...localStorage,...sessionStorage}).includes('synthetic-transient-query')"));
            p.locator("#clear-search").click();assertEquals("1 / 3",p.locator("#slide-position").textContent());assertEquals(2,bodies.size());
        }
    }

    @Test void languagePickerChangesStoredCardTitleAndSummaryWithoutNetwork(){
        try(var context=browser.newContext()){
            context.route("**/api/v1/news/edition*",r->{
                var mapper=tools.jackson.databind.json.JsonMapper.builder().build();var root=mapper.readTree(edition());
                for(var item:root.get("items")){
                    var object=(tools.jackson.databind.node.ObjectNode)item;
                    object.put("title","Synthetic original title");((tools.jackson.databind.node.ObjectNode)object.get("source")).put("language","en");
                    object.set("summaryBullets",mapper.valueToTree(List.of("Synthetic original summary.")));
                    object.set("translations",mapper.valueToTree(Map.of("ko",Map.of("title","합성 번역 제목","summaryBullets",List.of("합성 번역 요약입니다.")))));
                }
                r.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody(mapper.writeValueAsString(root)));
            });
            Page p=context.newPage();p.navigate(origin+"/index.html");p.waitForSelector("#paper article");
            assertEquals("합성 번역 제목",p.locator("#paper article h2 a").first().textContent());assertEquals("합성 번역 요약입니다.",p.locator("#paper article li").first().textContent());
            List<String> requests=new ArrayList<>();p.onRequest(r->requests.add(r.url()));
            p.locator("[data-language-select]").selectOption("en");p.waitForFunction("document.querySelector('#paper h2 a').textContent==='Synthetic original title'");
            assertEquals("Synthetic original summary.",p.locator("#paper article li").first().textContent());
            p.locator("[data-language-select]").selectOption("ko");p.waitForFunction("document.querySelector('#paper h2 a').textContent==='합성 번역 제목'");assertTrue(requests.isEmpty());
        }
    }

    @Test void encryptedPreferencesTransferBetweenIndependentBrowsers()throws Exception{
        active=A;var traffic=new ArrayList<String>();
        try(var first=browser.newContext();var second=browser.newContext()){
            first.onRequest(r->traffic.add(r.url()+" "+Objects.toString(r.postData(),"")));
            second.onRequest(r->traffic.add(r.url()+" "+Objects.toString(r.postData(),"")));
            Page a=first.newPage(),b=second.newPage();
            a.navigate(origin+"/settings.html");a.waitForSelector("#transfer-controls:not([disabled])");
            a.locator("#tag-space").uncheck();a.locator("#tag-ai").check();
            a.waitForFunction("async id => (await (await import('/assets/js/personalization/preferences.js')).loadPreferences(id)).weights.ai===3",A);
            a.locator("#transfer-password").fill("synthetic portable secret 12345");
            Download download=a.waitForDownload(()->a.locator("#transfer-export").click());
            Path file=Path.of("build/reports/preferences-transfer.moapan");Files.createDirectories(file.getParent());download.saveAs(file);
            String encrypted=Files.readString(file);assertFalse(encrypted.contains(A));assertFalse(encrypted.contains("weights"));assertFalse(encrypted.contains("synthetic portable"));
            b.navigate(origin+"/settings.html");b.waitForSelector("#transfer-controls:not([disabled])");assertFalse(b.locator("#tag-ai").isChecked());
            b.locator("#tag-space").uncheck();b.locator("#tag-space").check();b.waitForFunction("async id => (await (await import('/assets/js/personalization/preferences.js')).loadPreferences(id)).weights.space===3",A);
            b.locator("#transfer-file").setInputFiles(file);
            b.locator("#transfer-password").fill("synthetic wrong secret 12345");b.locator("#transfer-import").click();
            b.waitForFunction("document.querySelector('#transfer-status').textContent.includes('손상')");assertTrue(b.locator("#tag-space").isChecked());assertFalse(b.locator("#transfer-apply").isVisible());
            b.locator("#transfer-password").fill("synthetic portable secret 12345");b.locator("#transfer-import").click();b.waitForSelector("#transfer-apply");
            assertTrue(b.locator("#tag-space").isChecked());assertFalse(b.locator("#tag-ai").isChecked());assertEquals("",b.locator("#transfer-password").inputValue());
            b.locator("#transfer-cancel").click();assertTrue(b.locator("#tag-space").isChecked());
            b.locator("#transfer-file").setInputFiles(file);b.locator("#transfer-password").fill("synthetic portable secret 12345");b.locator("#transfer-import").click();b.waitForSelector("#transfer-apply");b.locator("#transfer-apply").click();
            b.waitForFunction("document.querySelector('#transfer-status').textContent.includes('가져왔습니다')");assertTrue(b.locator("#tag-ai").isChecked());assertFalse(b.locator("#tag-space").isChecked());
            b.reload();b.waitForSelector("#transfer-controls:not([disabled])");assertTrue(b.locator("#tag-ai").isChecked());
            b.locator("#tag-space").check();assertFalse(a.locator("#tag-space").isChecked());
            for(int width:new int[]{390,768,1440}){b.setViewportSize(width,1000);assertTrue((boolean)b.evaluate("document.documentElement.scrollWidth <= innerWidth"));b.screenshot(new Page.ScreenshotOptions().setPath(Path.of("build/reports/screenshots/transfer-"+width+".png")).setFullPage(true));}
            assertTrue(traffic.stream().allMatch(v->v.startsWith(origin)));assertTrue(traffic.stream().noneMatch(v->v.contains("synthetic portable")||v.contains("ciphertext")||v.contains("weights")));
            Files.delete(file);
        }
    }
    @Test void cardsOnlyShowRequestedFields(){
        try(var context=browser.newContext()){
            Page p=context.newPage();p.navigate(origin+"/index.html");p.waitForSelector("#paper article");
            var card=p.locator("#paper article").first();
            assertEquals(List.of("주제","작성일","작성자"),card.locator(".article-meta-line > span").evaluateAll("nodes=>nodes.map(n=>n.getAttribute('aria-label'))"));
            assertEquals("정보 없음",card.locator(".article-meta-line > span").nth(1).textContent());
            assertEquals("정보 없음",card.locator(".article-meta-line > span").nth(2).textContent());
            assertEquals(0,card.locator("h3").count());assertEquals(1,card.locator(".article-source").count());
            assertEquals(0,card.locator("button,.reason,.eyebrow,.byline").count());
            assertEquals(1,card.locator("h2 a[rel='noopener noreferrer']").count());
            assertFalse(card.textContent().contains("자동 추출"));assertFalse(card.textContent().contains("번역하지 않음"));
        }
    }

    @Test void slideBoundsAndLanguageSelection(){
      try(var context=browser.newContext()){
        Page p=context.newPage();p.navigate(origin+"/index.html");p.waitForSelector("#paper article");
        assertEquals(7,p.locator("#paper article").count());assertEquals("1 / 3",p.locator("#slide-position").textContent());
        p.locator("#next-slide").click();assertEquals("2 / 3",p.locator("#slide-position").textContent());
        p.locator("#next-slide").click();assertEquals(1,p.locator("#paper article").count());assertTrue(p.locator("#next-slide").isDisabled());
        p.locator("#paper").focus();p.keyboard().press("ArrowLeft");assertEquals("2 / 3",p.locator("#slide-position").textContent());
        assertEquals(true,p.evaluate("""
          async()=>{const {slideWindow}=await import('/assets/js/edition/slides.js');const rows=Array.from({length:200},(_,i)=>({id:String(i).padStart(3,'0'),publishedAt:new Date(2026,0,i+1).toISOString()}));const home=slideWindow(rows,100);const search=slideWindow(rows,100,true);return home.pages===6&&home.total===42&&home.items.length===7&&search.pages===20&&search.total===140&&search.page===19&&search.items.length===7&&slideWindow(rows,0,true).items[0].id==='199';}
          """));
        p.locator("[data-language-select]").selectOption("en");p.waitForFunction("document.documentElement.lang==='en'&&document.querySelector('#open-search').textContent.includes('Search')");
        p.navigate(origin+"/signup.html");p.waitForFunction("document.documentElement.lang==='en'");assertTrue(p.locator("h1").textContent().contains("Sign up"));
        p.locator("[data-language-select]").selectOption("ko");p.waitForFunction("document.querySelector('h1').textContent.includes('회원가입')");
      }
    }

    @Test void publicStaticPagesHaveEnglishCopyAndKeepLanguage(){
      try(var context=browser.newContext()){
        context.addInitScript("localStorage.setItem('moapan-language','en')");
        Page p=context.newPage();
        for(String file:List.of("about","privacy","terms","signup","login")){
          p.navigate(origin+"/"+file+".html");p.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
          assertEquals("en",p.locator("html").getAttribute("lang"));
          assertEquals(true,p.evaluate("""
            ()=>{const walker=document.createTreeWalker(document.querySelector('main'),NodeFilter.SHOW_TEXT);while(walker.nextNode()){const n=walker.currentNode;if(n.parentElement.closest('script,style,[data-original-content],.language-picker'))continue;if(/[가-힣]/.test(n.textContent))return false;}return true;}
            """),file);
        }
      }
    }

    @Test void articleLanguagePreservesOriginalAndMakesNoRequests(){
        try(var context=browser.newContext()){
            Page page=context.newPage();page.navigate(origin+"/about.html");
            assertEquals(true,page.evaluate("""
              async () => {
                const {articleLanguage}=await import('/assets/js/article/article-language.js');
                const article={title:'Original <title>',summaryBullets:['Original summary'],source:{language:'en'},summaryMode:'EXTRACTIVE',
                  translation:{language:'ko',title:'번역 제목',summaryBullets:['번역 요약']}};
                const link=document.createElement('a'),list=document.createElement('ul');
                const before=JSON.stringify(article);let requests=0;
                const previous=window.fetch;window.fetch=()=>{requests++;throw Error('unexpected request');};
                try {
                  const {controls,note}=articleLanguage(article,link,list);
                  if(link.textContent!==article.title||link.querySelector('title'))return false;
                  controls.querySelectorAll('button')[1].click();
                  if(link.textContent!=='번역 제목'||list.textContent!=='번역 요약'||!note.textContent.includes('자동 번역'))return false;
                  controls.querySelectorAll('button')[0].click();
                  if(link.textContent!==article.title||list.textContent!=='Original summary'||JSON.stringify(article)!==before)return false;
                  const missing=articleLanguage({...article,translation:null},link,list);
                  const malformed=articleLanguage({...article,translation:{...article.translation,summaryBullets:[]}},link,list);
                  return requests===0&&missing.controls.children.length===0&&malformed.controls.children.length===0;
                } finally {window.fetch=previous;}
              }
              """));
        }
    }

    @Test void portableSettingsRejectTamperingOtherAccountsAndInvalidSchemas(){
        try(var context=browser.newContext()){
            Page p=context.newPage();p.navigate(origin+"/settings.html");p.waitForSelector("#transfer-controls:not([disabled])");
            assertEquals(true,p.evaluate("""
              async ([a,b]) => {
                const m=await import('/assets/js/personalization/preferences-transfer.js');
                const settings={version:1,enabled:false,weights:{ai:5},excludedTags:['space'],excludedSources:['source-1']};
                const password='synthetic portable secret 12345';
                const text=await m.exportPreferences(a,settings,password);
                if(JSON.stringify(await m.importPreferences(text,a,password))!==JSON.stringify(settings))return false;
                const other=await m.exportPreferences(a,settings,password);if(text===other)return false;
                const rejects=async fn=>{try{await fn();return false;}catch{return true;}};
                const damaged=JSON.parse(text);damaged.ciphertext=(damaged.ciphertext[0]==='A'?'B':'A')+damaged.ciphertext.slice(1);
                const cases=[()=>m.importPreferences(text,b,password),()=>m.importPreferences(JSON.stringify(damaged),a,password),
                  ()=>m.importPreferences('x'.repeat(m.MAX_FILE_BYTES+1),a,password),()=>m.importPreferences('{}',a,password),
                  ()=>m.exportPreferences(a,settings,'short'),()=>m.exportPreferences(a,{...settings,phrase:'private'},password),
                  ()=>m.validatePreferences({...settings,weights:{ai:6}}),
                  ()=>m.validatePreferences({...settings,excludedTags:['space','space']}),
                  ()=>m.validatePreferences({...settings,weights:JSON.parse('{"__proto__":3}')}),
                  ()=>m.validatePreferences({...settings,excludedSources:Array(501).fill('s')})];
                for(const fn of cases)if(!await rejects(fn))return false;
                return (await (await import('/assets/js/personalization/preferences.js')).loadPreferences(a)).enabled===true;
              }
              """,List.of(A,B)));
        }
    }

    @Test void responsivePaperHasNoOverflowAndNoTracking()throws Exception{
        Path dir=Path.of("build/reports/screenshots");Files.createDirectories(dir);
        try(var context=browser.newContext()){Page p=context.newPage();List<String>outbound=new ArrayList<>();p.onRequest(r->{if(!r.url().startsWith(origin))outbound.add(r.url());});
            for(int width:new int[]{390,768,1440}){p.setViewportSize(width,1000);p.navigate(origin+"/index.html");p.waitForSelector("#paper article");assertEquals(7,p.locator("#paper article").count());assertTrue((boolean)p.evaluate("document.documentElement.scrollWidth <= window.innerWidth"));p.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("newspaper-"+width+".png")).setFullPage(true));}
            p.locator("#open-search").click();p.locator("#search").fill("synthetic-search-private-phrase");p.locator("#search-form [type=submit]").click();p.waitForFunction("document.querySelector('[role=status]').textContent==='' && document.querySelector('#paper article')===null");assertEquals(0,p.locator("#paper article").count());assertTrue(outbound.isEmpty());assertEquals(0,p.locator("script:not([type=module])").count());p.emulateMedia(new Page.EmulateMediaOptions().setMedia(com.microsoft.playwright.options.Media.PRINT));assertFalse(p.locator("#more").isVisible());
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
            Page terms=p.waitForPopup(()->p.locator(".agreement a").click());terms.waitForLoadState();assertTrue(terms.locator("main").textContent().contains("약관 개정 제 1판"));assertTrue(terms.locator("main").textContent().contains("복구할 수 없습니다"));terms.close();
            assertEquals("synthetic_terms",p.locator("#username").inputValue());p.locator("input[type=checkbox]").check();p.locator("button[type=submit]").click();p.waitForURL("**/login.html");
            assertEquals(1,bodies.size());assertTrue(bodies.getFirst().contains("\"termsAccepted\":true"));assertTrue(bodies.getFirst().contains("\"termsVersion\":\"terms-1\""));
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
    @Test void usernameAndPasswordLengthsMatchBrowserAndAccountMenu(){
        try(var context=browser.newContext()){
            Page p=context.newPage();var requests=new ArrayList<String>();
            p.route("**/api/v1/auth/signup",r->{requests.add(r.request().postData());r.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody("{}"));});
            p.navigate(origin+"/signup.html");assertEquals("128",p.locator("#username").getAttribute("maxlength"));p.locator("#username").fill("a");p.locator("#password").fill("abcde");p.locator("input[type=checkbox]").check();p.locator("button[type=submit]").click();assertTrue(requests.isEmpty());
            p.locator("#password").fill("abcdef");p.locator("button[type=submit]").click();p.waitForURL("**/login.html");assertEquals(1,requests.size());assertEquals("6",p.locator("#password").getAttribute("minlength"));assertEquals("1",p.locator("#username").getAttribute("minlength"));
            p.route("**/api/v1/account",r->r.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody("{\"id\":\""+A+"\",\"username\":\""+"u".repeat(128)+"\",\"role\":\"USER\"}")));
            p.setViewportSize(390,900);p.navigate(origin+"/account.html");p.waitForSelector("#account-menu button");assertEquals("6",p.locator("[name=newPassword]").getAttribute("minlength"));assertTrue((boolean)p.evaluate("document.documentElement.scrollWidth <= innerWidth"));
        }
    }
    @Test void interestSentenceStaysLocalAndAccountsAreSeparated(){
        try(var context=browser.newContext()){Page p=context.newPage();List<String>network=new ArrayList<>();p.onRequest(r->network.add(r.url()+" "+Objects.toString(r.postData(),"")));active=A;p.navigate(origin+"/settings.html");p.waitForSelector("#tag-settings input");p.locator("summary").click();p.locator("#interest-sentence").fill("인공지능 synthetic-interest-private-phrase");p.locator("#extract").click();p.locator("#confirm-tags").click();p.waitForFunction("document.querySelector('#interest-sentence').value === ''");
            assertEquals(3,((Number)p.evaluate("async id => (await import('/assets/js/personalization/preferences.js')).loadPreferences(id).then(s=>s.weights.ai)",A)).intValue());
            assertEquals(0,((Number)p.evaluate("async id => Object.keys((await (await import('/assets/js/personalization/preferences.js')).loadPreferences(id)).weights).length",B)).intValue());
            assertTrue(network.stream().noneMatch(v->v.contains("synthetic-interest-private-phrase")));assertTrue(network.stream().noneMatch(v->v.contains("synthetic-search-private-phrase")));
            p.locator("#clear-local").click();p.waitForFunction("document.querySelector('[role=status]').textContent.includes('削除') || document.querySelector('[role=status]').textContent.includes('삭제')");
        }
    }
    @Test void interestsAndAccountPagesKeepSeparateResponsibilities()throws Exception{
        try(var context=browser.newContext()){
            active=A;Page p=context.newPage();List<String>paths=new ArrayList<>();p.onRequest(r->paths.add(r.url()));
            p.navigate(origin+"/settings.html");p.waitForSelector("#tag-settings input");assertEquals(0,p.locator("#change-password,#delete-account").count());assertFalse(p.locator("#interest-sentence").isVisible());
            p.locator("#tag-ai").check();p.waitForFunction("async id => (await (await import('/assets/js/personalization/preferences.js')).loadPreferences(id)).weights.ai===3",A);
            p.locator("#account-menu a[href='/account.html']").click();p.waitForSelector("#account-controls:not([disabled])");assertEquals(2,p.locator("#change-password,#delete-account").count());assertEquals(0,p.locator("#tag-settings,#interest-sentence").count());
            paths.clear();p.reload();p.waitForSelector("#account-controls:not([disabled])");assertTrue(paths.stream().noneMatch(u->u.contains("/api/v1/tags")||u.contains("/api/v1/sources")));
            p.locator("#account-menu a[href='/settings.html']").click();p.waitForSelector("#tag-settings input");assertTrue(p.locator("#tag-ai").isChecked());
            Path dir=Path.of("build/reports/screenshots");Files.createDirectories(dir);
            for(int width:new int[]{390,768,1440})for(String page:List.of("settings","account")){p.setViewportSize(width,1000);p.navigate(origin+"/"+page+".html");p.waitForSelector(page.equals("settings")?"#interest-controls:not([disabled])":"#account-controls:not([disabled])");assertTrue((boolean)p.evaluate("document.documentElement.scrollWidth <= innerWidth"));p.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve(page+"-"+width+".png")).setFullPage(true));}
            p.evaluate("async id => {const m=await import('/assets/js/personalization/preferences.js');await m.savePreferences(id,{...m.defaults(),weights:{space:4}});}",B);
            p.route("**/api/v1/account",r->{if(r.request().method().equals("DELETE"))r.fulfill(new Route.FulfillOptions().setStatus(200).setBody(""));else r.fallback();});
            p.locator("#delete-account input").fill("synthetic password 12345");p.locator("#delete-account button").click();p.waitForURL("**/login.html");
            assertEquals(0,((Number)p.evaluate("async id => Object.keys((await (await import('/assets/js/personalization/preferences.js')).loadPreferences(id)).weights).length",A)).intValue());
            assertEquals(4,((Number)p.evaluate("async id => (await (await import('/assets/js/personalization/preferences.js')).loadPreferences(id)).weights.space",B)).intValue());
        }
    }
    @Test void actualModulesRankDeterministicallyAndRespectExclusions(){
        try(var context=browser.newContext()){Page p=context.newPage();p.navigate(origin+"/about.html");Object result=p.evaluate("""
            async () => {
              const {rank}=await import('/assets/js/personalization/ranking.js');
              const {extractTags,canonicalize}=await import('/assets/js/personalization/preferences.js');
              const now=Date.parse('2026-10-28T00:00:00Z');
              const candidates=[{id:'a',source:{id:'s1'},publishedAt:new Date(now-3600000).toISOString(),tags:[{id:'ai',confidence:1}]},{id:'b',source:{id:'s2'},publishedAt:new Date(now).toISOString(),tags:[{id:'space',confidence:1}]}];
              const base={enabled:true,weights:{ai:5},excludedTags:[],excludedSources:[]};
              return {a:rank(candidates,base,now)[0].id,b:rank(candidates,{...base,weights:{space:5}},now)[0].id,excluded:rank(candidates,{...base,excludedTags:['ai']},now).map(a=>a.id),noTags:rank(candidates,{...base,weights:{}},now)[0].id,partial:extractTags('rain said',[{alias:'ai',tagId:'ai'}]),merged:canonicalize(base,[{oldId:'ai',canonicalId:'new'}]).weights};
            }
            """);@SuppressWarnings("unchecked")var map=(Map<String,Object>)result;assertEquals("a",map.get("a"));assertEquals("b",map.get("b"));assertEquals(List.of("b"),map.get("excluded"));assertEquals("b",map.get("noTags"));assertEquals(List.of(),map.get("partial"));assertEquals(Map.of("new",5),map.get("merged"));}
    }

    @Test void guestMenusProtectedPagesAndSearchDialog()throws Exception{
        try(var context=browser.newContext()){
            context.route("**/api/v1/account",r->r.fulfill(new Route.FulfillOptions().setStatus(401).setContentType("application/json").setBody("{}")));
            Page p=context.newPage();List<String> traffic=new ArrayList<>();p.onRequest(r->traffic.add(r.url()+" "+Objects.toString(r.postData(),"")));
            for(String page:List.of("index","about","terms","privacy","login","signup")){
                p.navigate(origin+"/"+page+".html");p.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
                for(String destination:List.of("settings","account","admin"))assertEquals(0,p.locator("a[href='/"+destination+".html']:visible").count(),page+" leaks "+destination);
            }
            for(String page:List.of("settings","account","admin")){p.navigate(origin+"/"+page+".html");p.waitForURL("**/login.html");}
            p.navigate(origin+"/index.html");p.waitForSelector("#paper article");assertEquals(0,p.locator("#interest-basis,#range").count());assertEquals(0,p.locator("#paper .reason").count());
            assertEquals(0,p.locator("[data-view],#latest,.tags button").count());assertFalse(p.locator("#search").isVisible());
            p.locator("#open-search").click();assertTrue(p.locator("#search").isVisible());p.locator("#search").fill("synthetic-popup-private-query");p.keyboard().press("Escape");assertEquals(7,p.locator("#paper article").count());
            p.locator("#open-search").click();assertEquals("",p.locator("#search").inputValue());p.locator("#search").fill("synthetic-popup-private-query");p.locator("#search-form [type=submit]").click();p.waitForFunction("document.querySelector('[role=status]').textContent==='' && document.querySelector('#paper article')===null");assertEquals(0,p.locator("#paper article").count());assertFalse(p.locator("#search-dialog").isVisible());
            p.locator("#clear-search").click();assertEquals(7,p.locator("#paper article").count());
            for(int width:new int[]{390,768,1440}){p.setViewportSize(width,1000);p.locator("#open-search").click();assertTrue((boolean)p.evaluate("document.documentElement.scrollWidth<=innerWidth"));p.screenshot(new Page.ScreenshotOptions().setPath(Path.of("build/reports/screenshots/search-dialog-"+width+".png")));p.locator("#cancel-search").click();}
            assertEquals(1,traffic.stream().filter(v->v.contains("synthetic-popup-private-query")).count());assertTrue(traffic.stream().filter(v->v.contains("synthetic-popup-private-query")).allMatch(v->v.startsWith(origin+"/api/v1/public/news/search ")));assertTrue(traffic.stream().noneMatch(v->v.contains("/api/v1/news/edition")));
        }
    }

    @Test void memberAndAdminMenusAndRepresentativeEditor()throws Exception{
        for(String role:List.of("USER","ADMIN"))try(var context=browser.newContext()){
            context.route("**/api/v1/account",r->r.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody("{\"id\":\""+A+"\",\"username\":\"synthetic_role\",\"role\":\""+role+"\"}")));
            var writes=new ArrayList<String>();
            context.route("**/api/v1/admin/**",r->{String body="[]";if(r.request().url().endsWith("/interests")){if(r.request().method().equals("PUT")){writes.add(r.request().postData());body="{\"version\":1,\"weights\":{\"space\":3,\"ai\":3}}";}else body="{\"version\":0,\"weights\":{\"space\":3}}";}r.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody(body));});
            Page p=context.newPage();
            for(String page:List.of("index","about","settings","account","terms","privacy")){
                p.navigate(origin+"/"+page+".html");p.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
                if(p.locator("#account-menu").count()>0){assertEquals(1,p.locator("#account-menu a[href='/settings.html']").count());assertEquals(1,p.locator("#account-menu a[href='/account.html']").count());assertEquals(role.equals("ADMIN")?1:0,p.locator("#account-menu a[href='/admin.html']").count());assertEquals(0,p.locator("#account-menu a[href='/login.html']").count());}
                if(List.of("settings","account").contains(page))assertTrue(p.locator("main").isVisible());
            }
            p.navigate(origin+"/admin.html");
            if(role.equals("USER")){p.waitForURL("**/index.html");assertTrue(writes.isEmpty());}
            else {p.waitForSelector("#representative-ai");p.locator("#representative-ai").check();p.locator("#representative-form [type=submit]").click();p.waitForFunction("document.querySelector('#representative-status').textContent.includes('저장했습니다')");assertEquals(1,writes.size());assertTrue(writes.getFirst().contains("\"ai\":3"));assertTrue(writes.getFirst().contains("\"version\":0"));}
        }
    }
    @Test void defaultInterestsAndPolicyEditionsAreExplicit(){
        try(var context=browser.newContext()){
            Page p=context.newPage();p.navigate(origin+"/index.html");p.waitForSelector("#paper article");assertEquals(0,p.locator("#interest-basis,#range").count());assertEquals(0,p.locator("#paper .reason").count());
            assertEquals(0,p.locator("a[href='/admin.html']:visible").count());assertEquals(1,p.locator("#account-menu a[href='/settings.html']").count());
            assertEquals(true,p.evaluate("""
            async () => {const {effectivePreferences,rank}=await import('/assets/js/personalization/ranking.js');const {defaults}=await import('/assets/js/personalization/preferences.js');
              const preset={space:5};const empty=effectivePreferences(defaults(),preset);const own=effectivePreferences({...defaults(),weights:{ai:4}},preset);
              const disabled=effectivePreferences({...defaults(),enabled:false,weights:{ai:4},excludedTags:['space']},preset);
              return empty.weights.space===5&&own.weights.ai===4&&disabled.weights.space===5&&disabled.excludedTags[0]==='space';}
            """));
            p.evaluate("async id => {const m=await import('/assets/js/personalization/preferences.js');await m.savePreferences(id,{...m.defaults(),weights:{ai:5}});}",A);p.reload();p.waitForSelector("#paper article");assertEquals(0,p.locator("#paper .reason").count());assertEquals(0,p.locator("#paper button").count());
            for(String page:List.of("terms","privacy")){p.navigate(origin+"/"+page+".html");String text=p.locator("main").textContent();assertTrue(text.contains("약관 개정 제 1판"));assertFalse(text.contains("시행일:"));assertFalse(text.contains("최종 수정:"));assertEquals(0,p.locator("a[href='/policy-history.html'],a[href^='/policies/']").count());}
        }
    }


    @Test void editorialScrapGalleryAndInitialPolicies(){
        try(var context=browser.newContext()){
            Page p=context.newPage();p.navigate(origin+"/index.html");p.waitForSelector("#paper article");
            assertEquals("MoA-PaN",p.locator(".topline [data-brand-en]").textContent());
            assertEquals(p.evaluate("async()=> (await import('/assets/js/shared/config.js')).brand.mastheadQuote"),p.locator("h1").textContent());
            assertEquals(1,p.locator(".topline #open-search").count());
            assertEquals(0,p.locator("main #open-search,#range,#interest-basis,main nav:not(.slide-controls)").count());
            assertFalse(p.locator("header").textContent().contains("개인화 뉴스 V1"));
            for(int width:new int[]{390,768,1440}){
                p.setViewportSize(width,1000);
                p.evaluate("async()=>{await document.fonts.ready;await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));}");
                assertEquals(width==390?"1":width==768?"2":"4",p.locator("#paper").evaluate("e=>getComputedStyle(e).columnCount"));
                assertTrue((boolean)p.evaluate("document.documentElement.scrollWidth<=innerWidth"));
                p.screenshot(new Page.ScreenshotOptions().setPath(Path.of("build/reports/screenshots/scrap-gallery-"+width+".png")));
            }
            p.emulateMedia(new Page.EmulateMediaOptions().setMedia(com.microsoft.playwright.options.Media.PRINT));
            assertEquals("none",p.locator("#paper article").first().evaluate("e=>getComputedStyle(e).transform"));
            for(String old:List.of("policy-history","policies/terms-1","policies/terms-2","policies/terms-3","policies/privacy-1","policies/privacy-2","policies/privacy-3","policies/privacy-4"))assertEquals(404,context.request().get(origin+"/"+old+".html").status());
        }
    }

    @Test void loadingEmptyErrorAndKeyboardStatesAreUsable(){
        try(var context=browser.newContext()){
            Page p=context.newPage();var pending=new java.util.concurrent.atomic.AtomicReference<Route>();
            p.route("**/api/v1/news/edition*",pending::set);
            p.navigate(origin+"/index.html",new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.COMMIT));
            p.waitForFunction("document.querySelector('[role=status]').textContent.includes('불러오는 중')");p.waitForCondition(()->pending.get()!=null);assertNotNull(pending.get());
            pending.get().fulfill(new Route.FulfillOptions().setContentType("application/json").setBody("{\"editionId\":\"empty-fixture\",\"editionAt\":\"2026-10-28T00:00:00Z\",\"periodDays\":7,\"candidateCount\":0,\"items\":[],\"nextCursor\":null}"));
            p.waitForSelector("#paper p");assertEquals(0,p.locator("#paper article").count());assertTrue(p.locator("#paper").textContent().contains("표시할 기사가 없습니다"));
            p.keyboard().press("Tab");assertEquals("#main",p.evaluate("document.activeElement.getAttribute('href')"));
            p.unroute("**/api/v1/news/edition*");p.route("**/api/v1/news/edition*",r->r.fulfill(new Route.FulfillOptions().setStatus(503).setContentType("application/json").setBody("{\"error\":\"temporarily_unavailable\"}")));
            p.reload();p.waitForFunction("document.querySelector('[role=status]').textContent.includes('일시적으로')");assertFalse(p.locator("#more").isVisible());
        }
    }


    @Test void adminSiteSwitchClearsSourceAndSummarySavePreservesTags(){
        try(var context=browser.newContext()){
            List<String> writes=new ArrayList<>();
            context.route("**/api/v1/**",r->{
                String path=URI.create(r.request().url()).getPath();String body="[]";
                if(r.request().method().equals("PATCH")||r.request().method().equals("POST")){writes.add(path+" "+r.request().postData());body="{}";}
                else if(path.endsWith("/account"))body="{\"id\":\""+A+"\",\"role\":\"ADMIN\",\"username\":\"synthetic\"}";
                else if(path.endsWith("/csrf"))body="{\"token\":\"test\"}";
                else if(path.endsWith("/interests"))body="{\"version\":0,\"weights\":{}}";
                else if(path.endsWith("/tags"))body="{\"tags\":[],\"aliases\":[],\"redirects\":[]}";
                else if(path.equals("/api/v1/admin/sites"))body="[{\"id\":\"A\",\"name\":\"Site A\",\"homepage_url\":\"https://a.example\"},{\"id\":\"B\",\"name\":\"Site B\",\"homepage_url\":\"https://b.example\"}]";
                else if(path.equals("/api/v1/admin/sites/A"))body="{\"id\":\"A\",\"name\":\"Site A\",\"homepage_url\":\"https://a.example\",\"version\":0}";
                else if(path.equals("/api/v1/admin/sites/B"))body="{\"id\":\"B\",\"name\":\"Site B\",\"homepage_url\":\"https://b.example\",\"version\":0}";
                else if(path.equals("/api/v1/admin/sites/A/sources"))body="[{\"id\":\"source-A\",\"label\":\"Source A\",\"type\":\"RSS\"}]";
                else if(path.equals("/api/v1/admin/sources/source-A"))body="{\"id\":\"source-A\",\"site_id\":\"A\",\"label\":\"Source A\",\"type\":\"RSS\",\"endpoint_url\":\"https://a.example/feed\",\"language\":\"en\",\"interval_seconds\":900,\"rights_status\":\"UNREVIEWED\"}";
                else if(path.endsWith("/articles"))body="[{\"id\":\"article-A\",\"title\":\"Synthetic article\",\"summary_bullets\":\"[\\\"Original summary\\\"]\",\"tag_ids\":\"[\\\"tag-A\\\"]\",\"public\":true}]";
                r.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody(body));
            });
            Page p=context.newPage();p.navigate(origin+"/admin.html");
            p.locator("#sites tr").nth(0).getByText("상세·수정").click();p.locator("#source-list").getByText("수정",new com.microsoft.playwright.Locator.GetByTextOptions().setExact(true)).click();
            p.waitForFunction("document.querySelector('#source-form [name=label]').value==='Source A'");
            p.locator("#sites tr").nth(1).getByText("상세·수정").click();
            p.waitForFunction("document.querySelector('#site-form [name=name]').value==='Site B'");
            assertEquals("",p.locator("#source-form [name=label]").inputValue());assertEquals("",p.locator("#source-form [name=endpointUrl]").inputValue());
            p.locator("#refresh-articles").click();p.waitForSelector("#article-list form");
            assertEquals("tag-A",p.getByLabel("수동 태그 UUID 목록").inputValue());
            p.getByLabel("수동 요약 (한 줄 한 항목)").fill("Changed summary");p.locator("#article-list button").click();
            p.waitForCondition(()->!writes.isEmpty());assertTrue(writes.getFirst().contains("\"tagIds\":null"));
        }
    }

    @Test void expiredSessionPreservesLocalPreferences(){
        try(var context=browser.newContext()){
            Page p=context.newPage();p.navigate(origin+"/about.html");
            p.evaluate("async id=>{const m=await import('/assets/js/personalization/preferences.js');await m.savePreferences(id,{...m.defaults(),weights:{ai:5}});sessionStorage.setItem('moapan-account',id);}",A);
            context.route("**/api/v1/account",r->r.fulfill(new Route.FulfillOptions().setStatus(401).setContentType("application/json").setBody("{}")));
            p.evaluate("async()=>{try{await(await import('/assets/js/shared/api.js')).account();}catch(e){if(e.status!==401)throw e;}}");
            assertEquals(5,((Number)p.evaluate("async id=>(await(await import('/assets/js/personalization/preferences.js')).loadPreferences(id)).weights.ai",A)).intValue());
            assertNull(p.evaluate("sessionStorage.getItem('moapan-account')"));
        }
    }
    @Test void administratorCanEditBoundedClassificationTopics(){
        try(var context=browser.newContext()){
            var writes=new ArrayList<String>();
            context.route("**/api/v1/account",r->r.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody("{\"id\":\""+A+"\",\"username\":\"synthetic_admin\",\"role\":\"ADMIN\"}")));
            context.route("**/api/v1/admin/**",r->{String body="[]";String path=URI.create(r.request().url()).getPath();
                if(path.equals("/api/v1/admin/interests"))body="{\"version\":0,\"weights\":{\"ai\":3}}";
                if(path.equals("/api/v1/admin/classification"))body="{\"catalog\":{\"version\":0,\"topics\":[{\"id\":\"ai\",\"definition\":\"Model evaluation only\"}]},\"counts\":[],\"runtime\":{},\"recent\":[]}";
                if(path.endsWith("/classification/topics")){assertEquals("PUT",r.request().method());writes.add(r.request().postData());body="{}";}
                r.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody(body));});
            var p=context.newPage();p.navigate(origin+"/admin.html");p.locator("#refresh-classification").click();p.waitForFunction("document.querySelectorAll('#classification-topics textarea').length===2");
            assertTrue(p.locator("#classify-ai").isChecked());assertEquals("200",p.locator("#classification-topics textarea").first().getAttribute("maxlength"));
            p.locator("#classification-topics textarea").first().fill("LLM evaluation; exclude unrelated applications");p.locator("#classification-form button[type=submit]").click();p.waitForFunction("document.querySelector('#classification-message').textContent.includes('저장했습니다')");assertEquals(1,writes.size());assertTrue(writes.getFirst().contains("LLM evaluation"));assertTrue(writes.getFirst().contains("\"version\":0"));
        }
    }
}
