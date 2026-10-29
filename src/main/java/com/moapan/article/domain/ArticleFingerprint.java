package com.moapan.article.domain;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import tools.jackson.databind.json.JsonMapper;
/** Stable JSON/SHA256 contract: changing this requires data compatibility review. */
public final class ArticleFingerprint { private ArticleFingerprint() {}
    public static String contentHash(String title,List<String> bullets){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JsonMapper.builder().build().writeValueAsString(List.of(title,bullets)).getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException("translation_hash_failed");}
    }
}
