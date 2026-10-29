package com.moapan.source;
import com.moapan.ApiError;
import com.moapan.news.ContentProcessor;
import java.time.*;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;
/** Operator-defined collection scope, never a member interest profile. */
public final class CollectionFilter {
    private CollectionFilter(){}
    public static void validate(List<String> terms){if(terms!=null&&(terms.size()>40||terms.stream().anyMatch(t->t==null||t.isBlank()||t.length()>100)))throw new ApiError(400,"invalid_topic_terms");}
    public static boolean accepts(Map<String,Object> source,SafeFetch.Item item){
        Object value=source.get("published_since");
        if(value!=null){Instant start=LocalDate.parse(value.toString()).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant();if(item.publishedAt()==null||item.publishedAt().isBefore(start))return false;}
        Object raw=source.get("topic_terms");if(raw==null)return true;
        var terms=JsonMapper.builder().build().readTree(raw.toString());
        if(!terms.isArray())throw new ApiError(503,"invalid_collection_filter");
        if(terms.isEmpty())return true;
        String text=item.title()+" "+item.description();
        for(var term:terms)if(ContentProcessor.matches(text,term.asString()))return true;
        return false;
    }
}
