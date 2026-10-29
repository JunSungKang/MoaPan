package com.moapan.jobs;

import com.moapan.news.*;
import com.moapan.site.CatalogRepository;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

@Component
public class TranslationWorker {
    public static final String POLICY="auto-v2";
    public static final String PROVIDER="IndexTeam/Index-Translate-2B-Q8_0@3a0eca6ce931d88d00614d427ab3ed8ff0743902/"+TranslationGlossary.VERSION+"/"+TranslationValidator.VERSION+"/"+POLICY;
    private final CatalogRepository repo;
    private final LocalTranslator translator;
    private final ArticleTranslations translations;
    private final TransactionTemplate tx;
    private final JsonMapper json=JsonMapper.builder().build();
    public TranslationWorker(CatalogRepository repo,LocalTranslator translator,ArticleTranslations translations,PlatformTransactionManager manager){this.repo=repo;this.translator=translator;this.translations=translations;tx=new TransactionTemplate(manager);}
    @Scheduled(fixedDelay=1000)
    public void tick(){
        if(!"worker".equals(System.getenv("MOAPAN_ROLE"))||!translator.enabled())return;
        try{
            if(!runtimeReady())return;
            processOne();
        }catch(Exception ignored){/* No input/output/credentials. Next tick recovers transient DB failure; monitoring detects stale checked_at. */}
    }
    public boolean runtimeReady(){
        if(repo.db().sql("SELECT next_probe_at<=now() FROM translation_runtime WHERE id=1").query(Boolean.class).single()){
            if(!translator.healthy()){runtimeFailure("translation_health_failed");return false;}
            repo.db().sql("UPDATE translation_runtime SET state='READY',failures=0,error_code=NULL,checked_at=now(),next_probe_at=now()+interval '15 seconds' WHERE id=1").update();return true;
        }
        return "READY".equals(repo.db().sql("SELECT state FROM translation_runtime WHERE id=1").query(String.class).single());
    }
    private void runtimeFailure(String code){repo.db().sql("UPDATE translation_runtime SET state='UNAVAILABLE',failures=LEAST(failures+1,1000000),error_code=:code,checked_at=now(),next_probe_at=now()+CASE WHEN failures=0 THEN interval '30 seconds' WHEN failures=1 THEN interval '2 minutes' ELSE interval '10 minutes' END WHERE id=1").param("code",code).update();}
    public Map<String,Object> claim(){return tx.execute(status->{
        repo.db().sql("DELETE FROM article_translations WHERE article_id IN (SELECT article_id FROM article_translation_jobs WHERE policy_version<>:policy)").param("policy",POLICY).update();
        repo.db().sql("UPDATE article_translation_jobs SET policy_version=:policy,state='PENDING',progress='{}',field_attempts='{}',attempts=0,error_code=NULL,lease_until=NULL,lease_token=NULL,available_at=now() WHERE policy_version<>:policy").param("policy",POLICY).update();
        repo.db().sql("UPDATE article_translation_jobs j SET state='PENDING',error_code=NULL,available_at=now() WHERE state='CANCELLED' AND EXISTS(SELECT 1 FROM articles a JOIN sources s ON s.id=a.source_id WHERE a.id=j.article_id AND a.public AND s.allow_display AND s.allow_summary AND s.allow_translation AND s.rights_status IN ('APPROVED','RESTRICTED'))").update();
        // Each SQL selection rechecks public state and current rights. No perpetual queue for revoked content.
        repo.db().sql("UPDATE article_translation_jobs j SET state='CANCELLED',progress='{}',lease_until=NULL WHERE state IN ('PENDING','RETRY','RUNNING') AND NOT EXISTS(SELECT 1 FROM articles a JOIN sources s ON s.id=a.source_id WHERE a.id=j.article_id AND a.public AND s.allow_display AND s.allow_summary AND s.allow_translation AND s.rights_status IN ('APPROVED','RESTRICTED'))").update();
        repo.db().sql("INSERT INTO article_translation_jobs(article_id,policy_version) SELECT a.id,:policy FROM articles a JOIN sources s ON s.id=a.source_id WHERE a.public AND a.language IN ('en','ko') AND s.allow_display AND s.allow_summary AND s.allow_translation AND s.rights_status IN ('APPROVED','RESTRICTED') AND NOT EXISTS(SELECT 1 FROM article_translations t WHERE t.article_id=a.id AND t.language=CASE WHEN a.language='en' THEN 'ko' ELSE 'en' END) ON CONFLICT DO NOTHING").param("policy",POLICY).update();
        var rows=repo.db().sql("SELECT j.*,a.title,a.summary_bullets,a.language FROM article_translation_jobs j JOIN articles a ON a.id=j.article_id JOIN sources s ON s.id=a.source_id WHERE a.public AND s.allow_display AND s.allow_summary AND s.allow_translation AND s.rights_status IN ('APPROVED','RESTRICTED') AND j.policy_version=:policy AND j.state IN ('PENDING','RETRY','RUNNING') AND j.available_at<=now() AND (j.lease_until IS NULL OR j.lease_until<now()) ORDER BY COALESCE(a.published_at,a.collected_at) DESC,a.id LIMIT 1 FOR UPDATE OF j SKIP LOCKED").param("policy",POLICY).query().listOfRows();
        if(rows.isEmpty())return null;var row=rows.getFirst();UUID token=UUID.randomUUID();
        repo.db().sql("UPDATE article_translation_jobs SET state='RUNNING',attempts=LEAST(attempts+1,1000000),lease_token=:token,lease_until=now()+interval '90 seconds',updated_at=now() WHERE article_id=:id").param("token",token).param("id",row.get("article_id")).update();row.put("token",token);return row;
    });}
    public boolean heartbeat(UUID id,UUID token){return repo.db().sql("UPDATE article_translation_jobs SET lease_until=now()+interval '90 seconds',updated_at=now() WHERE article_id=:id AND lease_token=:token AND state='RUNNING' AND lease_until>now()").param("id",id).param("token",token).update()==1;}
    @SuppressWarnings("unchecked")
    public boolean processOne(){
        var job=claim();if(job==null)return false;UUID id=(UUID)job.get("article_id"),token=(UUID)job.get("token");
        String title=job.get("title").toString();var bullets=List.of(json.readValue(job.get("summary_bullets").toString(),String[].class));String language=job.get("language").equals("en")?"ko":"en";
        List<String> inputs=new ArrayList<>();inputs.add(title);inputs.addAll(bullets);
        Map<String,String> progress=new LinkedHashMap<>(json.readValue(job.get("progress").toString(),Map.class));
        Map<String,Number> counts=new LinkedHashMap<>(json.readValue(job.get("field_attempts").toString(),Map.class));
        int index=0;while(index<inputs.size()&&progress.containsKey(Integer.toString(index)))index++;
        if(index==inputs.size()){finish(job,title,bullets,language,progress);return true;}
        String field=Integer.toString(index);int prior=counts.getOrDefault(field,0).intValue();
        try(var heartbeats=Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory())){
            heartbeats.scheduleAtFixedRate(()->{try{heartbeat(id,token);}catch(Exception ignored){}},20,20,TimeUnit.SECONDS);
            try{
                String output=translator.translate(inputs.get(index),language,prior,(String)job.get("error_code"));
                progress.put(field,output);counts.put(field,prior+1);
                if(progress.size()==inputs.size())finish(job,title,bullets,language,progress);
                else update(job,progress,counts,"PENDING",null,0,false);
            }catch(Exception error){
                boolean infra=error instanceof TranslationFailure failure&&failure.infrastructure();
                String code=error instanceof TranslationFailure?error.getMessage():"translation_invalid";
                if(infra){runtimeFailure(code);update(job,progress,counts,"RETRY",code,30,true);}
                else{counts.put(field,prior+1);boolean exhausted=prior+1>=3;update(job,exhausted?Map.of():progress,counts,exhausted?"ORIGINAL":"RETRY",code,exhausted?0:5,false);}
            }finally{heartbeats.shutdownNow();}
        }
        return true;
    }
    private void update(Map<String,Object> job,Map<String,String> progress,Map<String,? extends Number> counts,String state,String code,int delay,boolean infra){
        tx.executeWithoutResult(status->{
        repo.db().sql("SELECT s.id FROM sources s JOIN articles a ON a.source_id=s.id WHERE a.id=:id FOR SHARE OF s").param("id",job.get("article_id")).query(UUID.class).list();
        repo.db().sql("UPDATE article_translation_jobs SET progress=CAST(:progress AS jsonb),field_attempts=CAST(:counts AS jsonb),state=:state,error_code=:code,lease_until=NULL,updated_at=now(),infra_failures=LEAST(infra_failures+:infra,1000000),available_at=now()+make_interval(secs=>:delay) WHERE article_id=:id AND lease_token=:token AND state='RUNNING' AND lease_until>now() AND EXISTS(SELECT 1 FROM articles a JOIN sources s ON s.id=a.source_id WHERE a.id=:id AND a.public AND s.allow_display AND s.allow_summary AND s.allow_translation AND s.rights_status IN ('APPROVED','RESTRICTED'))")
          .param("progress",json.writeValueAsString(progress)).param("counts",json.writeValueAsString(counts)).param("state",state).param("code",code).param("delay",delay).param("infra",infra?1:0).param("id",job.get("article_id")).param("token",job.get("token")).update();
        });
    }
    private void finish(Map<String,Object> job,String title,List<String> bullets,String language,Map<String,String> progress){
        tx.executeWithoutResult(status->{
            UUID id=(UUID)job.get("article_id");
            repo.db().sql("SELECT s.id FROM sources s JOIN articles a ON a.source_id=s.id WHERE a.id=:id FOR SHARE OF s").param("id",id).query(UUID.class).list();
            repo.db().sql("SELECT id FROM articles WHERE id=:id FOR UPDATE").param("id",id).query(UUID.class).list();
            var live=repo.db().sql("SELECT article_id FROM article_translation_jobs WHERE article_id=:id AND lease_token=:token AND state='RUNNING' AND lease_until>now() FOR UPDATE").param("id",id).param("token",job.get("token")).query(UUID.class).list();if(live.isEmpty())return;
            List<String> translated=new ArrayList<>();for(int i=1;i<=bullets.size();i++)translated.add(progress.get(Integer.toString(i)));
            boolean stored=translations.store(id,ArticleTranslations.contentHash(title,bullets),language,new ArticleTranslations.Text(progress.get("0"),translated),PROVIDER);
            repo.db().sql("UPDATE article_translation_jobs SET state=:state,progress='{}',lease_until=NULL,error_code=:code,updated_at=now() WHERE article_id=:id AND lease_token=:token").param("state",stored?"DONE":"CANCELLED").param("id",id).param("token",job.get("token")).param("code",stored?null:"translation_stale").update();
        });
    }
}
