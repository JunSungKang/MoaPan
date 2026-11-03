package com.moapan.identity;

import com.moapan.identity.application.*;
import com.moapan.identity.infrastructure.*;
import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.flyway.enabled=true","moapan.secret=synthetic-only-test-secret-32-characters","moapan.public-base-url=http://localhost","server.servlet.session.cookie.secure=false","moapan.mail.enabled=true","moapan.mail.host=localhost","moapan.mail.from=no-reply@example.test","moapan.mail.data-key="+EmailTestSupport.KEY})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE) @Import(EmailTestSupport.Config.class)
class EmailIdentityIntegrationTest {
    static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>(org.testcontainers.utility.DockerImageName.parse("postgres@sha256:edb0c08dc33befd0262ab3b5cf1c4f3f8cfd8b09e88c05b45adee29d6761ddb6").asCompatibleSubstituteFor("postgres"));
    static{postgres.start();org.flywaydb.core.Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).cleanDisabled(true).load().migrate();}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);}
    @AfterAll static void stop(){postgres.stop();}
    @Autowired MockMvc mvc;@Autowired JdbcClient db;@Autowired EmailOutbox outbox;@Autowired EmailTestSupport.Mailbox mailbox;@Autowired EmailSecrets secrets;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
    @Autowired javax.sql.DataSource dataSource;
    final JsonMapper json=JsonMapper.builder().build();final String password="synthetic email password";
    record Proof(Cookie session,UUID id,String username,String email,String code){}
    @BeforeEach void isolate(){mailbox.fail=false;mailbox.messages.clear();db.sql("DELETE FROM email_outbox").update();db.sql("DELETE FROM email_challenges").update();db.sql("DELETE FROM auth_rate_limits WHERE bucket LIKE 'email_%'").update();}
    String user(){return "email_"+UUID.randomUUID().toString().replace("-","");}
    Proof issue(String purpose,String username,String email)throws Exception{var session=new MockHttpSession();String route=purpose.equals("SIGNUP")?"/api/v1/auth/email/signup-code":"/api/v1/auth/password/reset-code";var body=new LinkedHashMap<String,Object>(Map.of("username",username,"email",email));if(purpose.equals("SIGNUP")){body.put("emailPolicyAccepted",true);body.put("emailPolicyVersion","email-1");}var result=mvc.perform(post(route).session(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isAccepted()).andReturn();UUID id=UUID.fromString(json.readTree(result.getResponse().getContentAsString()).path("challengeId").asText());while(outbox.dispatchOne()){}return new Proof(result.getResponse().getCookie("SESSION"),id,username,email,mailbox.code(email));}
    Map<String,Object> signupBody(Proof p){return new LinkedHashMap<>(Map.of("username",p.username,"password",password,"email",p.email,"challengeId",p.id,"code",p.code,"termsAccepted",true,"termsVersion","terms-1","emailPolicyAccepted",true,"emailPolicyVersion","email-1"));}
    Cookie login(String user,String pass)throws Exception{return mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("username",user,"password",pass,"rememberMe",true)))).andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");}
    Map<String,Object> resetBody(Proof p,String pass){return Map.of("username",p.username,"challengeId",p.id,"code",p.code,"newPassword",pass);}
    UUID create(String name,String address)throws Exception{var p=issue("SIGNUP",name,address);String result=mvc.perform(post("/api/v1/auth/signup").cookie(p.session).with(csrf()).contentType("application/json").content(json.writeValueAsString(signupBody(p)))).andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(true)).andExpect(jsonPath("$.email").doesNotExist()).andReturn().getResponse().getContentAsString();return UUID.fromString(json.readTree(result).path("id").asText());}
    @Test void termsArePublicReadOnlyAndKeepStrictBrowserSecurityHeaders()throws Exception{
        var response=mvc.perform(get("/api/v1/auth/terms")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.version").value("terms-1")).andExpect(jsonPath("$.nodes").isArray()).andReturn().getResponse();
        assertTrue(response.getHeader("Content-Security-Policy").contains("require-trusted-types-for 'script'; trusted-types 'none'"));
        String body=response.getContentAsString();assertFalse(body.contains("href"));assertFalse(body.contains("<script"));assertFalse(body.contains("회원가입으로 돌아가기"));assertNull(response.getCookie("SESSION"));
        mvc.perform(post("/api/v1/auth/terms").with(csrf())).andExpect(status().isUnauthorized());assertEquals(0,db.sql("SELECT count(*) FROM email_outbox").query(Integer.class).single());
    }
    @Test void signupRequiresCodeAndPolicyStoresOnlyProtectedEmailAndCannotInjectRole()throws Exception{
        String name=user(),address=name+"@example.test";var p=issue("SIGNUP",name,address);var body=signupBody(p);body.put("role","ADMIN");mvc.perform(post("/api/v1/auth/signup").cookie(p.session).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isBadRequest());body.remove("role");body.put("emailPolicyAccepted",false);mvc.perform(post("/api/v1/auth/signup").cookie(p.session).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isBadRequest());body.put("emailPolicyAccepted",true);
        mvc.perform(post("/api/v1/auth/signup").cookie(p.session).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("USER"));
        var row=db.sql("SELECT id,email_cipher,email_key,email_policy_version,email_policy_accepted_at FROM users WHERE username=:name").param("name",name).query().singleRow();assertEquals("email-1",row.get("email_policy_version"));assertNotNull(row.get("email_policy_accepted_at"));assertFalse(new String((byte[])row.get("email_cipher"),java.nio.charset.StandardCharsets.ISO_8859_1).contains(address));assertEquals(address,secrets.decrypt("account:"+row.get("id"),(byte[])row.get("email_cipher")));
        mvc.perform(get("/api/v1/account/email")).andExpect(status().isUnauthorized());mvc.perform(get("/api/v1/account/email").cookie(login(name,password))).andExpect(jsonPath("$.email").value(address));
        mvc.perform(post("/api/v1/auth/signup").cookie(p.session).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
    }
    @Test void wrongCodesCommitFiveAttemptsAndAnotherSessionCannotBurnTheOwnersChallenge()throws Exception{
        String name=user();var p=issue("SIGNUP",name,name+"@example.test");var body=signupBody(p);mvc.perform(post("/api/v1/auth/signup").session(new MockHttpSession()).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isBadRequest());assertEquals(0,db.sql("SELECT attempts FROM email_challenges WHERE id=:id").param("id",p.id).query(Integer.class).single());
        body.put("code",p.code.equals("000000")?"000001":"000000");for(int i=0;i<5;i++)mvc.perform(post("/api/v1/auth/signup").cookie(p.session).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isBadRequest());assertEquals(0,db.sql("SELECT count(*) FROM email_challenges WHERE id=:id").param("id",p.id).query(Integer.class).single());
        body.put("code",p.code);mvc.perform(post("/api/v1/auth/signup").cookie(p.session).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isBadRequest());assertEquals(0,db.sql("SELECT count(*) FROM users WHERE username=:name").param("name",name).query(Integer.class).single());
    }
    @Test void expiredCodeAndCrossPurposeUseCannotChangeAnAccount()throws Exception{
        String name=user();var p=issue("SIGNUP",name,name+"@example.test");mvc.perform(post("/api/v1/auth/password/reset").cookie(p.session).with(csrf()).contentType("application/json").content(json.writeValueAsString(resetBody(p,password)))).andExpect(status().isBadRequest());db.sql("UPDATE email_challenges SET expires_at=now()-interval '1 second' WHERE id=:id").param("id",p.id).update();mvc.perform(post("/api/v1/auth/signup").cookie(p.session).with(csrf()).contentType("application/json").content(json.writeValueAsString(signupBody(p)))).andExpect(status().isBadRequest());
    }
    @Test void twoConcurrentUsesCreateOnlyOneAccount()throws Exception{
        String name=user();var p=issue("SIGNUP",name,name+"@example.test");String body=json.writeValueAsString(signupBody(p));try(var pool=Executors.newFixedThreadPool(2)){var results=pool.invokeAll(List.<Callable<Integer>>of(()->mvc.perform(post("/api/v1/auth/signup").cookie(p.session).with(csrf()).contentType("application/json").content(body)).andReturn().getResponse().getStatus(),()->mvc.perform(post("/api/v1/auth/signup").cookie(p.session).with(csrf()).contentType("application/json").content(body)).andReturn().getResponse().getStatus()));assertEquals(List.of(200,400),results.stream().map(f->{try{return f.get();}catch(Exception e){throw new AssertionError(e);}}).sorted().toList());}assertEquals(1,db.sql("SELECT count(*) FROM users WHERE username=:name").param("name",name).query(Integer.class).single());
    }
    @Test void successfulResetEndsEveryRememberedSessionAndConsumesTheProof()throws Exception{
        String name=user(),address=name+"@example.test";create(name,address);Cookie one=login(name,password),two=login(name,password);db.sql("DELETE FROM auth_rate_limits WHERE bucket LIKE 'email_resend:%'").update();var p=issue("RESET",name,address);String changed="new synthetic password";mvc.perform(post("/api/v1/auth/password/reset").cookie(p.session).with(csrf()).contentType("application/json").content(json.writeValueAsString(resetBody(p,changed)))).andExpect(status().isOk());for(var cookie:List.of(one,two))mvc.perform(get("/api/v1/account").cookie(cookie)).andExpect(status().isUnauthorized());mvc.perform(post("/api/v1/auth/password/reset").session(new MockHttpSession()).with(csrf()).contentType("application/json").content(json.writeValueAsString(resetBody(p,password)))).andExpect(status().isBadRequest());assertNotNull(login(name,changed));
        while(outbox.dispatchOne()){}assertTrue(mailbox.messages.stream().anyMatch(m->m.purpose()==com.moapan.identity.domain.EmailPurpose.PASSWORD_CHANGED));assertTrue(mailbox.messages.stream().noneMatch(m->m.text().contains(changed)||m.text().contains(password)));
    }
    @Test void unknownAndKnownResetRequestsHaveSamePublicShapeButUnknownProofIsUseless()throws Exception{
        String name=user(),address=name+"@example.test";create(name,address);var fake=issue("RESET",user(),"unknown_"+user()+"@example.test");mvc.perform(post("/api/v1/auth/password/reset").cookie(fake.session).with(csrf()).contentType("application/json").content(json.writeValueAsString(resetBody(fake,password)))).andExpect(status().isBadRequest());assertNotNull(login(name,password));
        String other=user(),otherAddress=other+"@example.test";var session=new MockHttpSession();String body=json.writeValueAsString(Map.of("username",other,"email",otherAddress));var response=mvc.perform(post("/api/v1/auth/password/reset-code").session(session).with(csrf()).contentType("application/json").content(body)).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();assertEquals(Set.of("challengeId","expiresInSeconds","resendAfterSeconds"),json.readTree(response).propertyStream().map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()));
    }
    @Test void transportFailuresRetryOnlyThreeTimesAndRemoveSensitivePayload()throws Exception{
        mailbox.fail=true;String name=user(),address=name+"@example.test";mvc.perform(post("/api/v1/auth/email/signup-code").session(new MockHttpSession()).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("username",name,"email",address,"emailPolicyAccepted",true,"emailPolicyVersion","email-1")))).andExpect(status().isAccepted());for(int i=0;i<3;i++){db.sql("UPDATE email_outbox SET available_at=now()-interval '1 second'").update();assertTrue(outbox.dispatchOne());}assertEquals("FAILED",db.sql("SELECT status FROM email_outbox").query(String.class).single());assertEquals(0,db.sql("SELECT count(*) FROM email_outbox WHERE envelope_cipher IS NOT NULL").query(Integer.class).single());assertFalse(outbox.dispatchOne());
    }
    @Test void unsolicitedPurposeIsNotRepresentableAndConsentIsRequiredBeforeSending()throws Exception{
        assertThrows(IllegalArgumentException.class,()->com.moapan.identity.domain.EmailPurpose.valueOf("MARKETING"));for(boolean consent:List.of(false))mvc.perform(post("/api/v1/auth/email/signup-code").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("username",user(),"email","nobody@example.test","emailPolicyAccepted",consent,"emailPolicyVersion","email-1")))).andExpect(status().isBadRequest());assertEquals(0,db.sql("SELECT count(*) FROM email_outbox").query(Integer.class).single());
    }    @Test void legacyAccountCanEnrollEmailWithoutLosingIdentityOrRoleAndEverySessionEnds()throws Exception{
        String name=user(),address=name+"@example.test";UUID id=UUID.randomUUID();db.sql("INSERT INTO users(id,username,password_hash) VALUES(:id,:name,:hash)").param("id",id).param("name",name).param("hash",encoder.encode(password)).update();Cookie login=login(name,password);
        mvc.perform(get("/api/v1/account/email").cookie(login)).andExpect(jsonPath("$.verified").value(false)).andExpect(jsonPath("$.email").isEmpty());
        var body=new LinkedHashMap<String,Object>(Map.of("currentPassword","wrong synthetic password","email",address,"emailPolicyAccepted",true,"emailPolicyVersion","email-1"));
        mvc.perform(post("/api/v1/account/email/code").cookie(login).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isForbidden());assertEquals(0,db.sql("SELECT count(*) FROM email_outbox").query(Integer.class).single());
        body.put("currentPassword",password);var issue=mvc.perform(post("/api/v1/account/email/code").cookie(login).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isAccepted()).andReturn();while(outbox.dispatchOne()){}
        String proof=json.writeValueAsString(Map.of("challengeId",json.readTree(issue.getResponse().getContentAsString()).path("challengeId").asText(),"code",mailbox.code(address),"emailPolicyAccepted",true,"emailPolicyVersion","email-1"));
        mvc.perform(put("/api/v1/account/email").cookie(login).with(csrf()).contentType("application/json").content(proof)).andExpect(status().isOk());mvc.perform(get("/api/v1/account").cookie(login)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/account/email").cookie(login(name,password))).andExpect(jsonPath("$.email").value(address));assertEquals(id,db.sql("SELECT id FROM users WHERE username=:name").param("name",name).query(UUID.class).single());assertEquals("USER",db.sql("SELECT role FROM users WHERE id=:id").param("id",id).query(String.class).single());
    }
    @Test void passwordChangeInvalidatesPendingResetAndQueuesOnlySecurityNotice()throws Exception{
        String name=user(),address=name+"@example.test";UUID id=create(name,address);db.sql("DELETE FROM auth_rate_limits WHERE bucket LIKE 'email_resend:%'").update();var proof=issue("RESET",name,address);Cookie cookie=login(name,password);
        mvc.perform(patch("/api/v1/account/password").cookie(cookie).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("currentPassword",password,"newPassword","newer synthetic password")))).andExpect(status().isOk());assertEquals(0,db.sql("SELECT count(*) FROM email_challenges WHERE account_id=:id").param("id",id).query(Integer.class).single());
        mvc.perform(post("/api/v1/auth/password/reset").cookie(proof.session).with(csrf()).contentType("application/json").content(json.writeValueAsString(resetBody(proof,password)))).andExpect(status().isBadRequest());while(outbox.dispatchOne()){}assertTrue(mailbox.messages.stream().anyMatch(m->m.purpose()==com.moapan.identity.domain.EmailPurpose.PASSWORD_CHANGED));
    }
    @Test void resendAndCsrfAreEnforcedBeforeAnotherMailIsQueued()throws Exception{
        String name=user(),address=name+"@example.test";var p=issue("SIGNUP",name,address);String body=json.writeValueAsString(Map.of("username",name,"email",address,"emailPolicyAccepted",true,"emailPolicyVersion","email-1"));
        mvc.perform(post("/api/v1/auth/email/signup-code").cookie(p.session).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/email/signup-code").cookie(p.session).with(csrf()).contentType("application/json").content(body)).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));assertEquals(1,mailbox.messages.size());
    }
    @Test void expiredQueueIsNotSentAndSensitiveRecordsArePurged()throws Exception{
        String name=user();String body=json.writeValueAsString(Map.of("username",name,"email",name+"@example.test","emailPolicyAccepted",true,"emailPolicyVersion","email-1"));mvc.perform(post("/api/v1/auth/email/signup-code").with(csrf()).contentType("application/json").content(body)).andExpect(status().isAccepted());db.sql("UPDATE email_challenges SET expires_at=now()-interval '1 second'").update();outbox.cleanup();assertFalse(outbox.dispatchOne());assertTrue(mailbox.messages.isEmpty());assertEquals(0,db.sql("SELECT count(*) FROM email_outbox").query(Integer.class).single());
    }    @Test void resendLocksAccountBeforeReplacingChallengeWhenCredentialUpdateHoldsAccount()throws Exception{
        String name=user(),address=name+"@example.test";UUID id=create(name,address);db.sql("DELETE FROM auth_rate_limits WHERE bucket LIKE 'email_resend:%'").update();var proof=issue("RESET",name,address);db.sql("DELETE FROM auth_rate_limits WHERE bucket LIKE 'email_resend:%'").update();
        try(var connection=dataSource.getConnection();var pool=Executors.newSingleThreadExecutor()){
            connection.setAutoCommit(false);
            try(var lock=connection.prepareStatement("SELECT id FROM users WHERE id=? FOR UPDATE")){lock.setObject(1,id);try(var rows=lock.executeQuery()){assertTrue(rows.next());}}
            var pending=pool.submit(()->mvc.perform(post("/api/v1/auth/password/reset-code").cookie(proof.session).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("username",name,"email",address)))).andReturn());
            try{
                boolean blocked=false;for(int i=0;i<150&&!blocked;i++){blocked=db.sql("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE '%email_verified_at FROM users%FOR UPDATE%')").query(Boolean.class).single();if(!blocked)Thread.sleep(20);}assertTrue(blocked,"Reissue must wait on account lock before touching its challenge");
                try(var lock=connection.prepareStatement("SELECT id FROM email_challenges WHERE id=? FOR UPDATE NOWAIT")){lock.setObject(1,proof.id);try(var rows=lock.executeQuery()){assertTrue(rows.next(),"Challenge remains lockable while reissue waits for its account");}}
                try(var update=connection.prepareStatement("UPDATE users SET auth_version=auth_version+1 WHERE id=?")){update.setObject(1,id);assertEquals(1,update.executeUpdate());}
            }finally{connection.commit();}
            assertEquals(202,pending.get(10,TimeUnit.SECONDS).getResponse().getStatus());
        }
        mvc.perform(post("/api/v1/auth/password/reset").cookie(proof.session).with(csrf()).contentType("application/json").content(json.writeValueAsString(resetBody(proof,password)))).andExpect(status().isBadRequest());
    }    @Test void deletingAccountRemovesEmailChallengesOutboxAndAccountAddressLimits()throws Exception{
        String name=user(),address=name+"@example.test";UUID id=create(name,address);Cookie cookie=login(name,password);db.sql("DELETE FROM auth_rate_limits WHERE bucket LIKE 'email_resend:%'").update();issue("RESET",name,address);
        mvc.perform(delete("/api/v1/account").cookie(cookie).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("currentPassword",password)))).andExpect(status().isOk());
        for(String table:List.of("email_challenges","email_outbox"))assertEquals(0,db.sql("SELECT count(*) FROM "+table+" WHERE account_id=:id").param("id",id).query(Integer.class).single());
        String key=secrets.fingerprint("address",address);assertEquals(0,db.sql("SELECT count(*) FROM auth_rate_limits WHERE bucket IN (:a,:b,:c)").param("a","email_send_address:"+key).param("b","email_resend:"+key).param("c","email_bind:"+id).query(Integer.class).single());mvc.perform(get("/api/v1/account").cookie(cookie)).andExpect(status().isUnauthorized());
    }    @Test void realSignupProofStillEnforcesTheApprovedPasswordBoundaries()throws Exception{
        String name=user();var proof=issue("SIGNUP",name,name+"@example.test");var body=signupBody(proof);
        for(String invalid:List.of("abcde","a".repeat(129))){body.put("password",invalid);mvc.perform(post("/api/v1/auth/signup").cookie(proof.session).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isBadRequest());}
        assertEquals(0,db.sql("SELECT attempts FROM email_challenges WHERE id=:id").param("id",proof.id).query(Integer.class).single());body.put("password","abcdef");mvc.perform(post("/api/v1/auth/signup").cookie(proof.session).with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isOk());assertNotNull(login(name,"abcdef"));
    }
    @Test void fullOutboxFailsClosedAndDoesNotLeaveAChallengeWithoutItsMail()throws Exception{
        db.sql("INSERT INTO email_outbox(id,status,expires_at) SELECT gen_random_uuid(),'SENT',now()+interval '1 hour' FROM generate_series(1,10000)").update();String name=user();
        mvc.perform(post("/api/v1/auth/email/signup-code").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("username",name,"email",name+"@example.test","emailPolicyAccepted",true,"emailPolicyVersion","email-1")))).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error").value("mail_capacity"));assertEquals(0,db.sql("SELECT count(*) FROM email_challenges").query(Integer.class).single());assertEquals(10000,db.sql("SELECT count(*) FROM email_outbox").query(Integer.class).single());
    }
}
