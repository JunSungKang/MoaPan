package com.moapan.classification;

import com.moapan.ApiError;
import com.moapan.news.ArticleTranslations;
import com.moapan.site.CatalogRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;

@Service
public class TopicCatalog {
    public record Topic(UUID id,String slug,String name,String definition){}
    public record Snapshot(long version,List<Topic> topics,String hash){}
    public record Entry(UUID tagId,String definition){}
    public record Edit(long version,List<Entry> topics){}
    private final CatalogRepository repo;
    public TopicCatalog(CatalogRepository repo){this.repo=repo;}
    public Snapshot snapshot(boolean lock){
        long version=repo.db().sql("SELECT version FROM classification_config WHERE id=1"+(lock?" FOR SHARE":"")).query(Long.class).single();
        var topics=repo.db().sql("SELECT t.id,t.slug,t.name,c.definition FROM classification_topics c JOIN tags t ON t.id=c.tag_id WHERE t.active ORDER BY t.id"+(lock?" FOR SHARE OF t,c":"")).query((r,n)->new Topic(r.getObject("id",UUID.class),r.getString("slug"),r.getString("name"),r.getString("definition"))).list();
        String hash=ArticleTranslations.contentHash(LocalClassifier.POLICY+":"+version,topics.stream().map(t->t.id()+"|"+t.slug()+"|"+t.name()+"|"+t.definition()).toList());
        return new Snapshot(version,topics,hash);
    }
    @Transactional @PreAuthorize("hasRole('ADMIN')")
    public void edit(Edit input,UUID actor){
        if(input==null||input.topics()==null||input.topics().isEmpty()||input.topics().size()>8)throw new ApiError(400,"invalid_classification_topics");
        Set<UUID> ids=new HashSet<>();for(var e:input.topics())if(e==null||e.tagId()==null||!ids.add(e.tagId())||e.definition()==null||e.definition().isBlank()||e.definition().length()>200)throw new ApiError(400,"invalid_classification_topics");
        long version=repo.db().sql("SELECT version FROM classification_config WHERE id=1 FOR UPDATE").query(Long.class).single();
        if(input.version()!=version)throw new ApiError(409,"classification_topics_conflict");
        if(repo.db().sql("SELECT id FROM tags WHERE id IN (:ids) AND active ORDER BY id FOR SHARE").param("ids",ids).query(UUID.class).list().size()!=ids.size())throw new ApiError(400,"invalid_classification_topics");
        repo.db().sql("DELETE FROM classification_topics").update();
        for(var e:input.topics())repo.db().sql("INSERT INTO classification_topics VALUES(:id,:definition)").param("id",e.tagId()).param("definition",e.definition().strip()).update();
        repo.db().sql("UPDATE classification_config SET version=version+1 WHERE id=1").update();
        repo.db().sql("INSERT INTO admin_actions VALUES(:id,:actor,'classification_topics','00000000-0000-0000-0000-000000000001',now())").param("id",UUID.randomUUID()).param("actor",actor).update();
    }
}
