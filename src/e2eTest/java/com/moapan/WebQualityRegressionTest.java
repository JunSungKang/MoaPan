package com.moapan;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

class WebQualityRegressionTest {
    @BeforeAll static void start()throws Exception{BrowserTest.start();}
    @AfterAll static void stop(){BrowserTest.stop();}
    @Test void commonEditionLoadsInParallelWithAccountWithoutDuplicateNewsRequests(){
        try(var context=BrowserTest.browser.newContext()){
            var presentation=new java.util.concurrent.atomic.AtomicReference<Route>();var auth=new java.util.concurrent.atomic.AtomicReference<Route>();var requests=new java.util.concurrent.atomic.AtomicInteger();
            context.route("**/assets/js/edition/news.js",presentation::set);
            context.route("**/api/v1/auth/session",auth::set);
            context.route("**/api/v1/public/news/edition",r->{requests.incrementAndGet();r.fallback();});
            var resources=new ArrayList<String>();var page=context.newPage();page.onRequest(r->resources.add(r.url()));page.navigate(BrowserTest.origin+"/index.html",new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.COMMIT));
            page.waitForCondition(()->presentation.get()!=null&&requests.get()==1);
            assertNull(auth.get(),"Bounded edition data must start before presentation imports and account initialization");
            presentation.get().resume();page.waitForCondition(()->auth.get()!=null);
            assertEquals(0,page.locator("#paper article").count());
            auth.get().fulfill(new Route.FulfillOptions().setStatus(204));
            try{page.waitForSelector("#paper article");}catch(RuntimeException failure){throw new AssertionError(page.locator("[role=status]").textContent(),failure);}assertEquals(1,requests.get());assertEquals(7,page.locator("#paper article").count());
            page.waitForLoadState(LoadState.NETWORKIDLE);
            assertTrue(resources.stream().noneMatch(url->url.endsWith("/i18n-features.js")),"Edition must not download unrelated feature vocabulary");
            page.locator("[data-language-select]").selectOption("en");
            assertThat(page.locator("#open-search")).containsText("Search articles");
            assertThat(page.locator(".content-menu")).hasAttribute("aria-label","Content navigation");
        }
    }
    @Test void delayedAdvertisingSettingsNeverOverwriteEditsMadeWhileLoading(){
        try(var context=BrowserTest.browser.newContext()){
            SiteDesignAuditTest.routes(context,"ADMIN");var response=new java.util.concurrent.atomic.AtomicReference<Route>();
            context.route("**/api/v1/admin/advertisements",response::set);var page=context.newPage();
            page.navigate(BrowserTest.origin+"/admin.html#advertising");page.waitForCondition(()->response.get()!=null);
            page.locator("#advertising-form [name=title]").fill("Unsaved synthetic banner");
            response.get().fulfill(new Route.FulfillOptions().setContentType("application/json").setBody("{\"version\":2,\"enabled\":false,\"provider\":\"BANNER\",\"title\":\"Stored title\",\"description\":\"Stored description\"}"));
            assertThat(page.locator("#advertising-form [name=description]")).hasValue("Stored description");
            assertThat(page.locator("#advertising-form [name=title]")).hasValue("Unsaved synthetic banner");
            page.locator("#sections-tab-collection").click();page.goBack();
            assertThat(page.locator("#advertising-form [name=title]")).hasValue("Unsaved synthetic banner");
        }
    }
    @Test void advertisingSaveAndReloadCannotRace(){
        try(var context=BrowserTest.browser.newContext()){
            SiteDesignAuditTest.routes(context,"ADMIN");var saving=new java.util.concurrent.atomic.AtomicReference<Route>();var reads=new java.util.concurrent.atomic.AtomicInteger();
            context.route("**/api/v1/admin/advertisements",route->{if(route.request().method().equals("PUT"))saving.set(route);else{reads.incrementAndGet();route.fallback();}});
            var page=context.newPage();page.navigate(BrowserTest.origin+"/admin.html#advertising");
            assertThat(page.locator("#advertising-form [type=submit]")).isEnabled();
            page.locator("#advertising-form [name=title]").fill("Synthetic sponsor");page.locator("#advertising-form [name=targetUrl]").fill("https://example.org/");
            page.locator("#advertising-form [type=submit]").click();page.waitForCondition(()->saving.get()!=null);
            assertThat(page.locator("#reload-advertising")).isDisabled();
            assertThat(page.locator("#advertising-form [type=submit]")).isDisabled();
            page.evaluate("()=>document.querySelector('#advertising-form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}))");
            assertEquals(1,reads.get());
            saving.get().fulfill(new Route.FulfillOptions().setContentType("application/json").setBody(saving.get().request().postData()));
            assertThat(page.locator("#reload-advertising")).isEnabled();assertThat(page.locator("#advertising-form [type=submit]")).isEnabled();
            assertThat(page.locator("#advertising-form [name=title]")).hasValue("Synthetic sponsor");
        }
    }
    @Test void englishFirstDynamicControlsKeepTheirKoreanSourceKeys(){
        try(var context=BrowserTest.browser.newContext()){
            SiteDesignAuditTest.routes(context,"ADMIN");AdminUsabilityTest.dictionary(context);
            context.addInitScript("localStorage.setItem('moapan-language','en')");
            context.route("**/api/v1/admin/articles**",r->r.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(List.of(AdminUsabilityTest.article("synthetic-review","Synthetic review","2026-10-30T00:00:00Z"))))));
            var page=context.newPage();page.navigate(BrowserTest.origin+"/admin.html#articles");page.locator("#refresh-articles").click();
            var publication=page.locator(".article-review-options label").first();assertThat(publication).hasText("Published");
            page.evaluate("async()=>{const {el}=await import('/assets/js/shared/dom.js');const root=el('div');root.id='synthetic-labels';for(const text of ['수정','편집',' 수정 ']){const node=el('span',text);node.title=text.trim();root.append(node);}document.querySelector('main').append(root);}");
            page.locator("[data-language-select]").selectOption("ko");assertThat(publication).hasText("공개");
            assertEquals(List.of("수정","편집"," 수정 "),page.locator("#synthetic-labels span").allTextContents());
            assertEquals(List.of("수정","편집","수정"),page.locator("#synthetic-labels span").evaluateAll("nodes=>nodes.map(node=>node.title)"));
            assertEquals(List.of("Edit","Published"),page.evaluate("async()=>{const {articleCard}=await import('/assets/js/article/card.js');const node=articleCard({title:'Edit',summaryBullets:['Published'],tags:[],source:{site:'Synthetic',language:'en'},originalUrl:'https://example.org/'},0);document.querySelector('main').append(node);await new Promise(resolve=>setTimeout(resolve,0));return [node.querySelector('h2').textContent,node.querySelector('li').textContent];}"));
            assertEquals("constructor",page.evaluate("async()=>(await import('/assets/js/shared/i18n.js')).t('constructor')"));
            page.locator("[data-language-select]").selectOption("en");assertThat(publication).hasText("Published");
        }
    }
    @Test void originalFieldsAndResearchNotesNeverUseTheInterfaceDictionary(){
        try(var context=BrowserTest.browser.newContext()){
            SiteDesignAuditTest.routes(context,"ADMIN");context.route("**/api/v1/admin/newsletters",route->route.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody("[{\"id\":\""+BrowserTest.A+"\",\"title\":\"공개\"}]")));
            var page=context.newPage();page.navigate(BrowserTest.origin+"/newsletter-editor.html");assertThat(page.locator("#drafts button span").last()).hasText("공개");
            page.evaluate("""
                async()=>{const {readerTools}=await import('/assets/js/newsletter/reader-tools.js');const {originalEl}=await import('/assets/js/shared/dom.js');const panel=document.createElement('section');panel.id='synthetic-notes';
                document.querySelector('main').append(panel);panel.append(originalEl('span','공개','original-probe'),originalEl('span','Edit','original-probe'));
                readerTools(panel,{id:'synthetic',manuscript:{language:'und'},dossier:{weekStart:'2026-11-02',cutoff:'2026-11-03T00:00:00Z',forecasts:[{question:'Edit',observation:'공개',outlook:'Published',horizon:'Edit',experiment:'Edit',successCriteria:'Edit',stopCondition:'Edit',evidenceIds:['synthetic']}],correctionNote:'Edit'},evidence:[{id:'synthetic',title:'Published',url:'https://example.org/',collectedAt:'2026-11-03T00:00:00Z'}]},()=> 'Synthetic');}
                """);
            for(String language:List.of("en","ko","en")){
                page.locator("[data-language-select]").selectOption(language);
                assertThat(page.locator("#drafts button span").last()).hasText("공개");
                assertEquals(List.of("공개","Edit"),page.locator(".original-probe").allTextContents());
                assertThat(page.locator("#synthetic-notes h3")).hasText("Edit");assertThat(page.locator("#synthetic-notes dd").first()).hasText("공개");
                assertThat(page.locator("#synthetic-notes .research-notes a")).hasText("Published");
            }
            context.route("**/api/v1/newsletters",route->route.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody("[{\"id\":\""+BrowserTest.A+"\",\"title\":\"Edit\",\"introduction\":\"Published\",\"language\":\"en\"}]")));
            page.navigate(BrowserTest.origin+"/index.html");
            assertThat(page.locator("[data-newsletter-content] a")).hasText("Edit");assertThat(page.locator("[data-newsletter-content] a")).hasAttribute("lang","en");
            assertThat(page.locator("[data-newsletter-content] p")).hasText("Published");assertThat(page.locator("[data-newsletter-content] p")).hasAttribute("lang","en");
        }
    }
    @Test void keyboardAndHashRevealInitializeHiddenAdminPanelsOnce(){
        try(var context=BrowserTest.browser.newContext()){
            SiteDesignAuditTest.routes(context,"ADMIN");var page=context.newPage();var requests=new ArrayList<String>();
            page.onRequest(r->requests.add(r.url()));page.navigate(BrowserTest.origin+"/admin.html");page.waitForLoadState(LoadState.NETWORKIDLE);
            assertTrue(requests.stream().noneMatch(u->u.endsWith("/admin/classification")||u.endsWith("/admin/subscriptions/search")||u.endsWith("/admin/interests")||u.endsWith("/admin/advertisements")));
            page.locator("#sections-tab-collection").focus();page.keyboard().press("ArrowRight");page.keyboard().press("ArrowRight");
            assertThat(page.locator("#classification-topics .classification-choice")).hasCount(2);
            assertEquals(1,requests.stream().filter(u->u.endsWith("/admin/classification")).count());
            page.locator("#sections-tab-articles").click();page.locator("#sections-tab-topics").click();page.waitForLoadState(LoadState.NETWORKIDLE);
            assertEquals(1,requests.stream().filter(u->u.endsWith("/admin/classification")).count());
            page.navigate(BrowserTest.origin+"/admin.html#topics/dictionary");assertThat(page.locator("#topic-edit-choice option")).hasCount(3);
        }
    }
    @Test void sharedRendererKeepsManuscriptLanguageDimensionsAndDestinationDocument(){
        try(var context=BrowserTest.browser.newContext()){
            var page=context.newPage();page.navigate(BrowserTest.origin+"/index.html");page.waitForSelector("#paper article");
            var answer=(Map<?,?>)page.evaluate("""
                async()=>{const {render,manuscriptLanguage}=await import('/assets/js/newsletter/render.js');
                const doc=document.implementation.createHTMLDocument('Synthetic'),parent=doc.createElement('div');doc.body.append(parent);
                render(parent,{language:'ko',blocks:[{type:'paragraph',text:'합성 원고'},{type:'image',text:'합성 그림',caption:'Owned fixture',url:'data:image/png;base64,AAAA',width:40,height:30},{type:'reference',text:'unsafe',url:'javascript:alert(1)'}]});
                return {language:parent.lang,unknown:manuscriptLanguage({}),destination:[...parent.querySelectorAll('*')].every(n=>n.ownerDocument===doc),width:parent.querySelector('img').width,height:parent.querySelector('img').height,unsafeLinks:parent.querySelectorAll('a').length,text:parent.querySelector('p').textContent};}
                """);
            assertEquals("ko",answer.get("language"));assertEquals("und",answer.get("unknown"));assertEquals(true,answer.get("destination"));assertEquals(40,answer.get("width"));assertEquals(30,answer.get("height"));assertEquals(0,answer.get("unsafeLinks"));assertEquals("합성 원고",answer.get("text"));
        }
    }
    @Test void explicitManuscriptLanguageSurvivesUiLanguageSwitchAndExport(){
        try(var context=BrowserTest.browser.newContext()){
            SiteDesignAuditTest.routes(context,"ADMIN");var page=context.newPage();page.navigate(BrowserTest.origin+"/newsletter-editor.html");page.waitForSelector("#letter-form:visible");
            page.locator("[name=language]").selectOption("ko");page.locator("[name=title]").fill("합성 원고");page.locator("[data-language-select]").selectOption("en");
            assertEquals("ko",page.locator("#preview-title").getAttribute("lang"));assertEquals("ko",page.locator("#preview-body").getAttribute("lang"));
            assertEquals(true,page.evaluate("async()=>{const {htmlNewsletter}=await import('/assets/js/newsletter/publication-output.js');return htmlNewsletter({reuseAllowed:true,distribution:'FREE_PREVIEW',manuscript:{title:'합성',introduction:'합성',language:'ko',blocks:[]}},'2026-001',location.origin,'en').includes('<article lang=\"ko\">');}"));
        }
    }
    @Test void formBordersAndPlaceholdersKeepReadableContrastAcrossBothLayouts(){
        try(var context=BrowserTest.browser.newContext()){
            SiteDesignAuditTest.routes(context,"ADMIN");var page=context.newPage();
            for(String name:List.of("login","newsletter-editor")){
                page.navigate(BrowserTest.origin+"/"+name+".html");page.waitForLoadState(LoadState.NETWORKIDLE);
                if(name.equals("newsletter-editor"))page.locator("[data-add=paragraph]").click();
                var results=(List<?>)page.evaluate("""
                    ()=>{const rgb=s=>s.match(/[\\d.]+/g).map(Number),luminance=rgb=>rgb.slice(0,3).map(v=>{v/=255;return v<=.04045?v/12.92:((v+.055)/1.055)**2.4;}).reduce((n,v,i)=>n+v*[.2126,.7152,.0722][i],0),contrast=(a,b)=>{const x=luminance(a),y=luminance(b);return(Math.max(x,y)+.05)/(Math.min(x,y)+.05);};
                    return [...document.querySelectorAll('input:not([type=checkbox]),textarea,select')].filter(e=>e.getClientRects().length&&!e.disabled).map(e=>{let node=e,bg=[255,255,255];while(node){const c=rgb(getComputedStyle(node).backgroundColor);if(c.length===3||c[3]===1){bg=c;break;}node=node.parentElement;}const s=getComputedStyle(e);return {name:e.id||e.name,border:parseFloat(s.borderTopWidth)>0?contrast(rgb(s.borderTopColor),bg):null,placeholder:e.placeholder?contrast(rgb(getComputedStyle(e,'::placeholder').color),bg):null};});}
                    """);
                for(Object item:results){var values=(Map<?,?>)item;if(values.get("border") instanceof Number border)assertTrue(border.doubleValue()>=3,name+" "+values);if(values.get("placeholder") instanceof Number placeholder)assertTrue(placeholder.doubleValue()>=4.5,name+" "+values);}
            }
        }
    }
}
