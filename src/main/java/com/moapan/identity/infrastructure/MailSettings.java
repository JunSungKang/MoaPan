package com.moapan.identity.infrastructure;

import java.nio.file.*;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class MailSettings {
    public final boolean enabled;public final String host,from,username,password,tls,caFile;public final int port;private final byte[] key;
    public MailSettings(@Value("${moapan.mail.enabled:false}")boolean enabled,@Value("${moapan.mail.host:}")String host,@Value("${moapan.mail.port:587}")int port,
            @Value("${moapan.mail.from:}")String from,@Value("${moapan.mail.username:}")String username,@Value("${moapan.mail.password-file:}")String passwordFile,
            @Value("${moapan.mail.tls:STARTTLS}")String tls,@Value("${moapan.mail.ca-file:}")String caFile,
            @Value("${moapan.mail.data-key-file:}")String keyFile,@Value("${moapan.mail.data-key:}")String inlineKey){
        this.enabled=enabled;this.host=host;this.port=port;this.username=username;this.tls=tls;this.caFile=caFile;
        this.from=from.isBlank()?"":com.moapan.identity.domain.CredentialPolicy.email(from);
        try{password=passwordFile.isBlank()?"":Files.readString(Path.of(passwordFile));String encoded=keyFile.isBlank()?inlineKey:Files.readString(Path.of(keyFile)).strip();key=encoded.isBlank()?null:Base64.getDecoder().decode(encoded);}catch(Exception e){throw new IllegalStateException("Mail secret configuration unavailable");}
        if(enabled&&(host.isBlank()||!host.matches("[A-Za-z0-9.-]+")||port<1||port>65535||this.from.isBlank()||!java.util.Set.of("STARTTLS","SMTPS").contains(tls)||key==null||key.length!=32||username.isBlank()!=password.isBlank()))throw new IllegalStateException("Verified SMTP and email data key configuration required");
    }
    public byte[] dataKey(){if(key==null)throw new com.moapan.shared.api.ApiError(503,"mail_unavailable");return key.clone();}
}
