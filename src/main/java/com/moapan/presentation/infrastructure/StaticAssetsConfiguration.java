package com.moapan.presentation.infrastructure;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Public packaged assets only. Fixed filenames always revalidate; APIs/HTML retain no-store. */
@Configuration
@ConditionalOnWebApplication
public class StaticAssetsConfiguration implements WebMvcConfigurer {
    private final ConcurrentMap<String,String> packagedEtags=new ConcurrentHashMap<>();

    @Override public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**").addResourceLocations("classpath:/static/assets/")
            .setCacheControl(CacheControl.noCache().cachePublic()).setEtagGenerator(this::etag);
    }

    String etag(Resource resource) {
        try {
            // Development files may change without a restart. Packaged JAR resources are immutable.
            String key=resource.getURL().toExternalForm();
            if(!"jar".equals(resource.getURL().getProtocol())||packagedEtags.size()>=256)return digest(resource);
            return packagedEtags.computeIfAbsent(key,ignored->digest(resource));
        } catch(IOException e) { throw new UncheckedIOException(e); }
    }

    private static String digest(Resource resource) {
        try(var input=resource.getInputStream()) {
            var digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[8192];int n;
            while((n=input.read(buffer))!=-1)digest.update(buffer,0,n);
            // Equivalent across proxy content encodings; never a strong validator for gzip bytes.
            return "W/\""+HexFormat.of().formatHex(digest.digest())+"\"";
        } catch(IOException e) { throw new UncheckedIOException(e); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable",e); }
    }
}
