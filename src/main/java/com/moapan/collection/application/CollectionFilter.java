package com.moapan.collection.application;
import com.moapan.collection.domain.CollectionScope;
import com.moapan.shared.api.ApiError;
import java.time.*;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;
/** Operator-defined collection scope, never a member interest profile. */
public final class CollectionFilter {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private CollectionFilter(){}
    public static void validate(List<String> terms){if(terms!=null&&(terms.size()>40||terms.stream().anyMatch(t->t==null||t.isBlank()||t.length()>100)))throw new ApiError(400,"invalid_topic_terms");}
    public static CollectionScope compile(Map<String,Object> source){
        Object value=source.get("published_since");
        Instant start=value==null?null:LocalDate.parse(value.toString()).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant();
        Object raw=source.get("topic_terms");if(raw==null)return new CollectionScope(start,List.of());
        var values=JSON.readTree(raw.toString());if(!values.isArray())throw new ApiError(503,"invalid_collection_filter");
        var terms=new ArrayList<String>();for(var term:values)terms.add(term.asString());
        return new CollectionScope(start,terms);
    }
}
