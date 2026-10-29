package com.moapan.edition.application;

import com.moapan.shared.api.ApiError;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import tools.jackson.databind.json.JsonMapper;

@Service
public class RepresentativeInterests {
    public record Settings(Long version,Map<UUID,Integer> weights){}
    private final JdbcClient db;
    private final JsonMapper json=JsonMapper.builder().build();
    public RepresentativeInterests(JdbcClient db){this.db=db;}
    public Settings read(){
        var row=db.sql("SELECT version,weights::text FROM representative_interests WHERE singleton").query().singleRow();
        Map<UUID,Integer> weights=new LinkedHashMap<>();
        var node=json.readTree(row.get("weights").toString());
        var active=new HashSet<>(db.sql("SELECT id FROM tags WHERE active ORDER BY id").query(UUID.class).list());
        for(var tag:active){
            var value=node.get(tag.toString());if(value!=null)weights.put(tag,value.asInt());
        }
        // Resolve merged tags without exposing or storing any member preferences.
        for(var redirect:db.sql("SELECT old_id,canonical_id FROM tag_redirects").query().listOfRows()){
            var value=node.get(redirect.get("old_id").toString());
            UUID target=(UUID)redirect.get("canonical_id");
            if(value!=null&&active.contains(target))
                weights.merge(target,value.asInt(),Math::max);
        }
        return new Settings(((Number)row.get("version")).longValue(),weights);
    }
    @Transactional @PreAuthorize("hasRole('ADMIN')")
    public Settings update(Settings input,UUID actor){
        if(input.version()==null||input.weights()==null||input.weights().isEmpty()||input.weights().size()>30||input.version()<0||
            input.weights().entrySet().stream().anyMatch(e->e.getKey()==null||e.getValue()==null||e.getValue()<1||e.getValue()>5))
            throw new ApiError(400,"invalid_representative_interests");
        var active=db.sql("SELECT id FROM tags WHERE active AND id IN (:ids) FOR SHARE").param("ids",input.weights().keySet()).query(UUID.class).list();
        if(active.size()!=input.weights().size())throw new ApiError(400,"inactive_tag");
        if(db.sql("UPDATE representative_interests SET weights=CAST(:w AS jsonb),version=version+1 WHERE singleton AND version=:v")
            .param("w",json.writeValueAsString(input.weights())).param("v",input.version()).update()!=1)throw new ApiError(409,"version_conflict");
        db.sql("INSERT INTO admin_actions VALUES(:id,:actor,'representative_interests',:target,now())")
            .param("id",UUID.randomUUID()).param("actor",actor).param("target",new UUID(0,0)).update();
        return read();
    }
}
