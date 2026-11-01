package com.moapan.advertising.application;

import com.moapan.shared.api.ApiError;
import java.net.URI;
import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdvertisingSettings {
    public enum Provider {BANNER,ADSENSE}
    public record Settings(@NotNull @Min(0) Long version,boolean enabled,@NotNull Provider provider,
        @NotNull @Size(max=80) String title,@NotNull @Size(max=160) String description,
        @NotNull @Size(max=2048) String targetUrl,@NotNull @Size(max=240) String imagePath,
        @NotNull @Size(max=32) String publisherId,@NotNull @Size(max=20) String slotId){}
    public record Display(boolean enabled,String title,String description,String targetUrl,String imagePath){}
    private final JdbcClient db;
    private final com.moapan.shared.infrastructure.cache.VersionedReadCache<Display> cache;
    public AdvertisingSettings(JdbcClient db,com.moapan.shared.infrastructure.cache.SharedReadCaches caches){this.db=db;cache=caches.create(com.moapan.shared.infrastructure.cache.SharedReadCaches.Region.ADVERTISEMENT,s->256+2*(s.title().length()+s.description().length()+s.targetUrl().length()+s.imagePath().length()));}
    private Settings current(){return db.sql("SELECT * FROM advertising_settings WHERE singleton").query((r,n)->new Settings(r.getLong("version"),r.getBoolean("enabled"),Provider.valueOf(r.getString("provider")),r.getString("title"),r.getString("description"),r.getString("target_url"),r.getString("image_path"),r.getString("publisher_id"),r.getString("slot_id"))).single();}
    @PreAuthorize("hasRole('ADMIN')") public Settings read(){return current();}
    @PreAuthorize("isAuthenticated()") public Display display(){return cache.read("public-advertisement",()->db.sql("SELECT fingerprint FROM read_cache_fingerprints WHERE domain='advertising' AND subject='00000000-0000-0000-0000-000000000000'").query(String.class).optional().orElse(null),this::displayCurrent);}
    private Display displayCurrent(){return db.sql("SELECT enabled,provider,title,description,target_url,image_path FROM advertising_settings WHERE singleton").query((r,n)->r.getBoolean("enabled")&&r.getString("provider").equals("BANNER")?new Display(true,r.getString("title"),r.getString("description"),r.getString("target_url"),r.getString("image_path")):new Display(false,"","","","")).single();}
    @Transactional @PreAuthorize("hasRole('ADMIN')")
    public Settings update(Settings input,UUID actor){
        if(!input.targetUrl().isBlank()){
            try{var u=URI.create(input.targetUrl());if(!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getPort()!=-1&&u.getPort()!=443)throw new IllegalArgumentException();}
            catch(IllegalArgumentException e){throw new ApiError(400,"invalid_advertisement_url");}
        }
        if(!input.imagePath().isEmpty()&&!input.imagePath().matches("/assets/images/[A-Za-z0-9_-]+\\.(png|jpe?g|webp|gif)"))throw new ApiError(400,"invalid_advertisement_image");
        if(!input.publisherId().isEmpty()&&!input.publisherId().matches("ca-pub-[0-9]{16}"))throw new ApiError(400,"invalid_publisher_id");
        if(!input.slotId().isEmpty()&&!input.slotId().matches("[0-9]{1,20}"))throw new ApiError(400,"invalid_ad_slot");
        if(input.enabled()&&(input.title().isBlank()||input.targetUrl().isBlank()))throw new ApiError(400,"incomplete_advertisement");
        if(input.enabled()&&input.provider()==Provider.ADSENSE)throw new ApiError(409,"adsense_connection_required");
        int changed=db.sql("UPDATE advertising_settings SET enabled=:enabled,provider=:provider,title=:title,description=:description,target_url=:url,image_path=:image,publisher_id=:publisher,slot_id=:slot,version=version+1 WHERE singleton AND version=:version")
            .param("enabled",input.enabled()).param("provider",input.provider().name()).param("title",input.title()).param("description",input.description()).param("url",input.targetUrl()).param("image",input.imagePath()).param("publisher",input.publisherId()).param("slot",input.slotId()).param("version",input.version()).update();
        if(changed!=1)throw new ApiError(409,"version_conflict");
        db.sql("INSERT INTO admin_actions VALUES(:id,:actor,'advertising_settings',:target,now())").param("id",UUID.randomUUID()).param("actor",actor).param("target",new UUID(0,0)).update();return current();
    }
}
