package com.moapan.identity.application;

import com.moapan.identity.infrastructure.EmailSecrets;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Encrypted, bounded at-least-once delivery; SMTP acknowledgement is not proof of inbox delivery. */
@Service
public final class EmailOutbox {
    private final JdbcClient db;private final EmailSecrets secrets;private final MailTransport transport;private final TransactionTemplate tx;private final JsonMapper json=JsonMapper.builder().build();
    public EmailOutbox(JdbcClient db,EmailSecrets secrets,MailTransport transport,PlatformTransactionManager manager){this.db=db;this.secrets=secrets;this.transport=transport;tx=new TransactionTemplate(manager);}
    public void enqueue(UUID account,UUID challenge,String recipient,com.moapan.identity.domain.EmailPurpose purpose,String code,Instant expires){
        if(purpose==null)throw new IllegalArgumentException("Account email purpose required");
        boolean verification=java.util.Set.of(com.moapan.identity.domain.EmailPurpose.SIGNUP_CODE,com.moapan.identity.domain.EmailPurpose.EMAIL_BIND_CODE,com.moapan.identity.domain.EmailPurpose.PASSWORD_RESET_CODE).contains(purpose);
        if(verification&&(code==null||!code.matches("[0-9]{6}")))throw new IllegalArgumentException("Verification code required");
        String subject=switch(purpose){case SIGNUP_CODE,EMAIL_BIND_CODE -> "담고담고 이메일 인증번호";case PASSWORD_RESET_CODE -> "담고담고 비밀번호 재설정 인증번호";case PASSWORD_CHANGED -> "담고담고 비밀번호 변경 안내";case EMAIL_CHANGED -> "담고담고 비밀번호 복구 이메일 변경 안내";};
        String text=verification?"요청한 인증번호는 "+code+" 입니다. 5분 안에 입력해 주세요. 요청하지 않았다면 이 메일을 무시해 주세요.\nYour verification code is "+code+". It expires in 5 minutes.":"계정의 "+(purpose==com.moapan.identity.domain.EmailPurpose.PASSWORD_CHANGED?"비밀번호가":"비밀번호 복구 이메일이")+" 변경되었습니다. 본인이 변경하지 않았다면 계정 보안을 확인해 주세요.\nYour account security information was changed.";
        UUID id=UUID.randomUUID();var envelope=new MailTransport.Envelope(id,purpose,recipient,subject,text);
        tx.executeWithoutResult(s->{
            db.sql("SELECT pg_advisory_xact_lock(727010)").query((rs,n)->1).list();
            if(db.sql("SELECT count(*)>=10000 FROM email_outbox").query(Boolean.class).single())throw new com.moapan.shared.api.ApiError(503,"mail_capacity");
            db.sql("INSERT INTO email_outbox(id,account_id,challenge_id,envelope_cipher,expires_at) VALUES(:id,:account,:challenge,:cipher,:expires)").param("id",id).param("account",account).param("challenge",challenge).param("cipher",secrets.encrypt("outbox:"+id,json.writeValueAsString(envelope))).param("expires",expires.atOffset(ZoneOffset.UTC)).update();
        });
    }
    public void cleanup(){db.sql("DELETE FROM email_challenges WHERE expires_at<=now()").update();db.sql("UPDATE email_outbox SET status='FAILED',envelope_cipher=NULL,lease_until=NULL,lease_token=NULL,error_code='expired' WHERE expires_at<=now() AND status IN ('QUEUED','RUNNING')").update();db.sql("DELETE FROM email_outbox WHERE created_at<now()-interval '1 day'").update();}
    public boolean dispatchOne(){
        var job=tx.execute(s->{
            db.sql("UPDATE email_outbox SET status=CASE WHEN attempts>=3 THEN 'FAILED' ELSE 'QUEUED' END,envelope_cipher=CASE WHEN attempts>=3 THEN NULL ELSE envelope_cipher END,lease_until=NULL,lease_token=NULL,error_code='lease_expired' WHERE status='RUNNING' AND lease_until<now()").update();
            var rows=db.sql("SELECT id,envelope_cipher FROM email_outbox WHERE status='QUEUED' AND available_at<=now() AND expires_at>now() AND attempts<3 ORDER BY available_at,id FOR UPDATE SKIP LOCKED LIMIT 1").query().listOfRows();if(rows.isEmpty())return null;
            var row=rows.getFirst();UUID token=UUID.randomUUID();db.sql("UPDATE email_outbox SET status='RUNNING',attempts=attempts+1,lease_until=now()+interval '60 seconds',lease_token=:token WHERE id=:id").param("id",row.get("id")).param("token",token).update();row.put("token",token);return row;
        });
        if(job==null)return false;
        try{var envelope=json.readValue(secrets.decrypt("outbox:"+job.get("id"),(byte[])job.get("envelope_cipher")),MailTransport.Envelope.class);transport.send(envelope);
            db.sql("UPDATE email_outbox SET status='SENT',envelope_cipher=NULL,lease_until=NULL,lease_token=NULL,error_code=NULL WHERE id=:id AND lease_token=:token AND status='RUNNING'").param("id",job.get("id")).param("token",job.get("token")).update();
        }catch(Exception e){db.sql("UPDATE email_outbox SET status=CASE WHEN attempts>=3 THEN 'FAILED' ELSE 'QUEUED' END,envelope_cipher=CASE WHEN attempts>=3 THEN NULL ELSE envelope_cipher END,available_at=now()+interval '15 seconds',lease_until=NULL,lease_token=NULL,error_code='smtp_unavailable' WHERE id=:id AND lease_token=:token AND status='RUNNING'").param("id",job.get("id")).param("token",job.get("token")).update();}
        return true;
    }
}
