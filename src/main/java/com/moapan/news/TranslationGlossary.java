package com.moapan.news;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/** Versioned, local terminology. Source matching never uses member preferences. */
public final class TranslationGlossary {
    public static final String VERSION="glossary-v1";
    private record Term(String english,String korean) {}
    private static final List<Term> TERMS=load();
    private TranslationGlossary() {}
    private static List<Term> load(){
        try(var stream=Objects.requireNonNull(TranslationGlossary.class.getResourceAsStream("/translation/glossary.tsv"));
            var reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){
            return reader.lines().filter(s->!s.isBlank()&&!s.startsWith("#")).map(s->{
                String[] pair=s.split("\t",-1);
                if(pair.length!=2||pair[0].isBlank()||pair[1].isBlank())throw new IllegalStateException("invalid_translation_glossary");
                return new Term(pair[0],pair[1]);
            }).toList();
        }catch(IOException e){throw new IllegalStateException("translation_glossary_unavailable");}
    }
    public static String matching(String input,String language){
        if(!Set.of("ko","en").contains(language))throw new IllegalArgumentException("invalid_translation_language");
        // Stable order prefers the first canonical English form when Korean aliases overlap.
        Map<String,String> matched=new LinkedHashMap<>();
        for(var term:TERMS){
            String from=language.equals("ko")?term.english():term.korean();
            String to=language.equals("ko")?term.korean():term.english();
            if(ContentProcessor.matches(input,from))matched.putIfAbsent(from,to);
        }
        return matched.entrySet().stream().limit(24).map(e->e.getKey()+"="+e.getValue()).collect(Collectors.joining("; "));
    }
}
