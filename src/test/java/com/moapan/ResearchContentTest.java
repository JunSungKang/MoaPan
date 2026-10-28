package com.moapan;
import com.moapan.news.ContentProcessor;
import com.moapan.source.SafeFetch;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
class ResearchContentTest {
 @Test void researchSummaryUsesEvidenceInsteadOfBoilerplate(){
  String text="Subscribe to our newsletter for the latest news. We propose an algorithm for efficient retrieval. Evaluation results show a latency reduction of 12%. A limitation is the small benchmark dataset.";
  var result=ContentProcessor.summary("Synthetic research",text,true);
  assertEquals(3,result.size());assertFalse(result.getFirst().contains("Subscribe"));assertTrue(result.stream().allMatch(text::contains));
  assertTrue(ContentProcessor.summary("Title","Subscribe to our newsletter for the latest news.",true).isEmpty());
  assertTrue(ContentProcessor.summary("Title",text,false).isEmpty());
 }
 @Test void atomAndJsonRetainOnlyProvidedAuthors()throws Exception{
  String atom="<feed xmlns='http://www.w3.org/2005/Atom'><title>Test</title><id>urn:test</id><updated>2026-10-20T00:00:00Z</updated><entry><title>Test paper</title><id>urn:one</id><link href='https://example.org/one'/><author><name>Alice</name></author><author><name>Bob</name></author><published>2026-10-20T00:00:00Z</published><summary>We propose a method for retrieval.</summary></entry></feed>";
  var item=SafeFetch.parse(atom.getBytes(StandardCharsets.UTF_8),"ATOM","{}").getFirst();assertEquals("Alice, Bob",item.author());
  var json=SafeFetch.parse("[{\"title\":\"Test\",\"url\":\"https://example.org/x\",\"writer\":\"Alice\"}]".getBytes(StandardCharsets.UTF_8),"JSON_API","{\"items\":\"$\",\"author\":\"writer\"}").getFirst();assertEquals("Alice",json.author());
  assertNotEquals(ContentProcessor.hash(item),ContentProcessor.hash(new SafeFetch.Item(item.guid(),item.title(),item.url(),item.description(),item.publishedAt(),"Other")));
 }
}
