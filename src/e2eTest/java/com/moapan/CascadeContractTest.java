package com.moapan;
import com.microsoft.playwright.*;import com.microsoft.playwright.options.*;import org.junit.jupiter.api.*;import java.nio.file.*;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
/** Synthetic DOM/style measurements, never saved product CSS or customer data. */
class CascadeContractTest{
 @BeforeAll static void start()throws Exception{BrowserTest.start();}@AfterAll static void stop(){BrowserTest.stop();}
 @Test void preserveEffectivePageStylesAcrossLanguagesWidthsPrintAndFocus()throws Exception{
  var rows=new TreeMap<String,Object>();try(var context=BrowserTest.browser.newContext()){
   SiteDesignAuditTest.routes(context,"ADMIN");var page=context.newPage();var pages=new ArrayList<>(SiteDesignAuditTest.PAGES);pages.add("newsletter.html?id="+BrowserTest.A);
   for(String lang:List.of("ko","en"))for(int width:new int[]{390,768,1440})for(String name:pages){
    String path=name.contains(".html")?name:name+".html";page.setViewportSize(width,1000);page.emulateMedia(new Page.EmulateMediaOptions().setMedia(Media.SCREEN));page.navigate(BrowserTest.origin+"/"+path);page.waitForLoadState(LoadState.NETWORKIDLE);page.locator("[data-language-select]").selectOption(lang);
    for(var media:List.of(Media.SCREEN,Media.PRINT)){page.emulateMedia(new Page.EmulateMediaOptions().setMedia(media));rows.put(lang+"/"+width+"/"+name+"/"+media,styles(page));}
    if(name.equals("index")){page.emulateMedia(new Page.EmulateMediaOptions().setMedia(Media.SCREEN));page.locator("#open-search").click();rows.put(lang+"/"+width+"/search-dialog",styles(page));page.keyboard().press("Escape");}
   }
  }
  var json=tools.jackson.databind.json.JsonMapper.builder().build();Path folder=Path.of("build/reports/cascade");Files.createDirectories(folder);String stage=System.getenv().getOrDefault("CSS_AUDIT_STAGE","after");if(!Set.of("baseline","after").contains(stage))throw new IllegalArgumentException("Invalid measurement stage");Path output=folder.resolve("computed-"+stage+".json");Files.writeString(output,json.writeValueAsString(rows));
  if("1".equals(System.getenv("CSS_AUDIT_COMPARE"))){var before=json.readTree(Files.readString(Path.of(System.getenv().getOrDefault("CSS_AUDIT_BASELINE",folder.resolve("computed-baseline.json").toString()))));var after=json.valueToTree(rows);var mismatches=new ArrayList<String>();for(String key:before.propertyNames()){if(!before.get(key).equals(after.get(key)))mismatches.add(key);}assertTrue(mismatches.isEmpty(),"Effective styles changed: "+mismatches);}
  assertEquals(150,rows.size());
 }
 static Object styles(Page p){return p.evaluate("""
 ()=>{const props=('display position box-sizing width height min-width min-height max-width max-height color background-color background-image background-size background-position font-family font-size font-weight font-style font-variant-numeric line-height letter-spacing text-align text-decoration-line text-decoration-thickness text-underline-offset white-space word-break overflow-wrap margin-top margin-right margin-bottom margin-left padding-top padding-right padding-bottom padding-left border-top-width border-right-width border-bottom-width border-left-width border-top-style border-right-style border-bottom-style border-left-style border-top-color border-right-color border-bottom-color border-left-color border-radius outline-style outline-width outline-color outline-offset grid-template-columns grid-column-start grid-column-end grid-row-start grid-row-end column-count column-gap column-rule row-gap flex-direction flex-wrap flex-grow flex-shrink flex-basis align-items align-self justify-content gap overflow-x overflow-y opacity box-shadow transform cursor list-style-type vertical-align object-fit').split(' ');return [...document.body.querySelectorAll('*')].filter(e=>!['SCRIPT','NOSCRIPT'].includes(e.tagName)).map(e=>{const s=getComputedStyle(e),out=[e.tagName,e.id,e.className,...props.map(k=>s.getPropertyValue(k))];for(const pseudo of ['::before','::after']){const v=getComputedStyle(e,pseudo);out.push(v.content,v.display,v.color,v.fontSize);}return out;});}
 """);}
}
