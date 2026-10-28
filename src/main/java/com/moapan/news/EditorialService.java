package com.moapan.news;
import com.moapan.*;
import com.moapan.site.CatalogRepository;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;
@Service @PreAuthorize("hasRole('ADMIN')")
public class EditorialService {
    public record Edit(List<String>summaryBullets,boolean summaryLocked,boolean tagsLocked,boolean published,List<UUID>tagIds){}
    public record TagInput(String slug,String name,String category){}
    private final CatalogRepository repo;
    public EditorialService(CatalogRepository repo){this.repo=repo;}
    public Object list(){return repo.db().sql("SELECT id,source_id,title,summary_bullets,summary_mode,summary_locked,tags_locked,public,published_at,collected_at FROM articles ORDER BY collected_at DESC,id LIMIT 100").query().listOfRows();}
    @Transactional public void edit(UUID id,Edit e,UUID actor){
        if(e.summaryBullets()==null||e.summaryBullets().size()>4||e.summaryBullets().stream().anyMatch(s->s==null||s.length()>350)||e.tagIds()==null||e.tagIds().size()>6)throw new ApiError(400,"invalid_editorial_input");
        var row=repo.db().sql("SELECT * FROM articles WHERE id=:id FOR UPDATE").param("id",id).query().listOfRows().stream().findFirst().orElseThrow(()->new ApiError(404,"article_not_found"));
        repo.db().sql("UPDATE articles SET summary_bullets=CAST(:b AS jsonb),summary_mode='MANUAL',summary_locked=:sl,tags_locked=:tl,public=:p,updated_at=now() WHERE id=:id").param("id",id).param("b",JsonMapper.builder().build().writeValueAsString(e.summaryBullets())).param("sl",e.summaryLocked()).param("tl",e.tagsLocked()).param("p",e.published()).update();repo.db().sql("DELETE FROM article_tags WHERE article_id=:id").param("id",id).update();for(UUID tag:new HashSet<>(e.tagIds()))repo.db().sql("INSERT INTO article_tags SELECT :id,id,1,'MANUAL',true FROM tags WHERE id=:tag AND active").param("id",id).param("tag",tag).update();audit(actor,"article_edit",id);
    }
    @Transactional public Object tag(TagInput i,UUID actor){if(i.slug()==null||!i.slug().matches("[a-z0-9-]{1,60}")||i.name()==null||i.name().isBlank()||i.name().length()>100||i.category()==null||i.category().length()>60)throw new ApiError(400,"invalid_tag");UUID id=UUID.randomUUID();repo.db().sql("INSERT INTO tags(id,slug,name,category) VALUES(:id,:slug,:name,:category)").param("id",id).param("slug",i.slug()).param("name",i.name()).param("category",i.category()).update();audit(actor,"tag_create",id);return Map.of("id",id);}
    @Transactional public void updateTag(UUID id,TagInput i,UUID actor){
        if(i.slug()==null||!i.slug().matches("[a-z0-9-]{1,60}")||i.name()==null||i.name().isBlank()||i.name().length()>100||i.category()==null||i.category().length()>60)throw new ApiError(400,"invalid_tag");
        int n=repo.db().sql("UPDATE tags SET slug=:slug,name=:name,category=:category WHERE id=:id AND active").param("id",id).param("slug",i.slug()).param("name",i.name()).param("category",i.category()).update();if(n!=1)throw new ApiError(404,"tag_not_found");audit(actor,"tag_update",id);
    }
    @Transactional public void alias(UUID id,String alias,UUID actor){if(alias==null||alias.isBlank()||alias.length()>100)throw new ApiError(400,"invalid_alias");repo.db().sql("INSERT INTO tag_aliases VALUES(:a,:id)").param("a",alias.toLowerCase(Locale.ROOT)).param("id",id).update();audit(actor,"tag_alias",id);}
    @Transactional public void merge(UUID old,UUID canonical,UUID actor){if(old.equals(canonical))throw new ApiError(400,"invalid_merge");var rows=repo.db().sql("SELECT id,active FROM tags WHERE id IN (:ids) ORDER BY id FOR UPDATE").param("ids",List.of(old,canonical)).query().listOfRows();if(rows.size()!=2||rows.stream().anyMatch(r->!Boolean.TRUE.equals(r.get("active"))))throw new ApiError(409,"inactive_tag");
        repo.db().sql("INSERT INTO article_tags(article_id,tag_id,confidence,method,locked) SELECT article_id,:target,confidence,method,locked FROM article_tags WHERE tag_id=:old ON CONFLICT(article_id,tag_id) DO UPDATE SET confidence=GREATEST(article_tags.confidence,EXCLUDED.confidence),locked=article_tags.locked OR EXCLUDED.locked").param("target",canonical).param("old",old).update();repo.db().sql("DELETE FROM article_tags WHERE tag_id=:id").param("id",old).update();repo.db().sql("UPDATE tag_aliases SET tag_id=:target WHERE tag_id=:old").param("target",canonical).param("old",old).update();repo.db().sql("UPDATE tag_redirects SET canonical_id=:target WHERE canonical_id=:old").param("target",canonical).param("old",old).update();repo.db().sql("INSERT INTO tag_redirects VALUES(:old,:target)").param("old",old).param("target",canonical).update();repo.db().sql("UPDATE tags SET active=false WHERE id=:old").param("old",old).update();audit(actor,"tag_merge",old);
    }
    private void audit(UUID actor,String action,UUID id){repo.db().sql("INSERT INTO admin_actions VALUES(:id,:actor,:action,:target,now())").param("id",UUID.randomUUID()).param("actor",actor).param("action",action).param("target",id).update();}
}
