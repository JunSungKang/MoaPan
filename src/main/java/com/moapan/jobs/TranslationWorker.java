package com.moapan.jobs;

import com.moapan.news.*;
import com.moapan.site.CatalogRepository;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

@Component
public class TranslationWorker {
    public static final String PROVIDER="IndexTeam/Index-Translate-2B-Q8_0@3a0eca6ce931d88d00614d427ab3ed8ff0743902/"+TranslationGlossary.VERSION;
    private final CatalogRepository repo;
    private final LocalTranslator translator;
    private final ArticleTranslations translations;
    private final TransactionTemplate tx;
    private final JsonMapper json=JsonMapper.builder().build();
    public TranslationWorker(CatalogRepository repo,LocalTranslator translator,ArticleTranslations translations,PlatformTransactionManager manager){this.repo=repo;this.translator=translator;this.translations=translations;tx=new TransactionTemplate(manager);}
    @Scheduled(fixedDelay=1000)
    public void tick(){
        if(!"worker".equals(System.getenv("MOAPAN_ROLE"))||!translator.enabled())return;
        try{processOne();}catch(Exception ignored){/* No input/output or credentials in logs; retry next tick. */}
    }
    public Map<String,Object> claim(){return tx.execute(status->{
        repo.db().sql("INSERT INTO article_translation_jobs(article_id) SELECT a.id FROM articles a JOIN sources s ON s.id=a.source_id WHERE a.public AND a.language IN ('en','ko') AND s.allow_display AND s.allow_summary AND s.allow_translation AND s.rights_status IN ('APPROVED','RESTRICTED') AND NOT EXISTS(SELECT 1 FROM article_translations t WHERE t.article_id=a.id AND t.language=CASE WHEN a.language='en' THEN 'ko' ELSE 'en' END) ON CONFLICT DO NOTHING").update();
        var rows=repo.db().sql("SELECT j.article_id,a.title,a.summary_bullets,a.language FROM article_translation_jobs j JOIN articles a ON a.id=j.article_id JOIN sources s ON s.id=a.source_id WHERE a.public AND s.allow_display AND s.allow_summary AND s.allow_translation AND s.rights_status IN ('APPROVED','RESTRICTED') AND j.attempts<3 AND j.available_at<=now() AND (j.lease_until IS NULL OR j.lease_until<now()) AND NOT EXISTS(SELECT 1 FROM article_translations t WHERE t.article_id=a.id AND t.language=CASE WHEN a.language='en' THEN 'ko' ELSE 'en' END) ORDER BY COALESCE(a.published_at,a.collected_at) DESC,a.id LIMIT 1 FOR UPDATE OF j SKIP LOCKED").query().listOfRows();
        if(rows.isEmpty())return null;var row=rows.getFirst();UUID token=UUID.randomUUID();
        repo.db().sql("UPDATE article_translation_jobs SET attempts=attempts+1,lease_token=:token,lease_until=now()+interval '10 minutes' WHERE article_id=:id").param("token",token).param("id",row.get("article_id")).update();row.put("token",token);return row;
    });}
    public boolean processOne(){
        var job=claim();if(job==null)return false;UUID id=(UUID)job.get("article_id");
        try{
            String original=job.get("title").toString();var bullets=List.of(json.readValue(job.get("summary_bullets").toString(),String[].class));String language=job.get("language").equals("en")?"ko":"en";
            String title=translator.translate(original,language);List<String> result=new ArrayList<>();for(String bullet:bullets)result.add(translator.translate(bullet,language));
            tx.executeWithoutResult(status->{
                // Lock source/article before job, matching invalidation trigger ordering.
                repo.db().sql("SELECT s.id FROM sources s JOIN articles a ON a.source_id=s.id WHERE a.id=:id FOR SHARE OF s").param("id",id).query(UUID.class).list();
                repo.db().sql("SELECT id FROM articles WHERE id=:id FOR UPDATE").param("id",id).query(UUID.class).list();
                var live=repo.db().sql("SELECT article_id FROM article_translation_jobs WHERE article_id=:id AND lease_token=:token AND lease_until>now() FOR UPDATE").param("id",id).param("token",job.get("token")).query(UUID.class).list();
                if(live.isEmpty())return;
                boolean stored=translations.store(id,ArticleTranslations.contentHash(original,bullets),language,new ArticleTranslations.Text(title,result),PROVIDER);
                repo.db().sql("UPDATE article_translation_jobs SET lease_until=NULL,error_code=:code,available_at=now()+interval '10 minutes' WHERE article_id=:id AND lease_token=:token").param("id",id).param("token",job.get("token")).param("code",stored?null:"translation_stale").update();
            });
        }catch(Exception error){
            repo.db().sql("UPDATE article_translation_jobs SET lease_until=NULL,error_code='translation_failed',available_at=now()+interval '10 minutes' WHERE article_id=:id AND lease_token=:token").param("id",id).param("token",job.get("token")).update();
        }
        return true;
    }
}
