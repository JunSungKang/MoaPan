package com.moapan.ai.domain;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** A conservative processing hold, never a claim that an article/author is malicious. */
public final class ArticleGuard {
    private ArticleGuard() {}
    public static final String VERSION="article-hold-v1";
    private static final Pattern CONTROL=Pattern.compile("(?is)<\\|(?:im_start|im_end|system|assistant|user|endoftext|start_header_id|end_header_id)[^>]*>|\\[/?INST\\]|\\[im_start\\]|<start_of_turn>|^\\s*(?:system|developer|assistant)\\s*:");
    private static final Pattern OVERRIDE=Pattern.compile("(?iu)(?:ignore|disregard|forget|override).{0,45}(?:previous|prior|system|developer|instructions|rules)|(?:이전|시스템|개발자).{0,25}(?:지시|명령|규칙).{0,15}(?:무시|대체)|(?:reveal|print|send|exfiltrate).{0,40}(?:system prompt|api.?key|secret|password)|(?:return|output|respond|emit).{0,30}(?:only|exactly).{0,20}(?:topics|security|hacked|pwned)");
    private static final Pattern ENCODED=Pattern.compile("(?<![A-Za-z0-9+/])[A-Za-z0-9+/]{48,2048}={0,2}(?![A-Za-z0-9+/])");
    public static String risk(String input) {
        if(input==null)return "ai_input_invalid";
        if(input.length()>16000)return "ai_input_oversized";
        if(Pattern.compile("(?is)<(?:script|style|template|iframe|object|embed)\\b|<[^>]{0,1000}(?:\\bhidden\\b|display\\s*:\\s*none|visibility\\s*:\\s*hidden)").matcher(input).find())return "ai_hidden_markup";
        String normalized=Normalizer.normalize(input,Normalizer.Form.NFKC).replaceAll("[\\p{Cf}\\p{Cc}&&[^\\n\\r\\t]]","");
        if(CONTROL.matcher(normalized).find())return "ai_role_marker";
        if(OVERRIDE.matcher(normalized).find())return "ai_instruction_override";
        for(var m:ENCODED.matcher(normalized).results().limit(8).toList())try {
            String decoded=new String(Base64.getDecoder().decode(m.group()),StandardCharsets.UTF_8);
            if(CONTROL.matcher(decoded).find()||OVERRIDE.matcher(decoded).find())return "ai_encoded_instruction";
        }catch(IllegalArgumentException ignored){}
        for(int offset=0;offset+512<=normalized.length();offset+=64){boolean repeated=true;for(int n=1;n<8;n++)if(!normalized.regionMatches(offset,normalized,offset+n*64,64)){repeated=false;break;}if(repeated)return "ai_repeated_input";}
        return null;
    }
    public static void requireSafe(String input) {String risk=risk(input);if(risk!=null)throw new Held(risk);}
    public static final class Held extends IllegalArgumentException { public Held(String code){super(code);} }
}
