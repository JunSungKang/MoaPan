package com.moapan.identity.application;

import java.util.UUID;

public interface MailTransport {
    record Envelope(UUID id,com.moapan.identity.domain.EmailPurpose purpose,String recipient,String subject,String text) {}
    void send(Envelope message);
}
