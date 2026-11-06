package com.moapan.identity.infrastructure;

import com.moapan.identity.application.EmailOutbox;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

@Component
public final class EmailWorker {
    private final EmailOutbox outbox;private final MailSettings settings;
    public EmailWorker(EmailOutbox outbox,MailSettings settings){this.outbox=outbox;this.settings=settings;}
    @Scheduled(fixedDelay=1000) public void tick(){if(settings.enabled&&"worker".equals(System.getenv("DAMGOX2_ROLE"))){outbox.cleanup();outbox.dispatchOne();}}
}
