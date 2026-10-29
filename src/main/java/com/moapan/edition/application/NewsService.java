package com.moapan.edition.application;
import com.moapan.translation.infrastructure.ArticleTranslations;
import com.moapan.shared.api.ApiError;
import com.moapan.bootstrap.AdminCommand;
import com.moapan.access.infrastructure.Limits;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import tools.jackson.databind.json.JsonMapper;

@Service
public class NewsService {
    public record SourceView(UUID id,String name,String site,String language){}
    public record TagView(UUID id,String slug,String name,String category,double confidence){}
    public record ArticleView(UUID id,String title,List<String>summaryBullets,SourceView source,Instant publishedAt,Instant collectedAt,List<TagView>tags,String originalUrl,String summaryMode,String author,Map<String,ArticleTranslations.Text> translations){}
    public record Page(UUID editionId,Instant editionAt,int candidateCount,int periodDays,List<ArticleView>items,String nextCursor){}
    private final ArticleTranslations translations;private final JdbcClient db;private final Limits limits;private final TransactionTemplate tx;private final Clock clock;private final JsonMapper json=JsonMapper.builder().build();
    private static final String VISIBLE="a.public AND s.allow_display AND s.rights_status IN ('APPROVED','RESTRICTED')";
    public NewsService(JdbcClient db,Limits limits,PlatformTransactionManager tm,Clock clock,ArticleTranslations translations){this.translations=translations;this.db=db;this.limits=limits;tx=new TransactionTemplate(tm);this.clock=clock;}
    public Page publicEdition(){limits.auth("public_news",600,60);return page(null,edition(),0,42);}
    public Page first(UUID user,int size){validateSize(size);UUID lease=limits.beginNews(user);try{UUID edition=edition();return page(user,edition,0,size);}finally{limits.endNews(lease);}}
    public Page next(UUID user,String token){UUID lease=limits.beginNews(user);try{
        try{String[] parts=token.split("\\.",-1);if(parts.length!=2||token.length()>600)throw new IllegalArgumentException();String payload=new String(Base64.getUrlDecoder().decode(parts[0]),StandardCharsets.UTF_8);String expected=limits.fingerprint("cursor:"+payload);if(!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),parts[1].getBytes(StandardCharsets.US_ASCII)))throw new IllegalArgumentException();String[] fields=payload.split("\\|",-1);if(fields.length!=5||!fields[0].equals(user.toString())||Long.parseLong(fields[3])<=clock.instant().getEpochSecond())throw new IllegalArgumentException();int position=Integer.parseInt(fields[2]),size=Integer.parseInt(fields[4]);validateSize(size);if(position<1||position>=300)throw new IllegalArgumentException();return page(user,UUID.fromString(fields[1]),position,size);}catch(IllegalArgumentException e){throw new ApiError(400,"invalid_cursor");}
    }finally{limits.endNews(lease);}}
    private UUID edition(){return tx.execute(s->{db.sql("SELECT pg_advisory_xact_lock(727001)").query((r,n)->1).list();var existing=db.sql("SELECT id FROM news_editions WHERE created_at>now()-interval '15 minutes' AND expires_at>now() ORDER BY created_at DESC,id LIMIT 1").query(UUID.class).list();if(!existing.isEmpty())return existing.getFirst();UUID id=UUID.randomUUID();db.sql("INSERT INTO news_editions(id,expires_at) VALUES(:id,now()+interval '1 hour')").param("id",id).update();db.sql("INSERT INTO news_edition_items(edition_id,position,article_id) SELECT :id,(row_number() OVER(ORDER BY COALESCE(a.published_at,a.collected_at) DESC,a.id)-1)::integer,a.id FROM articles a JOIN sources s ON s.id=a.source_id WHERE "+VISIBLE+" AND COALESCE(a.published_at,a.collected_at)>=now()-interval '7 days' AND COALESCE(a.published_at,a.collected_at)<=now()+interval '5 minutes' ORDER BY COALESCE(a.published_at,a.collected_at) DESC,a.id LIMIT 300").param("id",id).update();return id;});}
    private Page page(UUID user,UUID edition,int start,int size){
        var metadata=db.sql("SELECT created_at,expires_at FROM news_editions WHERE id=:id AND expires_at>now()").param("id",edition).query().listOfRows().stream().findFirst().orElseThrow(()->new ApiError(400,"expired_edition"));
        int count=db.sql("SELECT count(*) FROM news_edition_items e JOIN articles a ON a.id=e.article_id JOIN sources s ON s.id=a.source_id WHERE e.edition_id=:id AND "+VISIBLE).param("id",edition).query(Integer.class).single();
        var rows=db.sql("SELECT a.id,a.title,a.author,CASE WHEN s.allow_summary THEN a.summary_bullets::text ELSE '[]' END summary_bullets,CASE WHEN s.allow_summary THEN a.summary_mode ELSE 'INSUFFICIENT' END summary_mode,a.published_at,a.collected_at,a.canonical_url,s.id source_id,s.label source_name,s.language,p.name site_name,e.position FROM news_edition_items e JOIN articles a ON a.id=e.article_id JOIN sources s ON s.id=a.source_id JOIN sites p ON p.id=s.site_id WHERE e.edition_id=:id AND e.position>=:position AND "+VISIBLE+" ORDER BY e.position LIMIT :size").param("id",edition).param("position",start).param("size",size).query().listOfRows();
        List<ArticleView> items=articleViews(rows);
        if(user!=null)limits.items(user,items.size());int next=rows.isEmpty()?300:((Number)rows.getLast().get("position")).intValue()+1;boolean more=db.sql("SELECT EXISTS(SELECT 1 FROM news_edition_items e JOIN articles a ON a.id=e.article_id JOIN sources s ON s.id=a.source_id WHERE e.edition_id=:id AND e.position>=:p AND "+VISIBLE+")").param("id",edition).param("p",next).query(Boolean.class).single();
        String cursor=null;if(more&&user!=null){String payload=user+"|"+edition+"|"+next+"|"+instant(metadata.get("expires_at")).getEpochSecond()+"|"+size;cursor=Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8))+"."+limits.fingerprint("cursor:"+payload);}
        return new Page(edition,instant(metadata.get("created_at")),count,7,items,cursor);
    }
    List<ArticleView> articleViews(List<Map<String,Object>> rows){
        List<UUID> ids=rows.stream().map(r->(UUID)r.get("id")).toList();Map<UUID,List<TagView>>tags=new HashMap<>();
        if(!ids.isEmpty())for(var t:db.sql("SELECT at.article_id,t.id,t.slug,t.name,t.category,at.confidence FROM article_tags at JOIN tags t ON t.id=at.tag_id WHERE at.article_id IN (:ids) AND t.active ORDER BY t.slug").param("ids",ids).query().listOfRows())tags.computeIfAbsent((UUID)t.get("article_id"),k->new ArrayList<>()).add(new TagView((UUID)t.get("id"),t.get("slug").toString(),t.get("name").toString(),t.get("category").toString(),((Number)t.get("confidence")).doubleValue()));
        List<ArticleView> items=rows.stream().map(r->new ArticleView((UUID)r.get("id"),(String)r.get("title"),bullets(r.get("summary_bullets").toString()),new SourceView((UUID)r.get("source_id"),(String)r.get("source_name"),(String)r.get("site_name"),(String)r.get("language")),instant(r.get("published_at")),instant(r.get("collected_at")),tags.getOrDefault((UUID)r.get("id"),List.of()),(String)r.get("canonical_url"),(String)r.get("summary_mode"),(String)r.get("author"),Map.of())).toList();
        var localized=translations.read(items.stream().map(a->new ArticleTranslations.Original(a.id(),a.title(),a.summaryBullets())).toList());return items.stream().map(a->new ArticleView(a.id(),a.title(),a.summaryBullets(),a.source(),a.publishedAt(),a.collectedAt(),a.tags(),a.originalUrl(),a.summaryMode(),a.author(),localized.getOrDefault(a.id(),Map.of()))).toList();
    }
    @SuppressWarnings("unchecked") private List<String>bullets(String value){return json.readValue(value,List.class);}
    private static void validateSize(int size){if(size<1||size>50)throw new ApiError(400,"invalid_size");}
    private static Instant instant(Object value){if(value==null)return null;if(value instanceof java.sql.Timestamp t)return t.toInstant();if(value instanceof OffsetDateTime t)return t.toInstant();return Instant.parse(value.toString());}
    public Object tags(){return Map.of("tags",db.sql("SELECT id,slug,name,category FROM tags WHERE active ORDER BY name").query().listOfRows(),"aliases",db.sql("SELECT a.alias,a.tag_id AS \"tagId\" FROM tag_aliases a JOIN tags t ON t.id=a.tag_id WHERE t.active").query().listOfRows(),"redirects",db.sql("SELECT old_id AS \"oldId\",canonical_id AS \"canonicalId\" FROM tag_redirects").query().listOfRows());}
    public Object sources(){return db.sql("SELECT s.id,s.label,s.language,p.name AS site,s.terms_url AS \"termsUrl\" FROM sources s JOIN sites p ON p.id=s.site_id WHERE s.allow_display AND s.rights_status IN ('APPROVED','RESTRICTED') ORDER BY p.name,s.label").query().listOfRows();}
}
