package com.moapan;
import com.moapan.shared.api.ApiError;
import com.moapan.collection.infrastructure.SafeFetch;
import com.moapan.collection.infrastructure.ArxivGate;
import com.moapan.collection.application.CollectionFilter;
import java.util.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CollectionFilterTest {
    SafeFetch.Item item(String title,Instant time){return new SafeFetch.Item("id",title,"https://example.org/paper","",time);}
    @Test void dateBoundaryAndUnknownFailClosed(){var s=Map.<String,Object>of("published_since",LocalDate.of(2026,10,10),"topic_terms","[\"RAG\"]");Instant boundary=Instant.parse("2026-10-09T15:00:00Z");assertTrue(CollectionFilter.accepts(s,item("RAG evaluation",boundary)));assertFalse(CollectionFilter.accepts(s,item("RAG evaluation",boundary.minusSeconds(1))));assertFalse(CollectionFilter.accepts(s,item("RAG evaluation",null)));assertFalse(CollectionFilter.accepts(s,item("fragment evaluation",boundary)));assertFalse(CollectionFilter.accepts(s,item("Astronomy",boundary)));}
    @Test void boundedLiteralTerms(){assertThrows(ApiError.class,()->CollectionFilter.validate(Collections.nCopies(41,"RAG")));assertThrows(ApiError.class,()->CollectionFilter.validate(List.of(" ")));assertTrue(CollectionFilter.accepts(Map.of(),item("Any topic",null)));assertTrue(CollectionFilter.accepts(Map.of("topic_terms","[\"database\",\"retrieval-augmented generation\"]"),item("Fast database queries",null)));}
    @Test void jsonRootArray()throws Exception{var items=SafeFetch.parse("[{\"id\":\"x\",\"title\":\"Synthetic vulnerability\",\"url\":\"https://example.org/advisory\",\"publishedAt\":\"2026-10-10T00:00:00Z\"}]".getBytes(),"JSON_API","{\"items\":\"$\"}");assertEquals(1,items.size());assertNotNull(items.getFirst().publishedAt());}
    @Test void arxivAtomMetadataAndHttps()throws Exception{String feed="<feed xmlns='http://www.w3.org/2005/Atom'><title>Synthetic papers</title><id>fixture</id><updated>2026-10-10T00:00:00Z</updated><entry><id>http://arxiv.org/abs/2610.12345v1</id><title>Synthetic RAG benchmark</title><link href='http://arxiv.org/abs/2610.12345v1'/><published>2026-10-10T00:00:00Z</published><updated>2026-10-11T00:00:00Z</updated><summary>Synthetic abstract metadata for testing, not a real paper.</summary></entry></feed>";var items=SafeFetch.parse(feed.getBytes(),"ATOM","{}");assertEquals(1,items.size());assertEquals("https://arxiv.org/abs/2610.12345v1",items.getFirst().url());assertEquals(Instant.parse("2026-10-10T00:00:00Z"),items.getFirst().publishedAt());assertTrue(items.getFirst().description().contains("Synthetic abstract"));}
}
