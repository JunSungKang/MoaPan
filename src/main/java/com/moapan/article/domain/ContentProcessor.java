package com.moapan.article.domain;
import java.util.*;
import java.text.BreakIterator;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class ContentProcessor {
    // Only bounded operator vocabulary is retained, never article/member text.
    private static final int ALIAS_CACHE_LIMIT=256;
    private static final Map<String,Pattern> ALIAS_PATTERNS=Collections.synchronizedMap(new LinkedHashMap<>(32,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,Pattern> entry){return size()>ALIAS_CACHE_LIMIT;}
    });
    // Fixed research vocabulary is compiled once; article text is never retained in these Patterns.
    private static final Pattern[][] RESEARCH_PATTERNS=Arrays.stream(new String[][]{
        {"propose","proposed","introduce","method","approach","architecture","algorithm","framework","설계","제안","방법"},
        {"experiment","benchmark","evaluation","evaluate","results","latency","throughput","accuracy","performance","실험","성능","평가","결과"},
        {"limitation","limitations","trade-off","challenge","failure","constraint","한계","제약"},
        {"vulnerability","vulnerable","exploit","attacker","mitigation","patched","취약점","공격","패치"}
    }).map(group->Arrays.stream(group).map(ContentProcessor::phrasePattern).toArray(Pattern[]::new)).toArray(Pattern[][]::new);
    public static List<String> summary(String title,String description,boolean allowed){
        if(!allowed||description==null||description.isBlank())return List.of();
        var iterator=BreakIterator.getSentenceInstance(Locale.ROOT);iterator.setText(description);
        record Sentence(String text,int position,int score){}
        List<Sentence> candidates=new ArrayList<>();int start=iterator.first();
        for(int end=iterator.next();end!=BreakIterator.DONE;start=end,end=iterator.next()){
            String sentence=description.substring(start,end).strip();
            if(sentence.length()<25||sentence.length()>350||sentence.replaceAll("[\\p{Punct}\\s]+", "").equalsIgnoreCase(title.replaceAll("[\\p{Punct}\\s]+", "")))continue;
            int score=researchScore(sentence);
            if(score>0&&candidates.stream().noneMatch(c->c.text().equals(sentence)))candidates.add(new Sentence(sentence,start,score));
        }
        return candidates.stream().sorted(Comparator.comparingInt(Sentence::score).reversed().thenComparingInt(Sentence::position)).limit(4)
            .sorted(Comparator.comparingInt(Sentence::position)).map(Sentence::text).toList();
    }
    private static int researchScore(String text){
        String lower=text.toLowerCase(Locale.ROOT);int score=0;
        for(Pattern[] group:RESEARCH_PATTERNS)if(Arrays.stream(group).anyMatch(pattern->pattern.matcher(lower).find()))score+=2;
        return score;
    }

    private static Pattern phrasePattern(String alias){return Pattern.compile("(?iu)(?<![\\p{L}\\p{N}_])"+Pattern.quote(alias)+(alias.matches(".*[가-힣].*")?"(?:은|는|이|가|을|를|와|과|에|의|도|로|으로|에서|부터|까지)?":"")+"(?![\\p{L}\\p{N}_])");}
    public static boolean matches(String text,String alias){return (alias.length()>100?phrasePattern(alias):ALIAS_PATTERNS.computeIfAbsent(alias,ContentProcessor::phrasePattern)).matcher(text).find();}
    public static String hash(ArticleInput i){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((i.title()+"\u0000"+i.description()+"\u0000"+i.url()+"\u0000"+i.publishedAt()+"\u0000"+i.author()).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException();}}
}
