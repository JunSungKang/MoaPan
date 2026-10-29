package com.moapan.classification.domain;
import java.util.*;
public final class TopicTaxonomy { private TopicTaxonomy() {}
    public record Topic(UUID id,String slug,String name,String definition){}
    public record Snapshot(long version,List<Topic> topics,String hash){}
}
