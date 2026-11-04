package com.moapan;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import java.nio.file.*;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

/** Explicit audit task, disposable fixtures only; never connects to the operating database. */
public final class WebQualityAudit {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private static String chromeExecutable;
    private static final List<String> PAGES=List.of("index","about","privacy","terms","login","signup","forgot-password","settings","account","admin","newsletter","newsletter-editor","404");
    public static void main(String[] args)throws Exception {
        String stage=args.length==0?"final":args[0];
        if(!Set.of("baseline","preflight","final").contains(stage))throw new IllegalArgumentException("Invalid stage");
        Path output=Path.of("build/reports/web-quality",stage);Files.createDirectories(output);
        var findings=accessibility(output);
        try(var postgres=new PostgreSQLContainer<>(DockerImageName.parse("postgres@sha256:edb0c08dc33befd0262ab3b5cf1c4f3f8cfd8b09e88c05b45adee29d6761ddb6").asCompatibleSubstituteFor("postgres"))){
            postgres.start();
            org.flywaydb.core.Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).cleanDisabled(true).load().migrate();
            int port;try(var socket=new java.net.ServerSocket(0,0,java.net.InetAddress.getLoopbackAddress())){port=socket.getLocalPort();}
            var app=new SpringApplication(MoapanApplication.class);
            try(var context=app.run("--spring.datasource.url="+postgres.getJdbcUrl(),"--spring.datasource.username="+postgres.getUsername(),"--spring.datasource.password="+postgres.getPassword(),"--spring.flyway.enabled=true","--moapan.secret=synthetic-quality-only-secret-32-characters","--moapan.public-base-url=http://localhost:"+port,"--moapan.mail.enabled=false","--server.address=127.0.0.1","--server.port="+port,"--server.servlet.session.cookie.secure=false")){
                seed(context.getBean(JdbcClient.class));
                String origin="http://localhost:"+((WebServerApplicationContext)context).getWebServer().getPort()+"/";
                run(List.of("node","scripts/test/lighthouse-audit.mjs",origin,stage),Map.of());
                if(!stage.equals("baseline"))run(List.of("node","scripts/test/site-http-audit.mjs"),Map.of("MOAPAN_AUDIT_BASE_URL",origin));
            }
        }
        if(!stage.equals("baseline")&&!findings.isEmpty())throw new IllegalStateException("Accessibility findings remain: "+findings);
    }
    private static List<Object> accessibility(Path output)throws Exception {
        String axe=Files.readString(Path.of(".local/web-audit-tools/node_modules/axe-core/axe.min.js"));
        var evidence=new ArrayList<Object>();var failures=new ArrayList<Object>();
        BrowserTest.start();chromeExecutable=BrowserTest.playwright.chromium().executablePath();
        try {
            for(int width:new int[]{390,768,1440})for(String lang:List.of("ko","en"))try(var context=BrowserTest.browser.newContext(new Browser.NewContextOptions().setViewportSize(width,900))){
                SiteDesignAuditTest.routes(context,"ADMIN");
                context.addInitScript("localStorage.setItem('moapan-language','"+lang+"')");
                var page=context.newPage();
                for(String name:PAGES){
                    page.navigate(BrowserTest.origin+"/"+name+".html");page.waitForLoadState(LoadState.NETWORKIDLE);
                    scan(page,axe,evidence,failures,name,lang,width);
                    if(name.equals("index")){page.locator("#open-search").click();scan(page,axe,evidence,failures,"search-dialog",lang,width);}
                    if(name.equals("signup")){page.locator("[data-open-terms]").click();page.locator("[data-terms-content] h2").first().waitFor();scan(page,axe,evidence,failures,"terms-dialog",lang,width);}
                    if(name.equals("admin"))for(String tab:List.of("articles","topics","subscriptions","interests","advertising","processing")){
                        var button=page.locator("#sections-tab-"+tab);if(button.count()==0)continue;
                        button.click();page.waitForLoadState(LoadState.NETWORKIDLE);scan(page,axe,evidence,failures,"admin-"+tab,lang,width);
                    }
                    if(name.equals("newsletter")){page.navigate(BrowserTest.origin+"/newsletter.html?id="+BrowserTest.A);page.waitForLoadState(LoadState.NETWORKIDLE);scan(page,axe,evidence,failures,"newsletter-detail",lang,width);}
                }
            }
        }finally{BrowserTest.stop();}
        Files.writeString(output.resolve("accessibility.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("checkedAt",java.time.Instant.now().toString(),"tool","axe-core 4.14.0","scope","13 pages, dynamic panels/dialogs, two languages, three widths; synthetic HTTP fixtures. Manual assistive-technology conformance is separate.","states",evidence,"failures",failures)));
        System.out.println("Accessibility audit: "+evidence.size()+" states; "+failures.size()+" failing states.");return failures;
    }
    private static void scan(Page page,String axe,List<Object> evidence,List<Object> failures,String state,String lang,int width){
        page.evaluate(axe);
        Object violations=page.evaluate("async()=>{const r=await axe.run(document,{runOnly:{type:'tag',values:['wcag2a','wcag2aa','wcag21a','wcag21aa','wcag22aa','best-practice']}});return r.violations.map(v=>({id:v.id,impact:v.impact,description:v.description,nodes:v.nodes.map(n=>({target:n.target,summary:n.failureSummary}))}));}");
        var item=Map.of("state",state,"language",lang,"width",width,"violations",violations);evidence.add(item);if(!((List<?>)violations).isEmpty())failures.add(item);
    }
    private static void run(List<String> command,Map<String,String> extra)throws Exception {
        var p=new ProcessBuilder(command).inheritIO();Path root=Path.of("").toRealPath();
        p.environment().put("HOME",root.resolve(".local/tool-home").toString());p.environment().put("TMPDIR",root.resolve(".local/tmp").toString());p.environment().putAll(extra);p.environment().put("CHROME_PATH",chromeExecutable);
        if(p.start().waitFor()!=0)throw new IllegalStateException("Audit command failed: "+command.get(1));
    }
    private static void seed(JdbcClient db){
        UUID site=UUID.randomUUID(),source=UUID.randomUUID();
        db.sql("INSERT INTO sites(id,name,homepage_url,normalized_homepage_url,status) VALUES(:id,'Synthetic audit source','https://example.org/','https://example.org/','ACTIVE')").param("id",site).update();
        db.sql("INSERT INTO sources(id,site_id,label,type,endpoint_url,normalized_endpoint_url,rights_status,allow_metadata,allow_display,allow_summary) VALUES(:id,:site,'Synthetic feed','RSS','https://example.org/feed','https://example.org/feed','APPROVED',true,true,true)").param("id",source).param("site",site).update();
        for(int i=0;i<42;i++)db.sql("INSERT INTO articles(id,source_id,source_guid,canonical_url,title,summary_bullets,summary_mode,language,input_hash) VALUES(:id,:source,:guid,:url,:title,CAST(:summary AS jsonb),'EXTRACTIVE_ORIGINAL_LANGUAGE','en',:hash)").param("id",UUID.randomUUID()).param("source",source).param("guid","synthetic-"+i).param("url","https://example.org/synthetic/"+i).param("title","[Synthetic] Research findings and conditional experiments "+i).param("summary",JSON.writeValueAsString(List.of("Synthetic fixture only. This text tests rendering, accessibility and loading without collecting personal information."))).param("hash","0".repeat(64)).update();
    }
}
