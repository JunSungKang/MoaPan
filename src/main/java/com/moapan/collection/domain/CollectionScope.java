package com.moapan.collection.domain;

import com.moapan.article.domain.ArticleInput;
import com.moapan.article.domain.ContentProcessor;
import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;

/** Immutable collection specification compiled from operator settings, not reader interests. */
public record CollectionScope(Instant publishedSince,List<String> terms) implements Predicate<ArticleInput> {
    public CollectionScope { terms=List.copyOf(terms); }
    @Override public boolean test(ArticleInput article){
        if(publishedSince!=null&&(article.publishedAt()==null||article.publishedAt().isBefore(publishedSince)))return false;
        if(terms.isEmpty())return true;
        String text=article.title()+" "+article.description();
        return terms.stream().anyMatch(term->ContentProcessor.matches(text,term));
    }
}
