package com.moapan.identity;

import com.moapan.identity.application.*;
import com.moapan.identity.domain.EmailPurpose;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.json.JsonMapper;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Synthetic mailbox only; no bypass of the production challenge, encryption, outbox or consumption paths. */
public final class EmailTestSupport {
    public static final String KEY="MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    public static final class Mailbox implements MailTransport {
        public final List<Envelope> messages=new CopyOnWriteArrayList<>();
        public volatile boolean fail;
        @Override public void send(Envelope message){if(fail)throw new IllegalStateException("synthetic transport unavailable");messages.add(message);}
        public String code(String address){return messages.reversed().stream().filter(m->m.recipient().equals(address)&&java.util.Set.of(EmailPurpose.SIGNUP_CODE,EmailPurpose.EMAIL_BIND_CODE,EmailPurpose.PASSWORD_RESET_CODE).contains(m.purpose())).findFirst().map(m->{var match=java.util.regex.Pattern.compile("인증번호는 ([0-9]{6})").matcher(m.text());if(!match.find())throw new AssertionError("Synthetic code absent");return match.group(1);}).orElseThrow();}
    }
    @TestConfiguration public static class Config {
        @Bean @Primary Mailbox fixtureMailbox(){return new Mailbox();}
    }
    public static MvcResult signup(MockMvc mvc,EmailOutbox outbox,Mailbox mailbox,String user,String password)throws Exception {
        String email="fixture_"+UUID.randomUUID().toString().replace("-","")+"@example.test";var session=new MockHttpSession();var json=JsonMapper.builder().build();
        var issued=mvc.perform(post("/api/v1/auth/email/signup-code").session(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("username",user,"email",email,"emailPolicyAccepted",true,"emailPolicyVersion","email-1")))).andExpect(status().isAccepted()).andReturn();
        String issue=issued.getResponse().getContentAsString();
        for(int i=0;i<100&&mailbox.messages.stream().noneMatch(m->m.recipient().equals(email));i++)if(!outbox.dispatchOne())throw new AssertionError("Synthetic outbox empty");
        return mvc.perform(post("/api/v1/auth/signup").cookie(issued.getResponse().getCookie("SESSION")).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("username",user,"password",password,"termsAccepted",true,"termsVersion","terms-1","email",email,"challengeId",json.readTree(issue).path("challengeId").asText(),"code",mailbox.code(email),"emailPolicyAccepted",true,"emailPolicyVersion","email-1")))).andExpect(status().isOk()).andReturn();
    }
}
