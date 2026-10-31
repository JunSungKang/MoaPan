package com.moapan.translation.application;

import com.moapan.ai.application.*;
import com.moapan.ai.domain.*;
import com.moapan.translation.domain.*;
import com.moapan.translation.infrastructure.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

@Component
public class TranslationWorker {
    public static final String POLICY="guard-v3/"+AiPolicy.fingerprint("IndexTeam/Index-Translate-2B-Q8_0@3a0eca6ce931d88d00614d427ab3ed8ff0743902",LocalTranslator.class,TranslationGlossary.class,TranslationValidator.class,ArticleGuard.class,com.moapan.ai.infrastructure.ModelTokenBudget.class,com.moapan.ai.infrastructure.ModelJson.class,com.moapan.ai.infrastructure.ModelStream.class);
    public static final String PROVIDER="index-translate-2b/"+POLICY;
    private static final String PERMITTED="a.public AND s.allow_display AND s.allow_summary AND s.allow_translation AND s.rights_status IN ('APPROVED','RESTRICTED')";
    private final JdbcClient db;private final TranslationModel translator;private final ArticleTranslations translations;private final TransactionTemplate tx;
    private final JsonMapper json=JsonMapper.builder().build();
    public TranslationWorker(JdbcClient db,TranslationModel translator,ArticleTranslations translations,PlatformTransactionManager manager){this.db=db;this.translator=translator;this.translations=translations;tx=new TransactionTemplate(manager);}
    @Scheduled(fixedDelay=1000) public void tick(){
        if(!"worker".equals(System.getenv("MOAPAN_ROLE"))||!translator.enabled())return;
        try{if(runtimeReady())processOne();}catch(Exception ignored){/* No article, output or credentials in logs. */}
    }
    public boolean runtimeReady(){
        if(db.sql("SELECT next_probe_at<=now() FROM translation_runtime WHERE id=1").query(Boolean.class).single()){
            if(!translator.healthy()){runtimeFailure("translation_health_failed");return false;}
            db.sql("UPDATE translation_runtime SET state='READY',checked_at=now(),next_probe_at=now()+interval '15 seconds' WHERE id=1").update();return true;
        }
        return "READY".equals(db.sql("SELECT state FROM translation_runtime WHERE id=1").query(String.class).single());
    }
    private void runtimeFailure(String code){db.sql("UPDATE translation_runtime SET state='UNAVAILABLE',failures=LEAST(failures+1,1000000),error_code=:code,checked_at=now(),next_probe_at=now()+CASE WHEN failures=0 THEN interval '30 seconds' WHEN failures=1 THEN interval '2 minutes' ELSE interval '10 minutes' END WHERE id=1").param("code",code).update();}
    public Map<String,Object> claim(){return tx.execute(status->{
        if(Boolean.TRUE.equals(db.sql("SELECT lease_until>now() FROM translation_runtime WHERE id=1 FOR UPDATE").query(Boolean.class).optional().orElse(false)))return null;
        db.sql("DELETE FROM article_translations WHERE NOT locked AND provider<>:provider").param("provider",PROVIDER).update();
        db.sql("UPDATE article_translation_jobs SET policy_version=:policy,state='PENDING',progress='{}',field_attempts='{}',attempts=0,infra_failures=0,error_code=NULL,lease_until=NULL,lease_token=NULL,started_at=now(),available_at=now() WHERE policy_version<>:policy").param("policy",POLICY).update();
        db.sql("UPDATE article_translation_jobs j SET state='CANCELLED',progress='{}',lease_until=NULL WHERE state IN ('PENDING','RETRY','RUNNING') AND NOT EXISTS(SELECT 1 FROM articles a JOIN sources s ON s.id=a.source_id WHERE a.id=j.article_id AND "+PERMITTED+")").update();
        db.sql("UPDATE article_translation_jobs j SET state='PENDING',error_code=NULL,started_at=now(),available_at=now() WHERE state='CANCELLED' AND EXISTS(SELECT 1 FROM articles a JOIN sources s ON s.id=a.source_id WHERE a.id=j.article_id AND "+PERMITTED+")").update();
        db.sql("INSERT INTO article_translation_jobs(article_id,policy_version) SELECT a.id,:policy FROM articles a JOIN sources s ON s.id=a.source_id WHERE "+PERMITTED+" AND a.language IN ('en','ko') AND NOT EXISTS(SELECT 1 FROM article_translations t WHERE t.article_id=a.id AND t.language=CASE WHEN a.language='en' THEN 'ko' ELSE 'en' END) ON CONFLICT DO NOTHING").param("policy",POLICY).update();
        db.sql("UPDATE article_translation_jobs SET state='ORIGINAL',progress='{}',lease_until=NULL,lease_token=NULL,error_code='translation_retry_exhausted' WHERE state IN ('PENDING','RETRY','RUNNING') AND (attempts>=20 OR infra_failures>=8 OR started_at<now()-interval '48 hours')").update();
        var rows=db.sql("SELECT j.*,a.source_id,a.title,a.summary_bullets,a.language FROM article_translation_jobs j JOIN articles a ON a.id=j.article_id JOIN sources s ON s.id=a.source_id WHERE "+PERMITTED+" AND j.policy_version=:policy AND j.state IN ('PENDING','RETRY','RUNNING') AND j.available_at<=now() AND (j.lease_until IS NULL OR j.lease_until<now()) AND NOT EXISTS(SELECT 1 FROM article_translations t WHERE t.article_id=a.id AND t.locked) ORDER BY COALESCE(a.published_at,a.collected_at) DESC,a.id LIMIT 1 FOR UPDATE OF j SKIP LOCKED").param("policy",POLICY).query().listOfRows();
        if(rows.isEmpty())return null;var row=rows.getFirst();UUID id=(UUID)row.get("article_id"),token=UUID.randomUUID();
        List<String> input=new ArrayList<>();input.add(row.get("title").toString());input.addAll(bullets(row));String risk=ArticleGuard.risk(String.join("\n",input));
        if(risk!=null){db.sql("UPDATE article_translation_jobs SET state='ORIGINAL',progress='{}',error_code=:code,lease_token=NULL,lease_until=NULL,updated_at=now() WHERE article_id=:id").param("id",id).param("code",risk).update();return null;}
        int seconds=LocalTranslator.timeoutSeconds()+25;
        if(!InferenceBudget.reserve(db,(UUID)row.get("source_id"),"translation",input.stream().mapToInt(String::length).sum(),seconds)){
            db.sql("UPDATE article_translation_jobs SET available_at=date_trunc('day',now())+interval '1 day',error_code='ai_source_budget' WHERE article_id=:id").param("id",id).update();return null;
        }
        db.sql("UPDATE article_translation_jobs SET state='RUNNING',attempts=attempts+1,lease_token=:token,lease_until=now()+interval '90 seconds',updated_at=now() WHERE article_id=:id").param("token",token).param("id",id).update();
        db.sql("UPDATE translation_runtime SET lease_token=:token,lease_until=now()+interval '90 seconds' WHERE id=1").param("token",token).update();row.put("token",token);row.put("reserved_seconds",seconds);row.put("budget_day",db.sql("SELECT current_date").query(java.time.LocalDate.class).single());return row;
    });}
    private List<String> bullets(Map<String,Object> row){return List.of(json.readValue(row.get("summary_bullets").toString(),String[].class));}
    public boolean heartbeat(UUID id,UUID token){return Boolean.TRUE.equals(tx.execute(status->{
        if(db.sql("UPDATE translation_runtime SET lease_until=now()+interval '90 seconds' WHERE id=1 AND lease_token=:token AND lease_until>now()").param("token",token).update()!=1)return false;
        return db.sql("UPDATE article_translation_jobs j SET lease_until=now()+interval '90 seconds',updated_at=now() WHERE article_id=:id AND lease_token=:token AND state='RUNNING' AND lease_until>now() AND EXISTS(SELECT 1 FROM articles a JOIN sources s ON s.id=a.source_id WHERE a.id=j.article_id AND "+PERMITTED+") AND NOT EXISTS(SELECT 1 FROM article_translations t WHERE t.article_id=j.article_id AND t.locked)").param("id",id).param("token",token).update()==1;
    }));}
    @SuppressWarnings("unchecked") public boolean processOne(){
        var job=claim();if(job==null)return false;long started=System.nanoTime();UUID id=(UUID)job.get("article_id"),token=(UUID)job.get("token");
        String title=job.get("title").toString(),language=job.get("language").equals("en")?"ko":"en";var bullets=bullets(job);
        List<String> inputs=new ArrayList<>();inputs.add(title);inputs.addAll(bullets);
        Map<String,String> progress=new LinkedHashMap<>(json.readValue(job.get("progress").toString(),Map.class));Map<String,Number> counts=new LinkedHashMap<>(json.readValue(job.get("field_attempts").toString(),Map.class));
        int index=0;while(index<inputs.size()&&progress.containsKey(Integer.toString(index)))index++;
        final int fieldIndex=index;String field=Integer.toString(index);int prior=counts.getOrDefault(field,0).intValue();
        try{
            if(index==inputs.size()){finish(job,title,bullets,language,progress);return true;}
            String output=InferenceControl.run(()->heartbeat(id,token),20000,(LocalTranslator.timeoutSeconds()+25)*1000,()->translator.translate(inputs.get(fieldIndex),language,prior,(String)job.get("error_code")));
            progress.put(field,output);counts.put(field,prior+1);
            if(progress.size()==inputs.size())finish(job,title,bullets,language,progress);else update(job,progress,counts,"PENDING",null,0,false);
            db.sql("UPDATE translation_runtime SET failures=0,error_code=NULL WHERE id=1 AND lease_token=:token").param("token",token).update();
        }catch(Exception error){
            boolean infra=error instanceof TranslationFailure failure&&failure.infrastructure();String code=error instanceof TranslationFailure||error instanceof ArticleGuard.Held||error instanceof com.moapan.ai.infrastructure.ModelTokenBudget.ArticleBudgetExceeded?error.getMessage():"translation_invalid";
            if(infra){runtimeFailure(code);boolean exhausted=((Number)job.get("infra_failures")).intValue()+1>=8;update(job,exhausted?Map.of():progress,counts,exhausted?"ORIGINAL":"RETRY",code,exhausted?0:30,true);}
            else{counts.put(field,prior+1);boolean exhausted=prior+1>=3||error instanceof ArticleGuard.Held||error instanceof com.moapan.ai.infrastructure.ModelTokenBudget.ArticleBudgetExceeded;update(job,exhausted?Map.of():progress,counts,exhausted?"ORIGINAL":"RETRY",code,exhausted?0:5,false);}
        }finally{
            int used=(int)Math.min(((Number)job.get("reserved_seconds")).intValue(),Math.max(1,(System.nanoTime()-started+999999999L)/1000000000L));
            InferenceBudget.settle(db,(UUID)job.get("source_id"),"translation",(java.time.LocalDate)job.get("budget_day"),((Number)job.get("reserved_seconds")).intValue(),used);
            db.sql("UPDATE translation_runtime SET lease_token=NULL,lease_until=NULL WHERE id=1 AND lease_token=:token").param("token",token).update();
        }
        return true;
    }
    private void update(Map<String,Object> job,Map<String,String> progress,Map<String,? extends Number> counts,String state,String code,int delay,boolean infra){
        tx.executeWithoutResult(status->{
            db.sql("SELECT s.id FROM sources s JOIN articles a ON a.source_id=s.id WHERE a.id=:id FOR SHARE OF s").param("id",job.get("article_id")).query(UUID.class).list();
            db.sql("UPDATE article_translation_jobs j SET progress=CAST(:progress AS jsonb),field_attempts=CAST(:counts AS jsonb),state=:state,error_code=:code,lease_until=NULL,updated_at=now(),infra_failures=infra_failures+:infra,available_at=now()+make_interval(secs=>:delay) WHERE article_id=:id AND lease_token=:token AND state='RUNNING' AND lease_until>now() AND policy_version=:policy AND EXISTS(SELECT 1 FROM articles a JOIN sources s ON s.id=a.source_id WHERE a.id=j.article_id AND "+PERMITTED+")")
                .param("progress",json.writeValueAsString(progress)).param("counts",json.writeValueAsString(counts)).param("state",state).param("code",code).param("delay",delay).param("infra",infra?1:0).param("id",job.get("article_id")).param("token",job.get("token")).param("policy",POLICY).update();
        });
    }
    private void finish(Map<String,Object> job,String title,List<String> bullets,String language,Map<String,String> progress){tx.executeWithoutResult(status->{
        UUID id=(UUID)job.get("article_id");db.sql("SELECT s.id FROM sources s JOIN articles a ON a.source_id=s.id WHERE a.id=:id FOR SHARE OF s").param("id",id).query(UUID.class).list();
        db.sql("SELECT id FROM articles WHERE id=:id FOR UPDATE").param("id",id).query(UUID.class).list();
        var live=db.sql("SELECT article_id FROM article_translation_jobs WHERE article_id=:id AND lease_token=:token AND state='RUNNING' AND lease_until>now() AND policy_version=:policy FOR UPDATE").param("id",id).param("token",job.get("token")).param("policy",POLICY).query(UUID.class).list();if(live.isEmpty())return;
        List<String> translated=new ArrayList<>();for(int i=1;i<=bullets.size();i++)translated.add(progress.get(Integer.toString(i)));
        boolean stored=translations.store(id,ArticleTranslations.contentHash(title,bullets),language,new ArticleTranslations.Text(progress.get("0"),translated),PROVIDER);
        db.sql("UPDATE article_translation_jobs SET state=:state,progress='{}',lease_until=NULL,error_code=:code,updated_at=now() WHERE article_id=:id AND lease_token=:token").param("state",stored?"DONE":"CANCELLED").param("id",id).param("token",job.get("token")).param("code",stored?null:"translation_stale").update();
    });}
}
