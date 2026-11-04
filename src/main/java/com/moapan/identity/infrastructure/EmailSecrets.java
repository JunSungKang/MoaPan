package com.moapan.identity.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.springframework.stereotype.Component;

/** Standard AES-GCM envelope encryption; separate SMTP-scoped key, never the cursor/session secret. */
@Component
public final class EmailSecrets {
    private final MailSettings settings;private final SecureRandom random=new SecureRandom();
    public EmailSecrets(MailSettings settings){this.settings=settings;}
    private byte[] mac(byte[] key,String value){try{var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));}catch(GeneralSecurityException e){throw new IllegalStateException("Email cryptography unavailable");}}
    public String fingerprint(String scope,String value){return HexFormat.of().formatHex(mac(mac(settings.dataKey(),"email-lookup-v1"),scope+":"+value));}
    public byte[] encrypt(String context,String value){try{byte[] iv=new byte[12];random.nextBytes(iv);var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(mac(settings.dataKey(),"email-encryption-v1"),"AES"),new GCMParameterSpec(128,iv));cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));byte[] encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));return java.nio.ByteBuffer.allocate(1+iv.length+encrypted.length).put((byte)1).put(iv).put(encrypted).array();}catch(GeneralSecurityException e){throw new IllegalStateException("Email encryption unavailable");}}
    public String decrypt(String context,byte[] envelope){try{if(envelope==null||envelope.length<29||envelope[0]!=1)throw new GeneralSecurityException();var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(mac(settings.dataKey(),"email-encryption-v1"),"AES"),new GCMParameterSpec(128,Arrays.copyOfRange(envelope,1,13)));cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));return new String(cipher.doFinal(Arrays.copyOfRange(envelope,13,envelope.length)),StandardCharsets.UTF_8);}catch(GeneralSecurityException e){throw new IllegalStateException("Email encrypted data rejected");}}
    public String code(){return String.format(Locale.ROOT,"%06d",random.nextInt(1_000_000));}
    public boolean matches(String expected,String actual){return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),actual.getBytes(StandardCharsets.US_ASCII));}
}
