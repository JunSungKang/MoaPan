package com.moapan.classification.api;
import com.moapan.classification.application.TopicCatalog;
import com.moapan.classification.infrastructure.LocalClassifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
@RestController @ConditionalOnWebApplication @RequestMapping("/api/v1/admin/classification") @PreAuthorize("hasRole('ADMIN')")
public class ClassificationController {
    private final TopicCatalog topics;private final JdbcClient db;
    public ClassificationController(TopicCatalog topics,JdbcClient db){this.topics=topics;this.db=db;}
    @GetMapping public Object status(){return Map.of("policy",LocalClassifier.POLICY,"catalog",topics.snapshot(false),"counts",db.sql("SELECT state,count(*) AS count FROM article_classification_jobs GROUP BY state ORDER BY state").query().listOfRows(),"runtime",db.sql("SELECT checked_at,error_code,lease_until FROM classification_runtime WHERE id=1").query().singleRow(),"recent",db.sql("SELECT article_id,state,attempts,error_code,updated_at FROM article_classification_jobs ORDER BY updated_at DESC LIMIT 20").query().listOfRows());}
    @PutMapping("/topics") public Object edit(@RequestBody TopicCatalog.Edit input,Authentication auth){topics.edit(input,UUID.fromString(auth.getName()));return topics.snapshot(false);}
}
