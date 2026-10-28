package com.moapan.jobs;
import com.moapan.*;
import com.moapan.site.*;
import com.moapan.source.SafeFetch;
import com.moapan.news.ContentProcessor;
import java.util.*;
import java.time.*;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

@Component
public class Worker {
    public record Counts(int created,int updated){}
    private final CatalogRepository repo;private final SafeFetch fetch;private final TransactionTemplate tx;private final String worker=UUID.randomUUID().toString();private final JsonMapper json=JsonMapper.builder().build();
    public Worker(CatalogRepository repo,SafeFetch fetch,PlatformTransactionManager tm){this.repo=repo;this.fetch=fetch;tx=new TransactionTemplate(tm);}
    @Scheduled(fixedDelay=5000)public void tick(){
        if(!"worker".equals(System.getenv("MOAPAN_ROLE")))return;
        repo.db().sql("INSERT INTO worker_status VALUES(:id,now()) ON CONFLICT(id) DO UPDATE SET heartbeat_at=now()").param("id",worker).update();
        recover();cleanup();schedule();processOne();
    }
    public void processOne(){var job=claim();if(job==null)return;
        UUID id=(UUID)job.get("id"), sourceId=(UUID)job.get("source_id");UUID run=UUID.randomUUID();
        repo.db().sql("INSERT INTO fetch_runs(id,source_id,result) VALUES(:id,:source,'RUNNING')").param("id",run).param("source",sourceId).update();
        try {var source=repo.source(sourceId,false);CatalogService.runnable(repo.site((UUID)source.get("site_id"),false),source);var result=fetch.read(source);Counts counts=apply(job,source,result);repo.db().sql("UPDATE fetch_runs SET result=:result,new_count=:new,updated_count=:changed,finished_at=now() WHERE id=:id").param("id",run).param("result",counts.created()+counts.updated()==0?"NO_CHANGE":"SUCCEEDED").param("new",counts.created()).param("changed",counts.updated()).update();}
        catch(Exception error){String code=error instanceof SafeFetch.FetchFailure?"source_retryable":error instanceof ApiError?error.getMessage():"worker_failed";fail(job,error,code);repo.db().sql("UPDATE fetch_runs SET result='FAILED',error_code=:code,finished_at=now() WHERE id=:id").param("code",code).param("id",run).update();}
        cleanup();
    }
    public void recover(){tx.executeWithoutResult(t->{repo.db().sql("UPDATE fetch_runs SET result='FAILED',error_code='worker_lease_expired',finished_at=now() WHERE result='RUNNING' AND source_id IN (SELECT source_id FROM content_jobs WHERE status='RUNNING' AND lease_until<now())").update();repo.db().sql("UPDATE content_jobs SET status=CASE WHEN attempts>=3 THEN 'FAILED' ELSE 'QUEUED' END,available_at=now()+interval '30 seconds',lease_until=NULL,lease_token=NULL,error_code='lease_expired' WHERE status='RUNNING' AND lease_until<now()").update();});}
    public void schedule(){repo.db().sql("INSERT INTO content_jobs(id,source_id,status,site_version,source_version) SELECT gen_random_uuid(),s.id,'QUEUED',p.version,s.version FROM sources s JOIN sites p ON p.id=s.site_id WHERE p.status='ACTIVE' AND s.enabled AND s.archived_at IS NULL AND s.validation_status='PASSED' AND s.rights_status IN ('APPROVED','RESTRICTED') AND s.allow_metadata AND s.allow_display AND s.next_run_at<=now() AND (s.retry_after IS NULL OR s.retry_after<=now()) ON CONFLICT DO NOTHING").update();}
    public Map<String,Object> claim(){return tx.execute(status->{var rows=repo.db().sql("SELECT * FROM content_jobs WHERE status='QUEUED' AND available_at<=now() ORDER BY available_at,id FOR UPDATE SKIP LOCKED LIMIT 1").query().listOfRows();if(rows.isEmpty())return null;var job=rows.getFirst();UUID token=UUID.randomUUID();repo.db().sql("UPDATE content_jobs SET status='RUNNING',attempts=attempts+1,lease_until=now()+interval '2 minutes',lease_token=:token WHERE id=:id").param("id",job.get("id")).param("token",token).update();job.put("lease_token",token);job.put("attempts",((Number)job.get("attempts")).intValue()+1);return job;});}
    public Counts apply(Map<String,Object> job,Map<String,Object> snapshot,SafeFetch.Result result){return tx.execute(status->{
        var site=repo.site((UUID)snapshot.get("site_id"),true);var source=repo.source((UUID)snapshot.get("id"),true);CatalogService.runnable(site,source);
        if(!Objects.equals(site.get("version"),job.get("site_version"))||!Objects.equals(source.get("version"),job.get("source_version")))throw new ApiError(409,"stale_job");
        int live=repo.db().sql("SELECT count(*) FROM content_jobs WHERE id=:id AND status='RUNNING' AND lease_token=:token AND lease_until>now()").param("id",job.get("id")).param("token",job.get("lease_token")).query(Integer.class).single();if(live!=1)throw new ApiError(409,"job_cancelled");
        var aliases=repo.db().sql("SELECT a.alias,a.tag_id FROM tag_aliases a JOIN tags t ON t.id=a.tag_id WHERE t.active ORDER BY length(a.alias) DESC").query().listOfRows();
        int created=0,updated=0;for(var item:result.items()){if(!com.moapan.source.CollectionFilter.accepts(source,item))continue;int change=save(source,item,aliases);if(change==1)created++;if(change==2)updated++;}
        repo.db().sql("UPDATE sources SET etag=COALESCE(:etag,etag),last_modified=COALESCE(:modified,last_modified),next_run_at=now()+make_interval(secs=>interval_seconds),retry_after=NULL WHERE id=:id").param("id",source.get("id")).param("etag",result.etag()).param("modified",result.modified()).update();
        repo.db().sql("UPDATE content_jobs SET status='SUCCEEDED',finished_at=now(),lease_until=NULL WHERE id=:id AND lease_token=:token").param("id",job.get("id")).param("token",job.get("lease_token")).update();
        return new Counts(created,updated);
    });}
    private int save(Map<String,Object>s,SafeFetch.Item item,List<Map<String,Object>>aliases){
        var old=repo.db().sql("SELECT * FROM articles WHERE source_id=:source AND (source_guid=:guid OR canonical_url=:url) FOR UPDATE").param("source",s.get("id")).param("guid",item.guid()).param("url",item.url()).query().listOfRows();String hash=ContentProcessor.hash(item);if(!old.isEmpty()&&hash.equals(old.getFirst().get("input_hash")))return 0;
        boolean isNew=old.isEmpty();var previous=isNew?Map.<String,Object>of():old.getFirst();UUID id=isNew?UUID.randomUUID():(UUID)previous.get("id");var bullets=ContentProcessor.summary(item.title(),item.description(),(boolean)s.get("allow_summary"));String summary=json.writeValueAsString(bullets);String mode=bullets.isEmpty()?"INSUFFICIENT":"EXTRACTIVE_ORIGINAL_LANGUAGE";
        if(isNew)repo.db().sql("INSERT INTO articles(id,source_id,source_guid,canonical_url,title,summary_bullets,summary_mode,language,input_hash,published_at) VALUES(:id,:source,:guid,:url,:title,CAST(:summary AS jsonb),:mode,:language,:hash,:published)").param("id",id).param("source",s.get("id")).param("guid",item.guid()).param("url",item.url()).param("title",item.title()).param("summary",summary).param("mode",mode).param("language",s.get("language")).param("hash",hash).param("published",item.publishedAt()==null?null:item.publishedAt().atOffset(ZoneOffset.UTC)).update();
        else{
            repo.db().sql("INSERT INTO article_revisions(id,article_id,input_hash,change_kind) VALUES(:rev,:id,:hash,'SOURCE_CHANGED')").param("rev",UUID.randomUUID()).param("id",id).param("hash",previous.get("input_hash")).update();
            repo.db().sql("UPDATE articles SET title=:title,summary_bullets=CASE WHEN summary_locked THEN summary_bullets ELSE CAST(:summary AS jsonb) END,summary_mode=CASE WHEN summary_locked THEN summary_mode ELSE :mode END,input_hash=:hash,published_at=:published,updated_at=now() WHERE id=:id").param("id",id).param("title",item.title()).param("summary",summary).param("mode",mode).param("hash",hash).param("published",item.publishedAt()==null?null:item.publishedAt().atOffset(ZoneOffset.UTC)).update();
        }
        if(Boolean.TRUE.equals(previous.get("tags_locked")))return 2;
        repo.db().sql("DELETE FROM article_tags WHERE article_id=:id AND NOT locked").param("id",id).update();Set<UUID> tagged=new HashSet<>();
        for(var alias:aliases){UUID tag=(UUID)alias.get("tag_id");if(tagged.size()>=6)break;if(ContentProcessor.matches(item.title()+" "+item.description(),alias.get("alias").toString())&&tagged.add(tag))repo.db().sql("INSERT INTO article_tags VALUES(:id,:tag,1,'RULE',false) ON CONFLICT DO NOTHING").param("id",id).param("tag",tag).update();}
        return isNew?1:2;
    }
    private void fail(Map<String,Object>job,Exception error,String code){Instant retry=Instant.now().plusSeconds(30L*(1L<<Math.min(3,((Number)job.get("attempts")).intValue())));if(error instanceof SafeFetch.FetchFailure f&&f.retry!=null){try{retry=Instant.now().plusSeconds(Math.max(0,Long.parseLong(f.retry)));}catch(Exception ignored){try{retry=ZonedDateTime.parse(f.retry,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();}catch(Exception ignoredAgain){}}}
        Instant when=retry;tx.executeWithoutResult(t->{repo.db().sql("UPDATE sources SET retry_after=:retry,next_run_at=GREATEST(next_run_at,:retry) WHERE id=:id").param("id",job.get("source_id")).param("retry",when.atOffset(ZoneOffset.UTC)).update();repo.db().sql("UPDATE content_jobs SET status=:s,error_code=:code,available_at=:retry,lease_until=NULL,finished_at=CASE WHEN :s='FAILED' THEN now() ELSE NULL END WHERE id=:id AND status='RUNNING' AND lease_token=:token").param("s",error instanceof ApiError||((Number)job.get("attempts")).intValue()>=3?"FAILED":"QUEUED").param("code",code).param("retry",when.atOffset(ZoneOffset.UTC)).param("id",job.get("id")).param("token",job.get("lease_token")).update();});
    }
    public void cleanup(){repo.db().sql("DELETE FROM auth_rate_limits WHERE expires_at<now()").update();repo.db().sql("DELETE FROM api_quotas WHERE expires_at<now()").update();repo.db().sql("DELETE FROM api_request_leases WHERE expires_at<now()").update();repo.db().sql("DELETE FROM news_editions WHERE expires_at<now()-interval '1 day'").update();repo.db().sql("DELETE FROM content_jobs WHERE finished_at<now()-interval '30 days'").update();repo.db().sql("DELETE FROM fetch_runs WHERE finished_at<now()-interval '30 days'").update();repo.db().sql("DELETE FROM worker_status WHERE heartbeat_at<now()-interval '1 day'").update();}
}
