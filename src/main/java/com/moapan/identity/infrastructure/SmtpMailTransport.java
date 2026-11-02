package com.moapan.identity.infrastructure;

import com.moapan.identity.application.MailTransport;
import jakarta.mail.internet.InternetAddress;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import javax.net.ssl.*;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/** Operator-configured SMTP only. STARTTLS is mandatory, with hostname verification. */
@Component
public final class SmtpMailTransport implements MailTransport {
    private final MailSettings settings;private final JavaMailSenderImpl sender;
    public SmtpMailTransport(MailSettings settings){this.settings=settings;sender=new JavaMailSenderImpl();sender.setHost(settings.host);sender.setPort(settings.port);sender.setDefaultEncoding("UTF-8");sender.setUsername(settings.username);sender.setPassword(settings.password);
        var p=sender.getJavaMailProperties();p.put("mail.debug","false");p.put("mail.smtp.auth",Boolean.toString(!settings.username.isBlank()));p.put("mail.smtp.connectiontimeout","3000");p.put("mail.smtp.timeout","3000");p.put("mail.smtp.writetimeout","3000");p.put("mail.smtp.ssl.checkserveridentity","true");p.put("mail.smtp.ssl.protocols","TLSv1.3 TLSv1.2");
        p.put("mail.smtp.ssl.enable",Boolean.toString(settings.tls.equals("SMTPS")));p.put("mail.smtp.starttls.enable",Boolean.toString(settings.tls.equals("STARTTLS")));p.put("mail.smtp.starttls.required",Boolean.toString(settings.tls.equals("STARTTLS")));
        if(!settings.caFile.isBlank())try(var in=Files.newInputStream(Path.of(settings.caFile))){var ca=CertificateFactory.getInstance("X.509").generateCertificate(in);var store=KeyStore.getInstance(KeyStore.getDefaultType());store.load(null,null);store.setCertificateEntry("operator-smtp-ca",ca);var managers=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());managers.init(store);var context=SSLContext.getInstance("TLS");context.init(null,managers.getTrustManagers(),null);p.put("mail.smtp.ssl.socketFactory",context.getSocketFactory());}catch(Exception e){throw new IllegalStateException("SMTP CA configuration rejected");}
    }
    @Override public void send(Envelope message){if(!settings.enabled)throw new IllegalStateException("SMTP unavailable");try{if(message.purpose()==null||message.id()==null)throw new IllegalArgumentException();String recipient=com.moapan.identity.domain.CredentialPolicy.email(message.recipient());var mail=sender.createMimeMessage();mail.setFrom(new InternetAddress(settings.from));mail.setRecipients(jakarta.mail.Message.RecipientType.TO,InternetAddress.parse(recipient,true));mail.setSubject(message.subject(),"UTF-8");mail.setText(message.text(),"UTF-8");mail.setHeader("X-DamGoX2-Message-ID",message.id().toString());sender.send(mail);}catch(Exception e){throw new IllegalStateException("SMTP delivery unavailable");}}
}
