package com.moapan.classification.application;
import com.moapan.classification.domain.ClassificationPolicy;

import com.moapan.shared.api.ApiError;
import com.moapan.article.domain.ArticleFingerprint;
import org.springframework.jdbc.core.simple.JdbcClient;
import java.util.*;
import com.moapan.classification.domain.TopicTaxonomy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;

@Service
public class TopicCatalog {
    public record Entry(UUID tagId,String definition){}
    public record Edit(long version,List<Entry> topics){}
    private final JdbcClient db;
    public TopicCatalog(JdbcClient db){this.db=db;}
    public TopicTaxonomy.Snapshot snapshot(boolean lock){
        long version=db.sql("SELECT version FROM classification_config WHERE id=1"+(lock?" FOR SHARE":"")).query(Long.class).single();
        var topics=db.sql("SELECT t.id,t.slug,t.name,c.definition FROM classification_topics c JOIN tags t ON t.id=c.tag_id WHERE t.active ORDER BY t.id"+(lock?" FOR SHARE OF t,c":"")).query((r,n)->new TopicTaxonomy.Topic(r.getObject("id",UUID.class),r.getString("slug"),r.getString("name"),r.getString("definition"))).list();
        String hash=ArticleFingerprint.contentHash(ClassificationPolicy.VERSION+":"+version,topics.stream().map(t->t.id()+"|"+t.slug()+"|"+t.name()+"|"+t.definition()).toList());
        return new TopicTaxonomy.Snapshot(version,topics,hash);
    }
    @Transactional @PreAuthorize("hasRole('ADMIN')")
    public void edit(Edit input,UUID actor){
        if(input==null||input.topics()==null||input.topics().isEmpty()||input.topics().size()>8)throw new ApiError(400,"invalid_classification_topics");
        Set<UUID> ids=new HashSet<>();for(var e:input.topics())if(e==null||e.tagId()==null||!ids.add(e.tagId())||e.definition()==null||e.definition().isBlank()||e.definition().length()>200)throw new ApiError(400,"invalid_classification_topics");
        long version=db.sql("SELECT version FROM classification_config WHERE id=1 FOR UPDATE").query(Long.class).single();
        if(input.version()!=version)throw new ApiError(409,"classification_topics_conflict");
        if(db.sql("SELECT id FROM tags WHERE id IN (:ids) AND active ORDER BY id FOR SHARE").param("ids",ids).query(UUID.class).list().size()!=ids.size())throw new ApiError(400,"invalid_classification_topics");
        db.sql("DELETE FROM classification_topics").update();
        for(var e:input.topics())db.sql("INSERT INTO classification_topics VALUES(:id,:definition)").param("id",e.tagId()).param("definition",e.definition().strip()).update();
        db.sql("UPDATE classification_config SET version=version+1 WHERE id=1").update();
        db.sql("INSERT INTO admin_actions VALUES(:id,:actor,'classification_topics','00000000-0000-0000-0000-000000000001',now())").param("id",UUID.randomUUID()).param("actor",actor).update();
    }
}
