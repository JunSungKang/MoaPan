package com.moapan.article.application;
import com.moapan.shared.api.ApiError;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.time.*;
import tools.jackson.databind.json.JsonMapper;
@Service @PreAuthorize("hasRole('ADMIN')")
public class EditorialService {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    public record Edit(List<String>summaryBullets,boolean summaryLocked,boolean tagsLocked,boolean published,List<UUID>tagIds){}
    public record TagInput(String slug,String name,String category){}
    private final JdbcClient db;
    public EditorialService(JdbcClient db){this.db=db;}
    public Object list(LocalDate from,LocalDate to,String sort){
        if(from!=null&&(from.getYear()<1||from.getYear()>9999)||to!=null&&(to.getYear()<1||to.getYear()>9999)||from!=null&&to!=null&&from.isAfter(to))throw new ApiError(400,"invalid_collection_period");
        String order=switch(sort){case "collected_desc"->"a.collected_at DESC,a.title,a.id";case "collected_asc"->"a.collected_at,a.title,a.id";case "title_asc"->"lower(a.title),a.title,a.collected_at DESC,a.id";default->throw new ApiError(400,"invalid_editorial_sort");};
        String where=" WHERE true"+(from==null?"":" AND a.collected_at>=:from")+(to==null?"":" AND a.collected_at<:until");
        var query=db.sql("SELECT a.id,a.source_id,a.title,a.summary_bullets,a.summary_mode,a.summary_locked,a.tags_locked,a.public,to_char(a.published_at AT TIME ZONE 'UTC','YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"') AS published_at,to_char(a.collected_at AT TIME ZONE 'UTC','YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"') AS collected_at,s.label AS source_label,si.name AS site_name,COALESCE((SELECT json_agg(tag_id ORDER BY tag_id) FROM article_tags WHERE article_id=a.id),'[]'::json)::text AS tag_ids FROM articles a JOIN sources s ON s.id=a.source_id JOIN sites si ON si.id=s.site_id"+where+" ORDER BY "+order+" LIMIT 100");
        if(from!=null)query.param("from",java.sql.Timestamp.from(from.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant()));
        if(to!=null){if(to.equals(LocalDate.MAX))throw new ApiError(400,"invalid_collection_period");query.param("until",java.sql.Timestamp.from(to.plusDays(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant()));}
        return query.query().listOfRows();
    }
    @Transactional public void edit(UUID id,Edit e,UUID actor){
        if(e.summaryBullets()==null||e.summaryBullets().size()>4||e.summaryBullets().stream().anyMatch(s->s==null||s.length()>350)||(e.tagIds()!=null&&(e.tagIds().size()>6||e.tagIds().stream().anyMatch(Objects::isNull))))throw new ApiError(400,"invalid_editorial_input");
        if(e.tagIds()!=null&&!e.tagIds().isEmpty()&&db.sql("SELECT id FROM tags WHERE id IN (:ids) AND active ORDER BY id FOR SHARE").param("ids",new HashSet<>(e.tagIds())).query(UUID.class).list().size()!=new HashSet<>(e.tagIds()).size())throw new ApiError(400,"invalid_editorial_tags");
        var row=db.sql("SELECT * FROM articles WHERE id=:id FOR UPDATE").param("id",id).query().listOfRows().stream().findFirst().orElseThrow(()->new ApiError(404,"article_not_found"));
        db.sql("UPDATE articles SET summary_bullets=CAST(:b AS jsonb),summary_mode='MANUAL',summary_locked=:sl,tags_locked=:tl,public=:p,updated_at=now() WHERE id=:id").param("id",id).param("b",JSON.writeValueAsString(e.summaryBullets())).param("sl",e.summaryLocked()).param("tl",e.tagsLocked()).param("p",e.published()).update();if(e.tagIds()!=null){db.sql("DELETE FROM article_tags WHERE article_id=:id").param("id",id).update();for(UUID tag:new HashSet<>(e.tagIds()))db.sql("INSERT INTO article_tags SELECT :id,id,1,'MANUAL',true FROM tags WHERE id=:tag AND active").param("id",id).param("tag",tag).update();}audit(actor,"article_edit",id);
    }
    private static void validTag(TagInput i){if(i==null||i.slug()!=null&&!i.slug().matches("[a-z0-9-]{1,60}")||i.name()==null||i.name().isBlank()||i.name().length()>100||i.category()==null||i.category().isBlank()||i.category().length()>60)throw new ApiError(400,"invalid_tag");}
    @Transactional public Object tag(TagInput i,UUID actor){
        validTag(i);UUID id=UUID.randomUUID();String slug=i.slug();
        if(slug==null){String base=java.text.Normalizer.normalize(i.name(),java.text.Normalizer.Form.NFKD).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("^-|-$","");if(base.isEmpty())base="topic";if(base.length()>40)base=base.substring(0,40);slug=base+"-"+id.toString().substring(0,8);}
        db.sql("INSERT INTO tags(id,slug,name,category) VALUES(:id,:slug,:name,:category)").param("id",id).param("slug",slug).param("name",i.name().strip()).param("category",i.category().strip()).update();audit(actor,"tag_create",id);return Map.of("id",id);
    }
    @Transactional public void updateTag(UUID id,TagInput i,UUID actor){
        validTag(i);int n=db.sql("UPDATE tags SET slug=COALESCE(:slug,slug),name=:name,category=:category WHERE id=:id AND active").param("id",id).param("slug",i.slug()).param("name",i.name().strip()).param("category",i.category().strip()).update();if(n!=1)throw new ApiError(404,"tag_not_found");audit(actor,"tag_update",id);
    }
    @Transactional public void alias(UUID id,String alias,UUID actor){if(alias==null||alias.isBlank()||alias.length()>100)throw new ApiError(400,"invalid_alias");if(db.sql("SELECT id FROM tags WHERE id=:id AND active FOR SHARE").param("id",id).query(UUID.class).list().isEmpty())throw new ApiError(404,"tag_not_found");db.sql("INSERT INTO tag_aliases VALUES(:a,:id)").param("a",alias.strip().toLowerCase(Locale.ROOT)).param("id",id).update();audit(actor,"tag_alias",id);}
    @Transactional public void merge(UUID old,UUID canonical,UUID actor){try{if(old==null||canonical==null||old.equals(canonical))throw new ApiError(400,"invalid_merge");lockMergeArticles(old,canonical);db.sql("SELECT version FROM classification_config WHERE id=1 FOR UPDATE NOWAIT").query(Long.class).single();var rows=db.sql("SELECT id,active FROM tags WHERE id IN (:ids) ORDER BY id FOR UPDATE NOWAIT").param("ids",List.of(old,canonical)).query().listOfRows();if(rows.size()!=2||rows.stream().anyMatch(r->!Boolean.TRUE.equals(r.get("active"))))throw new ApiError(409,"inactive_tag");
        lockMergeArticles(old,canonical);
        boolean configured=db.sql("SELECT count(*) FROM classification_topics WHERE tag_id=:old").param("old",old).query(Integer.class).single()>0;
        if(configured){db.sql("INSERT INTO classification_topics(tag_id,definition) SELECT :target,definition FROM classification_topics WHERE tag_id=:old ON CONFLICT(tag_id) DO NOTHING").param("target",canonical).param("old",old).update();db.sql("DELETE FROM classification_topics WHERE tag_id=:old").param("old",old).update();db.sql("UPDATE classification_config SET version=version+1 WHERE id=1").update();}
        db.sql("INSERT INTO article_tags(article_id,tag_id,confidence,method,locked) SELECT article_id,:target,confidence,method,locked FROM article_tags WHERE tag_id=:old ON CONFLICT(article_id,tag_id) DO UPDATE SET confidence=GREATEST(article_tags.confidence,EXCLUDED.confidence),locked=article_tags.locked OR EXCLUDED.locked").param("target",canonical).param("old",old).update();db.sql("DELETE FROM article_tags WHERE tag_id=:id").param("id",old).update();db.sql("UPDATE tag_aliases SET tag_id=:target WHERE tag_id=:old").param("target",canonical).param("old",old).update();db.sql("UPDATE tag_redirects SET canonical_id=:target WHERE canonical_id=:old").param("target",canonical).param("old",old).update();db.sql("INSERT INTO tag_redirects VALUES(:old,:target)").param("old",old).param("target",canonical).update();db.sql("UPDATE tags SET active=false WHERE id=:old").param("old",old).update();audit(actor,"tag_merge",old);
    }catch(org.springframework.dao.DataAccessException error){for(Throwable cause=error;cause!=null;cause=cause.getCause())if(cause instanceof java.sql.SQLException sql&&"55P03".equals(sql.getSQLState()))throw new ApiError(409,"tag_merge_busy");if(error instanceof org.springframework.dao.PessimisticLockingFailureException)throw new ApiError(409,"tag_merge_busy");throw error;}}
    private void lockMergeArticles(UUID old,UUID target){db.sql("SELECT a.id FROM articles a WHERE EXISTS(SELECT 1 FROM article_tags at WHERE at.article_id=a.id AND at.tag_id IN (:ids)) ORDER BY a.id FOR UPDATE OF a NOWAIT").param("ids",List.of(old,target)).query(UUID.class).list();}
    private void audit(UUID actor,String action,UUID id){db.sql("INSERT INTO admin_actions VALUES(:id,:actor,:action,:target,now())").param("id",UUID.randomUUID()).param("actor",actor).param("action",action).param("target",id).update();}
}
