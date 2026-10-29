package com.moapan.article.domain;
import java.time.Instant;
/** Transient collection input; implementations must not retain original text. */
public interface ArticleInput { String title(); String description(); String url(); Instant publishedAt(); String author(); }
