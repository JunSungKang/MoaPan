package com.moapan.security;
import com.moapan.ApiError;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;

@Service
public class Limits {
    private final JdbcClient db; private final TransactionTemplate tx; private final byte[] key;
    public Limits(JdbcClient db,PlatformTransactionManager manager,@Value("${moapan.secret}")String secret){
        this.db=db;tx=new TransactionTemplate(manager);tx.setPropagationBehavior(3);
        if(secret.length()<32) throw new IllegalStateException("MOAPAN_SECRET must have at least 32 characters"); key=secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    public String fingerprint(String value){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("HMAC unavailable");}}
    public void auth(String bucket,int maximum,int seconds){
        int count=tx.execute(s -> {
            if(db.sql("SELECT 1 FROM auth_rate_limits WHERE bucket=:b").param("b",bucket).query(Integer.class).list().isEmpty()){
                db.sql("SELECT pg_advisory_xact_lock(727002)").query((rs,n)->1).list();
                db.sql("DELETE FROM auth_rate_limits WHERE expires_at<now()").update();
                boolean full=db.sql("SELECT count(*)>=10000 AND NOT EXISTS(SELECT 1 FROM auth_rate_limits WHERE bucket=:b) FROM auth_rate_limits").param("b",bucket).query(Boolean.class).single();
                if(full)throw new ApiError(503,"security_counter_capacity");
            }
            return db.sql("INSERT INTO auth_rate_limits(bucket,window_start,used,expires_at) VALUES (:b,now(),1,now()+make_interval(secs=>:s)) ON CONFLICT(bucket) DO UPDATE SET used=CASE WHEN auth_rate_limits.expires_at<=now() THEN 1 ELSE auth_rate_limits.used+1 END, window_start=CASE WHEN auth_rate_limits.expires_at<=now() THEN now() ELSE auth_rate_limits.window_start END, expires_at=CASE WHEN auth_rate_limits.expires_at<=now() THEN now()+make_interval(secs=>:s) ELSE auth_rate_limits.expires_at END RETURNING used").param("b",bucket).param("s",seconds).query(Integer.class).single();});
        if(count>maximum) throw new ApiError(429,"rate_limited");
    }
    public void checkAuth(String bucket,int maximum){
        int count=db.sql("SELECT COALESCE((SELECT used FROM auth_rate_limits WHERE bucket=:b AND expires_at>now()),0)").param("b",bucket).query(Integer.class).single();
        if(count>=maximum)throw new ApiError(429,"rate_limited");
    }
    public UUID beginNews(UUID user){
        UUID id=UUID.randomUUID();
        boolean accepted=tx.execute(s->{
            db.sql("SELECT pg_advisory_xact_lock(hashtext(:id))").param("id",user.toString()).query((rs,n)->1).list();
            db.sql("DELETE FROM api_request_leases WHERE user_id=:u AND expires_at<now()").param("u",user).update();
            if(db.sql("SELECT count(*) FROM api_request_leases WHERE user_id=:u").param("u",user).query(Integer.class).single()>=3)return false;
            db.sql("INSERT INTO api_request_leases VALUES(:id,:u,now()+interval '60 seconds')").param("id",id).param("u",user).update();return true;
        });
        if(!accepted)throw new ApiError(429,"concurrent_limit");
        try{quota(user,"minute:"+Instant.now().getEpochSecond()/60,1,60,120);return id;}catch(RuntimeException e){endNews(id);throw e;}
    }
    public void items(UUID user,int amount){quota(user,"day:"+LocalDate.now(ZoneOffset.UTC),amount,6000,172800);}
    private void quota(UUID user,String bucket,int amount,int max,int ttl){
        boolean ok=tx.execute(s->!db.sql("INSERT INTO api_quotas(user_id,bucket,used,expires_at) VALUES(:u,:b,:n,now()+make_interval(secs=>:ttl)) ON CONFLICT(user_id,bucket) DO UPDATE SET used=api_quotas.used+EXCLUDED.used WHERE api_quotas.used+EXCLUDED.used<=:max RETURNING used").param("u",user).param("b",bucket).param("n",amount).param("ttl",ttl).param("max",max).query(Integer.class).list().isEmpty());
        if(!ok)throw new ApiError(429,"quota_exceeded");
    }
    public void endNews(UUID id){db.sql("DELETE FROM api_request_leases WHERE id=:id").param("id",id).update();}
}
