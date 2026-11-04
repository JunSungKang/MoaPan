package com.moapan.newsletter;

import com.moapan.newsletter.domain.PublicationResearch.*;
import com.moapan.shared.api.ApiError;
import com.moapan.shared.infrastructure.cache.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Internal immutable published content. NewsletterService enforces access and fresh evidence checks. */
@Service
public final class PublicationReadModels {
    record Header(Map<String,Object> fields,List<UUID> articleIds) {}
    record Detail(Map<String,Object> fields,List<UUID> articleIds) {}
    private final JdbcClient db;
    private final JsonMapper json=JsonMapper.builder().build();
    private final VersionedReadCache<List<Header>> catalog;
    private final VersionedReadCache<Detail> details;
    public PublicationReadModels(JdbcClient db,SharedReadCaches caches) {
        this.db=db;
        catalog=caches.create(SharedReadCaches.Region.PUBLICATION_CATALOG,v->3*json.writeValueAsString(v).length());
        details=caches.create(SharedReadCaches.Region.PUBLICATIONS,v->3*json.writeValueAsString(v).length());
    }
    private String stamp(String domain,UUID id) {
        return db.sql("SELECT fingerprint FROM read_cache_fingerprints WHERE domain=:domain AND subject=:id")
                .param("domain",domain).param("id",id).query(String.class).optional().orElse(null);
    }
    List<Header> catalog() {
        return catalog.read("published-catalog",()->stamp("publication_catalog",new UUID(0,0)),()->{
            var rows=db.sql("SELECT id,issue_number,published->>'title' title,published->>'introduction' introduction,published->>'topic' topic,published->>'language' language,published_at,first_published_at,publication_revision,distribution,published_dossier::text dossier FROM newsletters WHERE published IS NOT NULL ORDER BY published_at DESC,id LIMIT 100").query().listOfRows();
            return rows.stream().map(row->{
                Dossier dossier=dossier(row.get("dossier"));var copy=new LinkedHashMap<>(row);copy.remove("dossier");
                for(String field:List.of("published_at","first_published_at"))if(copy.get(field)!=null)copy.put(field,utc(copy.get(field)));
                return new Header(Collections.unmodifiableMap(copy),dossier==null?List.of():List.copyOf(dossier.articleIds()));
            }).toList();
        });
    }
    Detail detail(UUID id) {
        return details.read(id.toString(),()->stamp("publication",id),()->{
            var row=db.sql("SELECT id,issue_number,published::text content,published_dossier::text dossier,published_at,first_published_at,publication_revision,distribution FROM newsletters WHERE id=:id AND published IS NOT NULL")
                    .param("id",id).query().listOfRows().stream().findFirst().orElseThrow(()->new ApiError(404,"newsletter_not_found"));
            Dossier d=freeze(dossier(row.get("dossier")));var out=new LinkedHashMap<String,Object>();
            out.put("id",id);out.put("issueNumber",row.get("issue_number"));out.put("manuscript",freezeJson(json.readValue(row.get("content").toString(),Map.class)));
            out.put("publishedAt",utc(row.get("published_at")));out.put("firstPublishedAt",row.get("first_published_at")==null?null:utc(row.get("first_published_at")));
            out.put("revision",row.get("publication_revision"));out.put("distribution",row.get("distribution"));out.put("dossier",d);out.put("reuseAllowed",d!=null&&d.rightsConfirmed());
            return new Detail(Collections.unmodifiableMap(out),d==null?List.of():d.articleIds());
        });
    }
    private Dossier dossier(Object value){return value==null||value.toString().equals("null")?null:json.readValue(value.toString(),Dossier.class);}
    private static Dossier freeze(Dossier d) {
        if(d==null)return null;
        return new Dossier(d.weekStart(),d.cutoff(),List.copyOf(d.articleIds()),d.forecasts().stream()
                .map(f->new Forecast(f.question(),f.observation(),f.outlook(),f.horizon(),f.experiment(),f.successCriteria(),f.stopCondition(),List.copyOf(f.evidenceIds()))).toList(),d.rightsConfirmed(),d.editorialReviewed(),d.correctionNote());
    }
    private static Object freezeJson(Object value) {
        if(value instanceof Map<?,?> map){var copy=new LinkedHashMap<String,Object>();map.forEach((k,v)->copy.put(k.toString(),freezeJson(v)));return Collections.unmodifiableMap(copy);}
        if(value instanceof List<?> list)return list.stream().map(PublicationReadModels::freezeJson).toList();
        return value;
    }
    private static String utc(Object value) {
        if(value instanceof java.sql.Timestamp t)return t.toInstant().toString();
        if(value instanceof java.time.OffsetDateTime t)return t.toInstant().toString();
        throw new IllegalStateException("Unsupported publication timestamp");
    }
}
