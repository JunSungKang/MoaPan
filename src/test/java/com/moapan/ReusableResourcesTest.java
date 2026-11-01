package com.moapan;
import com.moapan.article.domain.ContentProcessor;
import java.util.*;import java.util.concurrent.*;import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class ReusableResourcesTest {
 @Test void reusedMapperPreservesThePreviouslyMeasuredStoredArticleHashContract(){
  assertEquals("113a33b97add39675a37816eb43781ffb463208f099aa9e32e75bbb8dd2f6b02",com.moapan.article.domain.ArticleFingerprint.contentHash("제목",List.of("첫째","둘째")));
  assertEquals("4fe4ac7f90155f101333d56e632a9bebb3250e316b80d05a20b9fb8057ca77ed",com.moapan.article.domain.ArticleFingerprint.contentHash("A\"\\B",List.of("line\nnext","CVE-2026-12345")));
 }
 @Test void aliasCacheStaysBoundedAndNeverRetainsArticleTextDuringConcurrentMatching()throws Exception{
  String article="private synthetic article never retain this body ";
  try(var executor=Executors.newFixedThreadPool(8)){
   var tasks=new ArrayList<Callable<Boolean>>();for(int i=0;i<1000;i++){final String alias="cache_topic_"+i;tasks.add(()->ContentProcessor.matches(article+alias,alias)&&!ContentProcessor.matches(article+alias+"suffix",alias));}
   for(var result:executor.invokeAll(tasks))assertTrue(result.get());
  }
  var field=ContentProcessor.class.getDeclaredField("ALIAS_PATTERNS");field.setAccessible(true);
  @SuppressWarnings("unchecked") Map<String,Pattern> cache=(Map<String,Pattern>)field.get(null);
  synchronized(cache){assertTrue(cache.size()<=256);assertTrue(cache.keySet().stream().noneMatch(k->k.contains("private synthetic")));assertTrue(cache.values().stream().noneMatch(v->v.pattern().contains("private synthetic")));}
  String longAlias="x".repeat(500);assertTrue(ContentProcessor.matches(article+longAlias,longAlias));synchronized(cache){assertFalse(cache.containsKey(longAlias));}
 }
}
