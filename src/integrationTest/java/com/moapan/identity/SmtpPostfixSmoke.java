package com.moapan.identity;

import com.moapan.identity.application.MailTransport;
import com.moapan.identity.domain.EmailPurpose;
import com.moapan.identity.infrastructure.*;
import java.nio.file.Path;
import java.util.UUID;

/** Explicit local-only smoke task: never sends to a real user's mailbox. */
public final class SmtpPostfixSmoke {
    private SmtpPostfixSmoke(){}
    public static void main(String[] args){
        var settings=new MailSettings(true,"localhost",2525,"no-reply@example.test","","","STARTTLS",Path.of(".local/mail/tls/ca.crt").toAbsolutePath().toString(),"","MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        new SmtpMailTransport(settings).send(new MailTransport.Envelope(UUID.randomUUID(),EmailPurpose.SIGNUP_CODE,"probe@example.test","DamGoX2 synthetic SMTP verification","Synthetic verification delivery only. No real user or password."));
        System.out.println("Synthetic SMTP accepted with mandatory STARTTLS and certificate/hostname validation; inbox delivery is checked separately.");
    }
}
