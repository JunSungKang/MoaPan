package com.moapan.newsletter.domain;

import java.util.List;

/** Portable manuscript value model; missing legacy language is unknown, never guessed. */
public record Manuscript(String title,String introduction,String topic,List<Block> blocks,String language) {
    public Manuscript { if(language==null)language="und"; }
    public Manuscript(String title,String introduction,String topic,List<Block> blocks){this(title,introduction,topic,blocks,"und");}
    public record Block(String type,String text,String url,String caption,Integer width,Integer height) {
        public Block(String type,String text,String url,String caption){this(type,text,url,caption,null,null);}
    }
}
