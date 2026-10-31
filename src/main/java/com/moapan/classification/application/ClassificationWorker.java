package com.moapan.classification.application;
import com.moapan.classification.application.TopicCatalog;
import com.moapan.classification.domain.ClassificationPolicy;

import com.moapan.article.domain.ArticleFingerprint;
import org.springframework.jdbc.core.simple.JdbcClient;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ClassificationWorker {
    private static final String PERMITTED="a.public AND NOT a.tags_locked AND s.allow_display AND s.allow_summary AND s.rights_status IN ('APPROVED','RESTRICTED')";
    private final JdbcClient db;private final TopicCatalog catalog;private final ClassificationModel classifier;private final TransactionTemplate tx;
    private static final JsonMapper JSON=JsonMapper.builder().build();
    public ClassificationWorker(JdbcClient db,TopicCatalog catalog,ClassificationModel classifier,PlatformTransactionManager tm){this.db=db;this.catalog=catalog;this.classifier=classifier;tx=new TransactionTemplate(tm);}
    public record Job(UUID id,UUID token,String hash,String title,List<String> summary,com.moapan.classification.domain.TopicTaxonomy.Snapshot topics,UUID source,java.time.LocalDate budgetDay){}
    @Scheduled(fixedDelay=1000) public void tick(){
        if(!"worker".equals(System.getenv("MOAPAN_ROLE"))||!classifier.enabled())return;
        try{processOne();}catch(Exception ignored){/* Only bounded error codes persist, never article/model text or credentials. */}
    }
    public Job claim(){return tx.execute(status->{
        var runtime=db.sql("SELECT lease_until>now() AS busy FROM classification_runtime WHERE id=1 FOR UPDATE").query().singleRow();if(Boolean.TRUE.equals(runtime.get("busy")))return null;
        var topics=catalog.snapshot(true);if(topics.topics().isEmpty())return null;
        db.sql("UPDATE article_classification_jobs j SET state='CANCELLED',lease_token=NULL,lease_until=NULL,updated_at=now() WHERE state IN ('PENDING','RETRY','RUNNING') AND NOT EXISTS(SELECT 1 FROM articles a JOIN sources s ON s.id=a.source_id WHERE a.id=j.article_id AND "+PERMITTED+")").update();
        var pending=db.sql("SELECT a.id,a.title,a.summary_bullets FROM articles a JOIN sources s ON s.id=a.source_id LEFT JOIN article_classification_jobs j ON j.article_id=a.id WHERE "+PERMITTED+" AND (j.article_id IS NULL OR j.policy_hash<>:policy OR j.content_hash=repeat('0',64) OR j.state='CANCELLED') ORDER BY COALESCE(a.published_at,a.collected_at) DESC,a.id LIMIT 50")
            .param("policy",topics.hash()).query().listOfRows();
        for(var a:pending){String hash=ArticleFingerprint.contentHash(a.get("title").toString(),bullets(a));
            db.sql("INSERT INTO article_classification_jobs(article_id,content_hash,policy_hash) VALUES(:id,:hash,:policy) ON CONFLICT(article_id) DO UPDATE SET content_hash=EXCLUDED.content_hash,policy_hash=EXCLUDED.policy_hash,state='PENDING',attempts=0,available_at=now(),lease_token=NULL,lease_until=NULL,error_code=NULL,updated_at=now(),started_at=now()")
                .param("id",a.get("id")).param("hash",hash).param("policy",topics.hash()).update();
        }
        db.sql("UPDATE article_classification_jobs SET state=CASE WHEN attempts>=3 THEN 'FAILED' ELSE 'RETRY' END,error_code='classification_lease_expired',lease_token=NULL,lease_until=NULL,available_at=now()+interval '30 seconds' WHERE state='RUNNING' AND lease_until<now()").update();
        db.sql("UPDATE article_classification_jobs SET state='FAILED',error_code='classification_deadline',lease_token=NULL,lease_until=NULL WHERE state IN ('PENDING','RETRY') AND started_at<now()-interval '48 hours'").update();
        var rows=db.sql("SELECT j.*,a.source_id,a.title,a.summary_bullets FROM article_classification_jobs j JOIN articles a ON a.id=j.article_id JOIN sources s ON s.id=a.source_id WHERE "+PERMITTED+" AND j.policy_hash=:policy AND j.state IN ('PENDING','RETRY') AND j.attempts<3 AND j.available_at<=now() ORDER BY COALESCE(a.published_at,a.collected_at) DESC,a.id LIMIT 1 FOR UPDATE OF j SKIP LOCKED").param("policy",topics.hash()).query().listOfRows();
        if(rows.isEmpty())return null;var row=rows.getFirst();UUID token=UUID.randomUUID(),id=(UUID)row.get("article_id");
        String text=row.get("title")+"\n"+String.join("\n",bullets(row));String risk=com.moapan.ai.domain.ArticleGuard.risk(text);
        if(risk!=null){db.sql("UPDATE article_classification_jobs SET state='FAILED',error_code=:code WHERE article_id=:id").param("code",risk).param("id",id).update();return null;}
        if(!com.moapan.ai.application.InferenceBudget.reserve(db,(UUID)row.get("source_id"),"classification",text.length(),205)){
            db.sql("UPDATE article_classification_jobs SET available_at=date_trunc('day',now())+interval '1 day',error_code='ai_source_budget' WHERE article_id=:id").param("id",id).update();return null;
        }
        db.sql("UPDATE article_classification_jobs SET state='RUNNING',attempts=attempts+1,lease_token=:token,lease_until=now()+interval '4 minutes',updated_at=now() WHERE article_id=:id").param("token",token).param("id",id).update();
        db.sql("UPDATE classification_runtime SET lease_token=:token,lease_until=now()+interval '4 minutes',checked_at=now(),error_code=NULL WHERE id=1").param("token",token).update();
        return new Job(id,token,row.get("content_hash").toString(),row.get("title").toString(),bullets(row),topics,(UUID)row.get("source_id"),db.sql("SELECT current_date").query(java.time.LocalDate.class).single());
    });}
    private static List<String> bullets(Map<String,Object> a){return List.of(JSON.readValue(a.get("summary_bullets").toString(),String[].class));}
    public boolean heartbeat(Job job){return Boolean.TRUE.equals(tx.execute(s->{
        if(db.sql("UPDATE classification_runtime SET lease_until=now()+interval '4 minutes',checked_at=now() WHERE id=1 AND lease_token=:token AND lease_until>now()").param("token",job.token()).update()!=1)return false;
        if(!catalog.snapshot(false).hash().equals(job.topics().hash()))return false;
        return db.sql("UPDATE article_classification_jobs j SET lease_until=now()+interval '4 minutes' WHERE article_id=:id AND lease_token=:token AND state='RUNNING' AND lease_until>now() AND EXISTS(SELECT 1 FROM articles a JOIN sources s ON s.id=a.source_id WHERE a.id=j.article_id AND "+PERMITTED+")").param("id",job.id()).param("token",job.token()).update()==1;
    }));}
    public boolean processOne(){var job=claim();if(job==null)return false;long started=System.nanoTime();
            try{finish(job,com.moapan.ai.application.InferenceControl.run(()->heartbeat(job),20000,205000,()->classifier.classify(job.title(),job.summary(),job.topics())));}
            catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();fail(job,e instanceof IllegalArgumentException?"classification_invalid_output":"classification_request_failed");}
            finally{com.moapan.ai.application.InferenceBudget.settle(db,job.source(),"classification",job.budgetDay(),205,(int)Math.min(205,Math.max(1,(System.nanoTime()-started+999999999L)/1000000000L)));}
        return true;
    }
    public boolean finish(Job job,Set<UUID> selected){return Boolean.TRUE.equals(tx.execute(status->{
        if(!ownsRuntime(job))return false;
        var current=catalog.snapshot(true);
        db.sql("SELECT s.id FROM sources s JOIN articles a ON a.source_id=s.id WHERE a.id=:id FOR SHARE OF s").param("id",job.id()).query(UUID.class).list();
        var article=db.sql("SELECT a.title,a.summary_bullets FROM articles a JOIN sources s ON s.id=a.source_id WHERE a.id=:id AND "+PERMITTED+" FOR UPDATE OF a").param("id",job.id()).query().listOfRows();
        boolean live=db.sql("SELECT count(*) FROM article_classification_jobs WHERE article_id=:id AND state='RUNNING' AND lease_token=:token AND lease_until>now()").param("id",job.id()).param("token",job.token()).query(Integer.class).single()==1;
        boolean valid=live&&!article.isEmpty()&&current.hash().equals(job.topics().hash())&&ArticleFingerprint.contentHash(article.getFirst().get("title").toString(),bullets(article.getFirst())).equals(job.hash());
        if(!valid){db.sql("UPDATE article_classification_jobs SET state='CANCELLED',lease_token=NULL,lease_until=NULL,error_code='classification_stale',updated_at=now() WHERE article_id=:id AND lease_token=:token").param("id",job.id()).param("token",job.token()).update();release(job,"classification_stale");return false;}
        Set<UUID> allowed=new HashSet<>(current.topics().stream().map(com.moapan.classification.domain.TopicTaxonomy.Topic::id).toList());
        if(selected==null||selected.size()>6||!allowed.containsAll(selected))throw new IllegalArgumentException("classification_invalid_output");
        db.sql("DELETE FROM article_tags WHERE article_id=:id AND NOT locked AND method<>'MANUAL'").param("id",job.id()).update();
        // Model confidence is not calibrated. 1 denotes membership, not probability of correctness.
        int preserved=db.sql("SELECT count(*) FROM article_tags WHERE article_id=:id").param("id",job.id()).query(Integer.class).single();
        var existing=new HashSet<>(db.sql("SELECT tag_id FROM article_tags WHERE article_id=:id").param("id",job.id()).query(UUID.class).list());
        for(UUID tag:selected.stream().filter(t->!existing.contains(t)).sorted().limit(Math.max(0,6-preserved)).toList())db.sql("INSERT INTO article_tags VALUES(:id,:tag,1,'LLM',false) ON CONFLICT DO NOTHING").param("id",job.id()).param("tag",tag).update();
        db.sql("UPDATE article_classification_jobs SET state=:state,lease_token=NULL,lease_until=NULL,error_code=NULL,updated_at=now() WHERE article_id=:id AND lease_token=:token").param("id",job.id()).param("token",job.token()).param("state",selected.isEmpty()?"UNCLASSIFIED":"DONE").update();release(job,null);return true;
    }));}
    private boolean ownsRuntime(Job job){return !db.sql("SELECT id FROM classification_runtime WHERE id=1 AND lease_token=:token AND lease_until>now() FOR UPDATE").param("token",job.token()).query(Integer.class).list().isEmpty();}
    public void fail(Job job,String code){tx.executeWithoutResult(s->{
        if(!ownsRuntime(job))return;
        db.sql("UPDATE article_classification_jobs SET state=CASE WHEN attempts>=3 THEN 'FAILED' ELSE 'RETRY' END,available_at=now()+make_interval(secs=>30*attempts),lease_token=NULL,lease_until=NULL,error_code=:code,updated_at=now() WHERE article_id=:id AND lease_token=:token AND state='RUNNING'").param("id",job.id()).param("token",job.token()).param("code",code).update();release(job,code);
    });}
    private void release(Job job,String code){db.sql("UPDATE classification_runtime SET lease_token=NULL,lease_until=NULL,checked_at=now(),error_code=:code WHERE id=1 AND lease_token=:token").param("token",job.token()).param("code",code).update();}
}
