package com.moapan.classification.application;
import java.util.*;
import com.moapan.classification.domain.TopicTaxonomy;
public interface ClassificationModel {
 boolean enabled();
 Set<UUID> classify(String title,List<String> summary,TopicTaxonomy.Snapshot topics) throws Exception;
}
