package com.moapan.news;

import com.moapan.site.CatalogRepository;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;

@RestController @ConditionalOnWebApplication @RequestMapping("/api/v1/admin/translations")
@PreAuthorize("hasRole('ADMIN')")
public class TranslationStatusController {
    private final CatalogRepository repo;
    public TranslationStatusController(CatalogRepository repo){this.repo=repo;}
    @GetMapping public Object status(){return Map.of(
        "runtime",repo.db().sql("SELECT state,failures,next_probe_at,checked_at,error_code FROM translation_runtime WHERE id=1").query().singleRow(),
        "counts",repo.db().sql("SELECT state,count(*) AS count FROM article_translation_jobs GROUP BY state ORDER BY state").query().listOfRows(),
        "failures",repo.db().sql("SELECT error_code,count(*) AS count FROM article_translation_jobs WHERE error_code IS NOT NULL GROUP BY error_code ORDER BY count(*) DESC").query().listOfRows(),
        "recent",repo.db().sql("SELECT article_id,state,error_code,attempts,infra_failures,updated_at FROM article_translation_jobs ORDER BY updated_at DESC LIMIT 20").query().listOfRows());}
}
