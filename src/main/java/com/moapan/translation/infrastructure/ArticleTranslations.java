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
            UUID id=(UUID)r.get("article_id");if(!hashes.get(id).equals(r.get("content_hash")))continue;
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
            db.sql("INSERT INTO article_translations(article_id,language,content_hash,title,summary_bullets,provider) VALUES(:id,:language,:hash,:title,CAST(:bullets AS jsonb),:provider) ON CONFLICT(article_id,language) DO UPDATE SET content_hash=EXCLUDED.content_hash,title=EXCLUDED.title,summary_bullets=EXCLUDED.summary_bullets,provider=EXCLUDED.provider,created_at=now()")
                .param("id",id).param("language",language).param("hash",expectedHash).param("title",text.title()).param("bullets",json.writeValueAsString(text.summaryBullets())).param("provider",provider).update();return true;
        }));
    }
}
