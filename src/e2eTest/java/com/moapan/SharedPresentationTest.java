package com.moapan;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SharedPresentationTest {
    @BeforeAll static void start()throws Exception{BrowserTest.start();}
    @AfterAll static void stop(){BrowserTest.stop();}

    @Test void dynamicTranslationsRoundTripAndRespectOriginalContent(){
        try(var context=BrowserTest.browser.newContext()){
            var page=context.newPage();page.navigate(BrowserTest.origin+"/about.html");page.waitForSelector("[data-language-select]");
            assertEquals(true,page.evaluate("""
                async()=>{const {setLanguage}=await import('/assets/js/shared/i18n.js');const root=document.createElement('section');
                const label=document.createElement('span');label.textContent='로그인';const input=document.createElement('input');input.placeholder='아이디';
                const original=document.createElement('div');original.dataset.originalContent='';const nested=document.createElement('span');nested.textContent='로그인';original.append(nested);
                const pre=document.createElement('pre');pre.textContent='로그인';const area=document.createElement('textarea');area.value='회원가입';root.append(label,input,original,pre,area);document.body.append(root);
                setLanguage('en');await new Promise(r=>setTimeout(r,0));if(label.textContent!=='Log in'||nested.textContent!=='로그인'||pre.textContent!=='로그인'||area.value!=='회원가입')return false;
                label.textContent='회원가입';input.setAttribute('aria-label','로그인');await new Promise(r=>setTimeout(r,0));if(label.textContent!=='Sign up'||input.getAttribute('aria-label')!=='Log in')return false;
                setLanguage('ko');await new Promise(r=>setTimeout(r,0));const ok=label.textContent==='회원가입'&&input.getAttribute('aria-label')==='로그인';root.remove();return ok;}
                """));
        }
    }

    @Test void concurrentWritesShareOneCsrfFetch(){
        try(var context=BrowserTest.browser.newContext()){
            var tokens=new AtomicInteger();var headers=new ArrayList<String>();
            context.route("**/api/v1/auth/csrf",r->{tokens.incrementAndGet();r.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody("{\"token\":\"synthetic-shared-token\"}"));});
            context.route("**/api/v1/synthetic-write",r->{headers.add(r.request().headerValue("X-CSRF-TOKEN"));r.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody("{}"));});
            var page=context.newPage();page.navigate(BrowserTest.origin+"/index.html");page.waitForSelector("#paper article");
            page.evaluate("async()=>{const {request}=await import('/assets/js/shared/api.js');await Promise.all([request('/api/v1/synthetic-write',{method:'POST',body:{}}),request('/api/v1/synthetic-write',{method:'POST',body:{}})]);}");
            assertEquals(1,tokens.get());assertEquals(List.of("synthetic-shared-token","synthetic-shared-token"),headers);
        }
    }

    @Test void expiredSessionCannotReviveAnInFlightCsrfToken(){
        try(var context=BrowserTest.browser.newContext()){
            var page=context.newPage();page.navigate(BrowserTest.origin+"/index.html");page.waitForSelector("#paper article");
            assertEquals(true,page.evaluate("""
                async()=>{const api=await import('/assets/js/shared/api.js');const real=window.fetch;let finish,first=true,header;
                window.fetch=(url,options)=>{if(url==='/api/v1/auth/csrf'){if(first){first=false;return new Promise(r=>finish=r);}return Promise.resolve(new Response(JSON.stringify({token:'synthetic-new'})));}
                if(url==='/api/v1/synthetic-expiry')return Promise.resolve(new Response('{}',{status:401}));
                if(url==='/api/v1/synthetic-write'){header=options.headers['X-CSRF-TOKEN'];return Promise.resolve(new Response('{}'));}return real(url,options);};
                try{const old=api.refreshCsrf().then(()=>false,()=>true);await api.request('/api/v1/synthetic-expiry').catch(()=>{});finish(new Response(JSON.stringify({token:'synthetic-old'})));const rejected=await old;await api.request('/api/v1/synthetic-write',{method:'POST',body:{}});return rejected&&header==='synthetic-new';}finally{window.fetch=real;}}
                """));
        }
    }

    @Test void cleanArticleEditorsAreReleasedButDraftsAndSavedStateSurvive(){
        try(var context=BrowserTest.browser.newContext()){
            SiteDesignAuditTest.routes(context,"ADMIN");AdminUsabilityTest.dictionary(context);
            var rows=new ArrayList<Object>();for(int i=0;i<30;i++)rows.add(AdminUsabilityTest.article("memory-"+i,"Synthetic "+i,"2026-10-30T01:00:00Z"));
            context.route("**/api/v1/admin/articles**",r->r.fulfill(new Route.FulfillOptions().setContentType("application/json").setBody(r.request().method().equals("PATCH")?"{}":AdminUsabilityTest.JSON.writeValueAsString(rows))));
            var page=context.newPage();page.navigate(BrowserTest.origin+"/admin.html#articles");page.locator("#refresh-articles").click();page.waitForSelector(".article-review-sheet");
            for(int i=1;i<30;i++)page.locator("#review-article-picker").selectOption(String.valueOf(i));
            assertEquals(1,page.locator(".article-review-sheet").count());
            page.getByLabel("수동 요약 (한 줄 한 항목)").fill("Synthetic retained draft");page.locator("#review-previous").click();
            assertEquals(2,page.locator(".article-review-sheet").count());page.locator("#review-next").click();assertEquals("Synthetic retained draft",page.getByLabel("수동 요약 (한 줄 한 항목)").inputValue());
            page.getByText("요약·태그·공개 상태 저장",new Page.GetByTextOptions().setExact(true)).click();page.waitForCondition(()->page.locator("#refresh-articles").isEnabled());
            page.locator("#review-previous").click();assertEquals(1,page.locator(".article-review-sheet").count());page.locator("#review-next").click();assertEquals("Synthetic retained draft",page.getByLabel("수동 요약 (한 줄 한 항목)").inputValue());
        }
    }
}
