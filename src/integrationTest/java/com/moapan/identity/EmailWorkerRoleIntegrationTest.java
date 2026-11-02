package com.moapan.identity;

import com.moapan.identity.application.*;
import com.moapan.identity.domain.EmailPurpose;
import com.moapan.identity.infrastructure.EmailWorker;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;

/** Worker/admin-cli use a non-web context; interactive identity beans must not leak into it. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={"spring.flyway.enabled=true","moapan.secret=synthetic-only-test-secret-32-characters","moapan.public-base-url=http://localhost","moapan.mail.enabled=true","moapan.mail.host=localhost","moapan.mail.from=no-reply@example.test","moapan.mail.data-key="+EmailTestSupport.KEY})
@Import(EmailTestSupport.Config.class)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class EmailWorkerRoleIntegrationTest {
    static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>(org.testcontainers.utility.DockerImageName.parse("postgres@sha256:edb0c08dc33befd0262ab3b5cf1c4f3f8cfd8b09e88c05b45adee29d6761ddb6").asCompatibleSubstituteFor("postgres"));
    static{postgres.start();org.flywaydb.core.Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).cleanDisabled(true).load().migrate();}
    @DynamicPropertySource static void database(DynamicPropertyRegistry r){r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);}
    @Autowired ApplicationContext context;@Autowired EmailOutbox outbox;@Autowired EmailTestSupport.Mailbox mailbox;
    @Test void nonWebRoleStartsMailConsumerWithoutInteractiveAuthentication(){
        assertTrue(context.getBeansOfType(EmailIdentity.class).isEmpty());assertTrue(context.getBeansOfType(IdentityPasswords.class).isEmpty());assertNotNull(context.getBean(EmailWorker.class));assertFalse(context instanceof org.springframework.web.context.WebApplicationContext);
        outbox.enqueue(null,null,"probe@example.test",EmailPurpose.PASSWORD_CHANGED,null,Instant.now().plusSeconds(300));assertTrue(outbox.dispatchOne());assertEquals(1,mailbox.messages.size());assertEquals(EmailPurpose.PASSWORD_CHANGED,mailbox.messages.getFirst().purpose());
    }
}
