package com.moapan.identity.application;

import com.moapan.access.infrastructure.Limits;
import com.moapan.identity.domain.*;
import com.moapan.identity.infrastructure.*;
import com.moapan.shared.api.ApiError;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
public class EmailIdentity {
    public static final String POLICY_VERSION="email-1";
    public record Issued(UUID challengeId,int expiresInSeconds,int resendAfterSeconds) {}
    public record Profile(boolean verified,String email,Instant verifiedAt) {}
    private record User(UUID id,String username,String passwordHash,long version,boolean active,String emailKey,byte[] emailCipher,Instant verifiedAt) {}
    private record Challenge(UUID id,String purpose,UUID account,Long version,String subject,String session,String emailKey,byte[] emailCipher,String codeHash,int attempts,Instant expires) {}
    private record Attempt<T>(T value,boolean valid) {}
    private final JdbcClient db;private final Limits limits;private final EmailSecrets secrets;private final MailSettings settings;private final EmailOutbox outbox;private final PasswordEncoder encoder;private final IdentityPasswords passwords;private final Clock clock;private final TransactionTemplate tx;
    public EmailIdentity(JdbcClient db,Limits limits,EmailSecrets secrets,MailSettings settings,EmailOutbox outbox,PasswordEncoder encoder,IdentityPasswords passwords,Clock clock,PlatformTransactionManager manager){this.db=db;this.limits=limits;this.secrets=secrets;this.settings=settings;this.outbox=outbox;this.encoder=encoder;this.passwords=passwords;this.clock=clock;tx=new TransactionTemplate(manager);}
    private void available(){if(!settings.enabled)throw new ApiError(503,"mail_unavailable");}
    private void policy(boolean accepted,String version){if(!accepted||!POLICY_VERSION.equals(version))throw new ApiError(400,"email_policy_required");}
    public Issued requestSignup(String username,String email,String session,boolean accepted,String policyVersion){policy(accepted,policyVersion);return issue("SIGNUP",CredentialPolicy.username(username),CredentialPolicy.email(email),session,null);}
    public Issued requestReset(String username,String email,String session){available();String name=CredentialPolicy.username(username),address=CredentialPolicy.email(email);String key=secrets.fingerprint("address",address);var user=findByNameAndEmail(name,key);return issue("RESET",name,address,session,user);}
    @PreAuthorize("isAuthenticated() and #actor.toString() == authentication.name") public Issued requestBinding(UUID actor,String password,String email,String session,boolean accepted,String policyVersion){available();policy(accepted,policyVersion);limits.auth("email_bind:"+actor,10,900);var user=user(actor,false);passwords.hash(()->{if(user==null||!user.active||!encoder.matches(password,user.passwordHash))throw new ApiError(403,"invalid_credentials");return true;});return issue("BIND",user.username,CredentialPolicy.email(email),session,user);}
    private Issued issue(String purpose,String name,String address,String session,User user){
        available();String key=secrets.fingerprint("address",address),binding=secrets.fingerprint("session",session);
        limits.auth("email_send_global",300,3600);limits.auth("email_send_session:"+binding,10,900);limits.auth("email_send_address:"+key,5,900);limits.auth("email_resend:"+key,1,60);
        return tx.execute(s->{
            db.sql("SELECT pg_advisory_xact_lock(727009)").query((rs,n)->1).list();
            // Same order as consume/password changes: account before challenge/FK writes.
            User bindingAccount=user;
            if(user!=null){var locked=user(user.id,true);if(locked==null||!locked.active||locked.version!=user.version){if(purpose.equals("BIND"))throw new ApiError(409,"credential_changed");bindingAccount=null;}}
            db.sql("DELETE FROM email_challenges WHERE expires_at<=now()").update();
            if(db.sql("SELECT count(*)>=10000 FROM email_challenges").query(Boolean.class).single())throw new ApiError(503,"mail_capacity");
            db.sql("DELETE FROM email_challenges WHERE purpose=:purpose AND email_key=:email AND session_key=:session").param("purpose",purpose).param("email",key).param("session",binding).update();
            UUID id=UUID.randomUUID();String code=secrets.code();Instant expires=clock.instant().plusSeconds(300);
            db.sql("INSERT INTO email_challenges(id,purpose,account_id,auth_version,subject_key,session_key,email_key,email_cipher,code_hash,expires_at) VALUES(:id,:purpose,:account,:version,:subject,:session,:email,:cipher,:code,:expires)")
                    .param("id",id).param("purpose",purpose).param("account",bindingAccount==null?null:bindingAccount.id).param("version",bindingAccount==null?null:bindingAccount.version).param("subject",secrets.fingerprint("subject",name)).param("session",binding).param("email",key).param("cipher",secrets.encrypt("challenge:"+id,address)).param("code",secrets.fingerprint("code",id+":"+code)).param("expires",expires.atOffset(ZoneOffset.UTC)).update();
            outbox.enqueue(bindingAccount==null?null:bindingAccount.id,id,address,switch(purpose){case "SIGNUP" -> EmailPurpose.SIGNUP_CODE;case "BIND" -> EmailPurpose.EMAIL_BIND_CODE;default -> EmailPurpose.PASSWORD_RESET_CODE;},code,expires);
            return new Issued(id,300,60);
        });
    }
    public UUID signup(String username,String password,String email,UUID challengeId,String code,String session,boolean consent,String terms,boolean emailConsent,String policyVersion){
        if(!consent||!CredentialPolicy.TERMS_VERSION.equals(terms))throw new ApiError(400,"terms_version_outdated");policy(emailConsent,policyVersion);CredentialPolicy.password(password);String name=CredentialPolicy.username(username),address=CredentialPolicy.email(email);
        return consume("SIGNUP",challengeId,code,name,session,null,c->{
            if(!c.emailKey.equals(secrets.fingerprint("address",address)))throw new ApiError(400,"invalid_verification");
            if(db.sql("SELECT EXISTS(SELECT 1 FROM users WHERE username=:name)").param("name",name).query(Boolean.class).single())throw new ApiError(409,"username_unavailable");
            if(db.sql("SELECT EXISTS(SELECT 1 FROM users WHERE email_key=:key)").param("key",c.emailKey).query(Boolean.class).single())throw new ApiError(409,"email_unavailable");
            UUID id=UUID.randomUUID();Instant now=clock.instant();String encoded=encoder.encode(password);
            db.sql("INSERT INTO users(id,username,password_hash,terms_version,terms_accepted_at,email_cipher,email_key,email_verified_at,email_policy_version,email_policy_accepted_at) VALUES(:id,:name,:hash,:terms,:at,:cipher,:key,:at,:policy,:at)")
                    .param("id",id).param("name",name).param("hash",encoded).param("terms",terms).param("at",now.atOffset(ZoneOffset.UTC)).param("cipher",secrets.encrypt("account:"+id,address)).param("key",c.emailKey).param("policy",POLICY_VERSION).update();return id;
        });
    }
    public void reset(String username,String newPassword,UUID challengeId,String code,String session){CredentialPolicy.password(newPassword);String name=CredentialPolicy.username(username);
        consume("RESET",challengeId,code,name,session,null,c->{
            var user=user(c.account,false);String hash=encoder.encode(newPassword);
            if(db.sql("UPDATE users SET password_hash=:hash,auth_version=auth_version+1 WHERE id=:id AND active AND auth_version=:version AND email_key=:email").param("hash",hash).param("id",user.id).param("version",c.version).param("email",c.emailKey).update()!=1)throw new ApiError(400,"invalid_verification");
            db.sql("DELETE FROM spring_session WHERE principal_name=:id").param("id",user.id.toString()).update();db.sql("DELETE FROM auth_rate_limits WHERE bucket=:bucket").param("bucket","login:"+limits.fingerprint(user.username)).update();
            db.sql("DELETE FROM email_challenges WHERE account_id=:id AND id<>:current").param("id",user.id).param("current",c.id).update();
            outbox.enqueue(user.id,null,secrets.decrypt("account:"+user.id,user.emailCipher),EmailPurpose.PASSWORD_CHANGED,null,clock.instant().plusSeconds(86400));return true;
        });
    }
    @PreAuthorize("isAuthenticated() and #actor.toString() == authentication.name") public Profile profile(UUID actor){var user=user(actor,false);if(user==null||!user.active)throw new ApiError(401,"authentication_required");return user.emailKey==null?new Profile(false,null,null):new Profile(true,secrets.decrypt("account:"+actor,user.emailCipher),user.verifiedAt);}
    @PreAuthorize("isAuthenticated() and #actor.toString() == authentication.name") public void bind(UUID actor,UUID challengeId,String code,String session,boolean accepted,String policyVersion){policy(accepted,policyVersion);var original=user(actor,false);if(original==null)throw new ApiError(401,"authentication_required");
        consume("BIND",challengeId,code,original.username,session,actor,c->{
            if(db.sql("SELECT EXISTS(SELECT 1 FROM users WHERE email_key=:key AND id<>:id)").param("key",c.emailKey).param("id",actor).query(Boolean.class).single())throw new ApiError(409,"email_unavailable");
            String address=secrets.decrypt("challenge:"+c.id,c.emailCipher);var before=user(actor,false);Instant now=clock.instant();
            db.sql("UPDATE users SET email_cipher=:cipher,email_key=:key,email_verified_at=:now,email_policy_version=:policy,email_policy_accepted_at=:now,auth_version=auth_version+1 WHERE id=:id")
                    .param("cipher",secrets.encrypt("account:"+actor,address)).param("key",c.emailKey).param("now",now.atOffset(ZoneOffset.UTC)).param("policy",POLICY_VERSION).param("id",actor).update();
            db.sql("DELETE FROM spring_session WHERE principal_name=:id").param("id",actor.toString()).update();db.sql("DELETE FROM email_challenges WHERE account_id=:id AND id<>:current").param("id",actor).param("current",c.id).update();
            outbox.enqueue(actor,null,address,EmailPurpose.EMAIL_CHANGED,null,now.plusSeconds(86400));
            if(before.emailKey!=null&&!before.emailKey.equals(c.emailKey))outbox.enqueue(actor,null,secrets.decrypt("account:"+actor,before.emailCipher),EmailPurpose.EMAIL_CHANGED,null,now.plusSeconds(86400));return true;
        });
    }
    /** Called inside the credential update transaction; never accepts a caller-selected message. */
    public void passwordChanged(UUID actor){
        db.sql("DELETE FROM email_challenges WHERE account_id=:id").param("id",actor).update();
        var account=user(actor,false);
        if(account!=null&&account.emailKey!=null)outbox.enqueue(actor,null,secrets.decrypt("account:"+actor,account.emailCipher),EmailPurpose.PASSWORD_CHANGED,null,clock.instant().plusSeconds(86400));
    }
    private <T>T consume(String purpose,UUID id,String code,String username,String session,UUID actor,Function<Challenge,T> action){
        available();if(id==null||code==null||!code.matches("[0-9]{6}"))throw new ApiError(400,"invalid_verification");String binding=secrets.fingerprint("session",session);limits.auth("email_verify_global",1000,900);limits.auth("email_verify_session:"+binding,50,900);
        var snapshot=challenge(id,false);
        Attempt<T> result=passwords.hash(()->tx.execute(s->{
            db.sql("SELECT pg_advisory_xact_lock(727009)").query((rs,n)->1).list();
            User owner=snapshot!=null&&snapshot.account!=null?user(snapshot.account,true):null;
            var c=challenge(id,true);
            if(c==null||!purpose.equals(c.purpose)||!secrets.matches(c.session,binding)||!secrets.matches(c.subject,secrets.fingerprint("subject",username))||actor!=null&&!actor.equals(c.account))return new Attempt<>(null,false);
            if(!c.expires.isAfter(clock.instant())||c.attempts>=5||!purpose.equals("SIGNUP")&&(owner==null||!owner.active||!Objects.equals(c.version,owner.version)||purpose.equals("RESET")&&!Objects.equals(c.emailKey,owner.emailKey))){db.sql("DELETE FROM email_challenges WHERE id=:id").param("id",id).update();return new Attempt<>(null,false);}
            if(!secrets.matches(c.codeHash,secrets.fingerprint("code",id+":"+code))){if(c.attempts>=4)db.sql("DELETE FROM email_challenges WHERE id=:id").param("id",id).update();else db.sql("UPDATE email_challenges SET attempts=attempts+1 WHERE id=:id").param("id",id).update();return new Attempt<>(null,false);}
            T value=action.apply(c);db.sql("DELETE FROM email_challenges WHERE id=:id").param("id",id).update();return new Attempt<>(value,true);
        }));
        if(!result.valid)throw new ApiError(400,"invalid_verification");return result.value;
    }
    private Challenge challenge(UUID id,boolean lock){return db.sql("SELECT * FROM email_challenges WHERE id=:id"+(lock?" FOR UPDATE":"")).param("id",id).query((rs,n)->new Challenge(rs.getObject("id",UUID.class),rs.getString("purpose"),rs.getObject("account_id",UUID.class),rs.getObject("auth_version",Long.class),rs.getString("subject_key"),rs.getString("session_key"),rs.getString("email_key"),rs.getBytes("email_cipher"),rs.getString("code_hash"),rs.getInt("attempts"),rs.getObject("expires_at",OffsetDateTime.class).toInstant())).optional().orElse(null);}
    private static final String USER="SELECT id,username,password_hash,auth_version,active,email_key,email_cipher,email_verified_at FROM users";
    private User user(UUID id,boolean lock){return db.sql(USER+" WHERE id=:id"+(lock?" FOR UPDATE":"")).param("id",id).query(EmailIdentity::map).optional().orElse(null);}
    private User findByNameAndEmail(String name,String key){return db.sql(USER+" WHERE username=:name AND email_key=:key AND email_verified_at IS NOT NULL AND active").param("name",name).param("key",key).query(EmailIdentity::map).optional().orElse(null);}
    private static User map(java.sql.ResultSet rs,int n)throws java.sql.SQLException{var verified=rs.getObject("email_verified_at",OffsetDateTime.class);return new User(rs.getObject("id",UUID.class),rs.getString("username"),rs.getString("password_hash"),rs.getLong("auth_version"),rs.getBoolean("active"),rs.getString("email_key"),rs.getBytes("email_cipher"),verified==null?null:verified.toInstant());}
}
