package com.moapan.news;
import java.util.*;
import java.text.BreakIterator;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import com.moapan.source.SafeFetch;
public final class ContentProcessor {
    public static List<String> summary(String title,String description,boolean allowed){
        if(!allowed||description==null||description.isBlank())return List.of();
        var iterator=BreakIterator.getSentenceInstance(Locale.ROOT);iterator.setText(description);List<String> bullets=new ArrayList<>();int start=iterator.first();
        for(int end=iterator.next();end!=BreakIterator.DONE&&bullets.size()<4;start=end,end=iterator.next()){
            String sentence=description.substring(start,end).strip();if(sentence.length()>=25&&sentence.length()<=350&&!sentence.replaceAll("[\\p{Punct}\\s]+", "").equalsIgnoreCase(title.replaceAll("[\\p{Punct}\\s]+", ""))&&!bullets.contains(sentence))bullets.add(sentence);
        }return bullets;
    }
    public static boolean matches(String text,String alias){return Pattern.compile("(?iu)(?<![\\p{L}\\p{N}_])"+Pattern.quote(alias)+(alias.matches(".*[가-힣].*")?"(?:은|는|이|가|을|를|와|과|에|의|도|로|으로|에서|부터|까지)?":"")+"(?![\\p{L}\\p{N}_])").matcher(text).find();}
    public static String hash(SafeFetch.Item i){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((i.title()+"\u0000"+i.description()+"\u0000"+i.url()+"\u0000"+i.publishedAt()).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException();}}
}
