package com.moapan.news;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;

/** Deterministic partial checks, not a proof of translation equivalence. */
public final class TranslationValidator {
    public static final String VERSION="rules-v2";
    private TranslationValidator() {}
    private static final Pattern IDENTIFIERS=Pattern.compile("`[^`]+`|https?://[^\\s<>]+|(?i:CVE-\\d{4}-\\d+|GHSA-[a-z0-9-]+)|(?<![\\w.])v?\\d+\\.\\d+\\.\\d+(?:[-+][\\w.-]+)?");
    private static final Pattern NUMBER=Pattern.compile("(?<![\\d.])[-+]?\\d+(?:,\\d{3})*(?:\\.\\d+)?(?:[eE][-+]?\\d+)?(?:\\s*(?:million|billion|thousand|백만|천만|억|만|천))?(?:\\s*(?:percentage points?|percent|%p|%|milliseconds?|seconds?|minutes?|hours?|ms|Mbps|Gbps|MiB|GiB|KiB|MB|GB|KB|밀리초|초|분|시간|퍼센트포인트|퍼센트))?",Pattern.CASE_INSENSITIVE);
    public static void validate(String input,String output,String language){
        if(output==null||output.isBlank()||output.length()>1500||output.contains("<think>")||output.contains("</think>")||output.contains("[im_start]")||output.contains("__MP")||output.indexOf('\u0000')>=0)fail("translation_invalid");
        if(language.equals("ko")&&!Pattern.compile("[가-힣]").matcher(output).find()&&!(input.equals(output)&&IDENTIFIERS.matcher(input).replaceAll("").matches("[\\p{Punct}\\s0-9]*")))fail("translation_wrong_language");
        if(language.equals("en")&&Pattern.compile("[가-힣]").matcher(output).find())fail("translation_wrong_language");
        if(!identifiers(input).equals(identifiers(output)))fail("translation_identifier_changed");
        if(!facts(input).equals(facts(output)))fail("translation_numbers_changed");
        if(!comparisons(input,language.equals("ko")?"en":"ko").equals(comparisons(output,language)))fail("translation_comparison_changed");
        // Only conservative, explicitly supported danger signals; arbitrary semantics remain unverified.
        if(negative(input,language.equals("ko")?"en":"ko")!=negative(output,language))fail("translation_negation_changed");
        if(increases(input,language.equals("ko")?"en":"ko")!=increases(output,language)||decreases(input,language.equals("ko")?"en":"ko")!=decreases(output,language))fail("translation_direction_changed");
        String[][] critical={{"median","중앙값"},{"throughput","처리량"},{"snapshot isolation","스냅샷 격리"},{"without authentication","인증 없이"}};
        for(var pair:critical){String from=language.equals("ko")?pair[0]:pair[1],to=language.equals("ko")?pair[1]:pair[0];if(ContentProcessor.matches(input,from)&&!ContentProcessor.matches(output,to))fail("translation_term_changed");}
    }
    private static List<String> comparisons(String text,String lang){
        String clean=IDENTIFIERS.matcher(text).replaceAll(" ");
        Pattern p=Pattern.compile(lang.equals("en")?"(?iu)<=|>=|±|<|>|\\bat least\\b|\\bat most\\b|\\bless than\\b|\\bmore than\\b":"<=|>=|±|<|>|이상|이하|미만|초과");
        return p.matcher(clean).results().map(m->switch(m.group().toLowerCase(Locale.ROOT)){
            case "at least","이상",">=" -> "GE"; case "at most","이하","<=" -> "LE";
            case "less than","미만","<" -> "LT"; case "more than","초과",">" -> "GT"; default -> "TOLERANCE";
        }).toList();
    }
    private static void fail(String code){throw new TranslationFailure(code,false);}
    private static List<String> identifiers(String s){return IDENTIFIERS.matcher(s).results().map(MatchResult::group).sorted().toList();}
    private static boolean negative(String s,String lang){return Pattern.compile(lang.equals("en")?"(?iu)\\b(?:not|no|never|cannot|without|neither)\\b|n't\\b":"없|않|아니|못하|불가능|불필요|없이").matcher(s).find();}
    private static boolean increases(String s,String lang){return Pattern.compile(lang.equals("en")?"(?iu)\\b(?:increas(?:e|es|ed|ing)|rose|risen|rises?|higher)\\b":"증가|상승|높아|늘었|늘어").matcher(s).find();}
    private static boolean decreases(String s,String lang){return Pattern.compile(lang.equals("en")?"(?iu)\\b(?:decreas(?:e|es|ed|ing)|reduc(?:e|es|ed|ing)|fell|fallen|falls?|lower)\\b":"감소|하락|낮아|줄었|줄어|줄이|줄였|단축").matcher(s).find();}
    private static String normalizeWords(String s){return s.replaceAll("(?iu)\\b(?:a|an|one)\\s+(minute|second|hour)\\b","1 $1").replaceAll("(?iu)\\btwo\\s+(minutes?|seconds?|hours?)\\b","2 $1").replaceAll("(?iu)\\bthree\\s+(minutes?|seconds?|hours?)\\b","3 $1").replaceAll("한\\s*(분|초|시간)","1$1");}
    public static List<String> facts(String text){
        String s=normalizeWords(IDENTIFIERS.matcher(text).replaceAll(" "));List<String> result=new ArrayList<>();
        for(var m:NUMBER.matcher(s).results().toList()){
            String raw=m.group().replace(",","").strip();Matcher value=Pattern.compile("[-+]?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?").matcher(raw);value.find();BigDecimal n=new BigDecimal(value.group());String unit=raw.substring(value.end()).strip();
            String[] magnitudes={"thousand","million","billion","천만","백만","억","만","천"};long[] factors={1000,1000000,1000000000,10000000,1000000,100000000,10000,1000};
            for(int i=0;i<magnitudes.length;i++)if(unit.toLowerCase(Locale.ROOT).startsWith(magnitudes[i])){n=n.multiply(BigDecimal.valueOf(factors[i]));unit=unit.substring(magnitudes[i].length()).strip();break;}
            String category=unit;
            switch(unit.toLowerCase(Locale.ROOT)){
                case "ms","millisecond","milliseconds","밀리초" -> {n=n.movePointLeft(3);category="seconds";}
                case "second","seconds","초" -> category="seconds";
                case "minute","minutes","분" -> {n=n.multiply(BigDecimal.valueOf(60));category="seconds";}
                case "hour","hours","시간" -> {n=n.multiply(BigDecimal.valueOf(3600));category="seconds";}
                case "percent","퍼센트","%" -> category="percent";
                case "percentage point","percentage points","%p","퍼센트포인트" -> category="percentage-points";
                default -> { /* Bytes/bits and product-specific units remain case-sensitive. */ }
            }
            result.add(n.stripTrailingZeros().toPlainString()+":"+category);
        }
        // Ordered facts retain changes to from/to, bounds and the direction of numerical comparisons.
        return result;
    }
    public record Protected(String text,Map<String,String> replacements){
        public String restore(String output){
            for(var entry:replacements.entrySet()){
                if(output.indexOf(entry.getKey())<0||output.indexOf(entry.getKey())!=output.lastIndexOf(entry.getKey()))fail("translation_placeholder_changed");
                output=output.replace(entry.getKey(),entry.getValue());
            }
            if(output.contains("__MP"))fail("translation_placeholder_changed");return output;
        }
    }
    public static Protected protect(String input){
        // Protect immutable identifiers and explicit numerical expressions; no model tools or source instructions.
        Pattern all=Pattern.compile(IDENTIFIERS.pattern()+"|"+NUMBER.pattern(),Pattern.CASE_INSENSITIVE);
        Matcher matcher=all.matcher(input);StringBuilder text=new StringBuilder();Map<String,String> values=new LinkedHashMap<>();
        while(matcher.find()){String key="__MP"+values.size()+"__";values.put(key,matcher.group());matcher.appendReplacement(text,Matcher.quoteReplacement(key));}matcher.appendTail(text);
        return new Protected(text.toString(),values);
    }
}
