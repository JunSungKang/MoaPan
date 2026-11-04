package com.moapan.discovery;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.resource.ResourceTransformer;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

/** Fixed origin metadata on four static documents; never inserts account/news data or templates. */
@Component @ConditionalOnWebApplication
public final class PublicDocumentMetadata implements ResourceTransformer {
    private static final Pattern CANONICAL=Pattern.compile("(<link\\s+rel=\"canonical\"\\s+href=\")[^\"]*(\")",Pattern.CASE_INSENSITIVE);
    private record Snapshot(long modified,Resource resource) {}
    private final Map<String,Snapshot> documents=new ConcurrentHashMap<>();
    private final String origin;
    public PublicDocumentMetadata(@Value("${moapan.public-base-url}")String base){origin=DiscoveryController.origin(base);}
    @Override public Resource transform(HttpServletRequest request,Resource resource,ResourceTransformerChain chain)throws IOException {
        Resource original=chain.transform(request,resource);
        String path="/"+original.getFilename();
        if(!DiscoveryController.PUBLIC_PAGES.contains(path))return original;
        long modified=original.lastModified();
        Snapshot cached=documents.get(path);if(cached!=null&&cached.modified()==modified)return cached.resource();
        String html;try(var input=original.getInputStream()){html=new String(input.readAllBytes(),StandardCharsets.UTF_8);}
        var matcher=CANONICAL.matcher(html);if(!matcher.find())throw new IOException("Public document canonical metadata missing");
        String replacement=matcher.group(1)+origin+path+matcher.group(2);
        if(matcher.find())throw new IOException("Duplicate public document canonical metadata");
        String rendered=matcher.replaceFirst(java.util.regex.Matcher.quoteReplacement(replacement));
        var transformed=new TransformedResource(original,rendered.getBytes(StandardCharsets.UTF_8));
        documents.put(path,new Snapshot(modified,transformed));return transformed;
    }
}
