SELECT
    (SELECT count(*) FROM article_translations
        WHERE NOT locked AND provider IS DISTINCT FROM :old_provider)
  + (SELECT count(*) FROM article_translation_jobs
        WHERE policy_version IS DISTINCT FROM :old_translation_policy)
  + (SELECT count(*) FROM article_classification_jobs
        WHERE model_policy IS DISTINCT FROM :old_classification_model)
