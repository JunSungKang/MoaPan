package com.moapan.translation.infrastructure;


import com.moapan.shared.api.ApiError;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class ArticleTranslations {
    public record Original(UUID id,String title,List<String> summaryBullets) {}
    public record Text(String title,List<String> summaryBullets) {}
    private final JdbcClient db;
    private final TransactionTemplate tx;
    private final JsonMapper json=JsonMapper.builder().build();
    public ArticleTranslations(JdbcClient db,PlatformTransactionManager manager){this.db=db;tx=new TransactionTemplate(manager);}
    public static String contentHash(String title,List<String> bullets){return com.moapan.article.domain.ArticleFingerprint.contentHash(title,bullets);}
    public Map<UUID,Map<String,Text>> read(List<Original> articles){
        Map<UUID,Map<String,Text>> result=new HashMap<>();if(articles.isEmpty())return result;
        Map<UUID,String> hashes=new HashMap<>();for(var a:articles)hashes.put(a.id(),contentHash(a.title(),a.summaryBullets()));
        for(var r:db.sql("SELECT t.* FROM article_translations t JOIN articles a ON a.id=t.article_id JOIN sources s ON s.id=a.source_id WHERE t.article_id IN (:ids) AND a.public AND s.allow_display AND s.allow_summary AND s.allow_translation AND s.rights_status IN ('APPROVED','RESTRICTED')").param("ids",hashes.keySet()).query().listOfRows()){
            UUID id=(UUID)r.get("article_id");if(!hashes.get(id).equals(r.get("content_hash"))||!Boolean.TRUE.equals(r.get("locked"))&&!com.moapan.translation.application.TranslationWorker.PROVIDER.equals(r.get("provider")))continue;
            result.computeIfAbsent(id,k->new HashMap<>()).put(r.get("language").toString(),new Text(r.get("title").toString(),List.of(json.readValue(r.get("summary_bullets").toString(),String[].class))));
        }
        return result;
    }
    public boolean store(UUID id,String expectedHash,String language,Text text,String provider){
        if(!Set.of("ko","en").contains(language)||text==null||text.title()==null||text.title().isBlank()||text.title().length()>1000||text.summaryBullets()==null||text.summaryBullets().size()>4||text.summaryBullets().stream().anyMatch(s->s==null||s.isBlank()||s.length()>1500)||provider==null||provider.isBlank()||provider.length()>160)throw new ApiError(400,"invalid_translation");
        return Boolean.TRUE.equals(tx.execute(status->{
            var permitted=db.sql("SELECT s.id FROM sources s JOIN articles a ON a.source_id=s.id WHERE a.id=:id AND s.allow_display AND s.allow_summary AND s.allow_translation AND s.rights_status IN ('APPROVED','RESTRICTED') FOR SHARE OF s").param("id",id).query(UUID.class).list();if(permitted.isEmpty())return false;
            var rows=db.sql("SELECT title,summary_bullets FROM articles WHERE id=:id AND public FOR UPDATE").param("id",id).query().listOfRows();if(rows.isEmpty())return false;var row=rows.getFirst();var original=List.of(json.readValue(row.get("summary_bullets").toString(),String[].class));
            if(!contentHash(row.get("title").toString(),original).equals(expectedHash)||original.size()!=text.summaryBullets().size())return false;
            return db.sql("INSERT INTO article_translations(article_id,language,content_hash,title,summary_bullets,provider) VALUES(:id,:language,:hash,:title,CAST(:bullets AS jsonb),:provider) ON CONFLICT(article_id,language) DO UPDATE SET content_hash=EXCLUDED.content_hash,title=EXCLUDED.title,summary_bullets=EXCLUDED.summary_bullets,provider=EXCLUDED.provider,created_at=now(),version=article_translations.version+1 WHERE NOT article_translations.locked")
                .param("id",id).param("language",language).param("hash",expectedHash).param("title",text.title()).param("bullets",json.writeValueAsString(text.summaryBullets())).param("provider",provider).update()==1;
        }));
    }
    public record Manual(long version,String contentHash,String title,List<String> summaryBullets,boolean locked){}
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public Object inspect(UUID id,String language){
        if(!Set.of("ko","en").contains(language))throw new ApiError(400,"invalid_translation");
        var rows=db.sql("SELECT a.id,a.title AS original_title,a.summary_bullets AS original_summary,t.title,t.summary_bullets,t.locked,t.version FROM articles a LEFT JOIN article_translations t ON t.article_id=a.id AND t.language=:language WHERE a.id=:id").param("id",id).param("language",language).query().listOfRows();
        if(rows.isEmpty())throw new ApiError(404,"article_not_found");var r=rows.getFirst();var b=List.of(json.readValue(r.get("original_summary").toString(),String[].class));
        return Map.of("contentHash",contentHash(r.get("original_title").toString(),b),"version",r.get("version")==null?-1L:r.get("version"),"title",r.get("title")==null?"":r.get("title"),"summaryBullets",r.get("summary_bullets")==null?List.of():List.of(json.readValue(r.get("summary_bullets").toString(),String[].class)),"locked",Boolean.TRUE.equals(r.get("locked")));
    }
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public void manual(UUID id,String language,Manual input,UUID actor){
        if(input==null||input.contentHash()==null||!input.contentHash().matches("[a-f0-9]{64}")||!Set.of("ko","en").contains(language)||input.title()==null||input.title().isBlank()||input.title().length()>1000||input.summaryBullets()==null||input.summaryBullets().size()>4||input.summaryBullets().stream().anyMatch(s->s==null||s.isBlank()||s.length()>1500))throw new ApiError(400,"invalid_translation");
        tx.executeWithoutResult(status->{
            var source=db.sql("SELECT s.id FROM sources s JOIN articles a ON a.source_id=s.id WHERE a.id=:id AND s.allow_display AND s.allow_summary AND s.allow_translation AND s.rights_status IN ('APPROVED','RESTRICTED') FOR SHARE OF s").param("id",id).query(UUID.class).list();if(source.isEmpty())throw new ApiError(409,"translation_not_permitted");
            var rows=db.sql("SELECT title,summary_bullets FROM articles WHERE id=:id FOR UPDATE").param("id",id).query().listOfRows();if(rows.isEmpty())throw new ApiError(404,"article_not_found");var row=rows.getFirst();var bullets=List.of(json.readValue(row.get("summary_bullets").toString(),String[].class));
            if(!input.contentHash().equals(contentHash(row.get("title").toString(),bullets))||bullets.size()!=input.summaryBullets().size())throw new ApiError(409,"translation_content_changed");
            var existing=db.sql("SELECT version FROM article_translations WHERE article_id=:id AND language=:language FOR UPDATE").param("id",id).param("language",language).query(Long.class).list();
            if((existing.isEmpty()?-1:existing.getFirst())!=input.version())throw new ApiError(409,"translation_version_conflict");
            db.sql("INSERT INTO article_translations(article_id,language,content_hash,title,summary_bullets,provider,locked,version) VALUES(:id,:language,:hash,:title,CAST(:bullets AS jsonb),:provider,:locked,0) ON CONFLICT(article_id,language) DO UPDATE SET content_hash=EXCLUDED.content_hash,title=EXCLUDED.title,summary_bullets=EXCLUDED.summary_bullets,provider=EXCLUDED.provider,locked=EXCLUDED.locked,version=article_translations.version+1,created_at=now()")
                .param("id",id).param("language",language).param("hash",input.contentHash()).param("title",input.title()).param("bullets",json.writeValueAsString(input.summaryBullets())).param("provider",input.locked()?"MANUAL":com.moapan.translation.application.TranslationWorker.PROVIDER).param("locked",input.locked()).update();
            db.sql("UPDATE article_translation_jobs SET state='CANCELLED',progress='{}',lease_token=NULL,lease_until=NULL,updated_at=now() WHERE article_id=:id").param("id",id).update();
            db.sql("INSERT INTO admin_actions VALUES(:action,:actor,'translation_edit',:id,now())").param("action",UUID.randomUUID()).param("actor",actor).param("id",id).update();
        });
    }
}
