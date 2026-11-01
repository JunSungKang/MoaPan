package com.moapan.edition.application;

import com.moapan.shared.infrastructure.cache.SharedReadCaches;
import com.moapan.shared.infrastructure.cache.VersionedReadCache;
import java.util.*;
import java.util.function.Function;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Common edition data only; authentication, quotas and account-bound cursors stay in NewsService. */
@Service
public final class EditionReadModels {
    private record Member(int position,UUID id) {}
    private record Selection(List<Member> visible,List<Member> selected,String fingerprint) {}
    public record Slice(List<NewsService.ArticleView> items,int candidateCount,int nextPosition,boolean more) {}
    private static final String VISIBLE="a.public AND s.allow_display AND s.rights_status IN ('APPROVED','RESTRICTED')";
    private final JdbcClient db;
    private final JsonMapper json=JsonMapper.builder().build();
    private final VersionedReadCache<List<Member>> memberships;
    private final VersionedReadCache<Slice> cards;
    private final VersionedReadCache<List<Map<String,Object>>> sources;
    public EditionReadModels(JdbcClient db,SharedReadCaches caches) {
        this.db=db;
        memberships=caches.create(SharedReadCaches.Region.EDITION_IDS,v->256+80*v.size());
        cards=caches.create(SharedReadCaches.Region.ARTICLE_CARDS,v->3*json.writeValueAsString(v).length());
        sources=caches.create(SharedReadCaches.Region.SOURCE_CATALOG,v->3*json.writeValueAsString(v).length());
    }
    private String stamp(String domain,UUID id) {
        return db.sql("SELECT fingerprint FROM read_cache_fingerprints WHERE domain=:domain AND subject=:id")
                .param("domain",domain).param("id",id).query(String.class).optional().orElse(null);
    }
    public Slice page(UUID edition,int start,int size,
            Function<List<Map<String,Object>>,List<NewsService.ArticleView>> assemble) {
        var members=memberships.read(edition.toString(),()->stamp("edition_membership",edition),
                ()->List.copyOf(db.sql("SELECT position,article_id FROM news_edition_items WHERE edition_id=:id ORDER BY position LIMIT 300")
                    .param("id",edition).query((r,n)->new Member(r.getInt("position"),r.getObject("article_id",UUID.class))).list()));
        return cards.read(edition+":"+start+":"+size,()->selection(members,start,size).fingerprint(),()->{
            var selection=selection(members,start,size);
            var selected=selection.selected();
            if(selected.isEmpty())return new Slice(List.of(),selection.visible().size(),300,false);
            var rows=db.sql("SELECT a.id,a.title,a.author,CASE WHEN s.allow_summary THEN a.summary_bullets::text ELSE '[]' END summary_bullets,CASE WHEN s.allow_summary THEN a.summary_mode ELSE 'INSUFFICIENT' END summary_mode,a.published_at,a.collected_at,a.canonical_url,s.id source_id,s.label source_name,s.language,p.name site_name FROM articles a JOIN sources s ON s.id=a.source_id JOIN sites p ON p.id=s.site_id WHERE a.id IN (:ids) AND "+VISIBLE)
                    .param("ids",selected.stream().map(Member::id).toList()).query().listOfRows();
            Map<UUID,Map<String,Object>> byId=new HashMap<>();for(var row:rows)byId.put((UUID)row.get("id"),row);
            var ordered=selected.stream().map(m->byId.get(m.id())).toList();
            if(ordered.stream().anyMatch(Objects::isNull))throw new IllegalStateException("Edition snapshot mismatch");
            int next=selected.getLast().position()+1;
            return new Slice(List.copyOf(assemble.apply(ordered)),selection.visible().size(),next,
                    selection.visible().stream().anyMatch(m->m.position()>=next));
        });
    }
    private Selection selection(List<Member> members,int start,int size) {
        if(members.isEmpty())return new Selection(List.of(),List.of(),"empty");
        var eligible=new HashSet<>(db.sql("SELECT a.id FROM articles a JOIN sources s ON s.id=a.source_id WHERE a.id IN (:ids) AND "+VISIBLE)
                .param("ids",members.stream().map(Member::id).toList()).query(UUID.class).list());
        var visible=members.stream().filter(m->eligible.contains(m.id())).toList();
        var selected=visible.stream().filter(m->m.position()>=start).limit(size).toList();
        StringBuilder signature=new StringBuilder();for(var m:visible)signature.append(m.position()).append(':').append(m.id()).append('|');
        if(selected.isEmpty())return new Selection(visible,selected,signature.toString());
        var hashes=db.sql("SELECT a.id,af.fingerprint article_hash,sf.fingerprint source_hash,pf.fingerprint site_hash,CASE WHEN cj.article_id IS NULL THEN 'none' ELSE cf.fingerprint END classification_hash,(SELECT fingerprint FROM read_cache_fingerprints WHERE domain='news_taxonomy' AND subject='00000000-0000-0000-0000-000000000000') taxonomy_hash FROM articles a JOIN sources s ON s.id=a.source_id JOIN sites p ON p.id=s.site_id LEFT JOIN article_classification_jobs cj ON cj.article_id=a.id LEFT JOIN read_cache_fingerprints cf ON cf.domain='article_classification' AND cf.subject=a.id LEFT JOIN read_cache_fingerprints af ON af.domain='article_card' AND af.subject=a.id LEFT JOIN read_cache_fingerprints sf ON sf.domain='news_source' AND sf.subject=s.id LEFT JOIN read_cache_fingerprints pf ON pf.domain='news_site' AND pf.subject=p.id WHERE a.id IN (:ids)")
                .param("ids",selected.stream().map(Member::id).toList()).query().listOfRows();
        Map<UUID,Map<String,Object>> indexed=new HashMap<>();for(var row:hashes)indexed.put((UUID)row.get("id"),row);
        for(var m:selected){var row=indexed.get(m.id());if(row==null)return new Selection(visible,selected,null);
            for(String field:List.of("article_hash","source_hash","site_hash","classification_hash","taxonomy_hash")){
                if(row.get(field)==null)return new Selection(visible,selected,null);
                signature.append(row.get(field)).append('|');
            }
        }
        return new Selection(visible,selected,signature.toString());
    }
    public List<Map<String,Object>> sources() {
        return sources.read("public-sources",()->stamp("news_sources",new UUID(0,0)),
            ()->db.sql("SELECT s.id,s.label,s.language,p.name AS site,s.terms_url AS \"termsUrl\" FROM sources s JOIN sites p ON p.id=s.site_id WHERE s.allow_display AND s.rights_status IN ('APPROVED','RESTRICTED') ORDER BY p.name,s.label,s.id")
                    .query().listOfRows().stream().map(r->Collections.unmodifiableMap(new LinkedHashMap<>(r))).toList());
    }
}
