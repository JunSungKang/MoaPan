package com.moapan.bootstrap;

import com.moapan.translation.application.TranslationWorker;
import com.moapan.classification.infrastructure.LocalClassifier;
import com.moapan.article.domain.ArticleFingerprint;
import java.sql.*;
import java.util.*;
import java.security.MessageDigest;
import tools.jackson.databind.json.JsonMapper;

/** Explicit, database-scoped compatibility transition. Never runs on ordinary worker startup. */
public final class AiPolicyTransition {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private AiPolicyTransition(){}
    private static tools.jackson.databind.JsonNode legacy(){try(var in=AiPolicyTransition.class.getResourceAsStream("/ai/java27-policy-transition.json")){return JSON.readTree(in);}catch(Exception e){throw new IllegalStateException("policy_transition_manifest_missing");}}
    public static Map<String,Object> identity(){
        var l=legacy();var classes=new TreeMap<String,String>();
        for(var c:l.path("classes")){String name=c.path("name").asText();try(var in=AiPolicyTransition.class.getResourceAsStream("/"+name)){if(in==null)throw new IllegalStateException();classes.put(name,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())));}catch(Exception e){throw new IllegalStateException("policy_bytes_missing");}}
        return Map.of("java",Runtime.version().feature(),"translationPolicy",TranslationWorker.POLICY,"translationProvider",TranslationWorker.PROVIDER,"classificationPolicy",LocalClassifier.POLICY,"classes",classes);
    }
    public static void printIdentity(){System.out.println(JSON.writeValueAsString(identity()));}
    public static Map<String,Integer> transition(Connection db,boolean rollback)throws SQLException {
        var l=legacy();String oldTranslation=l.path("translationPolicy").asText(),oldProvider=l.path("translationProvider").asText(),oldClass=l.path("classificationPolicy").asText();
        if(oldTranslation.isBlank()||oldProvider.isBlank()||oldClass.isBlank()||Runtime.version().feature()!=25)throw new IllegalStateException("policy_transition_manifest_invalid");
        boolean auto=db.getAutoCommit();db.setAutoCommit(false);
        try {
            try(var s=db.createStatement()){s.execute("SELECT pg_advisory_xact_lock(93251,25)");}
            long version;try(var s=db.createStatement();var r=s.executeQuery("SELECT version FROM classification_config WHERE id=1 FOR SHARE")){r.next();version=r.getLong(1);}
            var topics=new ArrayList<String>();try(var s=db.createStatement();var r=s.executeQuery("SELECT t.id,t.slug,t.name,c.definition FROM classification_topics c JOIN tags t ON t.id=c.tag_id WHERE t.active ORDER BY t.id FOR SHARE OF t,c")){while(r.next())topics.add(r.getString(1)+"|"+r.getString(2)+"|"+r.getString(3)+"|"+r.getString(4));}
            String oldHash=ArticleFingerprint.contentHash(oldClass+":"+version,topics),newHash=ArticleFingerprint.contentHash(LocalClassifier.POLICY+":"+version,topics);
            var changed=new LinkedHashMap<String,Integer>();
            changed.put("translations",update(db,"UPDATE article_translations SET provider=? WHERE provider=? AND NOT locked",rollback?oldProvider:TranslationWorker.PROVIDER,rollback?TranslationWorker.PROVIDER:oldProvider));
            changed.put("translationJobs",update(db,"UPDATE article_translation_jobs SET policy_version=?,state=CASE WHEN state='RUNNING' THEN 'RETRY' ELSE state END,lease_until=NULL,lease_token=NULL WHERE policy_version=?",rollback?oldTranslation:TranslationWorker.POLICY,rollback?TranslationWorker.POLICY:oldTranslation));
            try(var q=db.prepareStatement("UPDATE article_classification_jobs SET policy_hash=?,model_policy=?,state=CASE WHEN state='RUNNING' THEN 'RETRY' ELSE state END,lease_until=NULL,lease_token=NULL WHERE policy_hash=? AND (model_policy IS NULL OR model_policy=?)")){
                q.setString(1,rollback?oldHash:newHash);q.setString(2,rollback?oldClass:LocalClassifier.POLICY);q.setString(3,rollback?newHash:oldHash);q.setString(4,rollback?LocalClassifier.POLICY:oldClass);changed.put("classificationJobs",q.executeUpdate());
            }
            try(var q=db.createStatement()){q.executeUpdate("UPDATE translation_runtime SET lease_token=NULL,lease_until=NULL WHERE id=1");q.executeUpdate("UPDATE classification_runtime SET lease_token=NULL,lease_until=NULL WHERE id=1");}
            db.commit();return Map.copyOf(changed);
        }catch(Exception e){db.rollback();if(e instanceof SQLException sql)throw sql;throw new IllegalStateException("policy_transition_failed");}finally{db.setAutoCommit(auto);}
    }
    private static int update(Connection db,String sql,String target,String source)throws SQLException{try(var q=db.prepareStatement(sql)){q.setString(1,target);q.setString(2,source);return q.executeUpdate();}}
    public static void run(DatabaseSettings settings,String action){
        if(!Set.of("apply-reviewed-java25","rollback-reviewed-java27").contains(action==null?"":action))throw new IllegalStateException("explicit_policy_transition_required");
        try(var db=DriverManager.getConnection(settings.url(),settings.user(),settings.password())){System.out.println(JSON.writeValueAsString(transition(db,action.startsWith("rollback"))));}
        catch(SQLException e){throw new IllegalStateException("policy_transition_failed");}
    }
}
