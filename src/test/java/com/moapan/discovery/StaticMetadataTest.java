package com.moapan.discovery;
import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class StaticMetadataTest {
 @Test void allPagesHaveUniqueInitialTitlesDescriptionsAndValidLandmarks()throws Exception{
  Set<String> titles=new HashSet<>();
  try(var paths=Files.list(Path.of("src/main/resources/static"))){for(var p:paths.filter(x->x.toString().endsWith(".html")).toList()){
   var d=Jsoup.parse(p.toFile(),"UTF-8");assertEquals("ko",d.selectFirst("html").attr("lang"));assertTrue(d.title().contains("모아판 MoA-PaN"));assertTrue(titles.add(d.title()));assertEquals(1,d.select("title").size());assertFalse(d.selectFirst("meta[name=description]").attr("content").isBlank());assertEquals(1,d.select("main").size());
   var ids=d.select("[id]").eachAttr("id");assertEquals(ids.size(),new HashSet<>(ids).size());
   boolean indexable=Set.of("about.html","privacy.html","terms.html").contains(p.getFileName().toString());assertEquals(indexable?"index, follow":"noindex, follow",d.selectFirst("meta[name=robots]").attr("content"));assertEquals(indexable?1:0,d.select("link[rel=canonical]").size());
  }}
 }
 @Test void originRejectsUntrustedComponents(){for(String value:List.of("https://user:secret@example.test","https://example.test/a","https://example.test/?q=1","https://example.test/#x","javascript:foo"))assertThrows(RuntimeException.class,()->DiscoveryController.origin(value));assertEquals("https://example.test",DiscoveryController.origin("https://example.test/"));}
}
