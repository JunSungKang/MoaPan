package com.moapan.ai.domain;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Fingerprint executable policy/prompt classes, not a manually remembered version number. */
public final class AiPolicy {
    private AiPolicy() {}
    public static String fingerprint(String identity,Class<?>... classes) {
        try {
            var digest=MessageDigest.getInstance("SHA-256");digest.update(identity.getBytes(StandardCharsets.UTF_8));
            var all=new TreeMap<String,Class<?>>();for(var type:classes)collect(type,all);
            for(var type:all.values())try(var stream=type.getResourceAsStream(type.getSimpleName().isEmpty()?"":type.getName().substring(type.getName().lastIndexOf('.')+1)+".class")){
                if(stream==null)throw new IllegalStateException("ai_policy_bytes_missing");digest.update(type.getName().getBytes(StandardCharsets.UTF_8));digest.update(stream.readAllBytes());
            }
            return HexFormat.of().formatHex(digest.digest());
        }catch(Exception e){throw new IllegalStateException("ai_policy_fingerprint_failed");}
    }
    private static void collect(Class<?> type,Map<String,Class<?>> all){all.put(type.getName(),type);for(var nested:type.getDeclaredClasses())collect(nested,all);}
}
