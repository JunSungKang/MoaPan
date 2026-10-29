package com.moapan;
import com.moapan.shared.api.ApiError;
import com.moapan.collection.infrastructure.SafeFetch;
import com.moapan.article.domain.ContentProcessor;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ContentTest {
    @Test void canonicalPreservesIdentity(){assertEquals("https://example.org/story?id=42&lang=ko",SafeFetch.canonical("https://EXAMPLE.org/story?id=42&utm_source=x&lang=ko#part"));}
    @Test void rejectsCredentialsAndUnsafeSchemes(){for(String url:new String[]{"http://example.org/","https://127.0.0.1/","https://user@example.org/","https://example.org:8080/","file:///etc/passwd","javascript:alert(1)"})assertThrows(ApiError.class,()->SafeFetch.canonical(url));}
    @Test void blocksPrivateAndTransitionAddresses()throws Exception{for(String address:new String[]{"127.0.0.1","10.0.0.1","169.254.169.254","100.64.0.1","192.168.1.2","::1","fd00::1","fe80::1","2001:db8::1","2002:7f00:1::","::ffff:127.0.0.1"})assertFalse(SafeFetch.publicAddress(InetAddress.getByName(address)),address);assertTrue(SafeFetch.publicAddress(InetAddress.getByName("8.8.8.8")));assertTrue(SafeFetch.publicAddress(InetAddress.getByName("192.0.66.80")));assertFalse(SafeFetch.publicAddress(InetAddress.getByName("192.0.0.1")));assertFalse(SafeFetch.publicAddress(InetAddress.getByName("198.51.100.1")));}
    @Test void rejectsXXE(){String xml="<!DOCTYPE rss [<!ENTITY ex SYSTEM 'file:///etc/passwd'>]><rss version='2.0'><channel><title>&ex;</title></channel></rss>";assertThrows(Exception.class,()->SafeFetch.parse(xml.getBytes(StandardCharsets.UTF_8),"RSS","{}"));}
    @Test void stripsScriptAndDoesNotInventSummary(){String clean=SafeFetch.clean("<p>NASA announced a new mission to observe the ocean.</p><script>alert(1)</script>",8000);assertFalse(clean.contains("alert"));assertEquals(java.util.List.of(),ContentProcessor.summary("same","same",true));assertEquals(java.util.List.of(),ContentProcessor.summary("This is a sufficiently long synthetic title","This is a sufficiently long synthetic title.",true));assertEquals(java.util.List.of(),ContentProcessor.summary("Title",clean,false));assertTrue(ContentProcessor.summary("Title",clean,true).stream().allMatch(clean::contains));}
    @Test void aliasesHaveWordBoundaries(){assertTrue(ContentProcessor.matches("AI and PostgreSQL", "ai"));assertFalse(ContentProcessor.matches("rain and said", "ai"));assertTrue(ContentProcessor.matches("인공지능 기술", "인공지능"));assertFalse(ContentProcessor.matches("쿠버네티스형", "쿠버네티스"));assertTrue(ContentProcessor.matches("인공지능과 우주 소식", "인공지능"));}
    @Test void parserSeparatesPublishedFromCollected()throws Exception{String xml="<rss version='2.0'><channel><title>synthetic</title><link>https://example.org/</link><description>fixture only</description><item><guid>one</guid><title>synthetic title</title><link>https://example.org/a?id=1&amp;utm_source=x</link><description>synthetic description only.</description></item></channel></rss>";var items=SafeFetch.parse(xml.getBytes(StandardCharsets.UTF_8),"RSS","{}");assertEquals(1,items.size());assertNull(items.getFirst().publishedAt());assertEquals("https://example.org/a?id=1",items.getFirst().url());}
    @Test void rejectsOversizedParserInput(){assertThrows(ApiError.class,()->SafeFetch.parse(new byte[2_097_153],"RSS","{}"));}
    @org.junit.jupiter.api.Test void pinnedDnsCannotBeReboundOrRedirectedToAnotherHost()throws Exception {
        var publicIp=java.net.InetAddress.getByAddress(new byte[]{8,8,8,8});var privateIp=java.net.InetAddress.getByAddress(new byte[]{127,0,0,1});var original=new java.net.InetAddress[]{publicIp};
        var resolver=com.moapan.collection.infrastructure.SafeFetch.pinnedResolver("news.example.org",original);original[0]=privateIp;
        assertEquals(publicIp,resolver.resolve("news.example.org")[0]);var returned=resolver.resolve("news.example.org");returned[0]=privateIp;assertEquals(publicIp,resolver.resolve("news.example.org")[0]);
        assertThrows(java.net.UnknownHostException.class,()->resolver.resolve("metadata.example.org"));assertThrows(com.moapan.shared.api.ApiError.class,()->com.moapan.collection.infrastructure.SafeFetch.pinnedResolver("news.example.org",new java.net.InetAddress[]{publicIp,privateIp}));
        var current=java.net.URI.create("https://news.example.org/feed");assertEquals("https://news.example.org/new",com.moapan.collection.infrastructure.SafeFetch.redirect(current,"/new","news.example.org").toString());
        for(String target:java.util.List.of("https://evil.example.org/feed","http://news.example.org/feed","https://news.example.org:8443/feed","https://news.example.org@metadata.example.org/","//metadata.example.org/feed"))assertThrows(com.moapan.shared.api.ApiError.class,()->com.moapan.collection.infrastructure.SafeFetch.redirect(current,target,"news.example.org"));
    }

}
