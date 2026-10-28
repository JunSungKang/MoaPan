package com.moapan.news;

import com.moapan.ApiError;
import com.moapan.security.Limits;
import com.moapan.site.CatalogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.*;
import java.util.*;

/** Search input is transient: never stored, cached, or included in error messages. */
@Service
public class NewsSearch {
    public record Input(String search,String category,LocalDate day,int page) {}
    public record Result(List<NewsService.ArticleView> items,int page,int pages,int total) {}
    private final CatalogRepository repo;
    private final NewsService news;
    private final Limits limits;
    private final TransactionTemplate tx;
    public NewsSearch(CatalogRepository repo,NewsService news,Limits limits,PlatformTransactionManager tm){
        this.repo=repo;this.news=news;this.limits=limits;this.tx=new TransactionTemplate(tm);
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    public Result search(Input input,UUID user){
        if(input==null||input.page()<0||input.page()>=20||input.search()==null||input.search().length()>100||input.category()==null||input.category().length()>80||input.search().codePoints().anyMatch(Character::isISOControl)||input.day()!=null&&(input.day().getYear()<1970||input.day().getYear()>9999))throw new ApiError(400,"invalid_search");
        limits.auth("news_search",120,60);
        UUID lease=user==null?null:limits.beginNews(user);
        try {
            Result result=tx.execute(status->{
                String term=input.search().strip().toLowerCase(Locale.ROOT);
                String pattern="%"+term.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
                String where=" FROM articles a JOIN sources s ON s.id=a.source_id JOIN sites p ON p.id=s.site_id WHERE a.public AND s.allow_display AND s.rights_status IN ('APPROVED','RESTRICTED') AND COALESCE(a.published_at,a.collected_at)<=now()";
                Map<String,Object> params=new HashMap<>();
                if(!term.isEmpty()){
                    where+=" AND (lower(a.title) LIKE :term ESCAPE '!' OR (s.allow_summary AND lower(a.summary_bullets::text) LIKE :term ESCAPE '!') OR EXISTS(SELECT 1 FROM article_tags at JOIN tags t ON t.id=at.tag_id WHERE at.article_id=a.id AND t.active AND (lower(t.name) LIKE :term ESCAPE '!' OR lower(t.slug) LIKE :term ESCAPE '!')))";
                    params.put("term",pattern);
                }
                if(!input.category().isEmpty()){
                    where+=" AND EXISTS(SELECT 1 FROM article_tags at JOIN tags t ON t.id=at.tag_id WHERE at.article_id=a.id AND t.active AND t.category=:category)";
                    params.put("category",input.category());
                }
                if(input.day()!=null){
                    where+=" AND COALESCE(a.published_at,a.collected_at)>=:start AND COALESCE(a.published_at,a.collected_at)<:end";
                    params.put("start",OffsetDateTime.ofInstant(input.day().atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),ZoneOffset.UTC));
                    params.put("end",OffsetDateTime.ofInstant(input.day().plusDays(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),ZoneOffset.UTC));
                }
                int total=repo.db().sql("SELECT count(*) FROM (SELECT a.id"+where+" LIMIT 140) bounded").params(params).query(Integer.class).single();
                params.put("offset",input.page()*7);
                var rows=repo.db().sql("SELECT a.id,a.title,a.author,CASE WHEN s.allow_summary THEN a.summary_bullets::text ELSE '[]' END summary_bullets,CASE WHEN s.allow_summary THEN a.summary_mode ELSE 'INSUFFICIENT' END summary_mode,a.published_at,a.collected_at,a.canonical_url,s.id source_id,s.label source_name,s.language,p.name site_name"+where+" ORDER BY COALESCE(a.published_at,a.collected_at) DESC,a.id LIMIT 7 OFFSET :offset").params(params).query().listOfRows();
                return new Result(news.articleViews(rows),input.page(),(total+6)/7,total);
            });
            if(user!=null)limits.items(user,result.items().size());
            return result;
        }finally{if(lease!=null)limits.endNews(lease);}
    }
}
