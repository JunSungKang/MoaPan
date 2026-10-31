package com.moapan.translation.api;

import org.springframework.jdbc.core.simple.JdbcClient;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;

@RestController @ConditionalOnWebApplication @RequestMapping("/api/v1/admin/translations")
@PreAuthorize("hasRole('ADMIN')")
public class TranslationStatusController {
    private final JdbcClient db;private final com.moapan.translation.infrastructure.ArticleTranslations translations;
    public TranslationStatusController(JdbcClient db,com.moapan.translation.infrastructure.ArticleTranslations translations){this.db=db;this.translations=translations;}
    @GetMapping("/{id}/{language}") public Object inspect(@PathVariable UUID id,@PathVariable String language){return translations.inspect(id,language);}
    @PutMapping("/{id}/{language}") public void edit(@PathVariable UUID id,@PathVariable String language,@RequestBody com.moapan.translation.infrastructure.ArticleTranslations.Manual input,org.springframework.security.core.Authentication auth){translations.manual(id,language,input,UUID.fromString(auth.getName()));}
    @GetMapping public Object status(){return Map.of(
        "runtime",db.sql("SELECT state,failures,next_probe_at,checked_at,error_code FROM translation_runtime WHERE id=1").query().singleRow(),
        "counts",db.sql("SELECT state,count(*) AS count FROM article_translation_jobs GROUP BY state ORDER BY state").query().listOfRows(),
        "failures",db.sql("SELECT error_code,count(*) AS count FROM article_translation_jobs WHERE error_code IS NOT NULL GROUP BY error_code ORDER BY count(*) DESC").query().listOfRows(),
        "recent",db.sql("SELECT article_id,state,error_code,attempts,infra_failures,updated_at FROM article_translation_jobs ORDER BY updated_at DESC LIMIT 20").query().listOfRows());}
}
